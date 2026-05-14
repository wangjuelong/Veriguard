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
import io.veriguard.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.Base64;
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
                java.util.List.of("http_attack"),
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
    // The PlatformIdentityService bean's pub key is exposed via init() — easier to pluck via:
    JsonNode parsed = objectMapper.readTree(envelopeBytes);
    String signerPubB64 = parsed.get("signature").get("signer_pub_b64").asText();
    byte[] platformSignPub = Base64.getDecoder().decode(signerPubB64);

    VpackSerializer.VpackContents contents =
        vpackSerializer.parse(envelopeBytes, platformSignPub);
    org.assertj.core.api.Assertions.assertThat(contents.metadata().agentId())
        .isEqualTo(agent.agentId());
  }

  @Test
  void import_accepts_valid_vresults() throws Exception {
    RegisteredAgent agent = registerAgent();

    UUID packId = UUID.randomUUID();
    byte[] plaintextResults = "{\"results\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    X25519BoxService.SealedBox box =
        x25519.seal(plaintextResults, agent.encPair().publicKey(), agent.encPair().privateKey());

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
        .andExpect(jsonPath("$.rejected_count").value(0));
  }

  @Test
  void import_rejects_tampered_envelope() throws Exception {
    RegisteredAgent agent = registerAgent();

    UUID packId = UUID.randomUUID();
    byte[] plaintextResults = "{\"results\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    X25519BoxService.SealedBox box =
        x25519.seal(plaintextResults, agent.encPair().publicKey(), agent.encPair().privateKey());

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
        .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("envelope_invalid")));
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
}
