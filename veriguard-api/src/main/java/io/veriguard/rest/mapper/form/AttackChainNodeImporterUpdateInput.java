package io.veriguard.rest.mapper.form;

import static io.veriguard.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AttackChainNodeImporterUpdateInput {

  @JsonProperty("inject_importer_id")
  private String id;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("inject_importer_type_value")
  private String attackChainNodeTypeValue;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("inject_importer_injector_contract")
  private String nodeContractId;

  @JsonProperty("inject_importer_rule_attributes")
  private List<RuleAttributeUpdateInput> ruleAttributes = new ArrayList<>();
}
