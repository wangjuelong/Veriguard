package io.veriguard.integration.sandbox;

import org.springframework.stereotype.Component;

@Component
public class SandboxDriverRegistry {

  private final SandboxDriver driver;

  public SandboxDriverRegistry(SandboxDriver driver) {
    this.driver = driver;
  }

  public SandboxDriver driver() {
    return driver;
  }
}
