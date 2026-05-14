package io.veriguard.rest.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Regression tests for {@link AgentOfflinePackApi#clientIp} — Important #5 audit-spoofing fix.
 *
 * <p>Pins the invariant that {@code X-Forwarded-For} is NOT trusted when populating the {@code
 * offline_pack_audit.exported_from_ip} column. The helper must always return the immediate
 * connection's remote IP so that an attacker cannot inject a fake source IP into the audit log
 * simply by setting the {@code X-Forwarded-For} header on the export request.
 */
class AgentOfflinePackApiClientIpTest {

  @Test
  @DisplayName("clientIp: null request → null")
  void clientIp_nullRequestReturnsNull() {
    assertThat(AgentOfflinePackApi.clientIp(null)).isNull();
  }

  @Test
  @DisplayName("clientIp: 仅有 remoteAddr → 返回 remoteAddr")
  void clientIp_usesRemoteAddrWhenNoXff() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.10");

    assertThat(AgentOfflinePackApi.clientIp(request)).isEqualTo("192.0.2.10");
  }

  @Test
  @DisplayName("clientIp: X-Forwarded-For 单值不被信任 — 返回 remoteAddr 而不是 XFF（防 audit spoofing）")
  void clientIp_ignoresXForwardedForSingleValue() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.10");
    request.addHeader("X-Forwarded-For", "10.0.0.1");

    assertThat(AgentOfflinePackApi.clientIp(request))
        .as("X-Forwarded-For must NEVER override remoteAddr — audit attribution must be truthful")
        .isEqualTo("192.0.2.10");
  }

  @Test
  @DisplayName("clientIp: X-Forwarded-For 多值（client, proxy1, proxy2）不被信任 — 返回 remoteAddr")
  void clientIp_ignoresXForwardedForMultiValue() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.10");
    request.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1, 192.168.1.1");

    assertThat(AgentOfflinePackApi.clientIp(request))
        .as("Even comma-separated XFF chains must be ignored — only remoteAddr is trusted")
        .isEqualTo("192.0.2.10");
  }

  @Test
  @DisplayName("clientIp: 空 X-Forwarded-For header 不影响 remoteAddr 返回")
  void clientIp_handlesBlankXForwardedFor() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.10");
    request.addHeader("X-Forwarded-For", "");

    assertThat(AgentOfflinePackApi.clientIp(request)).isEqualTo("192.0.2.10");
  }
}
