package io.veriguard.rest;

import static io.veriguard.rest.attack_chain.AttackChainApi.SCENARIO_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.Variable;
import io.veriguard.database.repository.AttackChainRepository;
import io.veriguard.database.repository.VariableRepository;
import io.veriguard.service.attack_chain.AttackChainService;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utilstest.RabbitMQTestListener;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
public class VariableApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private AttackChainService attackChainService;
  @Autowired private AttackChainRepository attackChainRepository;
  @Autowired private VariableRepository variableRepository;

  static String VARIABLE_ID;
  static String SCENARIO_ID;

  @AfterAll
  void afterAll() {
    this.attackChainRepository.deleteById(SCENARIO_ID);
    this.variableRepository.deleteById(VARIABLE_ID);
  }

  // -- SCENARIOS --

  @DisplayName("Create variable for scenario succeed")
  @Test
  @Order(1)
  @WithMockUser(isAdmin = true)
  void createVariableForAttackChainTest() throws Exception {
    // -- PREPARE --
    AttackChain attackChain = new AttackChain();
    attackChain.setName("Scenario name");
    AttackChain attackChainCreated = this.attackChainService.createAttackChain(attackChain);
    SCENARIO_ID = attackChainCreated.getId();
    Variable variable = new Variable();

    // -- EXECUTE & ASSERT --
    this.mvc
        .perform(
            post(SCENARIO_URI + "/" + SCENARIO_ID + "/variables")
                .content(asJsonString(variable))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());

    // -- PREPARE --
    String variableKey = "key";
    variable.setKey(variableKey);
    variable.setAttackChain(attackChain);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI + "/" + SCENARIO_ID + "/variables")
                    .content(asJsonString(variable))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.variable_key").value(variableKey))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    VARIABLE_ID = JsonPath.read(response, "$.variable_id");
  }

  @DisplayName("Retrieve variables for scenario")
  @Test
  @Order(2)
  @WithMockUser(isAdmin = true)
  void retrieveVariableForAttackChainTest() throws Exception {
    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + SCENARIO_ID + "/variables")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Update variable for scenario")
  @Test
  @Order(3)
  @WithMockUser(isAdmin = true)
  void updateVariableForAttackChainTest() throws Exception {
    // -- PREPARE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + SCENARIO_ID + "/variables")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Variable variable = new Variable();
    String variableValue = "variable-value";
    variable.setKey(JsonPath.read(response, "$[0].variable_key"));
    variable.setValue("variable-value");

    // -- EXECUTE --
    response =
        this.mvc
            .perform(
                put(SCENARIO_URI + "/" + SCENARIO_ID + "/variables/" + VARIABLE_ID)
                    .content(asJsonString(variable))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(variableValue, JsonPath.read(response, "$.variable_value"));
  }

  @DisplayName("Delete variable for scenario")
  @Test
  @Order(4)
  @WithMockUser(isAdmin = true)
  void deleteVariableForAttackChainTest() throws Exception {
    // -- EXECUTE 1 ASSERT --
    this.mvc
        .perform(
            delete(SCENARIO_URI + "/" + SCENARIO_ID + "/variables/" + VARIABLE_ID).with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }
}
