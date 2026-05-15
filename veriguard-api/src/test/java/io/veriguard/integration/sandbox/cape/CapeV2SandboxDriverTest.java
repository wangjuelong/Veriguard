package io.veriguard.integration.sandbox.cape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.veriguard.integration.sandbox.SandboxIntegrationException;
import io.veriguard.integration.sandbox.SandboxIntegrationException.ReasonCode;
import io.veriguard.integration.sandbox.dto.MachineSnapshot;
import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import io.veriguard.integration.sandbox.dto.SandboxTaskStatus;
import io.veriguard.integration.sandbox.dto.SubmissionResult;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CapeV2SandboxDriver}.
 *
 * <p>Stands up a {@link HttpServer} on an ephemeral localhost port and routes the 4 CAPEv2
 * endpoints to per-test handlers. The driver is constructed against that local URL with a real JDK
 * {@link HttpClient}, so the wire path (headers, multipart body, status-code mapping) is exercised
 * end-to-end without ever touching a real CAPEv2 deployment.
 *
 * <p>All credentials below are <strong>synthetic test fixtures</strong> — no real dev / prod values
 * appear in this file.
 */
class CapeV2SandboxDriverTest {

  private HttpServer server;
  private String baseUrl;

  /** Per-path handlers — each test wires the shapes it needs. */
  private final Map<String, HttpHandler> handlers = new ConcurrentHashMap<>();

  /** Captured auth header from the last request, for auth-header assertions. */
  private final AtomicReference<String> capturedAuthHeader = new AtomicReference<>();

  /** Captured raw request body bytes from the last POST. */
  private final AtomicReference<byte[]> capturedRequestBody = new AtomicReference<>();

  /** Captured Content-Type from the last request. */
  private final AtomicReference<String> capturedContentType = new AtomicReference<>();

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          try (InputStream in = exchange.getRequestBody()) {
            capturedRequestBody.set(in.readAllBytes());
          }
          HttpHandler routed = handlers.get(exchange.getRequestURI().getPath());
          if (routed == null) {
            byte[] body =
                ("{\"error\":\"no handler for " + exchange.getRequestURI().getPath() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
          }
          routed.handle(exchange);
        });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  // ---- helpers --------------------------------------------------------------

  private CapeV2SandboxDriver driver(CapeV2SandboxDriverProperties props) {
    return new CapeV2SandboxDriver(props, HttpClient.newHttpClient(), new ObjectMapper());
  }

  private CapeV2SandboxDriverProperties propsBuilder() {
    return new CapeV2SandboxDriverProperties(
        baseUrl, null, null, null, Duration.ofSeconds(2), Duration.ofSeconds(2), null, true);
  }

  private static void respondJson(HttpExchange ex, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(status, bytes.length);
    ex.getResponseBody().write(bytes);
    ex.close();
  }

  // ---- healthCheck ----------------------------------------------------------

  @Test
  void healthCheck_accepts_real_cape_status_payload() {
    handlers.put(
        "/apiv2/cuckoo/status/",
        ex ->
            respondJson(
                ex,
                200,
                """
                {"tasks":{"reported":165,"running":2,"total":167,"completed":0,"pending":0},
                 "version":"1.0","protocol_version":1,"hostname":"Patient0",
                 "machines":{"available":4,"total":5},"tools":["vanilla"]}
                """));
    driver(propsBuilder()).healthCheck();
  }

  @Test
  void healthCheck_protocol_mismatch_when_version_missing() {
    handlers.put("/apiv2/cuckoo/status/", ex -> respondJson(ex, 200, "{\"hostname\":\"x\"}"));
    assertThatThrownBy(() -> driver(propsBuilder()).healthCheck())
        .isInstanceOf(SandboxIntegrationException.class)
        .satisfies(
            t ->
                assertThat(((SandboxIntegrationException) t).getReasonCode())
                    .isEqualTo(ReasonCode.PROTOCOL_MISMATCH));
  }

  @Test
  void healthCheck_auth_failed_on_401() {
    handlers.put(
        "/apiv2/cuckoo/status/", ex -> respondJson(ex, 401, "{\"detail\":\"unauthorized\"}"));
    assertThatThrownBy(() -> driver(propsBuilder()).healthCheck())
        .isInstanceOf(SandboxIntegrationException.class)
        .satisfies(
            t -> {
              SandboxIntegrationException sie = (SandboxIntegrationException) t;
              assertThat(sie.getReasonCode()).isEqualTo(ReasonCode.AUTHENTICATION_FAILED);
              assertThat(sie.getRemoteStatusCode()).isEqualTo(401);
            });
  }

  @Test
  void healthCheck_remote_error_on_500() {
    handlers.put(
        "/apiv2/cuckoo/status/", ex -> respondJson(ex, 500, "{\"error\":\"backend exploded\"}"));
    assertThatThrownBy(() -> driver(propsBuilder()).healthCheck())
        .isInstanceOf(SandboxIntegrationException.class)
        .satisfies(
            t -> {
              SandboxIntegrationException sie = (SandboxIntegrationException) t;
              assertThat(sie.getReasonCode()).isEqualTo(ReasonCode.REMOTE_ERROR);
              assertThat(sie.getRemoteStatusCode()).isEqualTo(500);
            });
  }

  // ---- listMachines ---------------------------------------------------------

  @Test
  void listMachines_parses_wrapped_array() {
    handlers.put(
        "/apiv2/machines/list/",
        ex ->
            respondJson(
                ex,
                200,
                """
                {"machines":[
                  {"name":"win10","label":"win10-x64","platform":"windows",
                   "snapshot":"clean","status":"poweroff"},
                  {"name":"ubuntu","label":"ubuntu-22","platform":"linux",
                   "snapshot":"clean","status":"running"}
                ]}
                """));
    List<MachineSnapshot> result = driver(propsBuilder()).listMachines();
    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("win10");
    assertThat(result.get(0).platform()).isEqualTo("windows");
    assertThat(result.get(0).status()).isEqualTo("poweroff");
    assertThat(result.get(1).label()).isEqualTo("ubuntu-22");
    assertThat(result.get(0).fetchedAt()).isNotNull();
  }

  @Test
  void listMachines_parses_bare_array() {
    handlers.put(
        "/apiv2/machines/list/",
        ex ->
            respondJson(
                ex,
                200,
                """
                [{"name":"only-one","platform":"linux","status":"poweroff"}]
                """));
    List<MachineSnapshot> result = driver(propsBuilder()).listMachines();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("only-one");
  }

  @Test
  void listMachines_protocol_mismatch_when_not_array() {
    handlers.put("/apiv2/machines/list/", ex -> respondJson(ex, 200, "{\"unexpected\":\"shape\"}"));
    assertThatThrownBy(() -> driver(propsBuilder()).listMachines())
        .isInstanceOf(SandboxIntegrationException.class)
        .satisfies(
            t ->
                assertThat(((SandboxIntegrationException) t).getReasonCode())
                    .isEqualTo(ReasonCode.PROTOCOL_MISMATCH));
  }

  // ---- submitSample ---------------------------------------------------------

  @Test
  void submitSample_posts_multipart_and_returns_task_id() {
    handlers.put("/apiv2/tasks/create/file/", ex -> respondJson(ex, 200, "{\"task_id\":42}"));

    SampleSubmissionRequest req =
        new SampleSubmissionRequest(
            "preset-1",
            "RANSOMWARE",
            "sample.exe",
            "abc123",
            new byte[] {0x4D, 0x5A, 0x00, 0x00}, // MZ stub
            "win10",
            120);
    SubmissionResult res = driver(propsBuilder()).submitSample(req);
    assertThat(res.capeTaskId()).isEqualTo(42L);

    String contentType = capturedContentType.get();
    assertThat(contentType).startsWith("multipart/form-data; boundary=");
    String body = new String(capturedRequestBody.get(), StandardCharsets.UTF_8);
    assertThat(body)
        .contains("Content-Disposition: form-data; name=\"file\"; filename=\"sample.exe\"")
        .contains("Content-Type: application/octet-stream")
        .contains("name=\"timeout\"")
        .contains("120")
        .contains("name=\"machine\"")
        .contains("win10");
  }

  @Test
  void submitSample_protocol_mismatch_when_task_id_missing() {
    handlers.put(
        "/apiv2/tasks/create/file/", ex -> respondJson(ex, 200, "{\"oops\":\"no task_id\"}"));
    SampleSubmissionRequest req =
        new SampleSubmissionRequest("p", "MINER", "x.exe", "h", new byte[] {1, 2, 3}, null, null);
    assertThatThrownBy(() -> driver(propsBuilder()).submitSample(req))
        .isInstanceOf(SandboxIntegrationException.class)
        .satisfies(
            t ->
                assertThat(((SandboxIntegrationException) t).getReasonCode())
                    .isEqualTo(ReasonCode.PROTOCOL_MISMATCH));
  }

  @Test
  void submitSample_rejects_empty_content() {
    assertThatThrownBy(
            () ->
                driver(propsBuilder())
                    .submitSample(
                        new SampleSubmissionRequest(
                            "p", "WORM", "x.exe", "h", new byte[0], null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- fetchTaskStatus ------------------------------------------------------

  @Test
  void fetchTaskStatus_maps_running() {
    handlers.put(
        "/apiv2/tasks/view/7/",
        ex -> respondJson(ex, 200, "{\"task\":{\"id\":7,\"status\":\"running\"}}"));
    SandboxTaskStatus s = driver(propsBuilder()).fetchTaskStatus(7);
    assertThat(s.status()).isEqualTo(SandboxTaskStatus.Status.RUNNING);
    assertThat(s.rawRemoteStatus()).isEqualTo("running");
  }

  @Test
  void fetchTaskStatus_maps_reported_to_completed() {
    handlers.put(
        "/apiv2/tasks/view/8/", ex -> respondJson(ex, 200, "{\"task\":{\"status\":\"reported\"}}"));
    assertThat(driver(propsBuilder()).fetchTaskStatus(8).status())
        .isEqualTo(SandboxTaskStatus.Status.COMPLETED);
  }

  @Test
  void fetchTaskStatus_maps_failed_variants() {
    handlers.put(
        "/apiv2/tasks/view/9/",
        ex ->
            respondJson(
                ex, 200, "{\"task\":{\"status\":\"failed_analysis\",\"errors\":\"VM crashed\"}}"));
    SandboxTaskStatus s = driver(propsBuilder()).fetchTaskStatus(9);
    assertThat(s.status()).isEqualTo(SandboxTaskStatus.Status.FAILED);
    assertThat(s.errorMessage()).isEqualTo("VM crashed");
  }

  @Test
  void fetchTaskStatus_unknown_for_unrecognized_string() {
    handlers.put(
        "/apiv2/tasks/view/10/",
        ex -> respondJson(ex, 200, "{\"task\":{\"status\":\"mystery-state\"}}"));
    assertThat(driver(propsBuilder()).fetchTaskStatus(10).status())
        .isEqualTo(SandboxTaskStatus.Status.UNKNOWN);
  }

  @Test
  void fetchTaskStatus_accepts_unwrapped_task_object() {
    handlers.put(
        "/apiv2/tasks/view/11/",
        ex -> respondJson(ex, 200, "{\"status\":\"completed\",\"id\":11}"));
    assertThat(driver(propsBuilder()).fetchTaskStatus(11).status())
        .isEqualTo(SandboxTaskStatus.Status.COMPLETED);
  }

  // ---- auth header (SYNTHETIC test creds only — never use real dev / prod values) ----

  @Test
  void api_token_sets_token_authorization_header() {
    handlers.put("/apiv2/cuckoo/status/", ex -> respondJson(ex, 200, "{\"version\":\"1.0\"}"));
    CapeV2SandboxDriverProperties props =
        new CapeV2SandboxDriverProperties(
            baseUrl,
            "synthetic-test-token-do-not-use",
            null,
            null,
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            null,
            true);
    driver(props).healthCheck();
    assertThat(capturedAuthHeader.get()).isEqualTo("Token synthetic-test-token-do-not-use");
  }

  @Test
  void basic_auth_sets_basic_authorization_header() {
    handlers.put("/apiv2/cuckoo/status/", ex -> respondJson(ex, 200, "{\"version\":\"1.0\"}"));
    CapeV2SandboxDriverProperties props =
        new CapeV2SandboxDriverProperties(
            baseUrl,
            null,
            "test-user",
            "test-pass",
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            null,
            true);
    driver(props).healthCheck();
    // base64("test-user:test-pass") = dGVzdC11c2VyOnRlc3QtcGFzcw==
    assertThat(capturedAuthHeader.get()).isEqualTo("Basic dGVzdC11c2VyOnRlc3QtcGFzcw==");
  }

  @Test
  void no_auth_when_neither_token_nor_basic_set() {
    handlers.put("/apiv2/cuckoo/status/", ex -> respondJson(ex, 200, "{\"version\":\"1.0\"}"));
    driver(propsBuilder()).healthCheck();
    assertThat(capturedAuthHeader.get()).isNull();
  }

  @Test
  void token_wins_over_basic_when_both_set() {
    handlers.put("/apiv2/cuckoo/status/", ex -> respondJson(ex, 200, "{\"version\":\"1.0\"}"));
    CapeV2SandboxDriverProperties props =
        new CapeV2SandboxDriverProperties(
            baseUrl,
            "the-token",
            "ignored-user",
            "ignored-pass",
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            null,
            true);
    driver(props).healthCheck();
    assertThat(capturedAuthHeader.get()).isEqualTo("Token the-token");
  }

  // ---- CAPE 2.5+ envelope handling (wrapped response shape) ----------------
  //
  // CAPE 2.5 wraps every /apiv2/... response in {"error": bool, "data": ...,
  // "error_value": "..."}. These tests verify the driver accepts the wrapped
  // shape AND surfaces server-side "error:true" payloads as REMOTE_ERROR
  // (typical when an endpoint is administratively disabled in api.conf).

  @Test
  void healthCheck_accepts_cape25_wrapped_response() {
    handlers.put(
        "/apiv2/cuckoo/status/",
        ex ->
            respondJson(
                ex,
                200,
                """
                {"error":false,
                 "data":{"version":"2.5","hostname":"cape-host",
                         "machines":{"available":1,"total":1}}}
                """));
    driver(propsBuilder()).healthCheck();
  }

  @Test
  void healthCheck_remote_error_when_endpoint_disabled() {
    // Real shape returned by CAPE 2.5 when `cuckoostatus` is disabled in api.conf.
    handlers.put(
        "/apiv2/cuckoo/status/",
        ex ->
            respondJson(
                ex, 200, "{\"error\":true,\"error_value\":\"Cuckoo Status API is disabled\"}"));
    assertThatThrownBy(() -> driver(propsBuilder()).healthCheck())
        .isInstanceOf(SandboxIntegrationException.class)
        .satisfies(
            t -> {
              SandboxIntegrationException sie = (SandboxIntegrationException) t;
              assertThat(sie.getReasonCode()).isEqualTo(ReasonCode.REMOTE_ERROR);
              assertThat(sie.getMessage()).contains("Cuckoo Status API is disabled");
            });
  }

  @Test
  void listMachines_accepts_cape25_wrapped_array() {
    handlers.put(
        "/apiv2/machines/list/",
        ex ->
            respondJson(
                ex,
                200,
                """
                {"error":false,
                 "data":[{"name":"win10","platform":"windows","status":"poweroff"}]}
                """));
    List<MachineSnapshot> result = driver(propsBuilder()).listMachines();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("win10");
  }

  @Test
  void submitSample_accepts_cape25_wrapped_task_ids_array() {
    // Real shape: {"error":false,"data":{"task_ids":[2],"message":"Task ID 2 has been submitted"}}.
    handlers.put(
        "/apiv2/tasks/create/file/",
        ex ->
            respondJson(
                ex,
                200,
                "{\"error\":false,\"data\":{\"task_ids\":[42],\"message\":\"Task ID 42 has been submitted\"}}"));
    SampleSubmissionRequest req =
        new SampleSubmissionRequest(
            "p", "RANSOMWARE", "x.exe", "h", new byte[] {1, 2, 3}, null, null);
    SubmissionResult res = driver(propsBuilder()).submitSample(req);
    assertThat(res.capeTaskId()).isEqualTo(42L);
  }

  @Test
  void fetchTaskStatus_accepts_cape25_wrapped_task_object() {
    // Real shape: {"error":false,"data":{"id":1,"status":"pending",...}} — `status`
    // sits directly under `data`, NOT nested under `task`.
    handlers.put(
        "/apiv2/tasks/view/77/",
        ex ->
            respondJson(
                ex,
                200,
                "{\"error\":false,\"data\":{\"id\":77,\"status\":\"pending\","
                    + "\"added_on\":\"2026-05-15 06:29:31\"}}"));
    SandboxTaskStatus s = driver(propsBuilder()).fetchTaskStatus(77);
    assertThat(s.status()).isEqualTo(SandboxTaskStatus.Status.QUEUED);
    assertThat(s.rawRemoteStatus()).isEqualTo("pending");
  }

  // ---- misc -----------------------------------------------------------------

  @Test
  void status_mapping_static_helper() {
    assertThat(CapeV2SandboxDriver.mapStatus("pending")).isEqualTo(SandboxTaskStatus.Status.QUEUED);
    assertThat(CapeV2SandboxDriver.mapStatus("queued")).isEqualTo(SandboxTaskStatus.Status.QUEUED);
    assertThat(CapeV2SandboxDriver.mapStatus("running"))
        .isEqualTo(SandboxTaskStatus.Status.RUNNING);
    assertThat(CapeV2SandboxDriver.mapStatus("processing"))
        .isEqualTo(SandboxTaskStatus.Status.RUNNING);
    assertThat(CapeV2SandboxDriver.mapStatus("completed"))
        .isEqualTo(SandboxTaskStatus.Status.COMPLETED);
    assertThat(CapeV2SandboxDriver.mapStatus("reported"))
        .isEqualTo(SandboxTaskStatus.Status.COMPLETED);
    assertThat(CapeV2SandboxDriver.mapStatus("failed_analysis"))
        .isEqualTo(SandboxTaskStatus.Status.FAILED);
    assertThat(CapeV2SandboxDriver.mapStatus("failed_processing"))
        .isEqualTo(SandboxTaskStatus.Status.FAILED);
    assertThat(CapeV2SandboxDriver.mapStatus("failed_reporting"))
        .isEqualTo(SandboxTaskStatus.Status.FAILED);
    assertThat(CapeV2SandboxDriver.mapStatus(null)).isEqualTo(SandboxTaskStatus.Status.UNKNOWN);
    assertThat(CapeV2SandboxDriver.mapStatus("anything-else"))
        .isEqualTo(SandboxTaskStatus.Status.UNKNOWN);
  }

  @Test
  void endpoint_strips_trailing_slash() {
    handlers.put("/apiv2/cuckoo/status/", ex -> respondJson(ex, 200, "{\"version\":\"1.0\"}"));
    CapeV2SandboxDriverProperties props =
        new CapeV2SandboxDriverProperties(
            baseUrl + "/",
            null,
            null,
            null,
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            null,
            true);
    driver(props).healthCheck();
  }
}
