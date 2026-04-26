package io.veriguard.notification.handler;

import io.veriguard.notification.model.NotificationEvent;

public interface NotificationEventHandler {
  void handle(NotificationEvent event);
}
