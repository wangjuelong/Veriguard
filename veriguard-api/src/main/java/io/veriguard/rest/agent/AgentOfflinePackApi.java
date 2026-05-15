package io.veriguard.rest.agent;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Veriguard Agent (C1) offline pack REST — Mode C 离线工作 export + import (spec §3.5.1 Mode C).
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>{@code POST /api/agent/offline-pack/export} — admin generates a {@code .vpack} for an
 *       agent_id + tasks; returns canonical JSON bytes (downloadable as {@code .vpack} file)
 *   <li>{@code POST /api/agent/offline-pack/import} — admin uploads a {@code .vresults} file
 *       (multipart) — verified, decrypted, results parsed; returns count summary
 * </ul>
 *
 * <p>Business logic — task drain + envelope build + audit — lives in {@link
 * OfflinePackExportService} and {@link OfflinePackImportService}. This controller only handles HTTP
 * boundary concerns: RBAC, multipart parsing, auth principal extraction, immediate-connection IP
 * extraction. Test the business logic via the service unit tests and the wire contract via {@code
 * AgentOfflinePackApiIntegrationTest}.
 */
@RestController
public class AgentOfflinePackApi {

  public static final String EXPORT_URI = "/api/agent/offline-pack/export";
  public static final String IMPORT_URI = "/api/agent/offline-pack/import";

  private static final Logger log = LoggerFactory.getLogger(AgentOfflinePackApi.class);

  private final OfflinePackExportService exportService;
  private final OfflinePackImportService importService;

  public AgentOfflinePackApi(
      OfflinePackExportService exportService, OfflinePackImportService importService) {
    this.exportService = exportService;
    this.importService = importService;
  }

  /**
   * Export — drain pending tasks for {@code agent_id} and seal them into a {@code .vpack} envelope.
   *
   * <p>Caller must provide an onboard_token via query param so the platform can locate the agent's
   * X25519 enc pub key (recipient of envelope encryption). The drained tasks are removed from the
   * shared {@link AgentTaskQueueService} queue — Mode A polling will not see them again until
   * re-enqueued.
   */
  @PostMapping(value = EXPORT_URI, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Export an offline .vpack for an agent (admin)")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.AGENT)
  public ResponseEntity<byte[]> export(
      @Valid @RequestBody AgentDtos.OfflinePackExportInput input,
      @RequestParam(value = "onboard_token") String onboardToken,
      HttpServletRequest request) {
    Optional<OfflinePackExportService.ExportResult> result =
        exportService.export(input, onboardToken, currentUsername(), clientIp(request));
    if (result.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    OfflinePackExportService.ExportResult er = result.get();
    log.info(
        "OfflinePack export OK pack_id={} agent_id={} task_count={}",
        er.packId(),
        input.agentId(),
        er.taskCount());
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + er.packId() + ".vpack\"")
        .body(er.vpackBytes());
  }

  /**
   * Import — accept and verify a {@code .vresults} envelope uploaded by the operator who runs the
   * agent offline.
   *
   * @return summary (pack_id + imported_count + rejected_count + errors[])
   */
  @PostMapping(value = IMPORT_URI, consumes = "multipart/form-data")
  @Operation(summary = "Import an agent .vresults file (admin)")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.AGENT)
  public ResponseEntity<AgentDtos.OfflinePackImportOutput> importPack(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "onboard_token") String onboardToken,
      HttpServletRequest request) {
    byte[] envelopeBytes;
    try {
      envelopeBytes = file.getBytes();
    } catch (IOException ex) {
      return ResponseEntity.badRequest()
          .body(
              new AgentDtos.OfflinePackImportOutput(
                  "", 0, 0, java.util.List.of("file_read_error: " + ex.getMessage())));
    }

    OfflinePackImportService.ImportResult result =
        importService.importPack(envelopeBytes, onboardToken, currentUsername(), clientIp(request));

    AgentDtos.OfflinePackImportOutput body =
        new AgentDtos.OfflinePackImportOutput(
            result.packId(), result.importedCount(), result.rejectedCount(), result.errors());

    return result.isSuccess() ? ResponseEntity.ok(body) : ResponseEntity.badRequest().body(body);
  }

  // ---- helpers ----

  /** Current admin username from SecurityContext; falls back to "unknown" if not authenticated. */
  private static String currentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getName() == null) {
      return "unknown";
    }
    return auth.getName();
  }

  /**
   * Returns the immediate connection's remote IP for audit attribution.
   *
   * <p><strong>Does NOT trust {@code X-Forwarded-For}.</strong> In production deployments, a
   * reverse proxy (nginx / ALB / CloudFront) is expected to either rewrite {@code RemoteAddr} via
   * PROXY protocol / {@code set_real_ip_from} so that {@link HttpServletRequest#getRemoteAddr()}
   * returns the true client IP, or perform its own audit-side attribution. Trusting {@code
   * X-Forwarded-For} directly here would let any agent client spoof an arbitrary IP into the {@code
   * offline_pack_audit.exported_from_ip} column.
   *
   * <p>Package-private for test access (see {@code AgentOfflinePackApiClientIpTest}).
   */
  static String clientIp(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    return request.getRemoteAddr();
  }
}
