package io.veriguard.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.veriguard.database.model.ContractOutputTechnicalType;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.rest.finding.FindingService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PortOutputProcessor extends FindingCapableOutputProcessor {

  public PortOutputProcessor(FindingService findingService) {
    super(ContractOutputType.Port, ContractOutputTechnicalType.Number, List.of(), findingService);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode);
  }
}
