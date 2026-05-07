package io.veriguard.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.veriguard.database.model.ContractOutputField;
import io.veriguard.database.model.ContractOutputTechnicalType;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.rest.attack_chain_node.service.ContractOutputContext;
import io.veriguard.rest.attack_chain_node.service.ExecutionProcessingContext;
import java.util.List;

/**
 * Handler interface for processing structured outputs in different contexts. Implementations of
 * this interface will define how to validate and process structured outputs based on their type and
 * technical type, as well as the contexts they support.
 */
public interface OutputProcessor {

  /** Get the type (matches ContractOutputType enum) */
  ContractOutputType getType();

  /** Get the technical type (matches ContractOutputTechnicalType enum) */
  ContractOutputTechnicalType getTechnicalType();

  /** Get fields */
  List<ContractOutputField> getFields();

  /** Validate that the JSON node is correctly formatted for this type */
  boolean validate(JsonNode jsonNode);

  /**
   * Process a set of operations like generating findings, matching expectations and process assets.
   */
  void process(
      ExecutionProcessingContext ctx,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode);
}
