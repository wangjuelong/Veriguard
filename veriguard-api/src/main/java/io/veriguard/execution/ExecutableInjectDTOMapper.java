package io.veriguard.execution;

import io.veriguard.database.model.Endpoint;
import io.veriguard.utils.mapper.AssetGroupMapper;
import io.veriguard.utils.mapper.EndpointMapper;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutableInjectDTOMapper {

  final EndpointMapper endpointMapper;
  final AssetGroupMapper assetGroupMapper;

  public ExecutableInjectDTO toExecutableInjectDTO(ExecutableInject executableInject) {
    return ExecutableInjectDTO.builder()
        .injection(executableInject.getInjection())
        .assets(
            executableInject.getAssets().stream()
                .map(asset -> endpointMapper.toEndpointTargetOutput((Endpoint) asset))
                .collect(Collectors.toSet()))
        .assetGroups(
            executableInject.getAssetGroups().stream()
                .map(assetGroupMapper::toAssetGroupSimple)
                .collect(Collectors.toSet()))
        .build();
  }
}
