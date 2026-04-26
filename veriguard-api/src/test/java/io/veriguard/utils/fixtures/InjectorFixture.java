package io.veriguard.utils.fixtures;

import static io.veriguard.integration.impl.injectors.email.EmailInjectorIntegration.EMAIL_INJECTOR_ID;

import io.veriguard.database.model.Injector;
import io.veriguard.database.repository.InjectorRepository;
import io.veriguard.injectors.email.EmailContract;
import io.veriguard.injectors.veriguard.VeriguardImplantContract;
import io.veriguard.integration.Manager;
import io.veriguard.integration.impl.injectors.veriguard.VeriguardInjectorIntegrationFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

  public Injector createOAEVEmailInjector() {
    return createInjector(EMAIL_INJECTOR_ID, "Email", EmailContract.TYPE);
  }

  public Injector getWellKnownOaevImplantInjector() {
    Injector injector =
        injectorRepository
            .findByType(VeriguardImplantContract.TYPE)
            .orElseGet(this::initializeOAEVImplantInjector);
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(true);
    injectorRepository.save(injector);
    return injector;
  }

  public Injector getWellKnownEmailInjector(boolean isPayload) {
    Optional<Injector> injectorOptional = injectorRepository.findByType(EmailContract.TYPE);
    Injector injector =
        injectorOptional.orElseGet(() -> injectorRepository.save(createOAEVEmailInjector()));
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(isPayload);
    injectorRepository.save(injector);
    return injector;
  }
}
