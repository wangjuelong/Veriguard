package io.veriguard.rest.agent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.utils.mockUser.WithMockUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for {@link AgentInstallScriptApi} — Task C.8.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>happy path bash one-liner with embedded token
 *   <li>happy path PowerShell equivalent
 *   <li>bad / unknown onboard token
 *   <li>missing binary returns 404
 *   <li>present binary returns 200 with SHA256
 * </ul>
 */
@DirtiesContext
@WithMockUser(isAdmin = true)
class AgentInstallScriptApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private static final String INIT_BODY =
      """
      {
        "display_name": "install-script-agent",
        "capabilities": ["http_attack"],
        "allowed_modes": ["A"]
      }
      """;

  private String createOnboardToken() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post(AgentOnboardApi.ONBOARD_INIT_URI)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(INIT_BODY))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode out = objectMapper.readTree(res.getResponse().getContentAsString());
    return out.get("onboard_token").asText();
  }

  @Test
  void install_bash_renders_one_liner_with_embedded_token() throws Exception {
    String token = createOnboardToken();

    MvcResult res =
        mockMvc
            .perform(get(AgentInstallScriptApi.INSTALL_BASH_URI, token))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/x-shellscript"))
            .andReturn();
    String body = res.getResponse().getContentAsString();

    Assertions.assertThat(body)
        .contains("#!/usr/bin/env bash")
        .contains("ONBOARD_TOKEN=\"" + token + "\"")
        .contains("/api/agent/install/binary/")
        .contains("veriguard-agent")
        .contains("init")
        .contains("--bootstrap");
  }

  @Test
  void install_ps1_renders_powershell_one_liner() throws Exception {
    String token = createOnboardToken();

    MvcResult res =
        mockMvc
            .perform(get(AgentInstallScriptApi.INSTALL_PS1_URI, token))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/plain"))
            .andReturn();
    String body = res.getResponse().getContentAsString();

    Assertions.assertThat(body)
        .contains("$OnboardToken = '" + token + "'")
        .contains("veriguard-agent.exe")
        .contains("--bootstrap");
  }

  @Test
  void install_bash_rejects_unknown_token() throws Exception {
    mockMvc
        .perform(get(AgentInstallScriptApi.INSTALL_BASH_URI, "nonexistent-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void install_binary_returns_404_when_missing() throws Exception {
    // macos/arm64 main resource is only a .keep placeholder
    mockMvc
        .perform(get(AgentInstallScriptApi.INSTALL_BINARY_URI, "macos", "arm64"))
        .andExpect(status().isNotFound());
  }

  @Test
  void install_binary_returns_200_with_sha256_when_fixture_present() throws Exception {
    // src/test/resources/agents/veriguard-agent/linux/x86_64/veriguard-agent exists w/ fixture
    mockMvc
        .perform(get(AgentInstallScriptApi.INSTALL_BINARY_URI, "linux", "x86_64"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/octet-stream"))
        .andExpect(header().exists("X-SHA256"))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"veriguard-agent\""));
  }
}
