package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of an export request from a search */
@Setter
@Getter
public class AttackChainNodeExportFromSearchRequestInput
    extends AttackChainNodeBulkProcessingInput {
  /** The export options to alter the shape of the response */
  @JsonProperty("options")
  private ExportOptionsInput exportOptions = new ExportOptionsInput();
}
