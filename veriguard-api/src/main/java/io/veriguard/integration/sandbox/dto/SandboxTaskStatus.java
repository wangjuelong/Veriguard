package io.veriguard.integration.sandbox.dto;

public record SandboxTaskStatus(Status status, String rawRemoteStatus, String errorMessage) {

  public enum Status {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    UNKNOWN
  }
}
