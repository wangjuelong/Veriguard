package io.veriguard.integration.impl.executors.veriguard;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.repository.AssetAgentJobRepository;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.veriguard.service.VeriguardExecutorContextService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.connector_instances.ConnectorInstanceService;

public class VeriguardExecutorIntegration extends Integration {
  private final ExecutorService executorService;
  private final AssetAgentJobRepository assetAgentJobRepository;

  public static final String VERIGUARD_EXECUTOR_ID = "2f9a0936-c327-4e95-b406-d161d32a2501";
  public static final String VERIGUARD_EXECUTOR_TYPE = "veriguard_agent";
  public static final String VERIGUARD_EXECUTOR_NAME = "Veriguard Agent";
  public static final String VERIGUARD_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.veriguard.io/latest/usage/veriguard-agent/";
  public static final String VERIGUARD_EXECUTOR_BACKGROUND_COLOR = "#001BDB";

  @QualifiedComponent(identifier = VERIGUARD_EXECUTOR_NAME)
  private VeriguardExecutorContextService veriguardExecutorContextService;

  public VeriguardExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      ExecutorService executorService,
      AssetAgentJobRepository assetAgentJobRepository,
      ComponentRequestEngine componentRequestEngine) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.assetAgentJobRepository = assetAgentJobRepository;
    this.executorService = executorService;
  }

  @Override
  protected void innerStart() throws Exception {
    executorService.register(
        VERIGUARD_EXECUTOR_ID,
        VERIGUARD_EXECUTOR_TYPE,
        VERIGUARD_EXECUTOR_NAME,
        VERIGUARD_EXECUTOR_DOCUMENTATION_LINK,
        VERIGUARD_EXECUTOR_BACKGROUND_COLOR,
        getClass().getResourceAsStream("/img/icon-veriguard.png"),
        getClass().getResourceAsStream("/img/banner-veriguard.png"),
        new String[] {
          Endpoint.PLATFORM_TYPE.Windows.name(),
          Endpoint.PLATFORM_TYPE.Linux.name(),
          Endpoint.PLATFORM_TYPE.MacOS.name()
        });

    this.veriguardExecutorContextService = new VeriguardExecutorContextService(assetAgentJobRepository);
  }

  @Override
  protected void refresh() throws Exception {
    // Nothing to refresh from DB
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
