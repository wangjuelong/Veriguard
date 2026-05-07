package io.veriguard.service;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.service.FileService.INJECTORS_IMAGES_BASE_PATH;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.veriguard.asset.QueueService;
import io.veriguard.config.RabbitmqConfig;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackPatternRepository;
import io.veriguard.database.repository.ConnectorInstanceConfigurationRepository;
import io.veriguard.database.repository.NodeContractRepository;
import io.veriguard.database.repository.NodeExecutorRepository;
import io.veriguard.healthcheck.enums.ExternalServiceDependency;
import io.veriguard.injector_contract.Contract;
import io.veriguard.injector_contract.Contractor;
import io.veriguard.rest.catalog_connector.dto.ConnectorIds;
import io.veriguard.rest.domain.DomainService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.injector.form.NodeExecutorCreateInput;
import io.veriguard.rest.injector.form.NodeExecutorOutput;
import io.veriguard.rest.injector.response.NodeExecutorConnection;
import io.veriguard.rest.injector.response.NodeExecutorRegistration;
import io.veriguard.rest.injector_contract.NodeContractService;
import io.veriguard.rest.injector_contract.form.NodeContractInput;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connectors.AbstractConnectorService;
import io.veriguard.service.exception.NodeExecutorRegistrationException;
import io.veriguard.utils.mapper.CatalogConnectorMapper;
import io.veriguard.utils.mapper.NodeExecutorMapper;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service("coreInjectorService")
// TODO needs to be merged with integrations/NodeExecutorService
public class NodeExecutorService extends AbstractConnectorService<NodeExecutor, NodeExecutorOutput> {
  public static final String DUMMY_SUFFIX = "_dummy";

  @Resource private RabbitmqConfig rabbitmqConfig;
  private final NodeExecutorRepository nodeExecutorRepository;
  private final NodeContractRepository nodeContractRepository;
  private final AttackPatternRepository attackPatternRepository;

  private final FileService fileService;
  private final NodeContractService nodeContractService;
  private final DomainService domainService;

  private final NodeExecutorMapper nodeExecutorMapper;

  private final QueueService queueService;

  @Autowired
  public NodeExecutorService(
      NodeExecutorRepository nodeExecutorRepository,
      NodeContractRepository nodeContractRepository,
      AttackPatternRepository attackPatternRepository,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      FileService fileService,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      NodeContractService nodeContractService,
      DomainService domainService,
      NodeExecutorMapper nodeExecutorMapper,
      CatalogConnectorMapper catalogConnectorMapper,
      QueueService queueService) {
    super(
        ConnectorType.INJECTOR,
        connectorInstanceConfigurationRepository,
        catalogConnectorService,
        connectorInstanceService,
        catalogConnectorMapper);
    this.nodeExecutorRepository = nodeExecutorRepository;
    this.nodeContractRepository = nodeContractRepository;
    this.attackPatternRepository = attackPatternRepository;
    this.fileService = fileService;
    this.nodeContractService = nodeContractService;
    this.domainService = domainService;
    this.nodeExecutorMapper = nodeExecutorMapper;
    this.queueService = queueService;
  }

  @Override
  public List<NodeExecutor> getAllConnectors() {
    return fromIterable(nodeExecutorRepository.findAll());
  }

  @Override
  protected NodeExecutor getConnectorById(String nodeExecutorId) {
    return nodeExecutorRepository.findById(nodeExecutorId).orElse(null);
  }

  @Override
  protected NodeExecutorOutput mapToOutput(
      NodeExecutor nodeExecutor,
      CatalogConnector catalogConnector,
      ConnectorInstance instance,
      boolean existingNodeExecutor) {
    return nodeExecutorMapper.toNodeExecutorOutput(nodeExecutor, catalogConnector, instance, existingNodeExecutor);
  }

  @Override
  protected NodeExecutor createNewConnector() {
    return new NodeExecutor();
  }

  /**
   * Create a dummmy nodeExecutor, that is used when importing the starter pack before the real
   * nodeExecutors are registered
   *
   * @param nodeExecutorType
   * @param nodeExecutorName
   * @return
   */
  public NodeExecutor createDummyNodeExecutor(
      @NotBlank final String nodeExecutorType, @NotBlank final String nodeExecutorName) {
    NodeExecutor nodeExecutor = new NodeExecutor();
    nodeExecutor.setName("Dummy " + nodeExecutorName);
    nodeExecutor.setType(nodeExecutorType + DUMMY_SUFFIX);
    nodeExecutor.setId(nodeExecutorType + DUMMY_SUFFIX);
    nodeExecutor.setDependencies(ExternalServiceDependency.fromNodeExecutorType(nodeExecutorType));
    return nodeExecutorRepository.save(nodeExecutor);
  }

  public NodeExecutor nodeExecutor(String id) {
    return nodeExecutorRepository
        .findById(id)
        .orElseThrow(() -> new ElementNotFoundException("Injector not found with id: " + id));
  }

  /**
   * Check if a dummy nodeExecutor exist for an nodeExecutor type and delete it
   *
   * @param nodeExecutorType to find dummy one
   */
  public void deleteDummyNodeExecutorIfItExists(@NotBlank final String nodeExecutorType) {
    deleteDummyNodeExecutorIfItExists(nodeExecutorType, null);
  }

  /**
   * This method will check if the nodeExecutor type is a dummy if yes it will remove the dummy suffix
   * if no it will return the parameter It is used to send the execution to the correct nodeExecutor
   * even if the current one is just a dummy nodeExecutor
   *
   * @param nodeExecutorType
   * @return
   */
  public String getOriginNodeExecutorType(@NotBlank final String nodeExecutorType) {
    if (nodeExecutorType.endsWith(DUMMY_SUFFIX)) {
      return nodeExecutorType.substring(0, nodeExecutorType.length() - DUMMY_SUFFIX.length());
    }
    return nodeExecutorType;
  }

  public List<NodeExecutor> findAll() {
    return StreamSupport.stream(nodeExecutorRepository.findAll().spliterator(), false)
        .collect(Collectors.toList());
  }

  /**
   * Retrieve all nodeExecutors.
   *
   * @param isIncludeNext Include pending nodeExecutors.
   * @return List of nodeExecutor output
   */
  public Iterable<NodeExecutorOutput> nodeExecutorsOutput(boolean isIncludeNext) {
    return getConnectorsOutput(isIncludeNext);
  }

  /**
   * Find nodeExecutor by its type
   *
   * @param nodeExecutorType nodeExecutor type to search for
   * @return an Optional containing the nodeExecutor if found, empty otherwise
   */
  public Optional<NodeExecutor> nodeExecutorByType(@NotBlank final String nodeExecutorType) {
    return nodeExecutorRepository.findByType(nodeExecutorType);
  }

  /**
   * Retrieves IDs of resources associated with an nodeExecutor.
   *
   * @param nodeExecutorId nodeExecutor identifier.
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getNodeExecutorRelationsId(String nodeExecutorId) {
    return getConnectorRelationsId(nodeExecutorId);
  }

  public NodeExecutorRegistration registerExternalNodeExecutor(
      NodeExecutorCreateInput input, Optional<MultipartFile> file) {
    ConnectionFactory factory = this.queueService.createConnectionFactory();
    // Declare queueing
    Connection connection = null;
    try {
      // Upload icon
      if (file.isPresent() && "image/png".equals(file.get().getContentType())) {
        fileService.uploadFile(
            FileService.INJECTORS_IMAGES_BASE_PATH + input.getType() + ".png", file.get());
      }
      connection = factory.newConnection();
      this.queueService.createChannel(connection, "_injector_" + input.getType(), input.getType());
      String queueName = rabbitmqConfig.getPrefix() + "_injector_" + input.getType();
      // We need to support upsert for registration
      NodeExecutor nodeExecutor = nodeExecutorRepository.findById(input.getId()).orElse(null);
      if (nodeExecutor == null) {
        NodeExecutor nodeExecutorChecking = nodeExecutorRepository.findByType(input.getType()).orElse(null);
      }
      if (nodeExecutor != null) {
        updateExistingExternalNodeExecutor(
            nodeExecutor,
            input.getType(),
            input.getName(),
            input.getContracts(),
            input.getCustomContracts(),
            input.getCategory(),
            input.getExecutorCommands(),
            input.getExecutorClearCommands(),
            input.getPayloads());
      } else {
        // save the nodeExecutor
        NodeExecutor newNodeExecutor = new NodeExecutor();
        newNodeExecutor.setId(input.getId());
        newNodeExecutor.setExternal(true);
        newNodeExecutor.setName(input.getName());
        newNodeExecutor.setType(input.getType());
        newNodeExecutor.setCategory(input.getCategory());
        newNodeExecutor.setCustomContracts(input.getCustomContracts());
        newNodeExecutor.setExecutorCommands(input.getExecutorCommands());
        newNodeExecutor.setExecutorClearCommands(input.getExecutorClearCommands());
        newNodeExecutor.setPayloads(input.getPayloads());
        NodeExecutor savedNodeExecutor = nodeExecutorRepository.save(newNodeExecutor);
        // Save the contracts
        List<NodeContract> nodeContracts =
            input.getContracts().stream()
                .map(in -> nodeContractService.convertNodeExecutorFromInput(in, savedNodeExecutor))
                .toList();
        nodeContractRepository.saveAll(nodeContracts);

        // delete the dummy nodeExecutor if it was created when importing the starter pack
        deleteDummyNodeExecutorIfItExists(input.getType(), savedNodeExecutor);
      }
      NodeExecutorConnection conn =
          new NodeExecutorConnection(
              rabbitmqConfig.getHostname(),
              rabbitmqConfig.getVhost(),
              rabbitmqConfig.isSsl(),
              rabbitmqConfig.getPort(),
              rabbitmqConfig.getUser(),
              rabbitmqConfig.getPass());
      return new NodeExecutorRegistration(conn, queueName);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (connection != null) {
        try {
          connection.close();
        } catch (IOException e) {
          log.error(
              "Unable to close RabbitMQ connection. You should worry as this could impact performance",
              e);
        }
      }
    }
  }

  public NodeExecutor updateExistingExternalNodeExecutor(
      NodeExecutor nodeExecutor,
      String type,
      String name,
      List<NodeContractInput> contracts,
      Boolean customContracts,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean payloads) {
    nodeExecutor.setUpdatedAt(Instant.now());
    nodeExecutor.setType(type);
    nodeExecutor.setName(name);
    nodeExecutor.setExternal(true);
    nodeExecutor.setCustomContracts(customContracts);
    nodeExecutor.setCategory(category);
    nodeExecutor.setExecutorCommands(executorCommands);
    nodeExecutor.setExecutorClearCommands(executorClearCommands);
    nodeExecutor.setPayloads(payloads);
    List<String> existing = new ArrayList<>();
    List<String> toDeletes = new ArrayList<>();
    nodeExecutor
        .getContracts()
        .forEach(
            contract -> {
              Optional<NodeContractInput> current =
                  contracts.stream().filter(c -> c.getId().equals(contract.getId())).findFirst();
              if (current.isPresent()) {
                existing.add(contract.getId());
                contract.setManual(current.get().isManual());
                contract.setLabels(current.get().getLabels());
                contract.setContent(current.get().getContent());
                contract.setAtomicTesting(current.get().isAtomicTesting());
                contract.setPlatforms(current.get().getPlatforms());
                if (!current.get().getAttackPatternsExternalIds().isEmpty()) {
                  List<AttackPattern> attackPatterns =
                      fromIterable(
                          attackPatternRepository.findAllByExternalIdInIgnoreCase(
                              current.get().getAttackPatternsExternalIds()));
                  contract.setAttackPatterns(attackPatterns);
                } else {
                  contract.setAttackPatterns(new ArrayList<>());
                }

                if (!payloads) {
                  Set<Domain> currentDomains =
                      this.domainService.upsertDomainEntities(contract.getDomains());
                  Set<Domain> domainsToAdd = this.domainService.upserts(current.get().getDomains());
                  contract.setDomains(
                      this.domainService.mergeDomains(currentDomains, domainsToAdd));
                }
              } else if (!contract.getCustom()) {
                toDeletes.add(contract.getId());
              }
            });
    List<NodeContract> toCreates =
        contracts.stream()
            .filter(c -> !existing.contains(c.getId()))
            .map(in -> nodeContractService.convertNodeExecutorFromInput(in, nodeExecutor))
            .toList();
    nodeContractRepository.deleteAllById(toDeletes);
    nodeContractRepository.saveAll(toCreates);
    return nodeExecutorRepository.save(nodeExecutor);
  }

  // -- BUILT - IN --

  /**
   * Registers or updates an nodeExecutor and its contracts.
   *
   * <p>This method handles the complete lifecycle of nodeExecutor registration:
   *
   * <ul>
   *   <li>Uploads nodeExecutor icons
   *   <li>Creates new nodeExecutors or updates existing ones
   *   <li>Synchronizes contracts (create/update/delete)
   * </ul>
   *
   * @param id unique identifier for the nodeExecutor
   * @param name display name for the nodeExecutor
   * @param contractor the contractor providing the nodeExecutor definition
   * @param isCustomizable whether custom contracts can be created
   * @param category the category this nodeExecutor belongs to
   * @param executorCommands commands for execution
   * @param executorClearCommands commands for cleanup
   * @param isPayloads whether this nodeExecutor uses payloads
   * @param dependencies external service dependencies
   * @throws NodeExecutorRegistrationException if registration fails due to conflicts or errors
   */
  @Transactional
  public void registerBuiltinNodeExecutor(
      String id,
      String name,
      Contractor contractor,
      Boolean isCustomizable,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean isPayloads,
      List<ExternalServiceDependency> dependencies)
      throws NodeExecutorRegistrationException {

    // Upload icon if available
    uploadNodeExecutorIcon(contractor);

    // Validate no ID conflicts exist
    validateNoIdConflict(id, contractor);

    // Get contracts from contractor
    List<Contract> staticContracts;
    try {
      staticContracts = contractor.contracts();
    } catch (Exception e) {
      throw new NodeExecutorRegistrationException(
          "Failed to retrieve contracts from contractor: " + contractor.getType(), e);
    }

    // Find existing nodeExecutor or create new
    NodeExecutor existingNodeExecutor = nodeExecutorRepository.findById(id).orElse(null);

    if (existingNodeExecutor != null) {
      updateExistingBuiltinNodeExecutor(
          existingNodeExecutor,
          name,
          contractor,
          isCustomizable,
          category,
          executorCommands,
          executorClearCommands,
          isPayloads,
          dependencies,
          staticContracts);
    } else {
      NodeExecutor createdNodeExecutor =
          createNewBuiltinNodeExecutor(
              id,
              name,
              contractor,
              isCustomizable,
              category,
              executorCommands,
              executorClearCommands,
              isPayloads,
              dependencies,
              staticContracts);

      // delete the dummy nodeExecutor if it was created when importing the starter pack
      deleteDummyNodeExecutorIfItExists(contractor.getType(), createdNodeExecutor);
    }

    log.info("Successfully registered injector '{}' (type: {})", name, contractor.getType());
  }

  /**
   * Found NodeExecutor by type
   *
   * @param type to find
   * @return found nodeExecutor
   */
  public Optional<NodeExecutor> findByType(@NotBlank String type) {
    return this.nodeExecutorRepository.findByType(type);
  }

  private void deleteDummyNodeExecutorIfItExists(
      @NotBlank final String nodeExecutorType, final NodeExecutor newNodeExecutor) {
    nodeExecutorRepository
        .findById(nodeExecutorType + DUMMY_SUFFIX)
        .ifPresent(
            dummyNodeExecutor -> {
              if (newNodeExecutor != null) {
                List<NodeContract> nodeContracts =
                    nodeContractRepository.findNodeContractsByNodeExecutor(dummyNodeExecutor);
                nodeContracts.forEach(
                    nodeContract -> nodeContract.setNodeExecutor(newNodeExecutor));
                nodeContractRepository.saveAll(nodeContracts);
              }
              nodeExecutorRepository.delete(dummyNodeExecutor);
            });
  }

  private void uploadNodeExecutorIcon(Contractor contractor) {
    if (contractor.getIcon() != null) {
      try {
        InputStream iconData = contractor.getIcon().getData();
        fileService.uploadStream(
            INJECTORS_IMAGES_BASE_PATH, contractor.getType() + ".png", iconData);
      } catch (Exception e) {
        log.warn(
            "Failed to upload icon for injector '{}': {}", contractor.getType(), e.getMessage());
      }
    }
  }

  private void validateNoIdConflict(String id, Contractor contractor)
      throws NodeExecutorRegistrationException {
    NodeExecutor existingNodeExecutor = nodeExecutorRepository.findById(id).orElse(null);
    if (existingNodeExecutor == null) {
      Optional<NodeExecutor> conflictingNodeExecutor = nodeExecutorRepository.findByType(contractor.getType());
      if (conflictingNodeExecutor.isPresent()) {
        throw new NodeExecutorRegistrationException(
            String.format(
                "Injector '%s' already exists with a different ID (%s). "
                    + "Please delete it or contact your administrator.",
                contractor.getType(), conflictingNodeExecutor.get().getId()));
      }
    }
  }

  private void updateExistingBuiltinNodeExecutor(
      NodeExecutor nodeExecutor,
      String name,
      Contractor contractor,
      Boolean isCustomizable,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean isPayloads,
      List<ExternalServiceDependency> dependencies,
      List<Contract> staticContracts) {

    // Update nodeExecutor properties
    nodeExecutor.setExternal(false);
    applyBuiltinNodeExecutorProperties(
        nodeExecutor,
        name,
        isCustomizable,
        contractor,
        category,
        executorCommands,
        executorClearCommands,
        isPayloads,
        dependencies);

    // Synchronize contracts
    List<String> existingIds = new ArrayList<>();
    List<NodeContract> toUpdate = new ArrayList<>();
    List<String> toDelete = new ArrayList<>();

    for (NodeContract contractDB : nodeExecutor.getContracts()) {
      Optional<Contract> matchingContract =
          staticContracts.stream()
              .filter(contract -> contract.getId().equals(contractDB.getId()))
              .findFirst();

      if (matchingContract.isPresent()) {
        this.nodeContractService.updateBuiltInNodeContract(
            contractDB, matchingContract.get(), isPayloads);
        existingIds.add(contractDB.getId());
        toUpdate.add(contractDB);
      } else if (shouldDeleteContract(contractDB, nodeExecutor)) {
        toDelete.add(contractDB.getId());
      }
    }

    // Create new contracts
    List<NodeContract> toCreate =
        staticContracts.stream()
            .filter(c -> !existingIds.contains(c.getId()))
            .map(
                contract ->
                    this.nodeContractService.createBuiltinNodeContract(
                        contract, nodeExecutor, isPayloads))
            .toList();

    // Persist changes
    nodeContractRepository.deleteAllById(toDelete);
    nodeContractRepository.saveAll(toCreate);
    nodeContractRepository.saveAll(toUpdate);
    nodeExecutorRepository.save(nodeExecutor);
  }

  private boolean shouldDeleteContract(NodeContract contractDB, NodeExecutor nodeExecutor) {
    return !contractDB.getCustom() && (!nodeExecutor.isPayloads() || contractDB.getPayload() == null);
  }

  private NodeExecutor createNewBuiltinNodeExecutor(
      String id,
      String name,
      Contractor contractor,
      Boolean isCustomizable,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean isPayloads,
      List<ExternalServiceDependency> dependencies,
      List<Contract> staticContracts) {

    NodeExecutor newNodeExecutor = new NodeExecutor();
    newNodeExecutor.setId(id);
    applyBuiltinNodeExecutorProperties(
        newNodeExecutor,
        name,
        isCustomizable,
        contractor,
        category,
        executorCommands,
        executorClearCommands,
        isPayloads,
        dependencies);

    NodeExecutor savedNodeExecutor = nodeExecutorRepository.save(newNodeExecutor);

    List<NodeContract> nodeContracts =
        staticContracts.stream()
            .map(
                contract ->
                    this.nodeContractService.createBuiltinNodeContract(
                        contract, savedNodeExecutor, isPayloads))
            .toList();
    nodeContractRepository.saveAll(nodeContracts);
    return savedNodeExecutor;
  }

  private void applyBuiltinNodeExecutorProperties(
      NodeExecutor nodeExecutor,
      String name,
      Boolean isCustomizable,
      Contractor contractor,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean isPayloads,
      List<ExternalServiceDependency> dependencies) {
    nodeExecutor.setExternal(false);
    nodeExecutor.setName(name);
    nodeExecutor.setCustomContracts(isCustomizable);
    nodeExecutor.setType(contractor.getType());
    nodeExecutor.setCategory(category);
    nodeExecutor.setExecutorCommands(executorCommands);
    nodeExecutor.setExecutorClearCommands(executorClearCommands);
    nodeExecutor.setPayloads(isPayloads);
    nodeExecutor.setUpdatedAt(Instant.now());
    nodeExecutor.setDependencies(dependencies.toArray(new ExternalServiceDependency[0]));
  }
}
