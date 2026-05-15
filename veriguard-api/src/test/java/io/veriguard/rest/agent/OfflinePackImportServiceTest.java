package io.veriguard.rest.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.audit.OfflinePackAuditService;
import io.veriguard.crypto.Ed25519SignatureService;
import io.veriguard.crypto.VresultsSerializer;
import io.veriguard.crypto.VresultsTaskResultParser;
import io.veriguard.crypto.X25519BoxService;
import io.veriguard.database.model.OfflinePackResultEntity;
import io.veriguard.database.repository.OfflinePackResultRepository;
import io.veriguard.database.repository.OfflinePackTaskRepository;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link OfflinePackImportService}.
 *
 * <p>Synthesizes valid + tampered {@code .vresults} envelopes inline using the real {@link
 * VresultsSerializer} + {@link X25519BoxService}, then drives them through the service under test.
 * The data layer (onboarding + audit) is mocked so failure / success paths can be asserted
 * independently of database state.
 */
class OfflinePackImportServiceTest {

  private static final String AGENT_ID = "agent-import-svc-1";
  private static final String OPERATOR = "bob@platform";
  private static final String CLIENT_IP = "10.0.0.43";
  private static final String ONBOARD_TOKEN = "tok-import-svc-1";

  private Ed25519SignatureService ed25519;
  private X25519BoxService x25519;
  private VresultsSerializer vresultsSerializer;
  private VresultsTaskResultParser parser;
  private ObjectMapper objectMapper;

  private PlatformIdentityService platformIdentity;
  private AgentOnboardingService onboardingService;
  private OfflinePackAuditService auditService;
  private OfflinePackResultRepository resultRepository;
  private OfflinePackTaskRepository taskRepository;

  private OfflinePackImportService importService;

  private Ed25519SignatureService.Ed25519KeyPair agentSignPair;
  private X25519BoxService.X25519KeyPair agentEncPair;
  private X25519BoxService.X25519KeyPair platformEncPair;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    ed25519 = new Ed25519SignatureService();
    x25519 = new X25519BoxService();
    vresultsSerializer = new VresultsSerializer(objectMapper, ed25519);
    parser = new VresultsTaskResultParser(vresultsSerializer, x25519, objectMapper);

    agentSignPair = ed25519.generate();
    agentEncPair = x25519.generate();
    platformEncPair = x25519.generate();

    platformIdentity = Mockito.mock(PlatformIdentityService.class);
    when(platformIdentity.getPlatformEncPriv()).thenReturn(platformEncPair.privateKey());
    when(platformIdentity.getPlatformEncPub()).thenReturn(platformEncPair.publicKey());

    onboardingService = Mockito.mock(AgentOnboardingService.class);
    when(onboardingService.findByToken(ONBOARD_TOKEN))
        .thenReturn(
            Optional.of(
                new AgentOnboardingService.AgentOnboardingState(
                    AGENT_ID,
                    ONBOARD_TOKEN,
                    "test-agent",
                    List.of("http_attack"),
                    List.of("A", "C"),
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    agentSignPair.publicKey(),
                    agentEncPair.publicKey(),
                    Instant.now())));

    auditService = Mockito.mock(OfflinePackAuditService.class);
    resultRepository = Mockito.mock(OfflinePackResultRepository.class);
    taskRepository = Mockito.mock(OfflinePackTaskRepository.class);
    // Default empty mapping — individual tests override to assert task_id backfill.
    Mockito.lenient()
        .when(taskRepository.findByPackIdOrderByOrdinalAsc(Mockito.any(java.util.UUID.class)))
        .thenReturn(List.of());

    importService =
        new OfflinePackImportService(
            platformIdentity,
            onboardingService,
            parser,
            auditService,
            resultRepository,
            taskRepository);
  }

  @Test
  void import_accepts_valid_envelope_returns_decoded_results() {
    UUID packId = UUID.randomUUID();
    List<AgentDtos.ResultInput> results =
        List.of(
            new AgentDtos.ResultInput(
                "SUCCESS", 0, "ok", "", "2026-05-15T10:00:00Z", "2026-05-15T10:00:01Z", null),
            new AgentDtos.ResultInput(
                "FAILURE",
                1,
                "",
                "boom",
                "2026-05-15T10:00:01Z",
                "2026-05-15T10:00:02Z",
                "non-zero exit"));
    byte[] envelopeBytes = buildEnvelope(packId, results);

    OfflinePackImportService.ImportResult out =
        importService.importPack(envelopeBytes, ONBOARD_TOKEN, OPERATOR, CLIENT_IP);

    assertThat(out.isSuccess()).isTrue();
    assertThat(out.packId()).isEqualTo(packId.toString());
    assertThat(out.importedCount()).isEqualTo(2);
    assertThat(out.rejectedCount()).isZero();
    assertThat(out.results()).hasSize(2);
    assertThat(out.results().get(0).status()).isEqualTo("SUCCESS");
    assertThat(out.results().get(1).status()).isEqualTo("FAILURE");
    verify(auditService, times(1))
        .recordImport(eq(packId), eq(OPERATOR), eq(CLIENT_IP), eq(2), eq(0));

    // Persistence: two offline_pack_result rows must have been saved with verbatim agent fields.
    @SuppressWarnings("unchecked")
    org.mockito.ArgumentCaptor<List<OfflinePackResultEntity>> rowsCaptor =
        org.mockito.ArgumentCaptor.forClass(List.class);
    verify(resultRepository, times(1)).saveAll(rowsCaptor.capture());
    List<OfflinePackResultEntity> saved = rowsCaptor.getValue();
    assertThat(saved).hasSize(2);

    OfflinePackResultEntity row0 = saved.get(0);
    assertThat(row0.getPackId()).isEqualTo(packId);
    assertThat(row0.getOrdinal()).isZero();
    assertThat(row0.getTaskId()).isNull();
    assertThat(row0.getStatus()).isEqualTo("SUCCESS");
    assertThat(row0.getExitCode()).isZero();
    assertThat(row0.getStdout()).isEqualTo("ok");
    assertThat(row0.getStderr()).isEmpty();
    assertThat(row0.getStartedAt()).isEqualTo("2026-05-15T10:00:00Z");
    assertThat(row0.getFinishedAt()).isEqualTo("2026-05-15T10:00:01Z");
    assertThat(row0.getErrorMessage()).isNull();
    assertThat(row0.getAgentId()).isEqualTo(AGENT_ID);
    assertThat(row0.getImportedAt()).isNotNull();

    OfflinePackResultEntity row1 = saved.get(1);
    assertThat(row1.getOrdinal()).isEqualTo(1);
    assertThat(row1.getStatus()).isEqualTo("FAILURE");
    assertThat(row1.getExitCode()).isEqualTo(1);
    assertThat(row1.getStderr()).isEqualTo("boom");
    assertThat(row1.getErrorMessage()).isEqualTo("non-zero exit");
  }

  @Test
  void import_rejects_unknown_onboard_token() {
    when(onboardingService.findByToken("nope")).thenReturn(Optional.empty());

    OfflinePackImportService.ImportResult out =
        importService.importPack(new byte[] {1, 2, 3}, "nope", OPERATOR, CLIENT_IP);

    assertThat(out.isSuccess()).isFalse();
    assertThat(out.errors()).contains("onboard_token_invalid");
    verify(auditService, never()).recordImport(any(), anyString(), anyString(), anyInt(), anyInt());
  }

  @Test
  void import_rejects_agent_not_registered() {
    when(onboardingService.findByToken(ONBOARD_TOKEN))
        .thenReturn(
            Optional.of(
                new AgentOnboardingService.AgentOnboardingState(
                    AGENT_ID,
                    ONBOARD_TOKEN,
                    "test-agent",
                    List.of("http_attack"),
                    List.of("A", "C"),
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    null,
                    null,
                    null)));

    OfflinePackImportService.ImportResult out =
        importService.importPack(new byte[] {1, 2, 3}, ONBOARD_TOKEN, OPERATOR, CLIENT_IP);

    assertThat(out.isSuccess()).isFalse();
    assertThat(out.errors()).contains("agent_not_registered");
    verify(auditService, never()).recordImport(any(), anyString(), anyString(), anyInt(), anyInt());
  }

  @Test
  void import_rejects_tampered_signature() throws Exception {
    UUID packId = UUID.randomUUID();
    byte[] envelopeBytes = buildEnvelope(packId, List.of());

    JsonNode root = objectMapper.readTree(envelopeBytes);
    ObjectNode sig = (ObjectNode) root.get("signature");
    byte[] sigBytes = Base64.getDecoder().decode(sig.get("sig_b64").asText());
    sigBytes[0] ^= 0x01;
    sig.put("sig_b64", Base64.getEncoder().encodeToString(sigBytes));
    byte[] tampered = objectMapper.writeValueAsBytes(root);

    OfflinePackImportService.ImportResult out =
        importService.importPack(tampered, ONBOARD_TOKEN, OPERATOR, CLIENT_IP);

    assertThat(out.isSuccess()).isFalse();
    assertThat(out.errors().get(0)).startsWith("envelope_invalid");
  }

  @Test
  void import_rejects_envelope_encrypted_to_wrong_recipient() {
    UUID packId = UUID.randomUUID();
    // Seal toward a fresh enc pair that is NOT the platform — decrypt must fail.
    X25519BoxService.X25519KeyPair wrong = x25519.generate();
    byte[] plaintext = "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    X25519BoxService.SealedBox box =
        x25519.seal(plaintext, wrong.publicKey(), agentEncPair.privateKey());

    VresultsSerializer.VresultsMetadata meta =
        new VresultsSerializer.VresultsMetadata(packId, AGENT_ID, Instant.now(), 0);
    VresultsSerializer.VresultsEncryptedEnvelope env =
        new VresultsSerializer.VresultsEncryptedEnvelope(
            agentEncPair.publicKey(), box.nonce(), box.ciphertext());
    byte[] envelopeBytes =
        vresultsSerializer.build(
            new VresultsSerializer.VresultsPayload(meta, env), agentSignPair.privateKey());

    OfflinePackImportService.ImportResult out =
        importService.importPack(envelopeBytes, ONBOARD_TOKEN, OPERATOR, CLIENT_IP);

    assertThat(out.isSuccess()).isFalse();
    assertThat(out.errors().get(0)).startsWith("envelope_decrypt_failed");
  }

  @Test
  void import_rejects_agent_id_mismatch() {
    UUID packId = UUID.randomUUID();
    byte[] envelopeBytes = buildEnvelopeForAgent(packId, List.of(), "different-agent");

    OfflinePackImportService.ImportResult out =
        importService.importPack(envelopeBytes, ONBOARD_TOKEN, OPERATOR, CLIENT_IP);

    assertThat(out.isSuccess()).isFalse();
    assertThat(out.errors()).contains("agent_id_mismatch");
  }

  @Test
  void import_surfaces_metadata_result_count_mismatch_as_warning_only() {
    UUID packId = UUID.randomUUID();
    List<AgentDtos.ResultInput> results =
        List.of(
            new AgentDtos.ResultInput(
                "SUCCESS", 0, "ok", "", "2026-05-15T10:00:00Z", "2026-05-15T10:00:01Z", null));
    // Build with metadata claiming resultCount = 5 (lying) but body has 1 element.
    byte[] envelopeBytes = buildEnvelopeWithClaimedCount(packId, results, /* claimedCount= */ 5);

    OfflinePackImportService.ImportResult out =
        importService.importPack(envelopeBytes, ONBOARD_TOKEN, OPERATOR, CLIENT_IP);

    // The decoded list is treated as truth; metadata mismatch is a soft warning.
    assertThat(out.isSuccess()).isFalse();
    assertThat(out.errors().get(0)).startsWith("metadata_result_count_mismatch");
    assertThat(out.importedCount()).isEqualTo(1);
    // Audit is still updated with the decoded count (truth), not the metadata claim.
    verify(auditService, times(1))
        .recordImport(eq(packId), eq(OPERATOR), eq(CLIENT_IP), eq(1), eq(0));
  }

  // ----- envelope synthesis helpers -----

  private byte[] buildEnvelope(UUID packId, List<AgentDtos.ResultInput> results) {
    return buildEnvelopeWithClaimedCount(packId, results, results.size());
  }

  private byte[] buildEnvelopeForAgent(
      UUID packId, List<AgentDtos.ResultInput> results, String agentId) {
    try {
      byte[] plaintext = objectMapper.writeValueAsBytes(results);
      X25519BoxService.SealedBox box =
          x25519.seal(plaintext, platformEncPair.publicKey(), agentEncPair.privateKey());
      VresultsSerializer.VresultsMetadata meta =
          new VresultsSerializer.VresultsMetadata(packId, agentId, Instant.now(), results.size());
      VresultsSerializer.VresultsEncryptedEnvelope env =
          new VresultsSerializer.VresultsEncryptedEnvelope(
              agentEncPair.publicKey(), box.nonce(), box.ciphertext());
      return vresultsSerializer.build(
          new VresultsSerializer.VresultsPayload(meta, env), agentSignPair.privateKey());
    } catch (Exception ex) {
      throw new RuntimeException("failed to synthesize .vresults envelope", ex);
    }
  }

  private byte[] buildEnvelopeWithClaimedCount(
      UUID packId, List<AgentDtos.ResultInput> results, int claimedCount) {
    try {
      byte[] plaintext = objectMapper.writeValueAsBytes(results);
      X25519BoxService.SealedBox box =
          x25519.seal(plaintext, platformEncPair.publicKey(), agentEncPair.privateKey());
      VresultsSerializer.VresultsMetadata meta =
          new VresultsSerializer.VresultsMetadata(packId, AGENT_ID, Instant.now(), claimedCount);
      VresultsSerializer.VresultsEncryptedEnvelope env =
          new VresultsSerializer.VresultsEncryptedEnvelope(
              agentEncPair.publicKey(), box.nonce(), box.ciphertext());
      return vresultsSerializer.build(
          new VresultsSerializer.VresultsPayload(meta, env), agentSignPair.privateKey());
    } catch (Exception ex) {
      throw new RuntimeException("failed to synthesize .vresults envelope", ex);
    }
  }
}
