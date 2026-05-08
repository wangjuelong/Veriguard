package io.veriguard.rest.collector.service;

import static io.veriguard.database.specification.CollectorSpecification.hasSecurityPlatform;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.service.FileService.COLLECTORS_IMAGES_BASE_PATH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.CollectorRepository;
import io.veriguard.database.repository.ConnectorInstanceConfigurationRepository;
import io.veriguard.rest.catalog_connector.dto.ConnectorIds;
import io.veriguard.rest.collector.form.CollectorOutput;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.service.FileService;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connectors.AbstractConnectorService;
import io.veriguard.utils.mapper.CatalogConnectorMapper;
import io.veriguard.utils.mapper.CollectorMapper;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.io.InputStream;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CollectorService extends AbstractConnectorService<Collector, CollectorOutput> {

  @Resource protected ObjectMapper mapper;

  private final CollectorRepository collectorRepository;

  private final FileService fileService;

  private final CollectorMapper collectorMapper;

  @Autowired
  public CollectorService(
      CollectorRepository collectorRepository,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      FileService fileService,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      CollectorMapper collectorMapper,
      CatalogConnectorMapper catalogConnectorMapper) {
    super(
        ConnectorType.COLLECTOR,
        connectorInstanceConfigurationRepository,
        catalogConnectorService,
        connectorInstanceService,
        catalogConnectorMapper);
    this.collectorRepository = collectorRepository;
    this.fileService = fileService;
    this.collectorMapper = collectorMapper;
  }

  @Override
  protected List<Collector> getAllConnectors() {
    return fromIterable(this.collectors());
  }

  @Override
  protected Collector getConnectorById(String collectorId) {
    return collector(collectorId);
  }

  @Override
  protected CollectorOutput mapToOutput(
      Collector collector,
      CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance,
      boolean existingCollector) {
    return collectorMapper.toCollectorOutput(
        collector, catalogConnector, connectorInstance, existingCollector);
  }

  @Override
  protected Collector createNewConnector() {
    return new Collector();
  }

  // -- CRUD --

  public Collector collector(String id) {
    return collectorRepository
        .findById(id)
        .orElseThrow(() -> new ElementNotFoundException("Collector not found with id: " + id));
  }

  /**
   * Retrieve all collectors
   *
   * @return List of collectors
   */
  public Iterable<Collector> collectors() {
    return collectorRepository.findAll();
  }

  /**
   * Retrieve all collectors.
   *
   * @param isIncludeNext Include pending collectors.
   * @return List of collector output
   */
  public Iterable<CollectorOutput> collectorsOutput(boolean isIncludeNext) {
    return getConnectorsOutput(isIncludeNext);
  }

  /**
   * Retrieves IDs of resources associated with a collector.
   *
   * @param collectorId collector identifier.
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getCollectorRelationsId(String collectorId) {
    return getConnectorRelationsId(collectorId);
  }

  /**
   * Finds a collector by its type.
   *
   * @param type the collector type to search for
   * @return the collector matching the given type
   * @throws ElementNotFoundException if no collector is found with the given type
   */
  public Collector collectorByType(String type) throws ElementNotFoundException {
    return findCollectorByType(type)
        .orElseThrow(() -> new ElementNotFoundException("Collector not found with type: " + type));
  }

  /**
   * Finds a collector by its type.
   *
   * @param type the collector type to search for
   * @return an Optional containing the collector if found, empty otherwise
   */
  public Optional<Collector> findCollectorByType(String type) {
    return collectorRepository.findByType(type);
  }

  public List<Collector> securityPlatformCollectors() {
    return fromIterable(collectorRepository.findAll(hasSecurityPlatform()));
  }

  public Collector updateCollectorState(Collector collectorToUpdate, ObjectNode newState) {
    ObjectNode state =
        Optional.ofNullable(collectorToUpdate.getState()).orElse(mapper.createObjectNode());
    newState
        .fieldNames()
        .forEachRemaining(fieldName -> state.set(fieldName, newState.get(fieldName)));
    collectorToUpdate.setState(state);
    return collectorRepository.save(collectorToUpdate);
  }

  // -- ACTION --

  @Transactional
  public void register(String id, String type, String name, InputStream iconData) throws Exception {
    if (iconData != null) {
      fileService.uploadStream(COLLECTORS_IMAGES_BASE_PATH, type + ".png", iconData);
    }
    Collector collector = collectorRepository.findById(id).orElse(null);
    if (collector == null) {
      Collector collectorChecking = collectorRepository.findByType(type).orElse(null);
      if (collectorChecking != null) {
        throw new Exception(
            "The collector "
                + type
                + " already exists with a different ID, please delete it or contact your administrator.");
      }
    }
    if (collector != null) {
      collector.setName(name);
      collector.setExternal(false);
      collector.setType(type);
      collectorRepository.save(collector);
    } else {
      // save the collector
      Collector newCollector = new Collector();
      newCollector.setId(id);
      newCollector.setName(name);
      newCollector.setType(type);
      collectorRepository.save(newCollector);
    }
  }

  public List<Collector> collectorsForPayload(String payloadId) {
    return collectorRepository.findByPayloadId(payloadId);
  }

  public List<Collector> collectorsForAtomicTesting(String attackChainNodeId) {
    return collectorRepository.findByAttackChainNodeId(attackChainNodeId);
  }
}
