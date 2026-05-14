package io.veriguard.service.exception;

/**
 * Thrown when a task dispatch requests a capability that no online Veriguard Agent has declared in
 * its {@code agent_capabilities} (C1 §3.5 / Task C.14).
 *
 * <p>Distinct from {@code IllegalArgumentException} (which would mean "invalid input") — here the
 * input may be perfectly valid (e.g. {@code "http_attack"}), but no Agent currently registered
 * exposes that capability. Callers should treat this as a routing-time failure: surface a clear
 * operator message ("deploy an Agent with capability X") instead of retrying blindly.
 */
public class CapabilityNotSupportedException extends RuntimeException {

  private final String capability;

  public CapabilityNotSupportedException(String capability, String message) {
    super(message);
    this.capability = capability;
  }

  public String getCapability() {
    return capability;
  }
}
