package io.veriguard.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.veriguard.database.model.ContractOutputField;
import io.veriguard.database.model.ContractOutputTechnicalType;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.rest.finding.FindingService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ComputerOutputProcessor extends FindingCapableOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String COMPUTER_NAME = "computer_name";
  private static final String HOST = "host";

  public ComputerOutputProcessor(FindingService findingService) {
    super(
        ContractOutputType.Computer,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(COMPUTER_NAME, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, false)),
        findingService);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(COMPUTER_NAME);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode, COMPUTER_NAME);
  }

  @Override
  public List<String> toFindingAssets(JsonNode jsonNode) {
    JsonNode assetIdNode = jsonNode.get(ASSET_ID);
    if (assetIdNode == null) {
      return Collections.emptyList();
    }
    if (assetIdNode.isArray()) {
      List<String> result = new ArrayList<>();
      for (JsonNode idNode : assetIdNode) {
        result.add(idNode.asText());
      }
      return result;
    }
    return List.of(assetIdNode.asText());
  }
}
