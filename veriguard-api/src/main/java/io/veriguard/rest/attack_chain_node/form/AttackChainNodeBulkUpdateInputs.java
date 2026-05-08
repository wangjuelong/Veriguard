package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk update call for attackChainNodes */
@Setter
@Getter
public class AttackChainNodeBulkUpdateInputs extends AttackChainNodeBulkProcessingInput {

  /** The operations to perform to update attackChainNodes */
  @JsonProperty("update_operations")
  private List<AttackChainNodeBulkUpdateOperation> updateOperations;
}
