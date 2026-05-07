package io.veriguard.rest.inject.service;

import io.veriguard.database.model.ContractOutputElement;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.database.model.Tag;
import io.veriguard.injector_contract.outputs.NodeContractContentOutputElement;

public record ContractOutputContext(
    String key, // maps to contractOutputElement.getKey() / contentOutputElement.getField()
    String name, // display name / label
    ContractOutputType type,
    boolean isMultiple,
    String[] tagIds,
    String[] labels) {

  public static ContractOutputContext from(ContractOutputElement element) {
    return new ContractOutputContext(
        element.getKey(),
        element.getName(),
        element.getType(),
        true,
        element.getTags().isEmpty()
            ? new String[0]
            : element.getTags().stream().map(Tag::getId).toArray(String[]::new),
        new String[0]);
  }

  public static ContractOutputContext from(NodeContractContentOutputElement element) {
    return new ContractOutputContext(
        element.getField(),
        element.getField(), // or derive name differently
        element.getType(),
        element.isMultiple(),
        new String[0], // tags not available here yet
        element.getLabels());
  }
}
