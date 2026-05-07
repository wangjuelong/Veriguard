package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class AttackChainNodeExportRequestInput {
  @JsonProperty("injects")
  private List<AttackChainNodeExportTarget> attackChainNodes;

  @JsonProperty("options")
  private ExportOptionsInput exportOptions = new ExportOptionsInput();

  @JsonIgnore
  public List<String> getTargetsIds() {
    if (attackChainNodes == null) {
      return List.of();
    }
    return attackChainNodes.stream().map(AttackChainNodeExportTarget::getId).toList();
  }
}
