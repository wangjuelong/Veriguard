package io.veriguard.utils.fixtures.user_event;

import io.veriguard.database.model.User;
import io.veriguard.database.model.UserEvent;
import io.veriguard.database.model.UserEventType;

public class UserEventFixture {

  public static UserEvent getUserEventLogin(User user) {
    UserEvent userEvent = new UserEvent();
    userEvent.setUser(user);
    userEvent.setType(UserEventType.LOGIN_SUCCESS);
    return userEvent;
  }
}
