package io.veriguard.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.*;
import io.veriguard.rest.inject.output.AttackChainNodeOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Data;

@Data
public class ImportTestSummary {

  @JsonProperty("import_message")
  private List<ImportMessage> importMessage = new ArrayList<>();

  @JsonProperty("total_injects")
  public int totalNumberOfAttackChainNodes;

  @JsonProperty("total_rows_analysed")
  public int totalRowsAnalysed;

  @JsonIgnore private List<AttackChainNode> attackChainNodes = new ArrayList<>();

  @JsonProperty("injects")
  @Deprecated
  public List<AttackChainNodeOutput> getAttackChainNodeResults() {
    return attackChainNodes.stream()
        .map(
            attackChainNode ->
                new AttackChainNodeOutput(
                    attackChainNode.getId(),
                    attackChainNode.getTitle(),
                    attackChainNode.isEnabled(),
                    attackChainNode.getContent(),
                    attackChainNode.isAllTeams(),
                    Optional.ofNullable(attackChainNode.getAttackChainRun()).map(AttackChainRun::getId).orElse(null),
                    Optional.ofNullable(attackChainNode.getAttackChain()).map(AttackChain::getId).orElse(null),
                    attackChainNode.getDependsDuration(),
                    attackChainNode.getNodeContract().orElse(null),
                    attackChainNode.getTags().stream().map(Tag::getId).toArray(String[]::new),
                    attackChainNode.getTeams().stream().map(Team::getId).toArray(String[]::new),
                    attackChainNode.getAssets().stream().map(Asset::getId).toArray(String[]::new),
                    attackChainNode.getAssetGroups().stream().map(AssetGroup::getId).toArray(String[]::new),
                    attackChainNode
                        .getNodeContract()
                        .map(NodeContract::getNodeExecutor)
                        .map(NodeExecutor::getType)
                        .orElse(null),
                    Optional.ofNullable(attackChainNode.getDependsOn())
                        .map(List::stream)
                        .flatMap(Stream::findAny)
                        .orElse(null)))
        .toList();
  }
}
