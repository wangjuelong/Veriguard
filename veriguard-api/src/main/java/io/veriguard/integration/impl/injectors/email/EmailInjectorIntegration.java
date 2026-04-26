package io.veriguard.integration.impl.injectors.email;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.executors.InjectorContext;
import io.veriguard.healthcheck.enums.ExternalServiceDependency;
import io.veriguard.injectors.email.EmailContract;
import io.veriguard.injectors.email.EmailExecutor;
import io.veriguard.injectors.email.service.EmailService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.IntegrationInMemory;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.InjectExpectationService;
import io.veriguard.service.InjectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class EmailInjectorIntegration extends IntegrationInMemory {
  private static final String EMAIL_INJECTOR_NAME = "Email";
  public static final String EMAIL_INJECTOR_ID = "41b4dd55-5bd1-4614-98cd-9e3770753306";

  private final EmailContract emailContract;
  private final InjectorContext injectorContext;

  private final InjectorService injectorService;
  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;

  @QualifiedComponent(identifier = {EmailContract.TYPE, EMAIL_INJECTOR_ID})
  private EmailExecutor emailExecutor;

  public EmailInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance instance,
      ConnectorInstanceService connectorInstanceService,
      EmailContract emailContract,
      InjectorContext injectorContext,
      EmailService emailService,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService) {
    super(componentRequestEngine, instance, connectorInstanceService);
    this.emailContract = emailContract;
    this.injectorContext = injectorContext;
    this.emailService = emailService;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    injectorService.registerBuiltinInjector(
        EMAIL_INJECTOR_ID,
        EMAIL_INJECTOR_NAME,
        emailContract,
        false,
        "communication",
        null,
        null,
        false,
        List.of(ExternalServiceDependency.SMTP, ExternalServiceDependency.IMAP));
    this.emailExecutor = new EmailExecutor(injectorContext, emailService, injectExpectationService);
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
