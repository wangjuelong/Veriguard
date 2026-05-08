package io.veriguard.utils.mapper;

import io.veriguard.database.model.CatalogConnector;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.Executor;
import io.veriguard.rest.executor.form.ExecutorOutput;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ExecutorMapper {
  private final CatalogConnectorMapper catalogConnectorMapper;
  private final ConnectorInstanceMapper connectorInstanceMapper;

  public ExecutorOutput toExecutorOutput(
      Executor executor,
      @Nullable CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance,
      boolean existingExecutor) {
    return ExecutorOutput.builder()
        .id(executor.getId())
        .name(executor.getName())
        .type(executor.getType())
        .updatedAt(executor.getUpdatedAt())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(connectorInstance != null)
        .platforms(executor.getPlatforms())
        .doc(executor.getDoc())
        .backgroundColor(executor.getBackgroundColor())
        .existing(existingExecutor)
        .connectorInstance(
            connectorInstance != null
                ? connectorInstanceMapper.toConnectorInstanceOutput(connectorInstance)
                : null)
        .build();
  }
}
