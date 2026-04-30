package io.veriguard.utils.fixtures;

import io.veriguard.database.model.Injector;
import io.veriguard.database.repository.InjectorRepository;
import io.veriguard.injectors.veriguard.VeriguardImplantContract;
import io.veriguard.integration.Manager;
import io.veriguard.integration.impl.injectors.veriguard.VeriguardInjectorIntegrationFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InjectorFixture {
  @Autowired InjectorRepository injectorRepository;
  @Autowired private VeriguardInjectorIntegrationFactory veriguardInjectorIntegrationFactory;

  public static Injector createDefaultPayloadInjector() {
    Injector injector =
        createInjector(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    injector.setPayloads(true);
    return injector;
  }

  public static Injector createInjector(String id, String name, String type) {
    Injector injector = new Injector();
    injector.setId(id);
    injector.setName(name);
    injector.setType(type);
    injector.setExternal(false);
    injector.setCreatedAt(Instant.now());
    injector.setUpdatedAt(Instant.now());
    return injector;
  }

  public static Injector createDefaultInjector(String injectorName) {
    return createInjector(
        UUID.randomUUID().toString(), injectorName, injectorName.toLowerCase().replace(" ", "-"));
  }

  private Injector initializeOAEVImplantInjector() {
    try {
      new Manager(List.of(veriguardInjectorIntegrationFactory)).monitorIntegrations();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize OAEV Implant Injector", e);
    }

    return injectorRepository
        .findByType(VeriguardImplantContract.TYPE)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Injector not found after initialization: " + VeriguardImplantContract.TYPE));
  }

  public Injector getWellKnownOaevImplantInjector() {
    Injector injector =
        injectorRepository
            .findByType(VeriguardImplantContract.TYPE)
            .orElseGet(this::initializeOAEVImplantInjector);
    injector.setPayloads(true);
    injectorRepository.save(injector);
    return injector;
  }
}
