package io.veriguard.integration.impl.injectors.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.database.repository.ArticleRepository;
import io.veriguard.executors.InjectorContext;
import io.veriguard.injectors.channel.ChannelContract;
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
public class ChannelInjectorIntegrationFactory extends IntegrationFactory {
  private final ChannelContract channelContract;
  private final InjectorContext injectorContext;

  private final EmailService emailService;
  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;
  private final ArticleRepository articleRepository;
  private final ConnectorInstanceService connectorInstanceService;
  private final ComponentRequestEngine componentRequestEngine;

  public ChannelInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ChannelContract channelContract,
      InjectorContext injectorContext,
      EmailService emailService,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      ArticleRepository articleRepository,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.channelContract = channelContract;
    this.injectorContext = injectorContext;
    this.emailService = emailService;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
    this.articleRepository = articleRepository;
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
            ChannelInjectorIntegration.CHANNEL_INJECTOR_ID,
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
    return new ChannelInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        channelContract,
        injectorContext,
        emailService,
        injectorService,
        injectExpectationService,
        articleRepository);
  }
}
