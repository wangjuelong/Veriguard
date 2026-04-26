package io.veriguard.integration.impl.injectors.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.InjectorContext;
import io.veriguard.injectors.email.EmailContract;
import io.veriguard.injectors.email.service.EmailService;
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
public class EmailInjectorIntegrationFactory extends IntegrationFactory {
  private final EmailContract emailContract;
  private final InjectorContext injectorContext;

  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;

  private final ComponentRequestEngine componentRequestEngine;

  public EmailInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      EmailContract emailContract,
      InjectorContext injectorContext,
      EmailService emailService,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.connectorInstanceService = connectorInstanceService;
    this.emailContract = emailContract;
    this.injectorContext = injectorContext;
    this.emailService = emailService;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
    this.componentRequestEngine = componentRequestEngine;
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
            EmailInjectorIntegration.EMAIL_INJECTOR_ID,
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
    return new EmailInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        emailContract,
        injectorContext,
        emailService,
        injectorService,
        injectExpectationService);
  }
}
