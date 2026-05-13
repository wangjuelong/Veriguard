package io.veriguard.combination.severity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 单测 —— PR D4 BaseAttackTypeSeverityCatalog 查表逻辑. */
class BaseAttackTypeSeverityCatalogTest {

  private final BaseAttackTypeSeverityCatalog catalog = new BaseAttackTypeSeverityCatalog();

  @Test
  void known_type_returns_table_value() {
    // Arrange / Act / Assert
    assertThat(catalog.severityFor("sql_injection")).isEqualTo(90);
    assertThat(catalog.severityFor("command_execution")).isEqualTo(95);
    assertThat(catalog.severityFor("xss")).isEqualTo(60);
    assertThat(catalog.severityFor("oversized_upload")).isEqualTo(35);
  }

  @Test
  void unknown_type_returns_fallback() {
    assertThat(catalog.severityFor("totally_made_up_attack"))
        .isEqualTo(BaseAttackTypeSeverityCatalog.UNKNOWN_SEVERITY);
    assertThat(catalog.severityFor(null))
        .isEqualTo(BaseAttackTypeSeverityCatalog.UNKNOWN_SEVERITY);
    assertThat(catalog.severityFor(""))
        .isEqualTo(BaseAttackTypeSeverityCatalog.UNKNOWN_SEVERITY);
    assertThat(catalog.severityFor("   "))
        .isEqualTo(BaseAttackTypeSeverityCatalog.UNKNOWN_SEVERITY);
  }

  @Test
  void lookup_is_case_insensitive() {
    assertThat(catalog.severityFor("SQL_INJECTION")).isEqualTo(90);
    assertThat(catalog.severityFor("Sql_Injection")).isEqualTo(90);
    assertThat(catalog.severityFor("XSS")).isEqualTo(60);
  }
}
