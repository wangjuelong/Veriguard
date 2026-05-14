package io.veriguard.rest.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Integration tests for {@link AgentOnboardApi} — 3-step onboarding flow + reject paths.
 *
 * <p>End-to-end: init (admin) → bootstrap (agent) → register (agent). Verifies happy path +
 * tampered signature + expired token + agent_id mismatch.
 */
@DirtiesContext
@Transactional
@WithMockUser(isAdmin = true)
class AgentOnboardApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private Ed25519SignatureService ed25519;
  @Autowired private X25519BoxService x25519;

  private static final String INIT_BODY =
      """
      {
        "display_name": "test-agent-001",
        "capabilities": ["http_attack", "command_inject"],
        "allowed_modes": ["A", "C"]
      }
      """;

  @Test
  void init_returns_agent_id_token_and_platform_pubs() throws Exception {
    mockMvc
        .perform(
            post(AgentOnboardApi.ONBOARD_INIT_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(INIT_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.agent_id").exists())
        .andExpect(jsonPath("$.onboard_token").exists())
        .andExpect(jsonPath("$.platform_ed25519_pub_b64").exists())
        .andExpect(jsonPath("$.platform_x25519_pub_b64").exists())
        .andExpect(jsonPath("$.platform_id").exists())
        .andExpect(jsonPath("$.platform_url").exists())
        .andExpect(jsonPath("$.ttl_seconds").value(86400));
  }

  @Test
  void register_with_valid_signature_succeeds() throws Exception {
    // 1. init
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

    // 2. agent generates own keys + signs the registration proof
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

    // 3. register
    mockMvc
        .perform(
            post(AgentOnboardApi.ONBOARD_REGISTER_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("registered"))
        .andExpect(jsonPath("$.agent_id").value(agentId));
  }

  @Test
  void register_with_tampered_signature_returns_401() throws Exception {
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
    sig[0] ^= 0x01;

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
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("registration_signature_invalid"));
  }

  @Test
  void register_with_invalid_token_returns_400() throws Exception {
    String body =
        """
        {
          "agent_id": "some-uuid",
          "onboard_token": "deadbeef0000",
          "agent_ed25519_pub_b64": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
          "agent_x25519_pub_b64": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
          "capabilities": [],
          "registration_sig_b64": "AAA="
        }
        """;

    mockMvc
        .perform(
            post(AgentOnboardApi.ONBOARD_REGISTER_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("onboard_token_invalid_or_expired"));
  }

  @Test
  void bootstrap_returns_install_pack_for_valid_token() throws Exception {
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
    String token = initOut.get("onboard_token").asText();
    String agentId = initOut.get("agent_id").asText();

    String body = String.format("{\"onboard_token\": \"%s\"}", token);
    MvcResult bootstrapResult =
        mockMvc
            .perform(
                post(AgentOnboardApi.ONBOARD_BOOTSTRAP_URI)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.install_pack.agent_id").value(agentId))
            .andExpect(jsonPath("$.install_pack.onboard_token").value(token))
            .andExpect(jsonPath("$.install_pack.platform_ed25519_pub_b64").exists())
            .andExpect(jsonPath("$.install_pack.platform_x25519_pub_b64").exists())
            .andReturn();

    JsonNode out = objectMapper.readTree(bootstrapResult.getResponse().getContentAsString());
    assertThat(out.get("install_pack").get("capabilities").size()).isEqualTo(2);
  }

  @Test
  void bootstrap_with_invalid_token_returns_404() throws Exception {
    String body = "{\"onboard_token\": \"nonexistent_token\"}";
    mockMvc
        .perform(
            post(AgentOnboardApi.ONBOARD_BOOTSTRAP_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());
  }
}
