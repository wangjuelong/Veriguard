package io.veriguard.utils.mapper;

import io.veriguard.database.model.CatalogConnector;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.NodeExecutor;
import io.veriguard.rest.injector.form.NodeExecutorOutput;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class NodeExecutorMapper {
  private final CatalogConnectorMapper catalogConnectorMapper;
  private final ConnectorInstanceMapper connectorInstanceMapper;

  public NodeExecutorOutput toNodeExecutorOutput(
      NodeExecutor nodeExecutor,
      @Nullable CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance,
      boolean existingNodeExecutor) {
    return NodeExecutorOutput.builder()
        .id(nodeExecutor.getId())
        .name(nodeExecutor.getName())
        .type(nodeExecutor.getType())
        .external(nodeExecutor.isExternal())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(connectorInstance != null)
        .updatedAt(nodeExecutor.getUpdatedAt())
        .existing(existingNodeExecutor)
        .connectorInstance(
            connectorInstance != null
                ? connectorInstanceMapper.toConnectorInstanceOutput(connectorInstance)
                : null)
        .build();
  }
}
