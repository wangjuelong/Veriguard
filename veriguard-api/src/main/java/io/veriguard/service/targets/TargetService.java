package io.veriguard.service.targets;

import io.veriguard.database.model.*;
import io.veriguard.service.targets.search.AgentTargetSearchAdaptor;
import io.veriguard.service.targets.search.AssetGroupTargetSearchAdaptor;
import io.veriguard.service.targets.search.EndpointTargetSearchAdaptor;
import io.veriguard.service.targets.search.PlayerTargetSearchAdaptor;
import io.veriguard.service.targets.search.TeamTargetSearchAdaptor;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.TargetType;
import io.veriguard.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TargetService {
  private final AssetGroupTargetSearchAdaptor assetGroupTargetSearchAdaptor;
  private final EndpointTargetSearchAdaptor endpointTargetSearchAdaptor;
  private final TeamTargetSearchAdaptor teamTargetSearchAdaptor;
  private final AgentTargetSearchAdaptor agentTargetSearchAdaptor;
  private final PlayerTargetSearchAdaptor playerTargetSearchAdaptor;

  public Page<AttackChainNodeTarget> searchTargets(
      TargetType attackChainNodeTargetType, AttackChainNode attackChainNode, SearchPaginationInput input) {

    // handle defaults if filter group is null
    if (input.getFilterGroup() == null) {
      Filters.FilterGroup filterGroup = new Filters.FilterGroup();
      filterGroup.setMode(Filters.FilterMode.and);
      filterGroup.setFilters(List.of());
      input.setFilterGroup(filterGroup);
    }
    return switch (attackChainNodeTargetType) {
      case ASSETS_GROUPS -> assetGroupTargetSearchAdaptor.search(input, attackChainNode);
      case ASSETS -> endpointTargetSearchAdaptor.search(input, attackChainNode);
      case TEAMS -> teamTargetSearchAdaptor.search(input, attackChainNode);
      case PLAYERS -> playerTargetSearchAdaptor.search(input, attackChainNode);
      case AGENT -> agentTargetSearchAdaptor.search(input, attackChainNode);
      default -> throw new IllegalArgumentException("Unsupported target type: " + attackChainNodeTargetType);
    };
  }

  public List<FilterUtilsJpa.Option> getTargetOptions(
      TargetType targetType, AttackChainNode attackChainNode, String textSearch) {
    return switch (targetType) {
      case ASSETS_GROUPS -> assetGroupTargetSearchAdaptor.getOptionsForAttackChainNode(attackChainNode, textSearch);
      case ASSETS -> endpointTargetSearchAdaptor.getOptionsForAttackChainNode(attackChainNode, textSearch);
      case TEAMS -> teamTargetSearchAdaptor.getOptionsForAttackChainNode(attackChainNode, textSearch);
      case PLAYERS -> playerTargetSearchAdaptor.getOptionsForAttackChainNode(attackChainNode, textSearch);
      case AGENT -> agentTargetSearchAdaptor.getOptionsForAttackChainNode(attackChainNode, textSearch);
      default -> throw new IllegalArgumentException("Unsupported target type: " + targetType);
    };
  }

  public List<FilterUtilsJpa.Option> getTargetOptionsByIds(
      TargetType targetType, List<String> ids) {
    return switch (targetType) {
      case ASSETS_GROUPS -> assetGroupTargetSearchAdaptor.getOptionsByIds(ids);
      case ASSETS -> endpointTargetSearchAdaptor.getOptionsByIds(ids);
      case TEAMS -> teamTargetSearchAdaptor.getOptionsByIds(ids);
      case AGENT -> agentTargetSearchAdaptor.getOptionsByIds(ids);
      case PLAYERS -> playerTargetSearchAdaptor.getOptionsByIds(ids);
      default -> throw new IllegalArgumentException("Unsupported target type: " + targetType);
    };
  }
}
