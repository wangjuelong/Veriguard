package io.veriguard.rest.agent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.crypto.Ed25519SignatureService;
import io.veriguard.crypto.VpackSerializer;
import io.veriguard.crypto.VresultsSerializer;
import io.veriguard.crypto.X25519BoxService;
import io.veriguard.database.model.OfflinePackResultEntity;
import io.veriguard.database.repository.OfflinePackAuditRepository;
import io.veriguard.database.repository.OfflinePackResultRepository;
import io.veriguard.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Integration tests for {@link AgentOfflinePackApi} — Mode C export + import. */
@DirtiesContext
@Transactional
@WithMockUser(isAdmin = true)
class AgentOfflinePackApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private Ed25519SignatureService ed25519;
  @Autowired private X25519BoxService x25519;
  @Autowired private VpackSerializer vpackSerializer;
  @Autowired private VresultsSerializer vresultsSerializer;
  @Autowired private OfflinePackAuditRepository auditRepository;
  @Autowired private OfflinePackResultRepository resultRepository;
  @Autowired private PlatformIdentityService platformIdentity;
  @Autowired private AgentTaskQueueService taskQueueService;

  private static final String INIT_BODY =
      """
      {
        "display_name": "test-agent-offline-pack",
        "capabilities": ["http_attack", "command_inject"],
        "allowed_modes": ["A", "C"]
      }
      """;

  private record RegisteredAgent(
      String agentId,
      String onboardToken,
      Ed25519SignatureService.Ed25519KeyPair signPair,
      X25519BoxService.X25519KeyPair encPair) {}

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
                List.of("http_attack"),
                Base64.getEncoder().encodeToString(sig)));

    mockMvc
        .perform(
            post(AgentOnboardApi.ONBOARD_REGISTER_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
        .andExpect(status().isOk());

    return new RegisteredAgent(agentId, token, signPair, encPair);
  }

  @Test
  void export_produces_valid_vpack_that_roundtrips() throws Exception {
    RegisteredAgent agent = registerAgent();

    String exportBody =
        objectMapper.writeValueAsString(new AgentDtos.OfflinePackExportInput(agent.agentId(), 100));

    MvcResult result =
        mockMvc
            .perform(
                post(AgentOfflinePackApi.EXPORT_URI)
                    .with(csrf())
                    .param("onboard_token", agent.onboardToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(exportBody))
            .andExpect(status().isOk())
            .andReturn();

    byte[] envelopeBytes = result.getResponse().getContentAsByteArray();

    // Parse the vpack envelope back — we need the platform sign pub key to verify.
    JsonNode parsed = objectMapper.readTree(envelopeBytes);
    String signerPubB64 = parsed.get("signature").get("signer_pub_b64").asText();
    byte[] platformSignPub = Base64.getDecoder().decode(signerPubB64);

    VpackSerializer.VpackContents contents = vpackSerializer.parse(envelopeBytes, platformSignPub);
    org.assertj.core.api.Assertions.assertThat(contents.metadata().agentId())
        .isEqualTo(agent.agentId());
  }

  @Test
  void export_then_import_full_roundtrip_with_two_tasks() throws Exception {
    RegisteredAgent agent = registerAgent();

    // Pre-load the queue so export drains real tasks (no longer the empty-stub case).
    taskQueueService.enqueue(
        agent.agentId(),
        new AgentDtos.AgentTask("task-1", "http_attack", "veriguard-web-attack", "{}", List.of()));
    taskQueueService.enqueue(
        agent.agentId(),
        new AgentDtos.AgentTask("task-2", "command_inject", "veriguard-command", "{}", List.of()));

    // 1. Export
    String exportBody =
        objectMapper.writeValueAsString(new AgentDtos.OfflinePackExportInput(agent.agentId(), 100));
    MvcResult exportResult =
        mockMvc
            .perform(
                post(AgentOfflinePackApi.EXPORT_URI)
                    .with(csrf())
                    .param("onboard_token", agent.onboardToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(exportBody))
            .andExpect(status().isOk())
            .andReturn();
    byte[] vpackBytes = exportResult.getResponse().getContentAsByteArray();

    // 2. Open the vpack body so the test acts as the agent: decrypt + read the task list.
    VpackSerializer.VpackContents vpackContents =
        vpackSerializer.parse(vpackBytes, platformIdentity.getPlatformSignPub());
    org.assertj.core.api.Assertions.assertThat(vpackContents.metadata().taskCount()).isEqualTo(2);
    byte[] tasksPlaintext =
        x25519.open(
            vpackContents.encryptedEnvelope().ciphertext(),
            vpackContents.encryptedEnvelope().nonce(),
            vpackContents.encryptedEnvelope().senderX25519Pub(),
            agent.encPair().privateKey());
    @SuppressWarnings("unchecked")
    List<java.util.Map<String, Object>> decodedTasks =
        objectMapper.readValue(tasksPlaintext, List.class);
    org.assertj.core.api.Assertions.assertThat(decodedTasks).hasSize(2);

    // 3. Synthesize per-task results 1-to-1 (matching what the Rust agent would emit).
    UUID packId = vpackContents.metadata().packId();
    List<AgentDtos.ResultInput> results =
        List.of(
            new AgentDtos.ResultInput(
                "SUCCESS", 0, "ok-1", "", "2026-05-15T10:00:00Z", "2026-05-15T10:00:01Z", null),
            new AgentDtos.ResultInput(
                "SUCCESS", 0, "ok-2", "", "2026-05-15T10:00:02Z", "2026-05-15T10:00:03Z", null));
    byte[] resultsPlaintext = objectMapper.writeValueAsBytes(results);

    // 4. Encrypt the results toward the platform (NOT the agent) — that is the real Mode C flow.
    X25519BoxService.SealedBox resultsBox =
        x25519.seal(
            resultsPlaintext, platformIdentity.getPlatformEncPub(), agent.encPair().privateKey());
    VresultsSerializer.VresultsMetadata meta =
        new VresultsSerializer.VresultsMetadata(packId, agent.agentId(), Instant.now(), 2);
    VresultsSerializer.VresultsEncryptedEnvelope env =
        new VresultsSerializer.VresultsEncryptedEnvelope(
            agent.encPair().publicKey(), resultsBox.nonce(), resultsBox.ciphertext());
    byte[] vresultsBytes =
        vresultsSerializer.build(
            new VresultsSerializer.VresultsPayload(meta, env), agent.signPair().privateKey());

    // 5. Import
    MockMultipartFile file =
        new MockMultipartFile("file", packId + ".vresults", "application/json", vresultsBytes);
    mockMvc
        .perform(
            multipart(AgentOfflinePackApi.IMPORT_URI)
                .file(file)
                .with(csrf())
                .param("onboard_token", agent.onboardToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pack_id").value(packId.toString()))
        .andExpect(jsonPath("$.imported_count").value(2))
        .andExpect(jsonPath("$.rejected_count").value(0));

    // 6. Per-result persistence (V21 offline_pack_result) — two rows in ordinal order with the
    //    agent's verbatim fields. Joins the proof from the wire layer (envelope decrypted, sig
    //    verified) onto the admin-visibility layer (rows queryable by pack_id).
    List<OfflinePackResultEntity> persisted =
        resultRepository.findByPackIdOrderByOrdinalAsc(packId);
    org.assertj.core.api.Assertions.assertThat(persisted).hasSize(2);
    org.assertj.core.api.Assertions.assertThat(persisted.get(0).getOrdinal()).isZero();
    org.assertj.core.api.Assertions.assertThat(persisted.get(0).getStatus()).isEqualTo("SUCCESS");
    org.assertj.core.api.Assertions.assertThat(persisted.get(0).getStdout()).isEqualTo("ok-1");
    org.assertj.core.api.Assertions.assertThat(persisted.get(0).getAgentId())
        .isEqualTo(agent.agentId());
    org.assertj.core.api.Assertions.assertThat(persisted.get(1).getOrdinal()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(persisted.get(1).getStdout()).isEqualTo("ok-2");
  }

  @Test
  void import_accepts_valid_vresults() throws Exception {
    RegisteredAgent agent = registerAgent();

    UUID packId = UUID.randomUUID();
    // Plaintext per Rust-locked wire schema: JSON array of ResultInput (empty array == empty list).
    byte[] plaintextResults = "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    // Encrypt toward the platform — the import service decrypts with platformEncPriv.
    X25519BoxService.SealedBox box =
        x25519.seal(
            plaintextResults, platformIdentity.getPlatformEncPub(), agent.encPair().privateKey());

    VresultsSerializer.VresultsMetadata meta =
        new VresultsSerializer.VresultsMetadata(packId, agent.agentId(), Instant.now(), 0);
    VresultsSerializer.VresultsEncryptedEnvelope env =
        new VresultsSerializer.VresultsEncryptedEnvelope(
            agent.encPair().publicKey(), box.nonce(), box.ciphertext());

    byte[] envelopeBytes =
        vresultsSerializer.build(
            new VresultsSerializer.VresultsPayload(meta, env), agent.signPair().privateKey());

    MockMultipartFile file =
        new MockMultipartFile("file", packId + ".vresults", "application/json", envelopeBytes);

    mockMvc
        .perform(
            multipart(AgentOfflinePackApi.IMPORT_URI)
                .file(file)
                .with(csrf())
                .param("onboard_token", agent.onboardToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pack_id").value(packId.toString()))
        .andExpect(jsonPath("$.imported_count").value(0))
        .andExpect(jsonPath("$.rejected_count").value(0));
  }

  @Test
  void import_rejects_tampered_envelope() throws Exception {
    RegisteredAgent agent = registerAgent();

    UUID packId = UUID.randomUUID();
    byte[] plaintextResults = "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    X25519BoxService.SealedBox box =
        x25519.seal(
            plaintextResults, platformIdentity.getPlatformEncPub(), agent.encPair().privateKey());

    VresultsSerializer.VresultsMetadata meta =
        new VresultsSerializer.VresultsMetadata(packId, agent.agentId(), Instant.now(), 0);
    VresultsSerializer.VresultsEncryptedEnvelope env =
        new VresultsSerializer.VresultsEncryptedEnvelope(
            agent.encPair().publicKey(), box.nonce(), box.ciphertext());

    byte[] envelopeBytes =
        vresultsSerializer.build(
            new VresultsSerializer.VresultsPayload(meta, env), agent.signPair().privateKey());

    // Tamper with signature field — find sig_b64 and flip a byte
    JsonNode root = objectMapper.readTree(envelopeBytes);
    com.fasterxml.jackson.databind.node.ObjectNode sigNode =
        (com.fasterxml.jackson.databind.node.ObjectNode) root.get("signature");
    String sigB64 = sigNode.get("sig_b64").asText();
    byte[] sig = Base64.getDecoder().decode(sigB64);
    sig[0] ^= 0x01;
    sigNode.put("sig_b64", Base64.getEncoder().encodeToString(sig));
    byte[] tampered = objectMapper.writeValueAsBytes(root);

    MockMultipartFile file =
        new MockMultipartFile("file", packId + ".vresults", "application/json", tampered);

    mockMvc
        .perform(
            multipart(AgentOfflinePackApi.IMPORT_URI)
                .file(file)
                .with(csrf())
                .param("onboard_token", agent.onboardToken()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[0]")
                .value(org.hamcrest.Matchers.containsString("envelope_invalid")));
  }

  @Test
  void export_rejects_invalid_onboard_token() throws Exception {
    RegisteredAgent agent = registerAgent();

    String exportBody =
        objectMapper.writeValueAsString(new AgentDtos.OfflinePackExportInput(agent.agentId(), 100));

    mockMvc
        .perform(
            post(AgentOfflinePackApi.EXPORT_URI)
                .with(csrf())
                .param("onboard_token", "nonexistent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportBody))
        .andExpect(status().isBadRequest());
  }

  /**
   * V21 dropped the {@code fk_pack_audit_agent} FK on {@code offline_pack_audit.agent_id}, so the
   * per-export audit row now writes unconditionally (Mode C agents go through {@code
   * /api/agent/onboard/register} and do not own an OpenAEV Asset / row in {@code agents}). This
   * test pins that the audit row IS written so a future migration cannot silently re-introduce the
   * dependency.
   */
  @Test
  void export_audit_row_persists() throws Exception {
    long before = auditRepository.count();
    RegisteredAgent agent = registerAgent();

    String exportBody =
        objectMapper.writeValueAsString(new AgentDtos.OfflinePackExportInput(agent.agentId(), 100));

    mockMvc
        .perform(
            post(AgentOfflinePackApi.EXPORT_URI)
                .with(csrf())
                .param("onboard_token", agent.onboardToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportBody))
        .andExpect(status().isOk());

    long after = auditRepository.count();
    org.assertj.core.api.Assertions.assertThat(after)
        .as("audit row MUST be written even when the agent has no OpenAEV agents row (V21)")
        .isEqualTo(before + 1);
  }
}
