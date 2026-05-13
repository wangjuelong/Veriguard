package io.veriguard.combination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BaseAttackPayloadCatalogTest {

  private final BaseAttackPayloadCatalog catalog = new BaseAttackPayloadCatalog();

  @Test
  void known_base_type_returns_payload() {
    assertThat(catalog.defaultPayloadFor("sql_injection")).contains("OR");
    assertThat(catalog.defaultPayloadFor("xss")).contains("script");
    assertThat(catalog.defaultPayloadFor("xxe")).contains("ENTITY");
  }

  @Test
  void unknown_base_type_throws_fail_fast() {
    assertThatThrownBy(() -> catalog.defaultPayloadFor("nonexistent_type"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown base_attack_type");
  }

  @Test
  void blank_base_type_throws() {
    assertThatThrownBy(() -> catalog.defaultPayloadFor(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> catalog.defaultPayloadFor(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isKnown_works() {
    assertThat(catalog.isKnown("sql_injection")).isTrue();
    assertThat(catalog.isKnown("nonexistent")).isFalse();
    assertThat(catalog.isKnown(null)).isFalse();
  }
}
