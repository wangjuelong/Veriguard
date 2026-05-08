package io.veriguard.rest.validation_parameter_set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.ValidationParameterSet;
import io.veriguard.database.repository.TagRepository;
import io.veriguard.database.repository.ValidationParameterSetRepository;
import io.veriguard.rest.validation_parameter_set.exceptions.CannotDeleteTemplateException;
import io.veriguard.rest.validation_parameter_set.exceptions.CannotEditTemplateException;
import io.veriguard.rest.validation_parameter_set.exceptions.DuplicateNameException;
import io.veriguard.rest.validation_parameter_set.exceptions.ParameterSetNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidationParameterSetServiceTest {

  @Mock ValidationParameterSetRepository repository;
  @Mock TagRepository tagRepository;
  @InjectMocks ValidationParameterSetService service;

  private static ValidationParameterSetInput sampleInput(String name) {
    return new ValidationParameterSetInput(name, "desc", null, 100, 1800, 100, 1800, null, null);
  }

  @Test
  @DisplayName("create with unique name persists entity")
  void create_uniqueName_persists() {
    var input = sampleInput("Custom-A");
    when(repository.existsByName("Custom-A")).thenReturn(false);
    when(repository.save(any(ValidationParameterSet.class))).thenAnswer(inv -> inv.getArgument(0));

    var saved = service.create(input);

    assertThat(saved.getName()).isEqualTo("Custom-A");
    assertThat(saved.getPreventionExpectedScore()).isEqualTo(100);
    assertThat(saved.isTemplate()).isFalse();
    verify(repository).save(any());
  }

  @Test
  @DisplayName("create with duplicate name throws")
  void create_duplicateName_throws() {
    var input = sampleInput("Strict");
    when(repository.existsByName("Strict")).thenReturn(true);

    assertThatThrownBy(() -> service.create(input))
        .isInstanceOf(DuplicateNameException.class);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("create rejects out-of-range prevention score")
  void create_invalidPreventionScore_throws() {
    var input = new ValidationParameterSetInput("X", null, null, 150, 1800, 100, 1800, null, null);
    assertThatThrownBy(() -> service.create(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("prevention_expected_score");
  }

  @Test
  @DisplayName("create rejects non-positive expiration")
  void create_invalidExpiration_throws() {
    var input = new ValidationParameterSetInput("X", null, null, 100, 0, 100, 1800, null, null);
    assertThatThrownBy(() -> service.create(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("prevention_expiration_seconds");
  }

  @Test
  @DisplayName("update on template throws CannotEditTemplateException")
  void update_template_throws() {
    var id = UUID.randomUUID();
    var template = templateEntity(id, "严格");
    when(repository.findById(id)).thenReturn(Optional.of(template));

    assertThatThrownBy(() -> service.update(id, sampleInput("严格-edited")))
        .isInstanceOf(CannotEditTemplateException.class);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("delete on template throws CannotDeleteTemplateException")
  void delete_template_throws() {
    var id = UUID.randomUUID();
    var template = templateEntity(id, "严格");
    when(repository.findById(id)).thenReturn(Optional.of(template));

    assertThatThrownBy(() -> service.delete(id))
        .isInstanceOf(CannotDeleteTemplateException.class);
    verify(repository, never()).delete(any(ValidationParameterSet.class));
  }

  @Test
  @DisplayName("get of non-existent throws ParameterSetNotFoundException")
  void get_missing_throws() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(id))
        .isInstanceOf(ParameterSetNotFoundException.class);
  }

  @Test
  @DisplayName("update with same name allowed (does not raise duplicate)")
  void update_sameName_allowed() {
    var id = UUID.randomUUID();
    var entity = nonTemplateEntity(id, "Custom-A");
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    lenient().when(repository.existsByName("Custom-A")).thenReturn(true); // existing self
    when(repository.save(any(ValidationParameterSet.class))).thenAnswer(inv -> inv.getArgument(0));

    var input = sampleInput("Custom-A");
    var updated = service.update(id, input);

    assertThat(updated.getName()).isEqualTo("Custom-A");
  }

  @Test
  @DisplayName("duplicate copies all fields except is_template (always false)")
  void duplicate_resetsTemplateFlag() {
    var sourceId = UUID.randomUUID();
    var source = templateEntity(sourceId, "严格");
    source.setPreventionExpectedScore(100);
    source.setDetectionExpectedScore(100);
    when(repository.findById(sourceId)).thenReturn(Optional.of(source));
    when(repository.existsByName("严格-copy")).thenReturn(false);
    when(repository.save(any(ValidationParameterSet.class))).thenAnswer(inv -> inv.getArgument(0));

    var copy = service.duplicate(sourceId, "严格-copy");

    assertThat(copy.getName()).isEqualTo("严格-copy");
    assertThat(copy.isTemplate()).isFalse();
    assertThat(copy.getPreventionExpectedScore()).isEqualTo(100);
  }

  private static ValidationParameterSet templateEntity(UUID id, String name) {
    var e = new ValidationParameterSet();
    e.setId(id);
    e.setName(name);
    e.setTemplate(true);
    return e;
  }

  private static ValidationParameterSet nonTemplateEntity(UUID id, String name) {
    var e = new ValidationParameterSet();
    e.setId(id);
    e.setName(name);
    e.setTemplate(false);
    return e;
  }
}
