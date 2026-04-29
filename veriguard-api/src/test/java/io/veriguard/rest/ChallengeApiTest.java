package io.veriguard.rest;

import static io.veriguard.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.veriguard.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.veriguard.utils.fixtures.ChallengeFixture.createDefaultChallenge;
import static io.veriguard.utils.fixtures.InjectFixture.createDefaultInjectChallenge;
import static io.veriguard.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.Challenge;
import io.veriguard.database.model.Inject;
import io.veriguard.database.model.Scenario;
import io.veriguard.database.repository.ChallengeRepository;
import io.veriguard.database.repository.InjectRepository;
import io.veriguard.database.repository.InjectorContractRepository;
import io.veriguard.integration.Manager;
import io.veriguard.integration.impl.injectors.challenge.ChallengeInjectorIntegrationFactory;
import io.veriguard.service.scenario.ScenarioService;
import io.veriguard.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
@Transactional
class ChallengeApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ScenarioService scenarioService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private ChallengeInjectorIntegrationFactory challengeInjectorIntegrationFactory;
  @Resource private ObjectMapper objectMapper;

  @BeforeEach
  public void before() throws Exception {
    new Manager(List.of(challengeInjectorIntegrationFactory)).monitorIntegrations();
  }

  // -- SCENARIOS --

  @DisplayName("Retrieve challenges for scenario")
  @Test
  @WithMockUser(isAdmin = true)
  void retrieveChallengesVariableForScenarioTest() throws Exception {
    // -- PREPARE --
    Scenario scenario = createDefaultCrisisScenario();
    Scenario scenarioCreated = this.scenarioService.createScenario(scenario);
    assertNotNull(scenarioCreated, "Scenario should be successfully created");
    String SCENARIO_ID = scenarioCreated.getId();

    Challenge challenge = createDefaultChallenge();
    Challenge challengeCreated = this.challengeRepository.save(challenge);
    assertNotNull(challengeCreated, "Challenge should be successfully created");
    String CHALLENGE_ID = challengeCreated.getId();

    Inject inject =
        createDefaultInjectChallenge(
            this.injectorContractRepository.findById(CHALLENGE_PUBLISH).orElseThrow(),
            this.objectMapper,
            List.of(CHALLENGE_ID));
    inject.setScenario(scenarioCreated);
    Inject injectCreated = this.injectRepository.save(inject);
    assertNotNull(injectCreated, "Inject should be successfully created");
    String INJECT_ID = injectCreated.getId();

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + SCENARIO_ID + "/challenges")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response, "Response should not be null");
    assertEquals(
        challenge.getName(),
        JsonPath.read(response, "$[0].challenge_name"),
        "Challenge name should match the expected value");
  }
}
