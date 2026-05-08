package io.veriguard.integration.sandbox;

public class SandboxIntegrationException extends RuntimeException {

  public enum ReasonCode {
    NOT_IMPLEMENTED,
    CONNECTION_FAILED,
    AUTHENTICATION_FAILED,
    REMOTE_ERROR,
    PROTOCOL_MISMATCH,
    TIMEOUT
  }

  private final ReasonCode reasonCode;
  private final Integer remoteStatusCode;

  public SandboxIntegrationException(ReasonCode reasonCode, String message) {
    this(reasonCode, message, null, null);
  }

  public SandboxIntegrationException(ReasonCode reasonCode, String message, Throwable cause) {
    this(reasonCode, message, null, cause);
  }

  public SandboxIntegrationException(
      ReasonCode reasonCode, String message, Integer remoteStatusCode, Throwable cause) {
    super(message, cause);
    this.reasonCode = reasonCode;
    this.remoteStatusCode = remoteStatusCode;
  }

  public ReasonCode getReasonCode() {
    return reasonCode;
  }

  public Integer getRemoteStatusCode() {
    return remoteStatusCode;
  }
}
