package io.veriguard.rest.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Integration tests for {@link AgentImplantDownloadApi}. */
@DirtiesContext
@Transactional
@WithMockUser(isAdmin = true)
class AgentImplantDownloadApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void download_returns_404_when_binary_absent() throws Exception {
    // macos/arm64 — no fixture provided in src/test/resources, only a .keep main resource
    mockMvc.perform(get("/api/agent/implant/download/macos/arm64")).andExpect(status().isNotFound());
  }

  @Test
  void download_returns_200_with_sha256_for_test_fixture() throws Exception {
    // src/test/resources/agents/veriguard-implant/linux/x86_64/veriguard-implant exists with
    // fixture content "FAKE_IMPLANT_BINARY_FIXTURE_CONTENTS" (36 bytes)
    mockMvc
        .perform(get("/api/agent/implant/download/linux/x86_64"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/octet-stream"))
        .andExpect(header().exists("X-SHA256"))
        .andExpect(header().string("Content-Length", "36"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition", "attachment; filename=\"veriguard-implant\""));
  }

  @Test
  void download_rejects_invalid_os_path_variable() throws Exception {
    mockMvc
        .perform(get("/api/agent/implant/download/freebsd/x86_64"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void download_rejects_invalid_arch_path_variable() throws Exception {
    mockMvc
        .perform(get("/api/agent/implant/download/linux/sparc"))
        .andExpect(status().isBadRequest());
  }
}
