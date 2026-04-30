package io.veriguard.rest.scenario;

import static io.veriguard.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.veriguard.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackPatternRepository;
import io.veriguard.database.repository.InjectRepository;
import io.veriguard.injectors.manual.ManualContract;
import io.veriguard.integration.Manager;
import io.veriguard.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.veriguard.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.veriguard.rest.inject.form.InjectInput;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.EndpointService;
import io.veriguard.service.scenario.ScenarioService;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.AttackPatternComposer;
import io.veriguard.utils.fixtures.composers.DomainComposer;
import io.veriguard.utils.fixtures.composers.InjectorContractComposer;
import io.veriguard.utils.fixtures.composers.PayloadComposer;
import io.veriguard.utils.fixtures.files.AttackPatternFixture;
import io.veriguard.utils.mockUser.WithMockUser;
import jakarta.servlet.ServletException;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
class ScenarioInjectApiTest extends IntegrationTest {

  static String SCENARIO_INJECT_ID;
  static Scenario SCENARIO;
  static AttackPattern ATTACKPATTERN;
  static Endpoint LINUX_X86_64;
  static Endpoint WINDOWS_X86_64;
  static Endpoint WINDOWS_ARM64;
  static AssetGroup ALL_ASSETGROUP;
  static AssetGroup ALL_WINDOWS;

  @Autowired private InjectorFixture injectorFixture;

  @Autowired private MockMvc mvc;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DomainComposer domainComposer;

  @Autowired private AttackPatternRepository attackPatternRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private EndpointService endpointService;
  @Autowired private ScenarioService scenarioService;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  List<InjectorContractComposer.Composer> injectorContractWrapperComposers = new ArrayList<>();

  @BeforeAll
  void beforeAll() throws Exception {
    new Manager(List.of(emailInjectorIntegrationFactory, manualInjectorIntegrationFactory))
        .monitorIntegrations();
    Scenario scenario = new Scenario();
    scenario.setName("Scenario name");
    scenario.setFrom("test@test.com");
    scenario.setReplyTos(List.of("test@test.com"));
    SCENARIO = scenarioService.createScenario(scenario);

    ATTACKPATTERN = attackPatternRepository.save(AttackPatternFixture.createDefaultAttackPattern());
    LINUX_X86_64 =
        endpointService.createEndpoint(
            EndpointFixture.createDefaultLinuxEndpointWithArch(Endpoint.PLATFORM_ARCH.x86_64));
    WINDOWS_X86_64 =
        endpointService.createEndpoint(
            EndpointFixture.createDefaultWindowsEndpointWithArch(Endpoint.PLATFORM_ARCH.x86_64));
    WINDOWS_ARM64 =
        endpointService.createEndpoint(
            EndpointFixture.createDefaultWindowsEndpointWithArch(Endpoint.PLATFORM_ARCH.arm64));
    ALL_ASSETGROUP =
        assetGroupService.createAssetGroup(
            AssetGroupFixture.createAssetGroupWithAssets(
                "all", List.of(LINUX_X86_64, WINDOWS_ARM64, WINDOWS_X86_64)));
    ALL_WINDOWS =
        assetGroupService.createAssetGroup(
            AssetGroupFixture.createAssetGroupWithAssets(
                "all", List.of(WINDOWS_ARM64, WINDOWS_X86_64)));
  }

  @AfterAll
  void afterAll() {
    attackPatternRepository.delete(ATTACKPATTERN);
  }

  @DisplayName("Add an inject for scenario")
  @Test
  @Order(1)
  @WithMockUser(isAdmin = true)
  void addInjectForScenarioTest() throws Exception {
    // -- PREPARE --
    InjectInput input = new InjectInput();
    input.setTitle("Test inject");
    input.setInjectorContract(EMAIL_DEFAULT);
    input.setDependsDuration(0L);

    // -- EXECUTE --
    String response =
        mvc.perform(
                post(SCENARIO_URI + "/" + SCENARIO.getId() + "/injects")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    SCENARIO_INJECT_ID = JsonPath.read(response, "$.inject_id");
    response =
        mvc.perform(
                get(SCENARIO_URI + "/" + SCENARIO.getId() + "/injects")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(SCENARIO_INJECT_ID, JsonPath.read(response, "$[0].inject_id"));
  }

  @DisplayName("Retrieve injects for scenario")
  @Test
  @Order(2)
  @WithMockUser(isAdmin = true)
  void retrieveInjectsForScenarioTest() throws Exception {
    // -- EXECUTE --
    String response =
        mvc.perform(
                get(SCENARIO_URI + "/" + SCENARIO.getId() + "/injects")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(SCENARIO_INJECT_ID, JsonPath.read(response, "$[0].inject_id"));
  }

  @DisplayName("Retrieve inject for scenario")
  @Test
  @Order(3)
  @WithMockUser(isAdmin = true)
  void retrieveInjectForScenarioTest() throws Exception {
    // -- EXECUTE --
    String response =
        mvc.perform(
                get(SCENARIO_URI + "/" + SCENARIO.getId() + "/injects/" + SCENARIO_INJECT_ID)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(SCENARIO_INJECT_ID, JsonPath.read(response, "$.inject_id"));
  }

  @DisplayName("Update inject for scenario")
  @Test
  @Order(4)
  @WithMockUser(isAdmin = true)
  void updateInjectForScenarioTest() throws Exception {
    // -- PREPARE --
    Inject inject = injectRepository.findById(SCENARIO_INJECT_ID).orElseThrow();
    InjectInput input = new InjectInput();
    String injectTitle = "A new title";
    input.setTitle(injectTitle);
    input.setInjectorContract(
        inject.getInjectorContract().map(InjectorContract::getId).orElse(null));
    input.setDependsDuration(inject.getDependsDuration());

    // -- EXECUTE --
    String response =
        mvc.perform(
                put(SCENARIO_URI + "/" + SCENARIO.getId() + "/injects/" + SCENARIO_INJECT_ID)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(injectTitle, JsonPath.read(response, "$.inject_title"));
  }

  @DisplayName("Delete inject for scenario")
  @Test
  @Order(5)
  @WithMockUser(isAdmin = true)
  void deleteInjectForScenarioTest() throws Exception {
    // -- EXECUTE 1 ASSERT --
    mvc.perform(
            delete(SCENARIO_URI + "/" + SCENARIO.getId() + "/injects/" + SCENARIO_INJECT_ID)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    assertFalse(injectRepository.existsById(SCENARIO_INJECT_ID));
  }

}
