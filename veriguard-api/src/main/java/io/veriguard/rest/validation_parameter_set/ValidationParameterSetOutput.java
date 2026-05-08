package io.veriguard.rest.validation_parameter_set;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.SocCorrelationRuleRef;
import io.veriguard.database.model.TargetRef;
import io.veriguard.database.model.ValidationParameterSet;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ValidationParameterSetOutput(
    @JsonProperty("parameter_set_id") UUID id,
    @JsonProperty("parameter_set_name") String name,
    @JsonProperty("parameter_set_description") String description,
    @JsonProperty("parameter_set_is_template") boolean isTemplate,
    @JsonProperty("parameter_set_default_targets") List<TargetRef> defaultTargets,
    @JsonProperty("parameter_set_prevention_expected_score") int preventionExpectedScore,
    @JsonProperty("parameter_set_prevention_expiration_seconds") int preventionExpirationSeconds,
    @JsonProperty("parameter_set_detection_expected_score") int detectionExpectedScore,
    @JsonProperty("parameter_set_detection_expiration_seconds") int detectionExpirationSeconds,
    @JsonProperty("parameter_set_soc_correlation_rules") List<SocCorrelationRuleRef> socCorrelationRules,
    @JsonProperty("parameter_set_tag_ids") Set<UUID> tagIds,
    @JsonProperty("parameter_set_created_at") Instant createdAt,
    @JsonProperty("parameter_set_updated_at") Instant updatedAt) {

  public static ValidationParameterSetOutput from(ValidationParameterSet entity) {
    return new ValidationParameterSetOutput(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.isTemplate(),
        entity.getDefaultTargets(),
        entity.getPreventionExpectedScore(),
        entity.getPreventionExpirationSeconds(),
        entity.getDetectionExpectedScore(),
        entity.getDetectionExpirationSeconds(),
        entity.getSocCorrelationRules(),
        entity.getTags() == null
            ? Set.of()
            : entity.getTags().stream()
                .map(t -> UUID.fromString(t.getId()))
                .collect(Collectors.toSet()),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
