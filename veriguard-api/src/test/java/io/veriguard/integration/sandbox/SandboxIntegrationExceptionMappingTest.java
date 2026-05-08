package io.veriguard.integration.sandbox;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.rest.helper.RestBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SandboxIntegrationExceptionMappingTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  RestBehavior.class,
  SandboxIntegrationExceptionMappingTest.ProbeController.class,
  SandboxIntegrationExceptionMappingTest.TestConfig.class
})
class SandboxIntegrationExceptionMappingTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void timeout_maps_to_504_with_reason_code_key() throws Exception {
    mockMvc
        .perform(get("/__probe/timeout"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.errors.children.timeout").exists());
  }

  @Test
  void authentication_failed_maps_to_502() throws Exception {
    mockMvc
        .perform(get("/__probe/auth"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.errors.children.authentication_failed").exists());
  }

  @RestController
  static class ProbeController {

    @GetMapping("/__probe/timeout")
    public void timeout() {
      throw new SandboxIntegrationException(
          SandboxIntegrationException.ReasonCode.TIMEOUT, "remote took too long");
    }

    @GetMapping("/__probe/auth")
    public void auth() {
      throw new SandboxIntegrationException(
          SandboxIntegrationException.ReasonCode.AUTHENTICATION_FAILED, "bad token");
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestConfig {}
}
