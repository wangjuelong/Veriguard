package io.veriguard.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.veriguard.database.model.ContractOutputTechnicalType;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.rest.finding.FindingService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NumberOutputProcessor extends FindingCapableOutputProcessor {

  public NumberOutputProcessor(FindingService findingService) {
    super(ContractOutputType.Number, ContractOutputTechnicalType.Number, List.of(), findingService);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode);
  }
}
