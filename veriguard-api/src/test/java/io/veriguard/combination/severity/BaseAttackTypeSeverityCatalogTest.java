package io.veriguard.combination.severity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.combination.BaseAttackType;
import io.veriguard.database.model.combination.BaseAttackTypeCategory;
import io.veriguard.database.model.combination.BaseAttackTypeTargetLayer;
import io.veriguard.database.repository.BaseAttackTypeRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 单测 —— PR D4 / B5 BaseAttackTypeSeverityCatalog 查表逻辑（DB-driven + fallback）. */
class BaseAttackTypeSeverityCatalogTest {

  // ============================================================
  // Legacy / fallback behavior (no-arg ctor — keeps D4 contract)
  // ============================================================

  @Test
  void no_arg_ctor_returns_legacy_table_value() {
    BaseAttackTypeSeverityCatalog catalog = new BaseAttackTypeSeverityCatalog();
    assertThat(catalog.isDbBacked()).isFalse();
    assertThat(catalog.severityFor("sql_injection")).isEqualTo(90);
    assertThat(catalog.severityFor("command_execution")).isEqualTo(95);
    assertThat(catalog.severityFor("xss")).isEqualTo(60);
    assertThat(catalog.severityFor("oversized_upload")).isEqualTo(35);
  }

  @Test
  void unknown_type_returns_fallback() {
    BaseAttackTypeSeverityCatalog catalog = new BaseAttackTypeSeverityCatalog();
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
    BaseAttackTypeSeverityCatalog catalog = new BaseAttackTypeSeverityCatalog();
    assertThat(catalog.severityFor("SQL_INJECTION")).isEqualTo(90);
    assertThat(catalog.severityFor("Sql_Injection")).isEqualTo(90);
    assertThat(catalog.severityFor("XSS")).isEqualTo(60);
  }

  // ============================================================
  // PR B5 — DB-driven mode + DB empty fallback
  // ============================================================

  @Test
  void db_backed_load_replaces_legacy_severity() {
    BaseAttackTypeRepository repo = mock(BaseAttackTypeRepository.class);
    when(repo.findAll())
        .thenReturn(
            List.of(
                newType("sql_injection", 99),
                newType("custom_zero_day", 88),
                newType("xss_reflected", 65)));
    BaseAttackTypeSeverityCatalog catalog = new BaseAttackTypeSeverityCatalog(repo);
    catalog.loadFromDb();

    assertThat(catalog.isDbBacked()).isTrue();
    // DB value supersedes the D4 legacy 90
    assertThat(catalog.severityFor("sql_injection")).isEqualTo(99);
    assertThat(catalog.severityFor("custom_zero_day")).isEqualTo(88);
    assertThat(catalog.severityFor("XSS_REFLECTED")).isEqualTo(65);
    assertThat(catalog.cacheSize()).isEqualTo(3);
  }

  @Test
  void db_empty_falls_back_to_legacy_severity() {
    BaseAttackTypeRepository repo = mock(BaseAttackTypeRepository.class);
    when(repo.findAll()).thenReturn(List.of());
    BaseAttackTypeSeverityCatalog catalog = new BaseAttackTypeSeverityCatalog(repo);
    catalog.loadFromDb();

    assertThat(catalog.isDbBacked()).isFalse();
    assertThat(catalog.severityFor("sql_injection")).isEqualTo(90);
    assertThat(catalog.cacheSize()).isEqualTo(BaseAttackTypeSeverityCatalog.LEGACY_SEVERITY.size());
  }

  @Test
  void unknown_type_after_db_load_still_returns_unknown_severity() {
    BaseAttackTypeRepository repo = mock(BaseAttackTypeRepository.class);
    when(repo.findAll()).thenReturn(List.of(newType("sql_injection", 99)));
    BaseAttackTypeSeverityCatalog catalog = new BaseAttackTypeSeverityCatalog(repo);
    catalog.loadFromDb();

    assertThat(catalog.severityFor("totally_unknown"))
        .isEqualTo(BaseAttackTypeSeverityCatalog.UNKNOWN_SEVERITY);
  }

  private static BaseAttackType newType(String name, int score) {
    BaseAttackType t = new BaseAttackType();
    t.setName(name);
    t.setCategory(BaseAttackTypeCategory.web_injection);
    t.setDisplayLabel(name);
    t.setSeverityScore(score);
    t.setTargetLayer(BaseAttackTypeTargetLayer.application);
    return t;
  }
}
