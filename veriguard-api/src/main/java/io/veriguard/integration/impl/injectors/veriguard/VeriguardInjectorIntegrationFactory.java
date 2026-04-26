package io.veriguard.integration.impl.injectors.veriguard;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.config.VeriguardConfig;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.InjectorContext;
import io.veriguard.injectors.veriguard.VeriguardImplantContract;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.rest.inject.service.InjectService;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.InjectExpectationService;
import io.veriguard.service.InjectorService;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VeriguardInjectorIntegrationFactory extends IntegrationFactory {

  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final VeriguardImplantContract veriguardImplantContract;
  private final VeriguardConfig veriguardConfig;
  private final InjectorContext injectorContext;
  private final AssetGroupService assetGroupService;
  private final InjectExpectationService injectExpectationService;
  private final InjectService injectService;

  public VeriguardInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      InjectorService injectorService,
      VeriguardImplantContract veriguardImplantContract,
      VeriguardConfig veriguardConfig,
      CatalogConnectorService catalogConnectorService,
      HttpClientFactory httpClientFactory,
      InjectorContext injectorContext,
      AssetGroupService assetGroupService,
      InjectExpectationService injectExpectationService,
      InjectService injectService) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.injectorService = injectorService;
    this.veriguardImplantContract = veriguardImplantContract;
    this.veriguardConfig = veriguardConfig;
    this.injectorContext = injectorContext;
    this.assetGroupService = assetGroupService;
    this.injectExpectationService = injectExpectationService;
    this.injectService = injectService;
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
            VeriguardInjectorIntegration.VERIGUARD_INJECTOR_ID,
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
    return new VeriguardInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        injectorService,
        veriguardImplantContract,
        veriguardConfig,
        injectorContext,
        assetGroupService,
        injectExpectationService,
        injectService);
  }
}
