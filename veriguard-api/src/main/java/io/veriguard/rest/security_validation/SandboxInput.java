package io.veriguard.rest.security_validation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.VeriguardSandbox;
import io.veriguard.database.model.VeriguardSandbox.SampleType;
import io.veriguard.database.model.VeriguardSandboxNetworkRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SandboxInput(
    @JsonProperty("sandbox_name") @NotBlank String name,
    @JsonProperty("sandbox_description") String description,
    @JsonProperty("sandbox_network_policy") @NotNull VeriguardSandbox.NetworkPolicy networkPolicy,
    @JsonProperty("sandbox_network_rules") @NotNull
        List<@Valid VeriguardSandboxNetworkRule> networkRules,
    @JsonProperty("sandbox_auto_restore_enabled") boolean autoRestoreEnabled,
    @JsonProperty("sandbox_supported_sample_types") @NotNull
        List<@NotNull SampleType> supportedSampleTypes,
    @JsonProperty("sandbox_status") @NotNull VeriguardSandbox.Status status) {}
