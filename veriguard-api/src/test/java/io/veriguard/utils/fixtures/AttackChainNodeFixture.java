package io.veriguard.utils.fixtures;

import static io.veriguard.database.model.NodeContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.rest.atomic_testing.form.AtomicTestingInput;
import io.veriguard.rest.inject.form.AttackChainNodeDocumentInput;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AttackChainNodeFixture {

  public static final String INJECT_EMAIL_NAME = "Test email inject";
  public static final String INJECT_CHALLENGE_NAME = "Test challenge inject";

  public static AtomicTestingInput createAtomicTesting(String title, String documentId) {
    AtomicTestingInput input = new AtomicTestingInput();
    // 二开移除 Email nodeExecutor — atomic testing 默认不再绑定 EMAIL_DEFAULT contract.
    input.setContent(attackChainNodeContent());
    input.setTitle(title);
    input.setAllTeams(false);
    if (documentId != null) {
      AttackChainNodeDocumentInput documentInput = new AttackChainNodeDocumentInput();
      documentInput.setDocumentId(documentId);
      documentInput.setAttached(true);
      input.setDocuments(List.of(documentInput));
    }
    return input;
  }

  public static AttackChainNode createAttackChainNode(NodeContract nodeContract, String title) {
    AttackChainNode attackChainNode = createAttackChainNodeWithTitle(title);
    attackChainNode.setNodeContract(nodeContract);
    attackChainNode.setEnabled(true);
    attackChainNode.setDependsDuration(0L);
    attackChainNode.setContent(attackChainNodeContent());
    return attackChainNode;
  }

  public static AttackChainNode createAttackChainNodeWithManualExpectation(
      NodeContract nodeContract, String title, String manualExpectationTitle) {
    AttackChainNode attackChainNode = createAttackChainNodeWithTitle(title);
    attackChainNode.setNodeContract(nodeContract);
    attackChainNode.setEnabled(true);
    attackChainNode.setDependsDuration(0L);
    attackChainNode.setContent(attackChainNodeContent(manualExpectationTitle));
    return attackChainNode;
  }

  private static ObjectNode attackChainNodeContent() {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode attackChainNodeContent = objectMapper.createObjectNode();
    attackChainNodeContent.set(
        CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS,
        objectMapper.convertValue(
            List.of(
                ExpectationFixture.createExpectation(AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL)),
            ArrayNode.class));
    return attackChainNodeContent;
  }

  private static ObjectNode attackChainNodeContent(String expectationName) {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode attackChainNodeContent = objectMapper.createObjectNode();
    attackChainNodeContent.set(
        CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS,
        objectMapper.convertValue(
            List.of(
                ExpectationFixture.createExpectation(
                    AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL, expectationName)),
            ArrayNode.class));
    return attackChainNodeContent;
  }

  public static AttackChainNode createTechnicalAttackChainNode(
      NodeContract nodeContract, String title, Asset asset) {
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setTitle(title);
    attackChainNode.setNodeContract(nodeContract);
    attackChainNode.setAssets(List.of(asset));
    attackChainNode.setEnabled(true);
    attackChainNode.setDependsDuration(0L);
    return attackChainNode;
  }

  public static AttackChainNode createTechnicalAttackChainNodeWithAssetGroup(
      NodeContract nodeContract, String title, AssetGroup assetGroup) {
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setTitle(title);
    attackChainNode.setNodeContract(nodeContract);
    attackChainNode.setAssetGroups(List.of(assetGroup));
    attackChainNode.setEnabled(true);
    attackChainNode.setDependsDuration(0L);
    return attackChainNode;
  }

  public static AttackChainNode getAttackChainNodeWithoutContract() {
    AttackChainNode attackChainNode = createAttackChainNodeWithTitle(INJECT_EMAIL_NAME);
    attackChainNode.setEnabled(true);
    attackChainNode.setDependsDuration(0L);
    return attackChainNode;
  }

  public static AttackChainNode getAttackChainNodeWithAllTeams() {
    AttackChainNode attackChainNode = createAttackChainNodeWithTitle(INJECT_EMAIL_NAME);
    attackChainNode.setEnabled(true);
    attackChainNode.setDependsDuration(0L);
    attackChainNode.setAllTeams(true);
    return attackChainNode;
  }

  public static AttackChainNode getDefaultAttackChainNode() {
    AttackChainNode attackChainNode = createAttackChainNodeWithDefaultTitle();
    attackChainNode.setEnabled(true);
    attackChainNode.setDependsDuration(0L);
    return attackChainNode;
  }

  public static AttackChainNode getDefaultAttackChainNodeWithDuration(long duration) {
    AttackChainNode attackChainNode = createAttackChainNodeWithDefaultTitle();
    attackChainNode.setEnabled(true);
    attackChainNode.setDependsDuration(duration);
    return attackChainNode;
  }

  public static AttackChainNode getAttackChainNodeForEmailContract(NodeContract nodeContract) {
    return createAttackChainNode(nodeContract, INJECT_EMAIL_NAME);
  }

  public static AttackChainNode createAttackChainNodeWithPayloadArg(Map<String, Object> payloadArguments) {
    AttackChainNode attackChainNode = createAttackChainNodeWithTitle("Inject title");
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode attackChainNodeContent = objectMapper.createObjectNode();
    payloadArguments.forEach(
        (key, value) -> attackChainNodeContent.set(key, objectMapper.convertValue(value, JsonNode.class)));

    payloadArguments.forEach(
        (key, value) -> {
          if (value instanceof String) {
            attackChainNodeContent.set(key, objectMapper.convertValue(value, JsonNode.class));
          } else if (value instanceof List) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            (((List<?>) value).stream().toList()).forEach(item -> arrayNode.add(item.toString()));
            attackChainNodeContent.set(key, arrayNode);
          } else {
            throw new IllegalArgumentException("Unsupported type for key: " + key);
          }
        });

    attackChainNode.setContent(attackChainNodeContent);
    return attackChainNode;
  }

  public static AttackChainNode createAttackChainNodeWithPayloadArg(
      NodeContract nodeContract, Map<String, Object> payloadArguments) {

    AttackChainNode attackChainNode = createAttackChainNodeWithPayloadArg(payloadArguments);
    attackChainNode.setNodeContract(nodeContract);
    return attackChainNode;
  }

  private static AttackChainNode createAttackChainNodeWithDefaultTitle() {
    return createAttackChainNodeWithTitle(null);
  }

  private static AttackChainNode createAttackChainNodeWithTitle(String title) {
    String new_title = title == null ? "inject-%s".formatted(UUID.randomUUID()) : title;
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setDependsDuration(0L);
    attackChainNode.setTitle(new_title);
    return attackChainNode;
  }
}
