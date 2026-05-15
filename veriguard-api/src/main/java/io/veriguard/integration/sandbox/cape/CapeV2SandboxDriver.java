package io.veriguard.integration.sandbox.cape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.integration.sandbox.SandboxDriver;
import io.veriguard.integration.sandbox.SandboxIntegrationException;
import io.veriguard.integration.sandbox.SandboxIntegrationException.ReasonCode;
import io.veriguard.integration.sandbox.dto.MachineSnapshot;
import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import io.veriguard.integration.sandbox.dto.SandboxTaskStatus;
import io.veriguard.integration.sandbox.dto.SubmissionResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * CAPEv2 sandbox driver (M2) — connects the platform to a running <a
 * href="https://github.com/kevoreilly/CAPEv2">CAPEv2</a> instance via its embedded Django REST API
 * (mounted under {@code /apiv2/} on the same port that serves the Web UI, default 8000 or 8090).
 *
 * <p>Registered as the {@code @Primary} {@link SandboxDriver} only when {@code
 * veriguard.sandbox.cape.endpoint} is populated (Spring {@link ConditionalOnProperty}); otherwise
 * the M1 {@code NotImplementedSandboxDriver} stays in charge — CI / dev hosts that lack CAPEv2
 * reachability still boot.
 *
 * <h2>Response envelope</h2>
 *
 * <p>CAPE 2.5+ wraps every {@code /apiv2/...} response in {@code {"error": bool, "data": ...,
 * "error_value": "..."}}; older versions return the payload directly. This driver accepts both
 * forms via {@link #unwrapApiv2Response}: if {@code error: true}, the response is surfaced as
 * {@link ReasonCode#REMOTE_ERROR} with {@code error_value} as the message (common when an endpoint
 * is administratively disabled in {@code api.conf} — e.g. CAPE 2.5 returns this for {@code
 * cuckoo/status/} and {@code machines/list/} unless explicitly enabled). The {@code data} field is
 * then peeled off so each SPI method works against the inner payload regardless of envelope shape.
 *
 * <h2>Endpoints</h2>
 *
 * <p>All paths are prefixed with {@code /apiv2/} and terminated with a trailing slash because
 * CAPEv2 runs Django with {@code APPEND_SLASH=True}; a missing slash returns 404.
 *
 * <table>
 *   <caption>CAPEv2 REST API surface consumed by this driver</caption>
 *   <tr><th>SPI method</th><th>HTTP</th><th>Path</th></tr>
 *   <tr><td>healthCheck</td><td>GET</td><td>/apiv2/cuckoo/status/</td></tr>
 *   <tr><td>listMachines</td><td>GET</td><td>/apiv2/machines/list/</td></tr>
 *   <tr><td>submitSample</td><td>POST (multipart)</td><td>/apiv2/tasks/create/file/</td></tr>
 *   <tr><td>fetchTaskStatus</td><td>GET</td><td>/apiv2/tasks/view/{id}/</td></tr>
 * </table>
 *
 * <h2>Authentication</h2>
 *
 * <p>Three modes are supported, in priority order: bearer-style token ({@code Authorization: Token
 * <token>}), HTTP basic auth (typical for deployments behind nginx), or none (CAPEv2 default
 * no-auth). See {@link CapeV2SandboxDriverProperties} for the property names; secrets MUST NOT
 * appear in checked-in source.
 *
 * <h2>Error mapping</h2>
 *
 * <ul>
 *   <li>{@link java.net.ConnectException} / IO errors → {@link ReasonCode#CONNECTION_FAILED}
 *   <li>{@link HttpTimeoutException} / blocking timeouts → {@link ReasonCode#TIMEOUT}
 *   <li>HTTP 401 / 403 → {@link ReasonCode#AUTHENTICATION_FAILED}
 *   <li>HTTP 4xx (non-auth) / 5xx → {@link ReasonCode#REMOTE_ERROR} + remote status code
 *   <li>Unparseable JSON / missing required field → {@link ReasonCode#PROTOCOL_MISMATCH}
 * </ul>
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "veriguard.sandbox.cape", name = "endpoint")
@EnableConfigurationProperties(CapeV2SandboxDriverProperties.class)
@Slf4j
public class CapeV2SandboxDriver implements SandboxDriver {

  private static final String MULTIPART_BOUNDARY_PREFIX = "----VeriguardCapeBoundary";

  private final CapeV2SandboxDriverProperties props;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public CapeV2SandboxDriver(CapeV2SandboxDriverProperties props) {
    this(props, buildHttpClient(props), new ObjectMapper());
  }

  /** Test hook — inject a mock {@link HttpClient} so unit tests can stand up a local server. */
  CapeV2SandboxDriver(
      CapeV2SandboxDriverProperties props, HttpClient httpClient, ObjectMapper objectMapper) {
    if (props.normalizedEndpoint() == null) {
      throw new IllegalStateException(
          "veriguard.sandbox.cape.endpoint must be set when CapeV2SandboxDriver is wired");
    }
    this.props = props;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  // ---- SandboxDriver SPI ----------------------------------------------------

  @Override
  public void healthCheck() {
    HttpRequest request = baseRequest("/apiv2/cuckoo/status/").GET().build();
    JsonNode body = unwrapApiv2Response(sendForJson(request, "healthCheck"), "healthCheck");
    // CAPEv2 status payload always contains at least `version` and `hostname`.
    // Missing either means we hit a non-CAPE endpoint (wrong base-url / nginx
    // catch-all). Treat as protocol mismatch so ops sees a clear hint.
    if (!body.has("version")) {
      throw new SandboxIntegrationException(
          ReasonCode.PROTOCOL_MISMATCH,
          "CAPEv2 /apiv2/cuckoo/status/ response missing `version` field: " + body.toString());
    }
  }

  @Override
  public List<MachineSnapshot> listMachines() {
    HttpRequest request = baseRequest("/apiv2/machines/list/").GET().build();
    JsonNode body = unwrapApiv2Response(sendForJson(request, "listMachines"), "listMachines");
    // CAPE wraps the list under `{"machines": [...]}` in some versions, returns
    // a bare array in others — accept both.
    JsonNode arr = body.isArray() ? body : body.path("machines");
    if (!arr.isArray()) {
      throw new SandboxIntegrationException(
          ReasonCode.PROTOCOL_MISMATCH,
          "CAPEv2 /apiv2/machines/list/ response is neither an array nor `{machines:[...]}`: "
              + body);
    }
    List<MachineSnapshot> out = new ArrayList<>(arr.size());
    Instant fetchedAt = Instant.now();
    for (JsonNode m : arr) {
      out.add(
          new MachineSnapshot(
              textOrNull(m, "name"),
              textOrNull(m, "label"),
              textOrNull(m, "platform"),
              textOrNull(m, "snapshot"),
              textOrNull(m, "status"),
              fetchedAt));
    }
    return out;
  }

  @Override
  public SubmissionResult submitSample(SampleSubmissionRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }
    if (request.content() == null || request.content().length == 0) {
      throw new IllegalArgumentException("request.content must not be empty");
    }
    if (request.originalFilename() == null || request.originalFilename().isBlank()) {
      throw new IllegalArgumentException("request.originalFilename must not be blank");
    }

    String boundary = MULTIPART_BOUNDARY_PREFIX + System.nanoTime();
    byte[] body = buildMultipart(request, boundary);

    HttpRequest httpRequest =
        baseRequest("/apiv2/tasks/create/file/")
            // Sample uploads may take longer than a list / status fetch — use the
            // dedicated submit timeout (defaults to readTimeout when unset).
            .timeout(props.sampleSubmitTimeout())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

    JsonNode json = unwrapApiv2Response(sendForJson(httpRequest, "submitSample"), "submitSample");
    JsonNode taskIdNode = json.path("task_id");
    if (!taskIdNode.canConvertToLong()) {
      // CAPEv2 also returns task_ids as a singleton list for some submitters; accept both.
      JsonNode ids = json.path("task_ids");
      if (ids.isArray() && !ids.isEmpty() && ids.get(0).canConvertToLong()) {
        return new SubmissionResult(ids.get(0).asLong());
      }
      throw new SandboxIntegrationException(
          ReasonCode.PROTOCOL_MISMATCH,
          "CAPEv2 /apiv2/tasks/create/file/ response missing numeric `task_id`: " + json);
    }
    return new SubmissionResult(taskIdNode.asLong());
  }

  @Override
  public SandboxTaskStatus fetchTaskStatus(long capeTaskId) {
    HttpRequest request = baseRequest("/apiv2/tasks/view/" + capeTaskId + "/").GET().build();
    JsonNode body = unwrapApiv2Response(sendForJson(request, "fetchTaskStatus"), "fetchTaskStatus");
    // CAPEv2 wraps the task under `task` in some versions: {"task": {"status": "...", ...}}.
    // CAPE 2.5 unwraps it (after envelope strip) directly: {"id": 1, "status": "...", ...}.
    JsonNode task = body.has("task") ? body.path("task") : body;
    String rawStatus = textOrNull(task, "status");
    String errorMessage = textOrNull(task, "errors");
    if (rawStatus == null || rawStatus.isBlank()) {
      throw new SandboxIntegrationException(
          ReasonCode.PROTOCOL_MISMATCH,
          "CAPEv2 /apiv2/tasks/view/" + capeTaskId + "/ missing `task.status`: " + body);
    }
    return new SandboxTaskStatus(mapStatus(rawStatus), rawStatus, errorMessage);
  }

  // ---- internals -----------------------------------------------------------

  /**
   * Map CAPEv2 raw task status strings (see {@code lib/cuckoo/core/database.py} {@code Task} status
   * constants) to the platform-neutral {@link SandboxTaskStatus.Status} enum.
   */
  static SandboxTaskStatus.Status mapStatus(String raw) {
    if (raw == null) {
      return SandboxTaskStatus.Status.UNKNOWN;
    }
    String norm = raw.toLowerCase();
    if (norm.startsWith("failed")) {
      return SandboxTaskStatus.Status.FAILED;
    }
    return switch (norm) {
      case "pending", "queued" -> SandboxTaskStatus.Status.QUEUED;
      case "running", "processing" -> SandboxTaskStatus.Status.RUNNING;
      case "completed", "reported" -> SandboxTaskStatus.Status.COMPLETED;
      default -> SandboxTaskStatus.Status.UNKNOWN;
    };
  }

  /**
   * Strip the CAPE 2.5+ apiv2 envelope and surface backend errors.
   *
   * <p>CAPE 2.5 wraps every {@code /apiv2/...} response in {@code {"error": bool, "data": ...}}.
   * Older CAPE versions return the payload directly. We accept both — if {@code error: true} is
   * set, throw with {@code error_value} as the message (typical when an endpoint is
   * administratively disabled in {@code api.conf} — e.g. "Cuckoo Status API is disabled"); if a
   * {@code data} field is present, return that as the working payload; otherwise return the body
   * unchanged for backwards compatibility.
   */
  private JsonNode unwrapApiv2Response(JsonNode body, String operation) {
    JsonNode err = body.path("error");
    if (err.isBoolean() && err.asBoolean()) {
      String errMsg = body.path("error_value").asText("(no error_value)");
      throw new SandboxIntegrationException(
          ReasonCode.REMOTE_ERROR,
          "CAPEv2 " + operation + " server-side error: " + errMsg,
          null,
          null);
    }
    JsonNode data = body.path("data");
    return data.isMissingNode() ? body : data;
  }

  /** Common request builder — applies endpoint, read-timeout, auth header. */
  private HttpRequest.Builder baseRequest(String path) {
    URI uri = URI.create(props.normalizedEndpoint() + path);
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(props.readTimeout());
    if (props.apiToken() != null && !props.apiToken().isBlank()) {
      builder.header("Authorization", "Token " + props.apiToken().trim());
    } else if (props.basicAuthUser() != null
        && !props.basicAuthUser().isBlank()
        && props.basicAuthPass() != null
        && !props.basicAuthPass().isBlank()) {
      String creds = props.basicAuthUser() + ":" + props.basicAuthPass();
      String b64 = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
      builder.header("Authorization", "Basic " + b64);
    }
    builder.header("Accept", "application/json");
    return builder;
  }

  /**
   * Execute the request, validate the HTTP status, parse the body as JSON, and normalize errors.
   * Centralizes the {@link SandboxIntegrationException} mapping so each SPI method stays focused.
   */
  private JsonNode sendForJson(HttpRequest request, String operation) {
    HttpResponse<byte[]> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (HttpTimeoutException ex) {
      throw new SandboxIntegrationException(
          ReasonCode.TIMEOUT, "CAPEv2 " + operation + " timed out", null, ex);
    } catch (IOException ex) {
      throw new SandboxIntegrationException(
          ReasonCode.CONNECTION_FAILED,
          "CAPEv2 " + operation + " connection failed: " + ex.getMessage(),
          null,
          ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new SandboxIntegrationException(
          ReasonCode.CONNECTION_FAILED, "CAPEv2 " + operation + " interrupted", null, ex);
    }

    int status = response.statusCode();
    if (status == 401 || status == 403) {
      throw new SandboxIntegrationException(
          ReasonCode.AUTHENTICATION_FAILED,
          "CAPEv2 " + operation + " rejected credentials (HTTP " + status + ")",
          status,
          null);
    }
    if (status >= 400) {
      String preview = new String(response.body(), StandardCharsets.UTF_8);
      if (preview.length() > 512) {
        preview = preview.substring(0, 512) + "…(truncated)";
      }
      throw new SandboxIntegrationException(
          ReasonCode.REMOTE_ERROR,
          "CAPEv2 " + operation + " HTTP " + status + ": " + preview,
          status,
          null);
    }

    try {
      return objectMapper.readTree(response.body());
    } catch (IOException ex) {
      throw new SandboxIntegrationException(
          ReasonCode.PROTOCOL_MISMATCH,
          "CAPEv2 " + operation + " returned non-JSON body: " + ex.getMessage(),
          status,
          ex);
    }
  }

  /** Build the multipart/form-data body for {@code POST /tasks/create/file}. */
  private static byte[] buildMultipart(SampleSubmissionRequest request, String boundary) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      appendFilePart(out, boundary, "file", request.originalFilename(), request.content());
      if (request.timeoutSeconds() != null && request.timeoutSeconds() > 0) {
        appendTextPart(out, boundary, "timeout", String.valueOf(request.timeoutSeconds()));
      }
      if (request.targetMachineName() != null && !request.targetMachineName().isBlank()) {
        appendTextPart(out, boundary, "machine", request.targetMachineName().trim());
      }
      // Closing boundary.
      out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    } catch (IOException ex) {
      // ByteArrayOutputStream does not throw IOException for write(); guard
      // for completeness in case the API ever changes.
      throw new IllegalStateException("multipart build failed", ex);
    }
    return out.toByteArray();
  }

  private static void appendTextPart(
      ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(
        ("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.write(value.getBytes(StandardCharsets.UTF_8));
    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private static void appendFilePart(
      ByteArrayOutputStream out, String boundary, String name, String filename, byte[] content)
      throws IOException {
    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(
        ("Content-Disposition: form-data; name=\""
                + name
                + "\"; filename=\""
                + filename.replace("\"", "")
                + "\"\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
    out.write(content);
    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode v = node.path(field);
    if (v.isMissingNode() || v.isNull()) {
      return null;
    }
    return v.asText();
  }

  /** Build the JDK {@link HttpClient} with configured connect timeout + optional TLS-skip. */
  private static HttpClient buildHttpClient(CapeV2SandboxDriverProperties props) {
    HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(props.connectTimeout());
    if (Boolean.FALSE.equals(props.verifyTls())) {
      try {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(
            null,
            new TrustManager[] {
              new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                  return new X509Certificate[0];
                }
              }
            },
            new SecureRandom());
        builder.sslContext(ctx);
      } catch (Exception ex) {
        throw new IllegalStateException("Failed to build trust-all SSLContext for CAPEv2", ex);
      }
      log.warn(
          "CapeV2SandboxDriver: TLS verification DISABLED"
              + " (veriguard.sandbox.capev2.verify-tls=false) — only use against trusted internal hosts");
    }
    return builder.build();
  }
}
