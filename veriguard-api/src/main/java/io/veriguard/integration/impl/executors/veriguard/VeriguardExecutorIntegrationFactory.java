package io.veriguard.integration.impl.executors.veriguard;

import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.database.repository.AssetAgentJobRepository;
import io.veriguard.executors.ExecutorService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class VeriguardExecutorIntegrationFactory extends IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final AssetAgentJobRepository assetAgentJobRepository;

  public VeriguardExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      AssetAgentJobRepository assetAgentJobRepository,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.executorService = executorService;
    this.componentRequestEngine = componentRequestEngine;
    this.assetAgentJobRepository = assetAgentJobRepository;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances() {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            VeriguardExecutorIntegration.VERIGUARD_EXECUTOR_ID,
            this.getClassName(),
            ConnectorType.EXECUTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new VeriguardExecutorIntegration(
        instance,
        connectorInstanceService,
        executorService,
        assetAgentJobRepository,
        componentRequestEngine);
  }
}
