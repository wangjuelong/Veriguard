package io.veriguard.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.veriguard.database.model.ContractOutputTechnicalType;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.rest.finding.FindingService;
import java.util.List;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.springframework.stereotype.Component;

@Component
public class IPv6OutputProcessor extends FindingCapableOutputProcessor {

  private static final InetAddressValidator VALIDATOR = InetAddressValidator.getInstance();

  public IPv6OutputProcessor(FindingService findingService) {
    super(ContractOutputType.IPv6, ContractOutputTechnicalType.Text, List.of(), findingService);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return VALIDATOR.isValidInet6Address(jsonNode.asText());
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode);
  }
}
