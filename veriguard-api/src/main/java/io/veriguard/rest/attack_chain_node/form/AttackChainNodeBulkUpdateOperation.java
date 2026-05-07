package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent an operation to perform on a list of attackChainNodes to update them */
@Setter
@Getter
public class AttackChainNodeBulkUpdateOperation {

  /** The operations to perform to update attackChainNodes */
  @JsonProperty("operation")
  private AttackChainNodeBulkUpdateSupportedOperations operation;

  /** The field to update in the attackChainNodes */
  @JsonProperty("field")
  private AttackChainNodeBulkUpdateSupportedFields field;

  /** The values involved in the update operation for given field */
  @JsonProperty("values")
  private List<String> values;
}
