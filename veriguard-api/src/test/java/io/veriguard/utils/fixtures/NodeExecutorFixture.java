package io.veriguard.utils.fixtures;

import io.veriguard.database.model.NodeExecutor;
import io.veriguard.database.repository.NodeExecutorRepository;
import io.veriguard.injectors.veriguard.VeriguardImplantContract;
import io.veriguard.integration.Manager;
import io.veriguard.integration.impl.injectors.veriguard.VeriguardNodeExecutorIntegrationFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NodeExecutorFixture {
  @Autowired NodeExecutorRepository nodeExecutorRepository;
  @Autowired private VeriguardNodeExecutorIntegrationFactory veriguardNodeExecutorIntegrationFactory;

  public static NodeExecutor createDefaultPayloadNodeExecutor() {
    NodeExecutor nodeExecutor =
        createNodeExecutor(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    nodeExecutor.setPayloads(true);
    return nodeExecutor;
  }

  public static NodeExecutor createNodeExecutor(String id, String name, String type) {
    NodeExecutor nodeExecutor = new NodeExecutor();
    nodeExecutor.setId(id);
    nodeExecutor.setName(name);
    nodeExecutor.setType(type);
    nodeExecutor.setExternal(false);
    nodeExecutor.setCreatedAt(Instant.now());
    nodeExecutor.setUpdatedAt(Instant.now());
    return nodeExecutor;
  }

  public static NodeExecutor createDefaultNodeExecutor(String nodeExecutorName) {
    return createNodeExecutor(
        UUID.randomUUID().toString(), nodeExecutorName, nodeExecutorName.toLowerCase().replace(" ", "-"));
  }

  private NodeExecutor initializeOAEVImplantNodeExecutor() {
    try {
      new Manager(List.of(veriguardNodeExecutorIntegrationFactory)).monitorIntegrations();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize OAEV Implant Injector", e);
    }

    return nodeExecutorRepository
        .findByType(VeriguardImplantContract.TYPE)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Injector not found after initialization: " + VeriguardImplantContract.TYPE));
  }

  public NodeExecutor getWellKnownOaevImplantNodeExecutor() {
    NodeExecutor nodeExecutor =
        nodeExecutorRepository
            .findByType(VeriguardImplantContract.TYPE)
            .orElseGet(this::initializeOAEVImplantNodeExecutor);
    nodeExecutor.setPayloads(true);
    nodeExecutorRepository.save(nodeExecutor);
    return nodeExecutor;
  }
}
