package io.veriguard.rest.payload.form;

import static io.veriguard.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.model.*;
import io.veriguard.rest.injector_contract.form.NodeContractDomainDTO;
import io.veriguard.rest.payload.output_parser.OutputParserInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;

// PR B7: WebAttack 子类型字段（method / url / body / headers / cookies / expected_*）

@Data
public class PayloadUpsertInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_type")
  private String type;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_name")
  private String name;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_source")
  private Payload.PAYLOAD_SOURCE source;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_status")
  private Payload.PAYLOAD_STATUS status;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_external_id")
  private String externalId;

  @JsonProperty("payload_collector")
  private String collector;

  @JsonProperty("payload_platforms")
  private Endpoint.PLATFORM_TYPE[] platforms;

  @JsonProperty("payload_execution_arch")
  private Payload.PAYLOAD_EXECUTION_ARCH executionArch =
      Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES;

  @JsonProperty("payload_expectations")
  @NotNull
  private AttackChainNodeExpectation.EXPECTATION_TYPE[] expectations =
      new AttackChainNodeExpectation.EXPECTATION_TYPE[] {
        AttackChainNodeExpectation.EXPECTATION_TYPE.PREVENTION,
        AttackChainNodeExpectation.EXPECTATION_TYPE.DETECTION
      };

  @JsonProperty("payload_description")
  private String description;

  @JsonProperty("command_executor")
  @Schema(nullable = true)
  private String executor;

  @JsonProperty("command_content")
  @Schema(nullable = true)
  private String content;

  @JsonProperty("executable_file")
  private String executableFile;

  @JsonProperty("file_drop_file")
  private String fileDropFile;

  @JsonProperty("dns_resolution_hostname")
  private String hostname;

  // -- PR B7: WebAttack subtype fields --
  // Field names must match WebAttackPayload getters/setters so BeanUtils.copyProperties
  // (PayloadUtils.copyProperties) auto-binds them. Service layer enforces method/url presence
  // when payload_type == "WebAttack".

  @JsonProperty("web_request_method")
  @Schema(nullable = true, description = "HTTP method; required when payload_type=WebAttack")
  private String method;

  @JsonProperty("web_request_url")
  @Schema(nullable = true, description = "Target URL; required when payload_type=WebAttack")
  private String url;

  @JsonProperty("web_request_body")
  @Schema(nullable = true)
  private String body;

  @JsonProperty("web_request_body_type")
  @Schema(nullable = true)
  private String bodyType;

  @JsonProperty("web_request_timeout_seconds")
  @Schema(nullable = true)
  private Integer timeoutSeconds;

  @JsonProperty("web_request_cookies")
  @Schema(nullable = true, description = "Raw Cookie header (e.g. \"a=1; b=2\")")
  private String cookies;

  @JsonProperty("web_request_headers")
  private List<WebRequestHeaderEntry> headers = new ArrayList<>();

  @JsonProperty("expected_status_codes")
  private List<Integer> expectedStatusCodes = new ArrayList<>();

  @JsonProperty("expected_body_regex")
  @Schema(nullable = true)
  private String expectedBodyRegex;

  // -- PR #68 follow-up: HostAttack subtype fields (HIDS P1.1) --
  // Property names mirror HostAttackPayload getters/setters so PayloadUtils.copyProperties (via
  // BeanUtils.copyProperties) auto-binds them. Service layer enforces required fields when
  // payload_type == "HostAttack".

  @JsonProperty("hids_category")
  @Schema(nullable = true, description = "Required when payload_type=HostAttack")
  private String hidsCategory;

  @JsonProperty("hids_execution_mode")
  @Schema(nullable = true, description = "Required when payload_type=HostAttack")
  private String hidsExecutionMode;

  @JsonProperty("hids_command_template")
  @Schema(nullable = true, description = "Required when payload_type=HostAttack")
  private String hidsCommandTemplate;

  @JsonProperty("hids_artifact_path")
  @Schema(nullable = true)
  private String hidsArtifactPath;

  @JsonProperty("hids_expected_artifacts")
  private List<String> hidsExpectedArtifacts = new ArrayList<>();

  // -- PR #68 follow-up: TrafficPattern subtype fields (NTA P1.2) --

  @JsonProperty("nta_category")
  @Schema(nullable = true, description = "Required when payload_type=TrafficPattern")
  private String ntaCategory;

  @JsonProperty("nta_protocol")
  @Schema(nullable = true, description = "Required when payload_type=TrafficPattern")
  private String ntaProtocol;

  @JsonProperty("nta_signature")
  @Schema(nullable = true, description = "Required when payload_type=TrafficPattern")
  private String ntaSignature;

  @JsonProperty("nta_pcap_path")
  @Schema(nullable = true)
  private String ntaPcapPath;

  @JsonProperty("nta_detection_hint")
  @Schema(nullable = true)
  private String ntaDetectionHint;

  @JsonProperty("payload_arguments")
  private List<PayloadArgument> arguments;

  @JsonProperty("payload_prerequisites")
  private List<PayloadPrerequisite> prerequisites;

  @JsonProperty("payload_cleanup_executor")
  @Schema(nullable = true)
  private String cleanupExecutor;

  @JsonProperty("payload_cleanup_command")
  @Schema(nullable = true)
  private String cleanupCommand;

  @JsonProperty("payload_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("payload_attack_patterns")
  private List<String> attackPatternsExternalIds = new ArrayList<>();

  @JsonProperty("payload_detection_remediations")
  @Schema(description = "List of detection remediation gaps for collectors")
  private List<DetectionRemediationInput> detectionRemediations = new ArrayList<>();

  @JsonProperty("payload_elevation_required")
  private boolean elevationRequired;

  @JsonProperty("payload_output_parsers")
  @Schema(description = "Set of output parsers")
  private Set<OutputParserInput> outputParsers = new HashSet<>();

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_domains")
  @Schema(description = "Update list of domains")
  private Set<NodeContractDomainDTO> domains = new HashSet<>();
}
