package io.veriguard.integration.impl.injectors.ovh;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.CatalogConnector;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.InjectorContext;
import io.veriguard.injectors.ovh.OvhSmsContract;
import io.veriguard.injectors.ovh.config.OvhSmsInjectorConfig;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.veriguard.integration.migration.OvhInjectorConfigurationMigration;
import io.veriguard.service.FileService;
import io.veriguard.service.InjectExpectationService;
import io.veriguard.service.InjectorService;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class OvhInjectorIntegrationFactory extends IntegrationFactory {

  private final OvhSmsContract ovhSmsContract;
  private final InjectorContext injectorContext;
  private final OvhInjectorConfigurationMigration ovhInjectorConfigurationMigration;

  private final CatalogConnectorService catalogConnectorService;
  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;
  private final FileService fileService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  private final ComponentRequestEngine componentRequestEngine;

  public OvhInjectorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ComponentRequestEngine componentRequestEngine,
      OvhSmsContract ovhSmsContract,
      InjectorContext injectorContext,
      OvhInjectorConfigurationMigration ovhInjectorConfigurationMigration,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      FileService fileService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.connectorInstanceService = connectorInstanceService;
    this.componentRequestEngine = componentRequestEngine;
    this.ovhSmsContract = ovhSmsContract;
    this.injectorContext = injectorContext;
    this.ovhInjectorConfigurationMigration = ovhInjectorConfigurationMigration;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
    this.catalogConnectorService = catalogConnectorService;
    this.fileService = fileService;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    ovhInjectorConfigurationMigration.migrate();
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    CatalogConnector connector = new CatalogConnector();
    String logoFilename = "%s-logo.png".formatted(ovhSmsContract.getType());
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-ovh-sms.png"));
    connector.setTitle("OVHCloud SMS Platform");
    connector.setSlug(ovhSmsContract.getType());
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        """
                    The OVHCloud SMS Platform injector is a built-in injector, meaning it is natively included in the platform.

                    It allows you to send SMS through the OVHCloud SMS services directly in your Veriguard simulations.
                """);
    connector.setShortDescription("Allow Veriguard to send SMS for table top exercises.");
    connector.setClassName(getClassName());
    connector.setContainerType(ConnectorType.INJECTOR);
    connector.setCatalogConnectorConfigurations(
        new OvhSmsInjectorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new OvhInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        ovhSmsContract,
        injectorContext,
        injectorService,
        injectExpectationService,
        baseIntegrationConfigurationBuilder);
  }
}
