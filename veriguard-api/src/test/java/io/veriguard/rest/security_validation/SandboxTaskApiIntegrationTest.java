package io.veriguard.rest.security_validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.VeriguardSandboxTask;
import io.veriguard.database.repository.VeriguardSandboxTaskRepository;
import io.veriguard.utils.mockUser.WithMockUser;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集成测试 —— sandbox task REST + Quartz-style poll，使用真实 Postgres（veriguard-dev compose）+ 嵌入式
 * HttpServer 伪造 CAPEv2。
 *
 * <p>{@code @DynamicPropertySource} 在 Spring 上下文初始化前注入 {@code veriguard.sandbox.cape.endpoint}，触发
 * {@code CapeV2SandboxDriver} 的 {@code @ConditionalOnProperty}，让它成为真实 SPI 实现（而非默认的 NotImplemented）。
 *
 * <p>测试不依赖 Quartz scheduler —— 显式调用 {@code SandboxTaskService.pollAllActive()} 验证轮询 行为。生产 trigger 在
 * {@code PlatformTriggers#sandboxTaskPollingTrigger} 已用 {@code @Profile("!test")} 关掉测试 profile
 * 下的自动触发。
 */
@DirtiesContext
@Transactional
@WithMockUser(isAdmin = true)
class SandboxTaskApiIntegrationTest extends IntegrationTest {

  private static HttpServer fakeCape;
  private static final Map<String, HttpHandler> HANDLERS = new ConcurrentHashMap<>();

  @BeforeAll
  static void startFakeCape() throws IOException {
    fakeCape = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeCape.createContext(
        "/",
        ex -> {
          HttpHandler routed = HANDLERS.get(ex.getRequestURI().getPath());
          if (routed == null) {
            byte[] body =
                ("{\"error\":\"unknown " + ex.getRequestURI().getPath() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, body.length);
            ex.getResponseBody().write(body);
            ex.close();
            return;
          }
          routed.handle(ex);
        });
    fakeCape.start();
  }

  @AfterAll
  static void stopFakeCape() {
    if (fakeCape != null) {
      fakeCape.stop(0);
    }
  }

  @DynamicPropertySource
  static void capeEndpoint(DynamicPropertyRegistry registry) {
    registry.add(
        "veriguard.sandbox.cape.endpoint",
        () -> "http://127.0.0.1:" + fakeCape.getAddress().getPort());
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SandboxTaskService sandboxTaskService;
  @Autowired private VeriguardSandboxTaskRepository taskRepository;

  @BeforeEach
  void resetHandlers() {
    HANDLERS.clear();
  }

  @Test
  void submit_then_poll_completes_task_end_to_end() throws Exception {
    HANDLERS.put(
        "/apiv2/tasks/create/file/",
        ex -> {
          drain(ex);
          respondJson(ex, 200, "{\"task_id\":4242}");
        });

    MockMultipartFile sample =
        new MockMultipartFile(
            "sample", "fake.exe", "application/octet-stream", "EVIL_SAMPLE".getBytes());

    String body =
        mockMvc
            .perform(
                multipart("/api/sandbox-tasks")
                    .file(sample)
                    .param("sample_type", "RANSOMWARE")
                    .param("target_machine", "win10x64")
                    .param("timeout_seconds", "120")
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sandbox_task_id").exists())
            .andExpect(jsonPath("$.sandbox_task_cape_task_id").value(4242))
            .andExpect(jsonPath("$.sandbox_task_status").value("QUEUED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode created = objectMapper.readTree(body);
    String taskId = created.get("sandbox_task_id").asText();

    HANDLERS.put(
        "/apiv2/tasks/view/4242/",
        ex -> {
          drain(ex);
          respondJson(ex, 200, "{\"task\":{\"id\":4242,\"status\":\"reported\",\"errors\":null}}");
        });

    int transitioned = sandboxTaskService.pollAllActive();
    assertThat(transitioned).isEqualTo(1);

    VeriguardSandboxTask reloaded = taskRepository.findById(taskId).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(VeriguardSandboxTask.Status.COMPLETED);
    assertThat(reloaded.getRawStatus()).isEqualTo("reported");
    assertThat(reloaded.getCompletedAt()).isNotNull();
    assertThat(reloaded.getCapeTaskId()).isEqualTo(4242L);
    assertThat(reloaded.getSampleFilename()).isEqualTo("fake.exe");
  }

  @Test
  void submit_empty_sample_returns_400() throws Exception {
    MockMultipartFile empty =
        new MockMultipartFile("sample", "empty.bin", "application/octet-stream", new byte[0]);
    mockMvc
        .perform(multipart("/api/sandbox-tasks").file(empty).with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.children.sandbox_task_sample_empty").exists());
  }

  @Test
  void refresh_endpoint_triggers_on_demand_poll() throws Exception {
    HANDLERS.put(
        "/apiv2/tasks/create/file/",
        ex -> {
          drain(ex);
          respondJson(ex, 200, "{\"task_id\":77}");
        });

    MockMultipartFile sample =
        new MockMultipartFile("sample", "x.bin", "application/octet-stream", new byte[] {1, 2, 3});
    String body =
        mockMvc
            .perform(multipart("/api/sandbox-tasks").file(sample).with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String taskId = objectMapper.readTree(body).get("sandbox_task_id").asText();

    HANDLERS.put(
        "/apiv2/tasks/view/77/",
        ex -> {
          drain(ex);
          respondJson(ex, 200, "{\"task\":{\"id\":77,\"status\":\"running\",\"errors\":null}}");
        });

    mockMvc
        .perform(post("/api/sandbox-tasks/" + taskId + "/refresh").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sandbox_task_status").value("RUNNING"))
        .andExpect(jsonPath("$.sandbox_task_raw_status").value("running"));
  }

  /**
   * 完整状态机：QUEUED → RUNNING → COMPLETED 跨两次轮询都被正确写入.
   *
   * <p>覆盖 {@link SandboxTaskService#pollAllActive()} 在中间态 (running) 不提前 set completedAt 的关键路径 ——
   * completedAt 仅在终态 (COMPLETED / FAILED) 写入.
   */
  @Test
  void running_then_completed_two_polls() throws Exception {
    HANDLERS.put(
        "/apiv2/tasks/create/file/",
        ex -> {
          drain(ex);
          respondJson(ex, 200, "{\"task_id\":555}");
        });
    MockMultipartFile sample =
        new MockMultipartFile("sample", "two.bin", "application/octet-stream", new byte[] {7});
    String body =
        mockMvc
            .perform(multipart("/api/sandbox-tasks").file(sample).with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String taskId = objectMapper.readTree(body).get("sandbox_task_id").asText();

    // First poll: CAPE reports "running" — DB transitions QUEUED → RUNNING, completedAt null.
    HANDLERS.put(
        "/apiv2/tasks/view/555/",
        ex -> {
          drain(ex);
          respondJson(ex, 200, "{\"task\":{\"id\":555,\"status\":\"running\",\"errors\":null}}");
        });
    int firstTransitions = sandboxTaskService.pollAllActive();
    assertThat(firstTransitions).isEqualTo(1);
    VeriguardSandboxTask afterFirst = taskRepository.findById(taskId).orElseThrow();
    assertThat(afterFirst.getStatus()).isEqualTo(VeriguardSandboxTask.Status.RUNNING);
    assertThat(afterFirst.getRawStatus()).isEqualTo("running");
    assertThat(afterFirst.getCompletedAt()).isNull();
    assertThat(afterFirst.getLastPolledAt()).isNotNull();

    // Second poll: CAPE reports "reported" — DB transitions RUNNING → COMPLETED, completedAt set.
    HANDLERS.put(
        "/apiv2/tasks/view/555/",
        ex -> {
          drain(ex);
          respondJson(ex, 200, "{\"task\":{\"id\":555,\"status\":\"reported\",\"errors\":null}}");
        });
    int secondTransitions = sandboxTaskService.pollAllActive();
    assertThat(secondTransitions).isEqualTo(1);
    VeriguardSandboxTask afterSecond = taskRepository.findById(taskId).orElseThrow();
    assertThat(afterSecond.getStatus()).isEqualTo(VeriguardSandboxTask.Status.COMPLETED);
    assertThat(afterSecond.getRawStatus()).isEqualTo("reported");
    assertThat(afterSecond.getCompletedAt()).isNotNull();
  }

  /**
   * CAPE 返回 {@code failed_analysis} —— driver 映射为 FAILED 终态; rawStatus 透传; completedAt 写入.
   *
   * <p>招标 §8 demo 物料：恶意样本沙箱失败也要能被运维看到.
   */
  @Test
  void cape_failed_status_transitions_to_failed() throws Exception {
    HANDLERS.put(
        "/apiv2/tasks/create/file/",
        ex -> {
          drain(ex);
          respondJson(ex, 200, "{\"task_id\":666}");
        });
    MockMultipartFile sample =
        new MockMultipartFile("sample", "bad.bin", "application/octet-stream", new byte[] {6});
    String body =
        mockMvc
            .perform(multipart("/api/sandbox-tasks").file(sample).with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String taskId = objectMapper.readTree(body).get("sandbox_task_id").asText();

    HANDLERS.put(
        "/apiv2/tasks/view/666/",
        ex -> {
          drain(ex);
          respondJson(
              ex,
              200,
              "{\"task\":{\"id\":666,\"status\":\"failed_analysis\","
                  + "\"errors\":[\"VM crashed\"]}}");
        });
    int transitions = sandboxTaskService.pollAllActive();
    assertThat(transitions).isEqualTo(1);
    VeriguardSandboxTask reloaded = taskRepository.findById(taskId).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(VeriguardSandboxTask.Status.FAILED);
    assertThat(reloaded.getRawStatus()).isEqualTo("failed_analysis");
    assertThat(reloaded.getCompletedAt()).isNotNull();
  }

  /**
   * 批量轮询多个任务彼此独立 —— 一条 done / 一条仍 running, 计数 transitioned=2, 状态各自正确, 不会相互污染.
   *
   * <p>这是 {@link SandboxTaskService#pollAllActive()} 批处理保证的最关键性质.
   */
  @Test
  void batch_poll_multiple_tasks_independently() throws Exception {
    AtomicInteger taskIdCounter = new AtomicInteger(1000);
    HANDLERS.put(
        "/apiv2/tasks/create/file/",
        ex -> {
          drain(ex);
          int id = taskIdCounter.getAndIncrement();
          respondJson(ex, 200, "{\"task_id\":" + id + "}");
        });
    String taskOneId = submitMinimal("a.bin");
    String taskTwoId = submitMinimal("b.bin");

    HANDLERS.put(
        "/apiv2/tasks/view/1000/",
        ex -> {
          drain(ex);
          respondJson(ex, 200, "{\"task\":{\"id\":1000,\"status\":\"reported\",\"errors\":null}}");
        });
    HANDLERS.put(
        "/apiv2/tasks/view/1001/",
        ex -> {
          drain(ex);
          respondJson(ex, 200, "{\"task\":{\"id\":1001,\"status\":\"running\",\"errors\":null}}");
        });

    int transitions = sandboxTaskService.pollAllActive();
    assertThat(transitions).isEqualTo(2);
    VeriguardSandboxTask one = taskRepository.findById(taskOneId).orElseThrow();
    VeriguardSandboxTask two = taskRepository.findById(taskTwoId).orElseThrow();
    assertThat(one.getStatus()).isEqualTo(VeriguardSandboxTask.Status.COMPLETED);
    assertThat(one.getCompletedAt()).isNotNull();
    assertThat(two.getStatus()).isEqualTo(VeriguardSandboxTask.Status.RUNNING);
    assertThat(two.getCompletedAt()).isNull();
  }

  private String submitMinimal(String filename) throws Exception {
    MockMultipartFile sample =
        new MockMultipartFile("sample", filename, "application/octet-stream", new byte[] {1});
    String body =
        mockMvc
            .perform(multipart("/api/sandbox-tasks").file(sample).with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("sandbox_task_id").asText();
  }

  // ---- helpers --------------------------------------------------------------

  private static void drain(HttpExchange ex) throws IOException {
    try (InputStream in = ex.getRequestBody()) {
      in.readAllBytes();
    }
  }

  private static void respondJson(HttpExchange ex, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(status, bytes.length);
    ex.getResponseBody().write(bytes);
    ex.close();
  }
}
