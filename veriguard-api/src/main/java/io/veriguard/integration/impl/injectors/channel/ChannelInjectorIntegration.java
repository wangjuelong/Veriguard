package io.veriguard.integration.impl.injectors.channel;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.repository.ArticleRepository;
import io.veriguard.executors.InjectorContext;
import io.veriguard.healthcheck.enums.ExternalServiceDependency;
import io.veriguard.injectors.channel.ChannelContract;
import io.veriguard.injectors.channel.ChannelExecutor;
import io.veriguard.injectors.email.service.EmailService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.IntegrationInMemory;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.InjectExpectationService;
import io.veriguard.service.InjectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class ChannelInjectorIntegration extends IntegrationInMemory {
  private static final String CHANNEL_INJECTOR_NAME = "Media pressure";
  public static final String CHANNEL_INJECTOR_ID = "8d932e36-353c-48fa-ba6f-86cb7b02ed19";

  private final ChannelContract channelContract;
  private final InjectorContext injectorContext;

  private final EmailService emailService;
  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;
  private final ArticleRepository articleRepository;

  @QualifiedComponent(identifier = {ChannelContract.TYPE, CHANNEL_INJECTOR_ID})
  private ChannelExecutor channelExecutor;

  public ChannelInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      ChannelContract channelContract,
      InjectorContext injectorContext,
      EmailService emailService,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      ArticleRepository articleRepository) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.channelContract = channelContract;
    this.injectorContext = injectorContext;
    this.emailService = emailService;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
    this.articleRepository = articleRepository;
  }

  @Override
  protected void innerStart() throws Exception {
    injectorService.registerBuiltinInjector(
        CHANNEL_INJECTOR_ID,
        CHANNEL_INJECTOR_NAME,
        this.channelContract,
        false,
        "media-pressure",
        null,
        null,
        false,
        List.of(ExternalServiceDependency.SMTP, ExternalServiceDependency.SMTP));
    this.channelExecutor =
        new ChannelExecutor(
            injectorContext,
            this.articleRepository,
            this.emailService,
            this.injectExpectationService);
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
