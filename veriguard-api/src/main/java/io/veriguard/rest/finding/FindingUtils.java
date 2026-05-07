package io.veriguard.rest.finding;

import io.veriguard.database.model.Finding;
import io.veriguard.rest.attack_chain_node.service.ContractOutputContext;
import org.jetbrains.annotations.NotNull;

public final class FindingUtils {

  private FindingUtils() {}

  public static Finding createFinding(@NotNull final ContractOutputContext element) {
    Finding finding = new Finding();
    finding.setType(element.type());
    finding.setField(element.key());
    finding.setLabels(element.labels()); // TODO: Set tags
    return finding;
  }
}
