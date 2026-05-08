package io.veriguard.rest.validation_parameter_set;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.SocCorrelationRuleRef;
import io.veriguard.database.model.TargetRef;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ValidationParameterSetInput(
    @JsonProperty("parameter_set_name") @NotBlank String name,
    @JsonProperty("parameter_set_description") String description,
    @JsonProperty("parameter_set_default_targets") List<TargetRef> defaultTargets,
    @JsonProperty("parameter_set_prevention_expected_score") @Min(0)
        int preventionExpectedScore,
    @JsonProperty("parameter_set_prevention_expiration_seconds") @Positive
        int preventionExpirationSeconds,
    @JsonProperty("parameter_set_detection_expected_score") @Min(0)
        int detectionExpectedScore,
    @JsonProperty("parameter_set_detection_expiration_seconds") @Positive
        int detectionExpirationSeconds,
    @JsonProperty("parameter_set_soc_correlation_rules") List<SocCorrelationRuleRef> socCorrelationRules,
    @JsonProperty("parameter_set_tag_ids") Set<UUID> tagIds) {}
