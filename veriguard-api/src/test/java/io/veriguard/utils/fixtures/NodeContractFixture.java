package io.veriguard.utils.fixtures;

import static io.veriguard.database.model.NodeContract.CONTRACT_CONTENT_FIELDS;
import static io.veriguard.database.model.NodeContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY;
import static io.veriguard.executors.Executor.CMD;
import static io.veriguard.injector_contract.ContractCardinality.Multiple;
import static io.veriguard.injector_contract.ContractDef.contractBuilder;
import static io.veriguard.injector_contract.fields.ContractAsset.assetField;
import static io.veriguard.injector_contract.fields.ContractAssetGroup.assetGroupField;
import static io.veriguard.injector_contract.fields.ContractSelect.selectFieldWithDefault;
import static io.veriguard.injectors.manual.ManualContract.MANUAL_DEFAULT;
import static io.veriguard.utils.fixtures.NodeExecutorFixture.createDefaultPayloadNodeExecutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.NodeContract;
import io.veriguard.database.model.NodeExecutor;
import io.veriguard.database.model.Payload;
import io.veriguard.database.repository.NodeContractRepository;
import io.veriguard.injector_contract.ContractCardinality;
import io.veriguard.injector_contract.ContractDef;
import io.veriguard.injector_contract.ContractTargetedProperty;
import io.veriguard.injector_contract.fields.*;
import io.veriguard.injector_contract.fields.ContractElement;
import io.veriguard.injector_contract.fields.ContractSelect;
import io.veriguard.injector_contract.fields.ContractTargetedAsset;
import io.veriguard.integration.Manager;
import io.veriguard.integration.impl.injectors.manual.ManualNodeExecutorIntegrationFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class NodeContractFixture {

  @Autowired private NodeContractRepository nodeContractRepository;
  @Autowired private ManualNodeExecutorIntegrationFactory manualNodeExecutorIntegrationFactory;

  private static ObjectNode createDefaultContent(ObjectMapper objectMapper) {
    ObjectNode node = objectMapper.createObjectNode();
    node.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(new ArrayList<>()));
    return node;
  }

  public static NodeContract createPayloadNodeContractWithFieldsContent(
      List<ContractCardinalityElement> customFieldsContent) throws JsonProcessingException {
    NodeContract nodeContract = new NodeContract();
    nodeContract.setId(UUID.randomUUID().toString());

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode content = createDefaultContent(objectMapper);
    content.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(customFieldsContent));

    nodeContract.setContent(objectMapper.writeValueAsString(content));
    nodeContract.setConvertedContent(content);

    return nodeContract;
  }

  public static NodeContract createPayloadNodeContractWithFieldsContent(
      NodeExecutor nodeExecutor,
      Payload payloadCommand,
      List<ContractCardinalityElement> customFieldsContent)
      throws JsonProcessingException {
    NodeContract nodeContract = createPayloadNodeContractWithFieldsContent(customFieldsContent);
    nodeContract.setNodeExecutor(nodeExecutor);
    nodeContract.setPayload(payloadCommand);
    return nodeContract;
  }

  @SneakyThrows
  private static NodeContract createDefaultNodeContractInternal() {
    NodeContract nodeContract = new NodeContract();
    nodeContract.setNodeExecutor(createDefaultPayloadNodeExecutor());
    nodeContract.setId(UUID.randomUUID().toString());

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode content = createDefaultContent(objectMapper);
    nodeContract.setContent(objectMapper.writeValueAsString(content));
    nodeContract.setConvertedContent(content);
    nodeContract.setDomains(new HashSet<>());
    return nodeContract;
  }

  public static NodeContract createDefaultNodeContract() {
    return createDefaultNodeContractInternal();
  }

  public static NodeContract createDefaultNodeContractWithExternalId(String externalId) {
    NodeContract nodeContract = createDefaultNodeContractInternal();
    nodeContract.setExternalId(externalId);
    return nodeContract;
  }

  public static NodeContract createNodeContractWithPlatforms(Endpoint.PLATFORM_TYPE[] platforms) {
    NodeContract nodeContract = createDefaultNodeContract();
    nodeContract.setPlatforms(platforms);
    return nodeContract;
  }

  public static NodeContract createPayloadNodeContract(
      NodeExecutor nodeExecutor, Payload payloadCommand) throws JsonProcessingException {
    return createPayloadNodeContractWithFieldsContent(nodeExecutor, payloadCommand, List.of());
  }

  public static NodeContract createPayloadNodeContractWithObfuscator(String executor)
      throws JsonProcessingException {
    ContractSelect obfuscatorSelect =
        new ContractSelect("obfuscator", "Obfuscators", ContractCardinality.One);

    if (CMD.equals(executor)) {
      obfuscatorSelect.setChoices(Map.of("plain-text", "plain-text"));
    } else {
      obfuscatorSelect.setChoices(Map.of("plain-text", "plain-text", "base64", "base64"));
    }

    return createPayloadNodeContractWithFieldsContent(List.of(obfuscatorSelect));
  }

  public static NodeContract createPayloadNodeContractWithObfuscator(
      NodeExecutor nodeExecutor, Payload payloadCommand) throws JsonProcessingException {
    ContractSelect obfuscatorSelect =
        new ContractSelect("obfuscator", "Obfuscators", ContractCardinality.One);
    obfuscatorSelect.setChoices(Map.of("plain-text", "plain-text", "base64", "base64"));

    return createPayloadNodeContractWithFieldsContent(
        nodeExecutor, payloadCommand, List.of(obfuscatorSelect));
  }

  public static NodeContract createNodeContract(Map<String, String> labels, String content)
      throws JsonProcessingException {
    NodeContract nodeContract = new NodeContract();
    nodeContract.setId(UUID.randomUUID().toString());
    nodeContract.setLabels(labels);
    nodeContract.setContent(content);
    nodeContract.setConvertedContent(new ObjectMapper().readValue(content, ObjectNode.class));
    nodeContract.setAtomicTesting(true);
    nodeContract.setCreatedAt(Instant.now());
    nodeContract.setUpdatedAt(Instant.now());
    return nodeContract;
  }

  public static NodeContract createNodeContract(ObjectNode convertedContent) {
    NodeContract nodeContract = new NodeContract();
    nodeContract.setId(UUID.randomUUID().toString());
    nodeContract.setConvertedContent(convertedContent);
    nodeContract.setContent(convertedContent.toString());
    nodeContract.setAtomicTesting(false);
    nodeContract.setCreatedAt(Instant.now());
    nodeContract.setUpdatedAt(Instant.now());
    return nodeContract;
  }

  public static NodeContract createNodeContract(Map<String, String> labels)
      throws JsonProcessingException {
    String content = "{\"fields\": []}";
    return createNodeContract(labels, content);
  }

  // -- BUILDER --

  public static void addField(
      NodeContract nodeContract, ObjectMapper mapper, List<ContractElement> contractElements)
      throws JsonProcessingException {
    ObjectNode content = mapper.readValue(nodeContract.getContent(), ObjectNode.class);
    List<ContractElement> elements =
        mapper.convertValue(content.get(CONTRACT_CONTENT_FIELDS), new TypeReference<>() {});
    if (CollectionUtils.isEmpty(elements)) {
      elements = new ArrayList<>();
    }

    elements.addAll(contractElements);

    content.set(CONTRACT_CONTENT_FIELDS, mapper.valueToTree(elements));
    nodeContract.setContent(mapper.writeValueAsString(content));
    nodeContract.setConvertedContent(content);
  }

  public static List<ContractElement> buildAssetField(final boolean mandatory) {
    ContractDef builder = contractBuilder();
    ContractAsset assetField = assetField(Multiple);
    if (mandatory) {
      builder.mandatory(assetField);
    } else {
      builder.optional(assetField);
    }
    return builder.build();
  }

  public static List<ContractElement> buildMandatoryGroup() {
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractDef builder = contractBuilder();
    builder.mandatoryGroup(assetField, assetGroupField);
    return builder.build();
  }

  public static List<ContractElement> buildMandatoryOnCondition() {
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractDef builder = contractBuilder();
    return builder
        .mandatoryOnCondition(assetField, assetGroupField)
        .optional(assetGroupField)
        .build();
  }

  public static List<ContractElement> buildMandatoryOnConditionValue(@NotBlank final String value) {
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractDef builder = contractBuilder();
    return builder
        .mandatoryOnConditionValue(assetField, assetGroupField, value)
        .optional(assetGroupField)
        .build();
  }

  public static List<ContractElement> buildMandatoryOnConditionValue(
      @NotNull final List<String> values) {
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractDef builder = contractBuilder();
    return builder
        .mandatoryOnConditionValue(assetField, assetGroupField, values)
        .optional(assetGroupField)
        .build();
  }

  public static void addTargetedAssetFields(
      NodeContract nodeContract, String key, ContractTargetedProperty defaultTargetedProperty) {
    ContractElement targetedAssetField = new ContractTargetedAsset(key, "label-" + key);
    ContractElement targetPropertySelector =
        selectFieldWithDefault(
            CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY + "-" + key,
            "Targeted Property",
            new HashMap<>(),
            defaultTargetedProperty.name());
    targetPropertySelector.setLinkedFields(List.of(targetedAssetField));

    JsonNode nodeContractFieldsNode =
        nodeContract.getConvertedContent().get(CONTRACT_CONTENT_FIELDS);

    if (!(nodeContractFieldsNode instanceof ArrayNode)) {
      throw new IllegalArgumentException("The fields node is not an ArrayNode");
    }

    ArrayNode arrayNode = (ArrayNode) nodeContractFieldsNode;
    ObjectMapper objectMapper = new ObjectMapper();

    arrayNode.add(objectMapper.valueToTree(targetedAssetField));
    arrayNode.add(objectMapper.valueToTree(targetPropertySelector));
    nodeContract.getConvertedContent().set(CONTRACT_CONTENT_FIELDS, arrayNode);
  }

  public NodeContract getWellKnownSingleManualContract() {
    Optional<NodeContract> nodeContract = nodeContractRepository.findById(MANUAL_DEFAULT);
    if (nodeContract.isPresent()) {
      return nodeContract.get();
    }
    try {
      Manager manager = new Manager(List.of(manualNodeExecutorIntegrationFactory));
      manager.monitorIntegrations();
      return nodeContractRepository.findById(MANUAL_DEFAULT).orElseThrow();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
