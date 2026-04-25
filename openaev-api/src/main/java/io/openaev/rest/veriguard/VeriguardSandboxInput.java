package io.openaev.rest.veriguard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.VeriguardSandbox;
import io.openaev.database.model.VeriguardSandboxNetworkRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record VeriguardSandboxInput(
    @JsonProperty("sandbox_name") @NotBlank String name,
    @JsonProperty("sandbox_description") String description,
    @JsonProperty("sandbox_provider_type") @NotNull VeriguardSandbox.ProviderType providerType,
    @JsonProperty("sandbox_endpoint") @NotBlank String endpoint,
    @JsonProperty("sandbox_network_policy") @NotNull VeriguardSandbox.NetworkPolicy networkPolicy,
    @JsonProperty("sandbox_network_rules")
        @NotEmpty
        List<@Valid VeriguardSandboxNetworkRule> networkRules,
    @JsonProperty("sandbox_auto_restore_enabled") @AssertTrue boolean autoRestoreEnabled,
    @JsonProperty("sandbox_supported_sample_types")
        @NotEmpty
        List<@NotNull VeriguardSandbox.SampleType> supportedSampleTypes,
    @JsonProperty("sandbox_status") @NotNull VeriguardSandbox.Status status) {}
