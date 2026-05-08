package io.veriguard.rest.validation_parameter_set;

import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.veriguard.database.model.Tag;
import io.veriguard.database.model.ValidationParameterSet;
import io.veriguard.database.repository.TagRepository;
import io.veriguard.database.repository.ValidationParameterSetRepository;
import io.veriguard.rest.validation_parameter_set.exceptions.CannotDeleteTemplateException;
import io.veriguard.rest.validation_parameter_set.exceptions.CannotEditTemplateException;
import io.veriguard.rest.validation_parameter_set.exceptions.DuplicateNameException;
import io.veriguard.rest.validation_parameter_set.exceptions.ParameterSetNotFoundException;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ValidationParameterSetService {

  private static final int MAX_SCORE = 100;

  private final ValidationParameterSetRepository repository;
  private final TagRepository tagRepository;

  public ValidationParameterSet create(@NotNull final ValidationParameterSetInput input) {
    validateScores(input);
    if (repository.existsByName(input.name())) {
      throw new DuplicateNameException(input.name());
    }
    var entity = new ValidationParameterSet();
    applyInput(entity, input);
    return repository.save(entity);
  }

  public ValidationParameterSet get(@NotNull final UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ParameterSetNotFoundException(id));
  }

  public ValidationParameterSet update(
      @NotNull final UUID id, @NotNull final ValidationParameterSetInput input) {
    validateScores(input);
    var entity = get(id);
    if (entity.isTemplate()) {
      throw new CannotEditTemplateException(id);
    }
    if (!entity.getName().equals(input.name()) && repository.existsByName(input.name())) {
      throw new DuplicateNameException(input.name());
    }
    applyInput(entity, input);
    return repository.save(entity);
  }

  public void delete(@NotNull final UUID id) {
    var entity = get(id);
    if (entity.isTemplate()) {
      throw new CannotDeleteTemplateException(id);
    }
    try {
      repository.delete(entity);
      // FK ON DELETE RESTRICT 由 V3 schema 兜底；如有引用会抛 DataIntegrityViolationException
    } catch (DataIntegrityViolationException e) {
      throw e;
    }
  }

  public ValidationParameterSet duplicate(
      @NotNull final UUID sourceId, @NotNull final String newName) {
    var source = get(sourceId);
    if (repository.existsByName(newName)) {
      throw new DuplicateNameException(newName);
    }
    var copy = new ValidationParameterSet();
    copy.setName(newName);
    copy.setDescription(source.getDescription());
    copy.setTemplate(false); // 复制出来的永远不是模板
    copy.setDefaultTargets(new ArrayList<>(source.getDefaultTargets()));
    copy.setPreventionExpectedScore(source.getPreventionExpectedScore());
    copy.setPreventionExpirationSeconds(source.getPreventionExpirationSeconds());
    copy.setDetectionExpectedScore(source.getDetectionExpectedScore());
    copy.setDetectionExpirationSeconds(source.getDetectionExpirationSeconds());
    copy.setSocCorrelationRules(new ArrayList<>(source.getSocCorrelationRules()));
    copy.setTags(new HashSet<>(source.getTags()));
    return repository.save(copy);
  }

  public Page<ValidationParameterSet> search(@NotNull final SearchPaginationInput input) {
    return buildPaginationJPA(
        repository::findAll, input, ValidationParameterSet.class);
  }

  private void validateScores(final ValidationParameterSetInput input) {
    if (input.preventionExpectedScore() < 0 || input.preventionExpectedScore() > MAX_SCORE) {
      throw new IllegalArgumentException(
          "prevention_expected_score must be 0..100, got " + input.preventionExpectedScore());
    }
    if (input.detectionExpectedScore() < 0 || input.detectionExpectedScore() > MAX_SCORE) {
      throw new IllegalArgumentException(
          "detection_expected_score must be 0..100, got " + input.detectionExpectedScore());
    }
    if (input.preventionExpirationSeconds() <= 0) {
      throw new IllegalArgumentException(
          "prevention_expiration_seconds must be positive, got "
              + input.preventionExpirationSeconds());
    }
    if (input.detectionExpirationSeconds() <= 0) {
      throw new IllegalArgumentException(
          "detection_expiration_seconds must be positive, got "
              + input.detectionExpirationSeconds());
    }
  }

  private void applyInput(
      final ValidationParameterSet entity, final ValidationParameterSetInput input) {
    entity.setName(input.name());
    entity.setDescription(input.description());
    entity.setDefaultTargets(
        input.defaultTargets() != null ? input.defaultTargets() : new ArrayList<>());
    entity.setPreventionExpectedScore(input.preventionExpectedScore());
    entity.setPreventionExpirationSeconds(input.preventionExpirationSeconds());
    entity.setDetectionExpectedScore(input.detectionExpectedScore());
    entity.setDetectionExpirationSeconds(input.detectionExpirationSeconds());
    entity.setSocCorrelationRules(
        input.socCorrelationRules() != null ? input.socCorrelationRules() : new ArrayList<>());
    if (input.tagIds() != null && !input.tagIds().isEmpty()) {
      List<String> tagIdStrings =
          input.tagIds().stream().map(UUID::toString).toList();
      Set<Tag> tags = new HashSet<>();
      tagRepository.findAllById(tagIdStrings).forEach(tags::add);
      entity.setTags(tags);
    } else {
      entity.setTags(new HashSet<>());
    }
  }
}
