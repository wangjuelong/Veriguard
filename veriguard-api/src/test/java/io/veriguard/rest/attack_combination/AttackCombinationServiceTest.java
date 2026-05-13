package io.veriguard.rest.attack_combination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.veriguard.combination.transform.IdentityTransform;
import io.veriguard.combination.transform.PayloadTransformRegistry;
import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import io.veriguard.database.repository.BypassDimensionRepository;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.BypassDimensionPageOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.CombinationPreviewOutput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AttackCombinationServiceTest {

  @Mock private BypassDimensionRepository repository;
  private PayloadTransformRegistry registry;
  private AttackCombinationService service;

  @BeforeEach
  void setUp() {
    registry = new PayloadTransformRegistry(List.of(new IdentityTransform()));
    service = new AttackCombinationService(repository, registry);
  }

  @Test
  void listDimensions_without_category_uses_findAll_paged() {
    BypassDimension d = makeDim("d1", "noise.x", BypassDimensionCategory.noise, "identity");
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(d)));

    BypassDimensionPageOutput out = service.listDimensions(Optional.empty(), 0, 20);
    assertThat(out.content()).hasSize(1);
    assertThat(out.content().getFirst().name()).isEqualTo("noise.x");
    assertThat(out.totalElements()).isEqualTo(1);
  }

  @Test
  void listDimensions_with_category_filters() {
    BypassDimension d = makeDim("d2", "encoding.y", BypassDimensionCategory.encoding, "identity");
    when(repository.findAllByCategory(eq(BypassDimensionCategory.encoding), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(d)));

    BypassDimensionPageOutput out =
        service.listDimensions(Optional.of(BypassDimensionCategory.encoding), 0, 20);
    assertThat(out.content()).hasSize(1);
    assertThat(out.content().getFirst().category()).isEqualTo("encoding");
  }

  @Test
  void listDimensions_with_invalid_page_throws() {
    assertThatThrownBy(() -> service.listDimensions(Optional.empty(), -1, 20))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void listDimensions_with_size_over_limit_throws() {
    assertThatThrownBy(() -> service.listDimensions(Optional.empty(), 0, 501))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preview_returns_cartesian_size_and_capped_samples() {
    BypassDimension d1 = makeDim("dim-1", "noise.a", BypassDimensionCategory.noise, "identity");
    BypassDimension d2 = makeDim("dim-2", "noise.b", BypassDimensionCategory.noise, "identity");
    when(repository.findById("dim-1")).thenReturn(Optional.of(d1));
    when(repository.findById("dim-2")).thenReturn(Optional.of(d2));

    CombinationPreviewOutput out =
        service.preview(List.of("sql_injection", "xss"), List.of("dim-1", "dim-2"), "PAYLOAD");

    // 2 base types × 2 dims = 4 combinations
    assertThat(out.totalCombinations()).isEqualTo(4);
    assertThat(out.samples()).hasSize(4);
    assertThat(out.samples()).allSatisfy(s -> assertThat(s.previewPayload()).isEqualTo("PAYLOAD"));
    assertThat(out.previewBasePayload()).isEqualTo("PAYLOAD");
  }

  @Test
  void preview_caps_samples_at_PREVIEW_SAMPLE_SIZE() {
    // 6 base × 6 dim = 36 combos, only 10 samples returned
    when(repository.findById(any()))
        .thenAnswer(
            inv -> {
              String id = inv.getArgument(0);
              return Optional.of(makeDim(id, "noise." + id, BypassDimensionCategory.noise, "identity"));
            });

    List<String> baseTypes = List.of("a", "b", "c", "d", "e", "f");
    List<String> dimIds = List.of("1", "2", "3", "4", "5", "6");
    CombinationPreviewOutput out = service.preview(baseTypes, dimIds, "PL");
    assertThat(out.totalCombinations()).isEqualTo(36);
    assertThat(out.samples()).hasSize(AttackCombinationService.PREVIEW_SAMPLE_SIZE);
  }

  @Test
  void preview_with_empty_base_types_throws() {
    assertThatThrownBy(() -> service.preview(List.of(), List.of("d"), "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preview_with_unknown_dim_id_throws() {
    when(repository.findById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.preview(List.of("sql"), List.of("missing"), "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing");
  }

  private static BypassDimension makeDim(
      String id, String name, BypassDimensionCategory cat, String transformType) {
    BypassDimension d = new BypassDimension();
    d.setId(id);
    d.setName(name);
    d.setCategory(cat);
    d.setTransformType(transformType);
    d.setTransformConfig(Map.of());
    return d;
  }
}
