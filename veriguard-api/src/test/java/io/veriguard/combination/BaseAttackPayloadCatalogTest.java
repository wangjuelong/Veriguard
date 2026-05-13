package io.veriguard.combination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.combination.BaseAttackType;
import io.veriguard.database.model.combination.BaseAttackTypeCategory;
import io.veriguard.database.model.combination.BaseAttackTypeTargetLayer;
import io.veriguard.database.repository.BaseAttackTypeRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaseAttackPayloadCatalogTest {

  // ============================================================
  // Legacy / fallback behavior (no-arg ctor — keeps D2 contract)
  // ============================================================

  @Test
  void no_arg_ctor_falls_back_to_legacy_defaults() {
    BaseAttackPayloadCatalog catalog = new BaseAttackPayloadCatalog();
    assertThat(catalog.isDbBacked()).isFalse();
    assertThat(catalog.defaultPayloadFor("sql_injection")).contains("OR");
    assertThat(catalog.defaultPayloadFor("xss")).contains("script");
    assertThat(catalog.defaultPayloadFor("xxe")).contains("ENTITY");
  }

  @Test
  void unknown_base_type_throws_fail_fast() {
    BaseAttackPayloadCatalog catalog = new BaseAttackPayloadCatalog();
    assertThatThrownBy(() -> catalog.defaultPayloadFor("nonexistent_type"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown base_attack_type");
  }

  @Test
  void blank_base_type_throws() {
    BaseAttackPayloadCatalog catalog = new BaseAttackPayloadCatalog();
    assertThatThrownBy(() -> catalog.defaultPayloadFor(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> catalog.defaultPayloadFor(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isKnown_works() {
    BaseAttackPayloadCatalog catalog = new BaseAttackPayloadCatalog();
    assertThat(catalog.isKnown("sql_injection")).isTrue();
    assertThat(catalog.isKnown("nonexistent")).isFalse();
    assertThat(catalog.isKnown(null)).isFalse();
  }

  // ============================================================
  // PR B5 — DB-driven mode + DB empty fallback
  // ============================================================

  @Test
  void db_backed_load_replaces_legacy_defaults() {
    BaseAttackTypeRepository repo = mock(BaseAttackTypeRepository.class);
    when(repo.findAll())
        .thenReturn(
            List.of(
                newType("sql_injection", "INJECT_FROM_DB"),
                newType("custom_zero_day", "ZERO_DAY_PAYLOAD"),
                newType("xss_reflected", "<svg/onload=alert(1)>")));
    BaseAttackPayloadCatalog catalog = new BaseAttackPayloadCatalog(repo);
    catalog.loadFromDb();

    assertThat(catalog.isDbBacked()).isTrue();
    assertThat(catalog.defaultPayloadFor("sql_injection")).isEqualTo("INJECT_FROM_DB");
    assertThat(catalog.defaultPayloadFor("custom_zero_day")).isEqualTo("ZERO_DAY_PAYLOAD");
    assertThat(catalog.defaultPayloadFor("XSS_REFLECTED")).contains("svg");
    assertThat(catalog.knownTypes()).hasSize(3);
  }

  @Test
  void db_empty_falls_back_to_legacy_hardcoded() {
    BaseAttackTypeRepository repo = mock(BaseAttackTypeRepository.class);
    when(repo.findAll()).thenReturn(List.of());
    BaseAttackPayloadCatalog catalog = new BaseAttackPayloadCatalog(repo);
    catalog.loadFromDb();

    assertThat(catalog.isDbBacked()).isFalse();
    assertThat(catalog.defaultPayloadFor("sql_injection")).contains("OR");
    assertThat(catalog.knownTypes()).hasSize(BaseAttackPayloadCatalog.LEGACY_DEFAULTS.size());
  }

  @Test
  void unknown_after_db_load_still_fail_fast() {
    BaseAttackTypeRepository repo = mock(BaseAttackTypeRepository.class);
    when(repo.findAll()).thenReturn(List.of(newType("sql_injection", "x")));
    BaseAttackPayloadCatalog catalog = new BaseAttackPayloadCatalog(repo);
    catalog.loadFromDb();

    assertThatThrownBy(() -> catalog.defaultPayloadFor("totally_unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown base_attack_type");
  }

  private static BaseAttackType newType(String name, String payload) {
    BaseAttackType t = new BaseAttackType();
    t.setName(name);
    t.setCategory(BaseAttackTypeCategory.web_injection);
    t.setDisplayLabel(name);
    t.setSeverityScore(50);
    t.setDefaultPayload(payload);
    t.setTargetLayer(BaseAttackTypeTargetLayer.application);
    return t;
  }
}
