package io.veriguard.combination.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PayloadTransformRegistry} — duplicate detection and lookup. */
class PayloadTransformRegistryTest {

  @Test
  void registry_indexes_transforms_by_type() {
    PayloadTransformRegistry registry =
        new PayloadTransformRegistry(
            List.of(new IdentityTransform(), new Base64EncodeTransform()));

    assertThat(registry.knownTypes()).containsExactlyInAnyOrder("identity", "base64_encode");
    assertThat(registry.get("identity")).isInstanceOf(IdentityTransform.class);
    assertThat(registry.get("base64_encode")).isInstanceOf(Base64EncodeTransform.class);
  }

  @Test
  void get_unknown_type_throws() {
    PayloadTransformRegistry registry =
        new PayloadTransformRegistry(List.of(new IdentityTransform()));
    assertThatThrownBy(() -> registry.get("does_not_exist"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does_not_exist");
  }

  @Test
  void duplicate_type_throws_at_construction() {
    PayloadTransform a = new IdentityTransform();
    PayloadTransform b =
        new PayloadTransform() {
          @Override
          public String type() {
            return "identity";
          }

          @Override
          public String apply(String basePayload, Map<String, Object> config) {
            return basePayload;
          }
        };
    assertThatThrownBy(() -> new PayloadTransformRegistry(List.of(a, b)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate PayloadTransform type");
  }

  @Test
  void transform_routes_to_correct_implementation() {
    PayloadTransformRegistry registry =
        new PayloadTransformRegistry(
            List.of(new IdentityTransform(), new Base64EncodeTransform()));
    assertThat(registry.transform("identity", "X", Map.of())).isEqualTo("X");
    assertThat(registry.transform("base64_encode", "X", Map.of())).isEqualTo("WA==");
  }
}
