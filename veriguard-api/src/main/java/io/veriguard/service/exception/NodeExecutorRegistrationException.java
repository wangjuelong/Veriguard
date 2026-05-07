package io.veriguard.service.exception;

public class NodeExecutorRegistrationException extends Exception {
  public NodeExecutorRegistrationException(String message) {
    super(message);
  }

  public NodeExecutorRegistrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
