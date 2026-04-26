package io.veriguard.integration.impl.injectors.manual;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.InjectorContext;
import io.veriguard.injectors.manual.ManualContract;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.service.InjectExpectationService;
import io.veriguard.service.InjectorService;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ManualInjectorIntegrationFactory extends IntegrationFactory {

  private final ManualContract manualContract;
  private final InjectorContext injectorContext;

  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;

  private final ComponentRequestEngine componentRequestEngine;

  public ManualInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ManualContract manualContract,
      InjectorContext injectorContext,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.manualContract = manualContract;
    this.injectorContext = injectorContext;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
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
            ManualInjectorIntegration.MANUAL_INJECTOR_ID,
            this.getClassName(),
            ConnectorType.INJECTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new ManualInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        manualContract,
        injectorContext,
        injectorService,
        injectExpectationService);
  }
}
