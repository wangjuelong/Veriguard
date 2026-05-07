package io.veriguard.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.veriguard.database.model.ContractOutputField;
import io.veriguard.database.model.ContractOutputTechnicalType;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.rest.finding.FindingService;
import io.veriguard.rest.inject.service.ExecutionProcessingContext;
import io.veriguard.service.AttackChainNodeExpectationService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CVEOutputProcessor extends FindingCapableOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String ID = "id";
  private static final String HOST = "host";
  private static final String SEVERITY = "severity";

  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  public CVEOutputProcessor(
      FindingService findingService, AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(
        ContractOutputType.CVE,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(ID, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(SEVERITY, ContractOutputTechnicalType.Text, true)),
        findingService);
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(ID) && jsonNode.hasNonNull(HOST) && jsonNode.hasNonNull(SEVERITY);
  }

  /** Matches vulnerability expectations after findings are generated. */
  @Override
  protected void afterFindings(
      ExecutionProcessingContext executionContext, JsonNode structuredOutputNode) {
    attackChainNodeExpectationService.matchesVulnerabilityExpectations(
        executionContext, structuredOutputNode);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode, ID);
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
