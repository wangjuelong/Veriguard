package io.veriguard.service;

import static io.veriguard.config.VeriguardAnonymous.ANONYMOUS;
import static io.veriguard.config.SessionHelper.currentUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.Exercise;
import io.veriguard.database.model.Inject;
import io.veriguard.database.model.User;
import io.veriguard.database.repository.InjectorContractRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.execution.ExecutableInject;
import io.veriguard.execution.ExecutionContext;
import io.veriguard.execution.ExecutionContextService;
import io.veriguard.injectors.email.EmailContract;
import io.veriguard.injectors.email.model.EmailContent;
import io.veriguard.integration.ManagerFactory;
import io.veriguard.rest.exception.ElementNotFoundException;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailingService {

  @Resource protected ObjectMapper mapper;

  private final UserRepository userRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final ExecutionContextService executionContextService;
  private final ManagerFactory managerFactory;

  public void sendEmail(
      String subject, String body, List<User> users, Optional<Exercise> exercise) {
    EmailContent emailContent = new EmailContent();
    emailContent.setSubject(subject);
    emailContent.setBody(body);

    Inject inject = new Inject();
    inject.setInjectorContract(
        this.injectorContractRepository
            .findById(EmailContract.EMAIL_DEFAULT)
            .orElseThrow(ElementNotFoundException::new));

    inject
        .getInjectorContract()
        .ifPresent(
            injectorContract -> {
              inject.setContent(this.mapper.valueToTree(emailContent));

              // When resetting the password, the user is not logged in (anonymous),
              // so there's no need to add the user to the inject.
              if (!ANONYMOUS.equals(currentUser().getId())) {
                inject.setUser(
                    this.userRepository
                        .findById(currentUser().getId())
                        .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
              }

              exercise.ifPresent(inject::setExercise);

              List<ExecutionContext> userInjectContexts =
                  users.stream()
                      .distinct()
                      .map(
                          user ->
                              this.executionContextService.executionContext(
                                  user, inject, "Direct execution"))
                      .toList();
              ExecutableInject injection =
                  new ExecutableInject(false, true, inject, userInjectContexts);
              io.veriguard.executors.Injector executor =
                  managerFactory
                      .getManager()
                      .requestInjectorExecutorByType(injectorContract.getInjector().getType());
              executor.executeInjection(injection);
            });
  }

  public void sendEmail(String subject, String body, List<User> users) {
    sendEmail(subject, body, users, Optional.empty());
  }
}
