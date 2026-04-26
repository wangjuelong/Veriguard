package io.veriguard.rest.veriguard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.VeriguardSandbox;
import io.veriguard.database.model.VeriguardSandboxNetworkRule;
import java.time.Instant;
import java.util.List;

public final class VeriguardDtos {

  private VeriguardDtos() {}

  public record CapabilityMatrixOutput(
      @JsonProperty("modules") List<CapabilityModuleOutput> modules,
      @JsonProperty("summary") CapabilitySummaryOutput summary) {}

  public record CapabilitySummaryOutput(
      @JsonProperty("prd_module_count") int prdModuleCount,
      @JsonProperty("acceptance_ready_count") int acceptanceReadyCount,
      @JsonProperty("external_integration_count") int externalIntegrationCount,
      @JsonProperty("total_use_case_templates") int totalUseCaseTemplates) {}

  public record CapabilityModuleOutput(
      @JsonProperty("module_key") String moduleKey,
      @JsonProperty("module_name") String moduleName,
      @JsonProperty("implementation_state") String implementationState,
      @JsonProperty("acceptance_ready") boolean acceptanceReady,
      @JsonProperty("controls") List<String> controls,
      @JsonProperty("external_integrations_required") List<String> externalIntegrationsRequired) {}

  public record AttackCatalogOutput(
      @JsonProperty("traffic_attack_types") List<AttackTypeOutput> trafficAttackTypes,
      @JsonProperty("host_attack_types") List<AttackTypeOutput> hostAttackTypes,
      @JsonProperty("custom_case_types") List<String> customCaseTypes,
      @JsonProperty("total_use_case_templates") int totalUseCaseTemplates,
      @JsonProperty("minimum_attack_type_requirement_met") boolean minimumAttackTypeRequirementMet,
      @JsonProperty("multiple_tuple_per_case_supported") boolean multipleTuplePerCaseSupported,
      @JsonProperty("generated_templates") List<UseCaseTemplateOutput> generatedTemplates) {}

  public record AttackTypeOutput(
      @JsonProperty("surface") String surface,
      @JsonProperty("attack_type") String attackType,
      @JsonProperty("template_count") int templateCount,
      @JsonProperty("prd_required") boolean prdRequired) {}

  public record UseCaseTemplateOutput(
      @JsonProperty("template_id") String templateId,
      @JsonProperty("surface") String surface,
      @JsonProperty("attack_type") String attackType,
      @JsonProperty("executor_kind") String executorKind,
      @JsonProperty("supports_multiple_tuples") boolean supportsMultipleTuples,
      @JsonProperty("mapped_custom_case_type") String mappedCustomCaseType) {}

  public record OrchestrationSchemaOutput(
      @JsonProperty("node_policy_fields") List<String> nodePolicyFields,
      @JsonProperty("execution_modes") List<String> executionModes,
      @JsonProperty("dependency_logic") List<String> dependencyLogic,
      @JsonProperty("soc_rule_match_fields") List<String> socRuleMatchFields,
      @JsonProperty("chain_result_states") List<String> chainResultStates) {}

  public record SandboxOutput(
      @JsonProperty("sandbox_id") String id,
      @JsonProperty("sandbox_name") String name,
      @JsonProperty("sandbox_description") String description,
      @JsonProperty("sandbox_provider_type") VeriguardSandbox.ProviderType providerType,
      @JsonProperty("sandbox_endpoint") String endpoint,
      @JsonProperty("sandbox_network_policy") VeriguardSandbox.NetworkPolicy networkPolicy,
      @JsonProperty("sandbox_network_rules") List<VeriguardSandboxNetworkRule> networkRules,
      @JsonProperty("sandbox_auto_restore_enabled") boolean autoRestoreEnabled,
      @JsonProperty("sandbox_supported_sample_types")
          List<VeriguardSandbox.SampleType> supportedSampleTypes,
      @JsonProperty("sandbox_status") VeriguardSandbox.Status status,
      @JsonProperty("sandbox_created_at") Instant createdAt,
      @JsonProperty("sandbox_updated_at") Instant updatedAt) {}
}
