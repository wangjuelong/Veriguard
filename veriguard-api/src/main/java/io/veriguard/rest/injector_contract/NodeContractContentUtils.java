package io.veriguard.rest.injector_contract;

import static io.veriguard.database.model.NodeContract.CONTRACT_ELEMENT_CONTENT_CARDINALITY;
import static io.veriguard.database.model.NodeContract.CONTRACT_ELEMENT_CONTENT_KEY;
import static io.veriguard.database.model.NodeContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.veriguard.database.model.NodeContract.CONTRACT_ELEMENT_CONTENT_KEY_NOT_DYNAMIC;
import static io.veriguard.database.model.NodeContract.DEFAULT_VALUE_FIELD;
import static io.veriguard.database.model.NodeContract.PREDEFINED_EXPECTATIONS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.NodeContract;
import io.veriguard.injector_contract.outputs.NodeContractContentOutputElement;
import java.util.List;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class NodeContractContentUtils {

  public static final String OUTPUTS = "outputs";
  public static final String FIELDS = "fields";
  public static final String MULTIPLE = "n";

  /**
   * Function used to get the outputs from the nodeExecutor contract content.
   *
   * @param content NodeExecutor Contract content
   * @param mapper ObjectMapper used to convert JSON to Java objects
   * @return List of ContractOutputElement ( from NodeExecutor contract content )
   */
  public List<NodeContractContentOutputElement> getContractOutputs(
      @NotNull final ObjectNode content, ObjectMapper mapper) {
    return StreamSupport.stream(content.get(OUTPUTS).spliterator(), false)
        .map(
            jsonNode -> {
              try {
                return mapper.treeToValue(jsonNode, NodeContractContentOutputElement.class);
              } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error processing JSON: " + jsonNode, e);
              }
            })
        .toList();
  }

  /**
   * Function used to get the dynamic fields for attackChainNode from the nodeExecutor contract.
   *
   * @param nodeContract NodeContract object containing the converted content
   * @return ObjectNode containing the dynamic fields for attackChainNode
   */
  public ObjectNode getDynamicNodeContractFieldsForAttackChainNode(NodeContract nodeContract) {
    ObjectNode convertedContent = nodeContract.getConvertedContent();

    if (convertedContent.has(FIELDS) && convertedContent.get(FIELDS).isArray()) {
      ArrayNode fieldsArray = (ArrayNode) convertedContent.get(FIELDS);
      ArrayNode fieldsNode = fieldsArray.deepCopy();
      ObjectNode attackChainNodeContent = new ObjectMapper().createObjectNode();

      for (JsonNode field : fieldsNode) {
        String key = field.get(CONTRACT_ELEMENT_CONTENT_KEY).asText();

        if (CONTRACT_ELEMENT_CONTENT_KEY_NOT_DYNAMIC.contains(key)) {
          continue;
        }

        JsonNode valueNode;

        // For expectation field, we should use predefinedExpectations
        if (CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS.equals(key)) {
          valueNode = field.get(PREDEFINED_EXPECTATIONS);
        } else {
          valueNode = field.get(DEFAULT_VALUE_FIELD);
        }

        if (valueNode == null || valueNode.isNull() || valueNode.isEmpty()) {
          continue;
        }

        JsonNode cardinalityValueNode = field.get(CONTRACT_ELEMENT_CONTENT_CARDINALITY);
        if (cardinalityValueNode != null
            && !cardinalityValueNode.isNull()
            && !cardinalityValueNode.asText().isEmpty()) {
          String cardinality = cardinalityValueNode.asText();
          if (MULTIPLE.equals(cardinality)) {
            attackChainNodeContent.set(key, valueNode);
          } else if (valueNode.has(0)) {
            attackChainNodeContent.set(key, valueNode.get(0));
          }
        } else {
          attackChainNodeContent.set(key, valueNode);
        }
      }

      return attackChainNodeContent;
    }

    return null;
  }

  /**
   * Function to find if into the nodeExecutor contract content a field with a key value exist
   *
   * @param nodeContract to analyse
   * @param field to find
   * @return true if field is found, false if not
   */
  public boolean hasField(NodeContract nodeContract, String field) {
    if (nodeContract == null || nodeContract.getContent() == null) {
      return false;
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode objectNode = (ObjectNode) mapper.readTree(nodeContract.getContent());

      return objectNode.get("fields") != null
          && objectNode.get("fields").isArray()
          && StreamSupport.stream(
                  Spliterators.spliteratorUnknownSize(objectNode.get("fields").iterator(), 0),
                  false)
              .anyMatch(node -> node.has("key") && field.equals(node.get("key").asText()));
    } catch (JsonProcessingException e) {
      return false;
    }
  }
}
