package io.veriguard.service;

import io.veriguard.database.model.NotificationRuleResourceType;
import io.veriguard.notification.handler.NotificationEventHandler;
import io.veriguard.notification.handler.AttackChainNotificationEventHandler;
import io.veriguard.notification.model.NotificationEvent;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationEventService {

  private final ApplicationEventPublisher appPublisher;
  private final AttackChainNotificationEventHandler attackChainNotificationEventHandler;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final Map<NotificationRuleResourceType, NotificationEventHandler> handlers;

  @Autowired
  public NotificationEventService(
      ApplicationEventPublisher appPublisher,
      AttackChainNotificationEventHandler attackChainNotificationEventHandler,
      ThreadPoolTaskScheduler taskScheduler) {

    this.appPublisher = appPublisher;
    this.attackChainNotificationEventHandler = attackChainNotificationEventHandler;
    this.taskScheduler = taskScheduler;
    this.handlers = Map.of(NotificationRuleResourceType.SCENARIO, attackChainNotificationEventHandler);
  }

  public void sendNotificationEvent(@NotNull final NotificationEvent notificationEvent) {
    appPublisher.publishEvent(notificationEvent);
  }

  /**
   * Send a notification with a delay (in seconds)
   *
   * @param notificationEvent notification to send
   * @param delay delay in seconds
   */
  public void sendNotificationEventWithDelay(
      @NotNull final NotificationEvent notificationEvent, long delay) {
    taskScheduler.schedule(
        () -> sendNotificationEvent(notificationEvent),
        Instant.now().plus(delay, ChronoUnit.SECONDS));
  }

  @EventListener
  public void handleNotificationEvent(@NotNull final NotificationEvent event) {
    NotificationEventHandler handler =
        Optional.ofNullable(handlers.get(event.getResourceType()))
            .orElseThrow(
                () ->
                    new UnsupportedOperationException(
                        "No handler registered for resource type " + event.getResourceType()));
    handler.handle(event);
  }
}
