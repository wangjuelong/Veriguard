package io.veriguard.execution;

import io.veriguard.database.model.Endpoint;
import io.veriguard.utils.mapper.AssetGroupMapper;
import io.veriguard.utils.mapper.EndpointMapper;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutableNodeDTOMapper {

  final EndpointMapper endpointMapper;
  final AssetGroupMapper assetGroupMapper;

  public ExecutableNodeDTO toExecutableNodeDTO(ExecutableNode executableAttackChainNode) {
    return ExecutableNodeDTO.builder()
        .injection(executableAttackChainNode.getInjection())
        .assets(
            executableAttackChainNode.getAssets().stream()
                .map(asset -> endpointMapper.toEndpointTargetOutput((Endpoint) asset))
                .collect(Collectors.toSet()))
        .assetGroups(
            executableAttackChainNode.getAssetGroups().stream()
                .map(assetGroupMapper::toAssetGroupSimple)
                .collect(Collectors.toSet()))
        .build();
  }
}
