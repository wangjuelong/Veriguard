package io.veriguard.rest.security_validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@DirtiesContext
@Transactional
@WithMockUser(isAdmin = true)
class SandboxApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private static final String VALID_BODY =
      """
      {
        "sandbox_name": "勒索沙箱",
        "sandbox_description": "ransomware preset",
        "sandbox_network_policy": "DENY_ALL",
        "sandbox_network_rules": [],
        "sandbox_auto_restore_enabled": true,
        "sandbox_supported_sample_types": ["RANSOMWARE"],
        "sandbox_status": "ACTIVE"
      }
      """;

  @Test
  void create_returns_201_and_persists() throws Exception {
    mockMvc
        .perform(post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sandbox_id").exists())
        .andExpect(jsonPath("$.sandbox_provider_type").doesNotExist())
        .andExpect(jsonPath("$.sandbox_endpoint").doesNotExist());
  }

  @Test
  void create_with_disabled_auto_restore_returns_400_with_reason_code() throws Exception {
    String body =
        VALID_BODY.replace(
            "\"sandbox_auto_restore_enabled\": true", "\"sandbox_auto_restore_enabled\": false");
    mockMvc
        .perform(post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.children.sandbox_auto_restore_required").exists());
  }

  @Test
  void create_with_duplicate_name_returns_400_duplicated() throws Exception {
    mockMvc
        .perform(post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated());
    mockMvc
        .perform(post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.children.sandbox_name_duplicated").exists());
  }

  @Test
  void list_returns_persisted_sandbox() throws Exception {
    mockMvc
        .perform(post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/api/sandboxes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sandbox_name").value("勒索沙箱"));
  }

  @Test
  void export_iptables_returns_text_plain_with_attachment_header() throws Exception {
    String created =
        mockMvc
            .perform(
                post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(created).get("sandbox_id").asText();

    mockMvc
        .perform(get("/api/sandboxes/" + id + "/network-rules/exports/iptables"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    org.hamcrest.Matchers.containsString("attachment; filename=\"")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("#!/bin/sh")));
  }

  @Test
  void export_routing_conf_returns_text_plain() throws Exception {
    String created =
        mockMvc
            .perform(
                post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(created).get("sandbox_id").asText();

    mockMvc
        .perform(get("/api/sandboxes/" + id + "/network-rules/exports/routing-conf"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("routing.conf 片段")));
  }
}
