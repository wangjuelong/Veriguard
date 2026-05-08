package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AttackChainNodeIndividualExportRequestInput {
  @JsonProperty("options")
  private ExportOptionsInput exportOptions = new ExportOptionsInput();
}
