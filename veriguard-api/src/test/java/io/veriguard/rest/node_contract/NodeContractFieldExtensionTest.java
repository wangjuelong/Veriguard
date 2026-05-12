package io.veriguard.rest.node_contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.veriguard.database.model.NodeContract;
import io.veriguard.database.model.contract.DefenseLayer;
import io.veriguard.database.model.contract.NetworkProtocolFamily;
import io.veriguard.database.model.contract.SoftwareCategory;
import io.veriguard.database.model.contract.TargetOs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies Jackson round-trip for the 6 IPv6 安全验证 fields added to {@link NodeContract} in A3.
 *
 * <p>This is a pure unit test against a vanilla {@link ObjectMapper} — no Spring context, no JPA,
 * no Bean Validation enforcement. It guards the wire contract: snake_case property names match the
 * column / migration naming, enums emit their lowercase {@code name()}, primitives default to
 * {@code false}, and {@code rollbackSteps} (a {@link JsonNode}) accepts arbitrary jsonb-shaped
 * payloads in both directions.
 */
@DisplayName("NodeContract A3 field extension serialization")
class NodeContractFieldExtensionTest {

  private static final String ID = "test-1";
  private static final String CONTENT = "{}";

  /**
   * Builds a fresh {@link ObjectMapper} with the JSR-310 module registered so {@link
   * NodeContract}'s {@code @CreationTimestamp Instant} fields (createdAt / updatedAt) can be
   * serialized. Mirrors the production Spring Boot ObjectMapper configuration without pulling in
   * the Spring context for this unit test.
   */
  private static ObjectMapper newMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Test
  @DisplayName("serialize writes all six new fields under snake_case property names")
  void serialize_writesAllSixFieldsAsSnakeCaseProperties() throws Exception {
    ObjectMapper mapper = newMapper();
    NodeContract contract = new NodeContract();
    contract.setId(ID);
    contract.setContent(CONTENT);
    contract.setSoftwareCategory(SoftwareCategory.web_component);
    contract.setDefenseLayer(DefenseLayer.boundary);
    contract.setNetworkProtocolFamily(NetworkProtocolFamily.ipv6);
    contract.setTargetOs(TargetOs.linux);
    contract.setNetworkDependent(true);
    contract.setRollbackSteps(
        mapper.readTree("[{\"action\":\"delete_file\",\"path\":\"/tmp/x\"}]"));

    String json = mapper.writeValueAsString(contract);

    assertTrue(
        json.contains("\"injector_contract_software_category\":\"web_component\""),
        "software_category should serialize as snake_case lowercase enum");
    assertTrue(
        json.contains("\"injector_contract_defense_layer\":\"boundary\""),
        "defense_layer should serialize as snake_case lowercase enum");
    assertTrue(
        json.contains("\"injector_contract_network_protocol_family\":\"ipv6\""),
        "network_protocol_family should serialize as snake_case lowercase enum");
    assertTrue(
        json.contains("\"injector_contract_target_os\":\"linux\""),
        "target_os should serialize as snake_case lowercase enum");
    assertTrue(
        json.contains("\"injector_contract_network_dependent\":true"),
        "network_dependent should serialize as boolean primitive");
    assertTrue(
        json.contains(
            "\"injector_contract_rollback_steps\":[{\"action\":\"delete_file\",\"path\":\"/tmp/x\"}]"),
        "rollback_steps should serialize as embedded JSON array");
  }

  @Test
  @DisplayName("deserialize reads all six new fields from snake_case JSON")
  void deserialize_readsAllSixFieldsFromSnakeCase() throws Exception {
    ObjectMapper mapper = newMapper();
    String json =
        "{"
            + "\"injector_contract_id\":\"test-1\","
            + "\"injector_contract_content\":\"{}\","
            + "\"injector_contract_software_category\":\"security_product\","
            + "\"injector_contract_defense_layer\":\"host\","
            + "\"injector_contract_network_protocol_family\":\"both\","
            + "\"injector_contract_target_os\":\"windows\","
            + "\"injector_contract_network_dependent\":true,"
            + "\"injector_contract_rollback_steps\":[{\"action\":\"kill_process\",\"pid\":1234}]"
            + "}";

    NodeContract contract = mapper.readValue(json, NodeContract.class);

    assertEquals(ID, contract.getId());
    assertEquals(CONTENT, contract.getContent());
    assertEquals(SoftwareCategory.security_product, contract.getSoftwareCategory());
    assertEquals(DefenseLayer.host, contract.getDefenseLayer());
    assertEquals(NetworkProtocolFamily.both, contract.getNetworkProtocolFamily());
    assertEquals(TargetOs.windows, contract.getTargetOs());
    assertTrue(contract.isNetworkDependent());
    JsonNode rollback = contract.getRollbackSteps();
    assertNotNull(rollback, "rollback_steps should deserialize to a JsonNode");
    assertTrue(rollback.isArray());
    assertEquals("kill_process", rollback.get(0).get("action").asText());
    assertEquals(1234, rollback.get(0).get("pid").asInt());
  }

  @Test
  @DisplayName("networkDependent defaults to false when constructed without setters")
  void networkDependent_defaultsToFalseWhenAbsent() {
    NodeContract contract = new NodeContract();

    assertFalse(
        contract.isNetworkDependent(),
        "primitive boolean networkDependent must default to false per Java semantics");
  }

  @Test
  @DisplayName("rollbackSteps absent in JSON deserializes to null and serializes back as null")
  void rollbackSteps_acceptsNullJsonNode() throws Exception {
    ObjectMapper mapper = newMapper();
    NodeContract contract = new NodeContract();
    contract.setId(ID);
    contract.setContent(CONTENT);

    // Field never set — should remain null.
    assertNull(contract.getRollbackSteps(), "rollbackSteps must be null when unset");

    String json = mapper.writeValueAsString(contract);
    // Jackson's default behaviour for a null POJO field is to emit "key":null.
    assertTrue(
        json.contains("\"injector_contract_rollback_steps\":null"),
        "null rollbackSteps should serialize as JSON null, observed: " + json);
  }
}
