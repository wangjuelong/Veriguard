package io.veriguard.injectors.ovh;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.database.model.ExecutionTrace.getNewSuccessTrace;
import static org.springframework.util.StringUtils.hasText;

import io.veriguard.database.model.Execution;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.database.model.Inject;
import io.veriguard.execution.ExecutableInject;
import io.veriguard.execution.ExecutionContext;
import io.veriguard.execution.ProtectUser;
import io.veriguard.executors.Injector;
import io.veriguard.executors.InjectorContext;
import io.veriguard.injectors.ovh.model.OvhSmsContent;
import io.veriguard.injectors.ovh.service.OvhSmsService;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.model.Expectation;
import io.veriguard.model.expectation.ManualExpectation;
import io.veriguard.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.util.StringUtils;

public class OvhSmsExecutor extends Injector {

  private final OvhSmsService smsService;
  private final InjectExpectationService injectExpectationService;

  public OvhSmsExecutor(
      InjectorContext context,
      OvhSmsService smsService,
      InjectExpectationService injectExpectationService) {
    super(context);
    this.smsService = smsService;
    this.injectExpectationService = injectExpectationService;
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection)
      throws Exception {
    Inject inject = injection.getInjection().getInject();
    OvhSmsContent content = contentConvert(injection, OvhSmsContent.class);
    String smsMessage = content.buildMessage(inject.getFooter(), inject.getHeader());
    List<ExecutionContext> users = injection.getUsers();
    if (users.isEmpty()) {
      throw new UnsupportedOperationException("Sms needs at least one user");
    }

    // We check that at least one user receive the sms before to create expectations
    AtomicBoolean isSmsSent = new AtomicBoolean(false);

    users.stream()
        .parallel()
        .forEach(
            context -> {
              ProtectUser user = context.getUser();
              String phone = user.getPhone();
              String email = user.getEmail();
              if (!StringUtils.hasLength(phone)) {
                String message = "Sms fail for " + email + ": no phone number";
                execution.addTrace(
                    getNewErrorTrace(
                        message, ExecutionTraceAction.COMPLETE, List.of(user.getId())));
              } else {
                try {
                  String callResult = smsService.sendSms(context, phone, smsMessage);
                  isSmsSent.set(true);

                  // Extraction simplifiée avec regex pour le champ "invalidReceivers"
                  Pattern pattern = Pattern.compile("\"invalidReceivers\":\\[(.*?)\\]");
                  Matcher matcher = pattern.matcher(callResult);
                  if (matcher.find() && hasText(matcher.group(1))) {
                    String message =
                        "Sms sent to "
                            + email
                            + " through "
                            + phone
                            + " contains error ("
                            + callResult
                            + ")";
                    execution.addTrace(
                        getNewErrorTrace(
                            message, ExecutionTraceAction.COMPLETE, List.of(user.getId())));
                  } else {
                    String message =
                        "Sms sent to " + email + " through " + phone + " (" + callResult + ")";
                    execution.addTrace(
                        getNewSuccessTrace(
                            message, ExecutionTraceAction.COMPLETE, List.of(user.getId())));
                  }
                } catch (Exception e) {
                  execution.addTrace(
                      getNewErrorTrace(
                          e.getMessage(), ExecutionTraceAction.COMPLETE, List.of(user.getId())));
                }
              }
            });
    if (isSmsSent.get()) {
      List<Expectation> expectations =
          content.getExpectations().stream()
              .flatMap(
                  entry ->
                      switch (entry.getType()) {
                        case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                        default -> Stream.of();
                      })
              .toList();

      injectExpectationService.buildAndSaveInjectExpectations(injection, expectations);
    }
    return new ExecutionProcess(false);
  }
}
