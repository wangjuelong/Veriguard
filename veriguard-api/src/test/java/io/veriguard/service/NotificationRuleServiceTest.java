package io.veriguard.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.NotificationRule;
import io.veriguard.database.model.NotificationRuleResourceType;
import io.veriguard.database.model.NotificationRuleTrigger;
import io.veriguard.database.model.NotificationRuleType;
import io.veriguard.database.repository.NotificationRuleRepository;
import io.veriguard.service.attack_chain.AttackChainService;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class NotificationRuleServiceTest extends IntegrationTest {

  @Mock private NotificationRuleRepository notificationRuleRepository;

  @Mock private EmailNotificationService emailNotificationService;

  @Mock private UserService userService;

  @Mock private AttackChainService attackChainService;

  @Mock private PlatformSettingsService platformSettingsService;

  @InjectMocks private NotificationRuleService notificationRuleService;

  @Test
  public void test_activateNotificationRules() {

    Map<String, String> data = new HashMap<>();
    NotificationRule rule = new NotificationRule();
    rule.setResourceId("id");
    rule.setNotificationResourceType(NotificationRuleResourceType.SCENARIO);
    rule.setType(NotificationRuleType.EMAIL);
    rule.setSubject("subject");
    rule.setTrigger(NotificationRuleTrigger.DIFFERENCE);
    when(notificationRuleRepository.findNotificationRuleByResourceAndTrigger(
            rule.getResourceId(), rule.getTrigger()))
        .thenReturn(List.of(rule));

    notificationRuleService.activateNotificationRules(
        rule.getResourceId(), rule.getTrigger(), data);

    verify(emailNotificationService).sendNotification(rule, data);
  }
}
