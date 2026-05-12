package io.veriguard.integration.impl.injectors.email;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.email.EmailContract;
import io.veriguard.injectors.email.EmailExecutor;
import io.veriguard.injectors.email.service.MailInjector;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.IntegrationInMemory;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.SmtpProfileService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class EmailNodeExecutorIntegration extends IntegrationInMemory {

  public static final String EMAIL_INJECTOR_ID = "21f4a8d2-7b3e-4c5a-b7f9-d8e1a4b2c3f5";
  private static final String EMAIL_INJECTOR_NAME = "Email";

  private final EmailContract emailContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final NodeExecutorService nodeExecutorService;
  private final SmtpProfileService smtpProfileService;
  private final MailInjector mailInjector;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  @QualifiedComponent(identifier = {EmailContract.TYPE, EMAIL_INJECTOR_ID})
  private EmailExecutor emailExecutor;

  public EmailNodeExecutorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EmailContract emailContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      SmtpProfileService smtpProfileService,
      MailInjector mailInjector,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.emailContract = emailContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.smtpProfileService = smtpProfileService;
    this.mailInjector = mailInjector;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    nodeExecutorService.registerBuiltinNodeExecutor(
        EMAIL_INJECTOR_ID,
        EMAIL_INJECTOR_NAME,
        emailContract,
        true,
        "generic",
        null,
        null,
        false,
        List.of());
    this.emailExecutor =
        new EmailExecutor(
            nodeExecutorContext,
            smtpProfileService,
            mailInjector,
            attackChainNodeExpectationService);
  }

  @Override
  protected void innerStop() {
    // not possible to stop this integration
  }
}
