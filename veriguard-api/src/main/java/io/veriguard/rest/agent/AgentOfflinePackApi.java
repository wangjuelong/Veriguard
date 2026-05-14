package io.veriguard.rest.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.crypto.Ed25519SignatureService;
import io.veriguard.crypto.VpackSerializer;
import io.veriguard.crypto.VresultsSerializer;
import io.veriguard.crypto.X25519BoxService;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 *       agent_id + tasks; returns JSON bytes (downloadable as {@code .vpack} file)
 *   <li>{@code POST /api/agent/offline-pack/import} — admin uploads a {@code .vresults} file
 *       (multipart) — verified, decrypted, results parsed; returns count summary
 * </ul>
 *
 * <p><strong>Scaffold mode</strong>:
 *
 * <ul>
 *   <li>Export uses an empty plaintext tasks JSON for the encrypted envelope content (real task
 *       selection from Inject DB is C1-Platform-2 scope)
 *   <li>Import accepts and parses the envelope only — does not yet write results to
 *       inject_execution / execution_traces (C1-Platform-2 scope)
 *   <li>offline_pack_audit DB write is also scaffolded (logged but not persisted) — full audit
 *       service is later
 * </ul>
 */
@RestController
public class AgentOfflinePackApi {

  public static final String EXPORT_URI = "/api/agent/offline-pack/export";
  public static final String IMPORT_URI = "/api/agent/offline-pack/import";

  private final PlatformIdentityService platformIdentity;
  private final VpackSerializer vpackSerializer;
  private final VresultsSerializer vresultsSerializer;
  private final X25519BoxService x25519;
  private final Ed25519SignatureService ed25519;
  private final AgentOnboardingService onboardingService;
  private final ObjectMapper objectMapper;

  public AgentOfflinePackApi(
      PlatformIdentityService platformIdentity,
      VpackSerializer vpackSerializer,
      VresultsSerializer vresultsSerializer,
      X25519BoxService x25519,
      Ed25519SignatureService ed25519,
      AgentOnboardingService onboardingService,
      ObjectMapper objectMapper) {
    this.platformIdentity = platformIdentity;
    this.vpackSerializer = vpackSerializer;
    this.vresultsSerializer = vresultsSerializer;
    this.x25519 = x25519;
    this.ed25519 = ed25519;
    this.onboardingService = onboardingService;
    this.objectMapper = objectMapper;
  }

  /**
   * Export — generate a {@code .vpack} envelope for {@code agent_id} (no real task selection
   * yet — scaffold uses empty tasks array).
   *
   * <p>Caller must provide an onboard_token via header so the platform can locate the agent's
   * X25519 enc pub key (which it uses as the recipient for envelope encryption).
   */
  @PostMapping(value = EXPORT_URI, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Export an offline .vpack for an agent (admin)")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.AGENT)
  public ResponseEntity<byte[]> export(
      @Valid @RequestBody AgentDtos.OfflinePackExportInput input,
      @RequestParam(value = "onboard_token") String onboardToken) {
    Optional<AgentOnboardingService.AgentOnboardingState> state =
        onboardingService.findByToken(onboardToken);
    if (state.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    AgentOnboardingService.AgentOnboardingState s = state.get();
    if (s.agentEncPub() == null) {
      return ResponseEntity.badRequest().build();
    }
    if (!s.agentId().equals(input.agentId())) {
      return ResponseEntity.badRequest().build();
    }

    // Scaffold — real task selection from JPA Inject lookup is C1-Platform-2 scope.
    byte[] plaintextTasks = "{\"tasks\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    X25519BoxService.SealedBox box =
        x25519.seal(plaintextTasks, s.agentEncPub(), platformIdentity.getPlatformEncPriv());

    UUID packId = UUID.randomUUID();
    VpackSerializer.VpackMetadata meta =
        new VpackSerializer.VpackMetadata(
            packId,
            platformIdentity.getPlatformId(),
            input.agentId(),
            Instant.now(),
            0,
            "1.0",
            "operator-c1-platform-1-scaffold");
    VpackSerializer.VpackEncryptedEnvelope env =
        new VpackSerializer.VpackEncryptedEnvelope(
            platformIdentity.getPlatformEncPub(), box.nonce(), box.ciphertext());

    byte[] envelopeBytes =
        vpackSerializer.build(
            new VpackSerializer.VpackPayload(meta, env), platformIdentity.getPlatformSignPriv());

    return ResponseEntity.ok()
        .header(
            "Content-Disposition",
            "attachment; filename=\"" + packId + ".vpack\"")
        .body(envelopeBytes);
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
      @RequestParam(value = "onboard_token") String onboardToken) {
    Optional<AgentOnboardingService.AgentOnboardingState> state =
        onboardingService.findByToken(onboardToken);
    if (state.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(
              new AgentDtos.OfflinePackImportOutput(
                  "", 0, 0, java.util.List.of("onboard_token_invalid")));
    }
    AgentOnboardingService.AgentOnboardingState s = state.get();
    if (s.agentSignPub() == null) {
      return ResponseEntity.badRequest()
          .body(
              new AgentDtos.OfflinePackImportOutput(
                  "", 0, 0, java.util.List.of("agent_not_registered")));
    }

    byte[] envelopeBytes;
    try {
      envelopeBytes = file.getBytes();
    } catch (IOException ex) {
      return ResponseEntity.badRequest()
          .body(
              new AgentDtos.OfflinePackImportOutput(
                  "", 0, 0, java.util.List.of("file_read_error: " + ex.getMessage())));
    }

    VresultsSerializer.VresultsContents parsed;
    try {
      parsed = vresultsSerializer.parse(envelopeBytes, s.agentSignPub());
    } catch (VpackSerializer.SignatureVerificationException
        | VpackSerializer.SchemaVersionException
        | VpackSerializer.VpackParseException ex) {
      return ResponseEntity.badRequest()
          .body(
              new AgentDtos.OfflinePackImportOutput(
                  "", 0, 0, java.util.List.of("envelope_invalid: " + ex.getMessage())));
    }

    // Verify agent_id matches the onboard state.
    if (!parsed.metadata().agentId().equals(s.agentId())) {
      return ResponseEntity.badRequest()
          .body(
              new AgentDtos.OfflinePackImportOutput(
                  parsed.metadata().packId().toString(),
                  0,
                  parsed.metadata().resultCount(),
                  java.util.List.of("agent_id_mismatch")));
    }

    // Scaffold — actual result decryption + persist is C1-Platform-2 scope.
    // We've validated signature + agent identity; that's the security boundary for this PR.
    return ResponseEntity.ok(
        new AgentDtos.OfflinePackImportOutput(
            parsed.metadata().packId().toString(),
            parsed.metadata().resultCount(),
            0,
            java.util.List.of()));
  }
}
