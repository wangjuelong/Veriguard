package io.veriguard.rest.agent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.IntegrationTest;
import io.veriguard.crypto.Ed25519SignatureService;
import io.veriguard.crypto.X25519BoxService;
import io.veriguard.utils.mockUser.WithMockUser;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Integration tests for {@link AgentTaskQueueApi} — poll + result with signature verification. */
@DirtiesContext
@Transactional
@WithMockUser(isAdmin = true)
class AgentTaskQueueApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private Ed25519SignatureService ed25519;
  @Autowired private X25519BoxService x25519;
  @Autowired private AgentTaskQueueService queueService;

  private static final String INIT_BODY =
      """
      {
        "display_name": "test-agent-task-queue",
        "capabilities": ["http_attack"],
        "allowed_modes": ["A"]
      }
      """;

  /** Helper — perform init + register, return registered agent context. */
  private RegisteredAgent registerAgent() throws Exception {
    MvcResult initResult =
        mockMvc
            .perform(
                post(AgentOnboardApi.ONBOARD_INIT_URI)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(INIT_BODY))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode initOut = objectMapper.readTree(initResult.getResponse().getContentAsString());
    String agentId = initOut.get("agent_id").asText();
    String token = initOut.get("onboard_token").asText();

    Ed25519SignatureService.Ed25519KeyPair signPair = ed25519.generate();
    X25519BoxService.X25519KeyPair encPair = x25519.generate();
    byte[] signedBytes =
        AgentOnboardingService.registrationSignedBytes(
            token, signPair.publicKey(), encPair.publicKey());
    byte[] sig = ed25519.sign(signPair.privateKey(), signedBytes);

    String registerBody =
        objectMapper.writeValueAsString(
            new AgentDtos.RegisterInput(
                agentId,
                token,
                Base64.getEncoder().encodeToString(signPair.publicKey()),
                Base64.getEncoder().encodeToString(encPair.publicKey()),
                java.util.List.of("http_attack"),
                Base64.getEncoder().encodeToString(sig)));

    mockMvc
        .perform(
            post(AgentOnboardApi.ONBOARD_REGISTER_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
        .andExpect(status().isOk());

    return new RegisteredAgent(agentId, token, signPair);
  }

  private record RegisteredAgent(
      String agentId, String onboardToken, Ed25519SignatureService.Ed25519KeyPair signPair) {}

  @Test
  void poll_returns_empty_when_no_tasks() throws Exception {
    RegisteredAgent agent = registerAgent();

    String timestamp = String.valueOf(System.currentTimeMillis());
    byte[] sig =
        ed25519.sign(
            agent.signPair().privateKey(),
            timestamp.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    mockMvc
        .perform(
            get(AgentTaskQueueApi.POLL_URI)
                .param("agent_id", agent.agentId())
                .header(AgentTaskQueueApi.TIMESTAMP_HEADER, timestamp)
                .header(AgentTaskQueueApi.SIGNATURE_HEADER, Base64.getEncoder().encodeToString(sig))
                .header("X-Veriguard-Onboard-Token", agent.onboardToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.tasks").isEmpty());
  }

  @Test
  void poll_with_bad_signature_returns_401() throws Exception {
    RegisteredAgent agent = registerAgent();

    String timestamp = String.valueOf(System.currentTimeMillis());
    byte[] sig =
        ed25519.sign(
            agent.signPair().privateKey(),
            timestamp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    sig[0] ^= 0x01;

    mockMvc
        .perform(
            get(AgentTaskQueueApi.POLL_URI)
                .param("agent_id", agent.agentId())
                .header(AgentTaskQueueApi.TIMESTAMP_HEADER, timestamp)
                .header(AgentTaskQueueApi.SIGNATURE_HEADER, Base64.getEncoder().encodeToString(sig))
                .header("X-Veriguard-Onboard-Token", agent.onboardToken()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void poll_without_signature_returns_401() throws Exception {
    RegisteredAgent agent = registerAgent();

    mockMvc
        .perform(get(AgentTaskQueueApi.POLL_URI).param("agent_id", agent.agentId()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void poll_returns_pending_tasks() throws Exception {
    RegisteredAgent agent = registerAgent();
    queueService.enqueueTask(
        agent.agentId(),
        new AgentDtos.AgentTask(
            "T-1",
            "http_attack",
            "web_attack",
            "{\"url\":\"http://target/x\"}",
            java.util.List.of("PREVENTION")));

    String timestamp = String.valueOf(System.currentTimeMillis());
    byte[] sig =
        ed25519.sign(
            agent.signPair().privateKey(),
            timestamp.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    mockMvc
        .perform(
            get(AgentTaskQueueApi.POLL_URI)
                .param("agent_id", agent.agentId())
                .header(AgentTaskQueueApi.TIMESTAMP_HEADER, timestamp)
                .header(AgentTaskQueueApi.SIGNATURE_HEADER, Base64.getEncoder().encodeToString(sig))
                .header("X-Veriguard-Onboard-Token", agent.onboardToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tasks[0].task_id").value("T-1"))
        .andExpect(jsonPath("$.tasks[0].capability").value("http_attack"));
  }

  @Test
  void result_with_valid_signature_returns_200() throws Exception {
    RegisteredAgent agent = registerAgent();

    String taskId = "T-test-1";
    String resultBody =
        """
        {
          "status": "completed",
          "exit_code": 0,
          "stdout": "ok",
          "stderr": "",
          "started_at": "2026-05-14T10:00:00Z",
          "finished_at": "2026-05-14T10:00:05Z"
        }
        """;

    String timestamp = String.valueOf(System.currentTimeMillis());
    byte[] canonicalBody =
        canonicalResultBytes(
            taskId, "completed", 0, "ok", "", "2026-05-14T10:00:00Z", "2026-05-14T10:00:05Z", "");
    byte[] signedInput = concatTimestampBody(timestamp, canonicalBody);
    byte[] sig = ed25519.sign(agent.signPair().privateKey(), signedInput);

    mockMvc
        .perform(
            post("/api/agent/task/" + taskId + "/result")
                .with(csrf())
                .param("agent_id", agent.agentId())
                .header(AgentTaskQueueApi.TIMESTAMP_HEADER, timestamp)
                .header(AgentTaskQueueApi.SIGNATURE_HEADER, Base64.getEncoder().encodeToString(sig))
                .header("X-Veriguard-Onboard-Token", agent.onboardToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(resultBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("accepted"));
  }

  /**
   * Verify that tampering with {@code stdout} after signing breaks verification — proves the
   * Ed25519 signature covers all 7 ResultInput fields (+ task_id), not just status/exit_code.
   */
  @Test
  void tamperedStdoutFailsSignatureVerification() throws Exception {
    RegisteredAgent agent = registerAgent();

    String taskId = "T-tamper-1";
    String originalStdout = "ok";
    // Sign over the original (untampered) canonical bytes
    byte[] canonicalBody =
        canonicalResultBytes(
            taskId,
            "completed",
            0,
            originalStdout,
            "",
            "2026-05-14T10:00:00Z",
            "2026-05-14T10:00:05Z",
            "");
    String timestamp = String.valueOf(System.currentTimeMillis());
    byte[] signedInput = concatTimestampBody(timestamp, canonicalBody);
    byte[] sig = ed25519.sign(agent.signPair().privateKey(), signedInput);

    // POST a body whose stdout differs from what was signed — server-side canonicalization will
    // reconstruct different bytes → Ed25519 verify must fail → 401.
    String tamperedBody =
        """
        {
          "status": "completed",
          "exit_code": 0,
          "stdout": "HACKED",
          "stderr": "",
          "started_at": "2026-05-14T10:00:00Z",
          "finished_at": "2026-05-14T10:00:05Z"
        }
        """;

    mockMvc
        .perform(
            post("/api/agent/task/" + taskId + "/result")
                .with(csrf())
                .param("agent_id", agent.agentId())
                .header(AgentTaskQueueApi.TIMESTAMP_HEADER, timestamp)
                .header(AgentTaskQueueApi.SIGNATURE_HEADER, Base64.getEncoder().encodeToString(sig))
                .header("X-Veriguard-Onboard-Token", agent.onboardToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(tamperedBody))
        .andExpect(status().isUnauthorized());
  }

  /**
   * Mirror of {@link AgentTaskQueueApi#canonicalResultBytes} on the agent side — Rust agent
   * eventually replicates this same canonical form (sorted-keys, no-whitespace JSON).
   */
  private byte[] canonicalResultBytes(
      String taskId,
      String status,
      int exitCode,
      String stdout,
      String stderr,
      String startedAt,
      String finishedAt,
      String errorMessage)
      throws Exception {
    ObjectMapper canonical =
        objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    ObjectNode node = canonical.getNodeFactory().objectNode();
    node.put("error_message", errorMessage);
    node.put("exit_code", exitCode);
    node.put("finished_at", finishedAt);
    node.put("started_at", startedAt);
    node.put("status", status);
    node.put("stderr", stderr);
    node.put("stdout", stdout);
    node.put("task_id", taskId);
    return canonical.writeValueAsBytes(node);
  }

  private static byte[] concatTimestampBody(String timestamp, byte[] body) {
    byte[] tsBytes = timestamp.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] signedInput = new byte[tsBytes.length + body.length];
    System.arraycopy(tsBytes, 0, signedInput, 0, tsBytes.length);
    System.arraycopy(body, 0, signedInput, tsBytes.length, body.length);
    return signedInput;
  }

  @Test
  void result_with_bad_signature_returns_401() throws Exception {
    RegisteredAgent agent = registerAgent();

    String taskId = "T-bad";
    String resultBody =
        """
        {"status": "completed", "exit_code": 0, "stdout": "", "stderr": "",
         "started_at": "2026-05-14T10:00:00Z", "finished_at": "2026-05-14T10:00:05Z"}
        """;

    String timestamp = String.valueOf(System.currentTimeMillis());
    byte[] badSig = new byte[64];

    mockMvc
        .perform(
            post("/api/agent/task/" + taskId + "/result")
                .with(csrf())
                .param("agent_id", agent.agentId())
                .header(AgentTaskQueueApi.TIMESTAMP_HEADER, timestamp)
                .header(
                    AgentTaskQueueApi.SIGNATURE_HEADER, Base64.getEncoder().encodeToString(badSig))
                .header("X-Veriguard-Onboard-Token", agent.onboardToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(resultBody))
        .andExpect(status().isUnauthorized());
  }
}
