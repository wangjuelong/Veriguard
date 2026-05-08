package io.veriguard.integration;

import static io.veriguard.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.veriguard.aop.lock.Lock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerFactory {
  private final List<IntegrationFactory> factories;
  private volatile Manager manager = null;

  @Transactional
  @Lock(type = MANAGER_FACTORY, key = "manager-factory")
  public Manager getManager() {
    if (manager == null) {
      try {
        this.manager = new Manager(factories);
        this.manager.monitorIntegrations();
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize Manager", e);
      }
    }
    return this.manager;
  }
}
