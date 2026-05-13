package io.veriguard.combination.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.combination.CombinationInstance;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link HttpInjectExecutor}. PR D5 Step 5. */
class HttpInjectExecutorTest {

  private final HttpInjectExecutor executor = new HttpInjectExecutor();

  @Test
  void supports_all_eleven_web_attack_base_types() {
    String[] expected = {
      "sql_injection",
      "xss",
      "xxe",
      "ssrf",
      "ssti",
      "command_execution",
      "directory_traversal",
      "csrf",
      "weak_credential",
      "upload_bypass",
      "oversized_upload"
    };
    for (String base : expected) {
      assertThat(executor.supports(base))
          .as("base_type=%s should be supported", base)
          .isTrue();
    }
    assertThat(HttpInjectExecutor.SUPPORTED_BASE_TYPES).hasSize(expected.length);
  }

  @Test
  void supports_unrelated_or_pcap_base_types_returns_false() {
    assertThat(executor.supports("pcap_replay")).isFalse();
    assertThat(executor.supports("rogue_router_advertisement")).isFalse();
    assertThat(executor.supports("unknown_type")).isFalse();
    assertThat(executor.supports("")).isFalse();
  }

  @Test
  void supports_null_returns_false_not_throws() {
    assertThat(executor.supports(null)).isFalse();
  }

  @Test
  void execute_skeleton_throws_unsupported_operation() {
    CombinationInstance instance =
        new CombinationInstance(
            "run-1",
            "sql_injection:dim-1",
            "sql_injection",
            "dim-1",
            "asset-1",
            "' OR 1=1 --",
            "' OR 1=1 --");
    assertThatThrownBy(() -> executor.execute(instance))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("HttpInjectExecutor skeleton")
        .hasMessageContaining("sql_injection");
  }

  @Test
  void execute_null_instance_throws_illegal_argument() {
    assertThatThrownBy(() -> executor.execute(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
