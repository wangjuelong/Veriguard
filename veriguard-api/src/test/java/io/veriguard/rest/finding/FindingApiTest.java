package io.veriguard.rest.finding;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static io.veriguard.utils.fixtures.FindingFixture.createDefaultTextFindingWithRandomValue;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.FindingRepository;
import io.veriguard.database.specification.FindingSpecification;
import io.veriguard.rest.finding.form.RelatedFindingOutput;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.*;
import io.veriguard.utils.mapper.FindingMapper;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.veriguard.utils.pagination.SortField;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Findings search tests")
class FindingApiTest extends IntegrationTest {

  private static final String FINDING_URI = "/api/findings";

  @Resource protected ObjectMapper mapper;
  @Autowired private MockMvc mvc;

  @Autowired private FindingComposer findingComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AttackChainNodeComposer attackChainNodeComposer;
  @Autowired private NodeContractComposer nodeContractComposer;
  @Autowired private AttackChainComposer attackChainComposer;
  @Autowired private AttackChainRunComposer simulationComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private NodeExecutorFixture nodeExecutorFixture;
  @Autowired private FindingRepository findingRepository;
  @Autowired private FindingMapper findingMapper;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    attackChainComposer.reset();
    simulationComposer.reset();
    attackChainNodeComposer.reset();
    tagComposer.reset();
    agentComposer.reset();
    findingComposer.reset();
    endpointComposer.reset();
    assetGroupComposer.reset();
    nodeContractComposer.reset();
  }

  @Nested
  @DisplayName("With several simulations from same scenario in database")
  class WithSeveralSimulationsFromSameAttackChain {
    private final int numberOfPreviousSimulations = 5;
    private final String firstAttackChainNodeName = "firstInjectName";
    private final String secondAttackChainNodeName = "secondInjectName";
    private final String thirdAttackChainNodeName = "thirdInjectName";
    private final String fourthAttackChainNodeName = "fourthInjectName";

    private AttackChainComposer.Composer getAttackChainWithSimulationsWrapper() {
      AttackChainComposer.Composer attackChainWrapper =
          attackChainComposer.forAttackChain(AttackChainFixture.getAttackChain());

      // add simulations with default findings
      for (int i = 0; i < numberOfPreviousSimulations; i++) {
        Hashtable<String, AttackChainNodeComposer.Composer> attackChainNodes =
            attachSimulationToAttackChain(
                attackChainWrapper, AttackChainRunFixture.createFinishedAttackAttackChainRun());
        for (Map.Entry<String, AttackChainNodeComposer.Composer> entry : attackChainNodes.entrySet()) {
          for (FindingComposer.Composer findingWrapper : getDefaultFindings()) {
            entry.getValue().withFinding(findingWrapper);
          }
        }
      }

      return attackChainWrapper;
    }

    private Hashtable<String, AttackChainNodeComposer.Composer> attachSimulationToAttackChain(
        AttackChainComposer.Composer attackChainWrapper, AttackChainRun simulationFixture) {
      // create arbitrary attackChainNodes
      Hashtable<String, AttackChainNodeComposer.Composer> attackChainNodes = new Hashtable<>();
      attackChainNodes.put(
          firstAttackChainNodeName,
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withNodeContract(
                  nodeContractComposer
                      .forNodeContract(NodeContractFixture.createDefaultNodeContract())
                      .withNodeExecutor(nodeExecutorFixture.getWellKnownOaevImplantNodeExecutor())));
      attackChainNodes.put(
          secondAttackChainNodeName,
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withNodeContract(
                  nodeContractComposer
                      .forNodeContract(NodeContractFixture.createDefaultNodeContract())
                      .withNodeExecutor(nodeExecutorFixture.getWellKnownOaevImplantNodeExecutor())));
      attackChainNodes.put(
          thirdAttackChainNodeName,
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withNodeContract(
                  nodeContractComposer
                      .forNodeContract(NodeContractFixture.createDefaultNodeContract())
                      .withNodeExecutor(nodeExecutorFixture.getWellKnownOaevImplantNodeExecutor())));
      attackChainNodes.put(
          fourthAttackChainNodeName,
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withNodeContract(
                  nodeContractComposer
                      .forNodeContract(NodeContractFixture.createDefaultNodeContract())
                      .withNodeExecutor(nodeExecutorFixture.getWellKnownOaevImplantNodeExecutor())));

      AttackChainRunComposer.Composer simulationWrapper =
          simulationComposer.forAttackChainRun(simulationFixture);
      for (Map.Entry<String, AttackChainNodeComposer.Composer> entry : attackChainNodes.entrySet()) {
        simulationWrapper.withAttackChainNode(entry.getValue());
      }

      attackChainWrapper.withSimulation(simulationWrapper);

      return attackChainNodes;
    }

    private List<FindingComposer.Composer> getDefaultFindings() {
      return new ArrayList<>(
          List.of(
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue()),
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue()),
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue()),
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue())));
    }

    @Nested
    @DisplayName("When searching globally for findings")
    class WhenSearchingGloballyForFindings {
      @Test
      @DisplayName("Returns only findings for latest simulation of each scenario")
      public void ReturnsOnlyFindingsForLatestSimulationOfEachAttackChain() throws Exception {
        List<AttackChainComposer.Composer> attackChainWrappers =
            List.of(getAttackChainWithSimulationsWrapper(), getAttackChainWithSimulationsWrapper());

        // latest findings
        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();

        // add latest simulation to each attackChain
        for (AttackChainComposer.Composer attackChainWrapper : attackChainWrappers) {
          Hashtable<String, AttackChainNodeComposer.Composer> latestSimulationAttackChainNodeWrappers =
              attachSimulationToAttackChain(
                  attackChainWrapper, AttackChainRunFixture.createFinishedAttackAttackChainRun());
          for (Map.Entry<String, AttackChainNodeComposer.Composer> entry :
              latestSimulationAttackChainNodeWrappers.entrySet()) {
            FindingComposer.Composer findingWrapper =
                findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
            latestFindingWrappers.add(findingWrapper);
            entry.getValue().withFinding(findingWrapper);
          }
          attackChainWrapper.persist();
        }

        // add attackChainNodes (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withFinding(findingWrapper)
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toRelatedFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }

      @Test
      @DisplayName("Returns only findings for latest finished simulation of each scenario")
      public void ReturnsOnlyFindingsForLatestFinishedSimulationOfEachAttackChain() throws Exception {
        List<AttackChainComposer.Composer> attackChainWrappers =
            List.of(getAttackChainWithSimulationsWrapper(), getAttackChainWithSimulationsWrapper());

        // latest findings
        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();

        // add latest simulations to each attackChain
        for (AttackChainComposer.Composer attackChainWrapper : attackChainWrappers) {
          ///  FINISHED simulation
          Hashtable<String, AttackChainNodeComposer.Composer> latestSimulationAttackChainNodeWrappers =
              attachSimulationToAttackChain(
                  attackChainWrapper, AttackChainRunFixture.createFinishedAttackAttackChainRun());
          for (Map.Entry<String, AttackChainNodeComposer.Composer> entry :
              latestSimulationAttackChainNodeWrappers.entrySet()) {
            FindingComposer.Composer findingWrapper =
                findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
            latestFindingWrappers.add(findingWrapper);
            entry.getValue().withFinding(findingWrapper);
          }

          /// RUNNING simulation with no findings
          attachSimulationToAttackChain(
              attackChainWrapper, AttackChainRunFixture.createRunningAttackAttackChainRun());
          attackChainWrapper.persist();
        }

        // add attackChainNodes (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withFinding(findingWrapper)
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toRelatedFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
    }

    @Nested
    @DisplayName("When searching for findings on scenario")
    class WhenSearchingForFindingsOnAttackChain {
      @Test
      @DisplayName("Returns only findings for latest simulation")
      public void ReturnsOnlyFindingsForLatestSimulation() throws Exception {
        AttackChainComposer.Composer attackChainWrapper = getAttackChainWithSimulationsWrapper();

        // latest findings
        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();

        // add latest simulation to attackChain
        Hashtable<String, AttackChainNodeComposer.Composer> latestSimulationAttackChainNodeWrappers =
            attachSimulationToAttackChain(
                attackChainWrapper, AttackChainRunFixture.createFinishedAttackAttackChainRun());
        for (Map.Entry<String, AttackChainNodeComposer.Composer> entry :
            latestSimulationAttackChainNodeWrappers.entrySet()) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          entry.getValue().withFinding(findingWrapper);
        }
        attackChainWrapper.persist();

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/scenarios/" + attackChainWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toRelatedFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
    }

    @Nested
    @DisplayName("When searching for findings on simulation")
    class WhenSearchingForFindingsOnSimulation {
      @Test
      @DisplayName("Returns all findings for observed simulation")
      public void ReturnsAllFindingsForObservedSimulation() throws Exception {
        AttackChainComposer.Composer attackChainWrapper = getAttackChainWithSimulationsWrapper();
        attackChainWrapper.persist();

        AttackChainRun ex = attackChainWrapper.get().getAttackChainRuns().getFirst();

        SearchPaginationInput input = PaginationFixture.getDefault().build();
        input.setSorts(
            List.of(
                new SortField("finding_created_at", "asc", null),
                new SortField("finding_value", "asc", null)));

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/exercises/" + ex.getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        ex.getAttackChainNodes().stream()
                            .flatMap(attackChainNode -> attackChainNode.getFindings().stream().map(Finding::getId))
                            .toList()))
                .stream()
                .map(findingMapper::toRelatedFindingOutput)
                .sorted(
                    (o1, o2) -> {
                      if (o1.getCreationDate().equals(o2.getCreationDate())) {
                        return o1.getValue().compareTo(o2.getValue());
                      }
                      return o1.getCreationDate().compareTo(o2.getCreationDate());
                    })
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
    }

    @Nested
    @DisplayName("When searching for findings on inject")
    class WhenSearchingForFindingsOnAttackChainNode {
      @Test
      @DisplayName("Returns all findings for observed inject")
      public void ReturnsAllFindingsForObservedAttackChainNode() throws Exception {
        AttackChainComposer.Composer attackChainWrapper = getAttackChainWithSimulationsWrapper();
        attackChainWrapper.persist();

        AttackChainNode attackChainNode = attackChainWrapper.get().getAttackChainRuns().getFirst().getAttackChainNodes().getFirst();

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/injects/" + attackChainNode.getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        attackChainNode.getFindings().stream().map(Finding::getId).toList()))
                .stream()
                .map(findingMapper::toRelatedFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
    }

    @Nested
    @DisplayName("When searching for findings on Endpoint")
    class WhenSearchingForFindingsOnEndpoint {
      @Test
      @DisplayName("Returns all findings for latest simulations involving endpoint")
      public void ReturnsAllFindingsForLatestSimulationsInvolvingEndpoint() throws Exception {
        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        AttackChainComposer.Composer attackChainWrapper = getAttackChainWithSimulationsWrapper();

        // hack findings to attach to endpoint
        for (AttackChainRun ex : attackChainWrapper.get().getAttackChainRuns()) {
          for (AttackChainNode attackChainNode : ex.getAttackChainNodes()) {
            for (Finding finding : attackChainNode.getFindings()) {
              finding.setAssets(new ArrayList<>(List.of(endpointWrapper.get())));
            }
          }
        }

        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();
        // add latest simulation to attackChain
        Hashtable<String, AttackChainNodeComposer.Composer> latestSimulationAttackChainNodeWrappers =
            attachSimulationToAttackChain(
                attackChainWrapper, AttackChainRunFixture.createFinishedAttackAttackChainRun());
        for (Map.Entry<String, AttackChainNodeComposer.Composer> entry :
            latestSimulationAttackChainNodeWrappers.entrySet()) {
          FindingComposer.Composer findingWrapper =
              findingComposer
                  .forFinding(createDefaultTextFindingWithRandomValue())
                  .withEndpoint(endpointWrapper);
          entry.getValue().withFinding(findingWrapper);
          latestFindingWrappers.add(findingWrapper);
        }
        attackChainWrapper.persist();

        // add attackChainNodes (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withFinding(findingWrapper.withEndpoint(endpointWrapper))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/endpoints/" + endpointWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toRelatedFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }

      @Test
      @DisplayName(
          "Returns all unsolved findings for latest finished simulations involving endpoint")
      public void ReturnsAllUnsolvedFindingsForLatestFinishedSimulationsInvolvingEndpoint()
          throws Exception {
        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        AttackChainComposer.Composer attackChainWrapper = getAttackChainWithSimulationsWrapper();

        // hack findings to attach to endpoint
        for (AttackChainRun ex : attackChainWrapper.get().getAttackChainRuns()) {
          for (AttackChainNode attackChainNode : ex.getAttackChainNodes()) {
            for (Finding finding : attackChainNode.getFindings()) {
              finding.setAssets(new ArrayList<>(List.of(endpointWrapper.get())));
            }
          }
        }

        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();
        // add finished simulation to attackChain with no findings (= all previous findings solved)
        attachSimulationToAttackChain(attackChainWrapper, AttackChainRunFixture.createFinishedAttackAttackChainRun());

        attackChainWrapper.persist();

        // add attackChainNodes (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withFinding(findingWrapper.withEndpoint(endpointWrapper))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/endpoints/" + endpointWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toRelatedFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }

      @Test
      @DisplayName("Returns all findings for latest finished simulations involving endpoint")
      public void ReturnsAllFindingsForLatestFinishedSimulationsInvolvingEndpoint()
          throws Exception {
        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        AttackChainComposer.Composer attackChainWrapper = getAttackChainWithSimulationsWrapper();

        // hack findings to attach to endpoint
        for (AttackChainRun ex : attackChainWrapper.get().getAttackChainRuns()) {
          for (AttackChainNode attackChainNode : ex.getAttackChainNodes()) {
            for (Finding finding : attackChainNode.getFindings()) {
              finding.setAssets(new ArrayList<>(List.of(endpointWrapper.get())));
            }
          }
        }

        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();
        // add latest simulation to attackChain
        Hashtable<String, AttackChainNodeComposer.Composer> latestSimulationAttackChainNodeWrappers =
            attachSimulationToAttackChain(
                attackChainWrapper, AttackChainRunFixture.createFinishedAttackAttackChainRun());
        for (Map.Entry<String, AttackChainNodeComposer.Composer> entry :
            latestSimulationAttackChainNodeWrappers.entrySet()) {
          FindingComposer.Composer findingWrapper =
              findingComposer
                  .forFinding(createDefaultTextFindingWithRandomValue())
                  .withEndpoint(endpointWrapper);
          entry.getValue().withFinding(findingWrapper);
          latestFindingWrappers.add(findingWrapper);
        }

        attachSimulationToAttackChain(attackChainWrapper, AttackChainRunFixture.createRunningAttackAttackChainRun());

        attackChainWrapper.persist();

        // add attackChainNodes (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withFinding(findingWrapper.withEndpoint(endpointWrapper))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/endpoints/" + endpointWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toRelatedFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
    }
  }

  @Nested
  @DisplayName("Basic tests")
  class BasicTests {
    private AttackChainRun savedSimulation;
    private AttackChain savedAttackChain;
    private AssetGroup savedAssetGroup;
    private Endpoint savedEndpoint;
    private AttackChainNodeComposer.Composer attackChainNodeWrapper;
    private AttackChainNodeComposer.Composer attackChainNodeWrapper2;

    @BeforeEach
    void setup() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());

      savedEndpoint =
          endpointWrapper
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .get();

      AssetGroupComposer.Composer assetGroupWrapper =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("asset-group"))
              .withAsset(endpointWrapper);

      savedAssetGroup = assetGroupWrapper.get();

      attackChainNodeWrapper =
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withAssetGroup(assetGroupWrapper);

      attackChainNodeWrapper2 = attackChainNodeComposer.forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode());

      AttackChainRunComposer.Composer simulationWrapper =
          simulationComposer
              .forAttackChainRun(AttackChainRunFixture.createFinishedAttackAttackChainRun())
              .withAttackChainNode(attackChainNodeWrapper);

      savedAttackChain =
          attackChainComposer
              .forAttackChain(AttackChainFixture.createDefaultCrisisAttackChain())
              .withSimulation(simulationWrapper)
              .persist()
              .get();

      savedSimulation = savedAttackChain.getAttackChainRuns().getFirst();
    }

    @DisplayName("Search global findings")
    @Test
    public void given_a_search_input_should_return_page_of_findings() throws Exception {
      Finding savedFinding =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .withAttackChainNode(attackChainNodeWrapper)
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("Finding")))
              .persist()
              .get();
      SearchPaginationInput input =
          buildDefaultFilters(
              ContractOutputType.Text,
              savedFinding,
              savedSimulation,
              savedAttackChain,
              savedEndpoint,
              savedAssetGroup);

      entityManager.flush();
      entityManager.clear();

      performCallbackRequest(FINDING_URI + "/search", input)
          .andExpect(
              jsonPath("$.content.[0].finding_type").value(savedFinding.getType().getLabel()))
          .andExpect(jsonPath("$.content.[0].finding_value").value("text_value"))
          .andExpect(
              jsonPath("$.content.[0].finding_assets.[0].asset_id").value(savedEndpoint.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_inject.inject_id")
                  .value(savedFinding.getAttackChainNode().getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_simulation.exercise_id")
                  .value(savedSimulation.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_scenario.scenario_id").value(savedAttackChain.getId()));
    }

    @Test
    @DisplayName("Search findings by simulation")
    void should_return_findings_by_simulation() throws Exception {
      Finding savedFinding =
          findingComposer
              .forFinding(FindingFixture.createDefaultIPV6Finding())
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .withAttackChainNode(attackChainNodeWrapper)
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("Finding IPv6")))
              .persist()
              .get();
      SearchPaginationInput input =
          buildDefaultFilters(
              ContractOutputType.IPv6, savedFinding, savedSimulation, null, savedEndpoint, null);

      performCallbackRequest(
              FINDING_URI + "/exercises/" + savedSimulation.getId() + "/search", input)
          .andExpect(
              jsonPath("$.content.[0].finding_type").value(savedFinding.getType().getLabel()))
          .andExpect(
              jsonPath("$.content.[0].finding_value")
                  .value("2001:0000:130F:0000:0000:09C0:876A:130B"));
    }

    @Test
    @DisplayName("Search findings by scenario")
    void should_return_findings_by_attackChain() throws Exception {
      Finding savedFinding =
          findingComposer
              .forFinding(FindingFixture.createDefaultFindingCredentials())
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .withAttackChainNode(attackChainNodeWrapper)
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("Finding")))
              .persist()
              .get();

      SearchPaginationInput input =
          buildDefaultFilters(
              ContractOutputType.Credentials,
              savedFinding,
              null,
              savedAttackChain,
              savedEndpoint,
              savedAssetGroup);

      entityManager.flush();
      entityManager.clear();

      performCallbackRequest(FINDING_URI + "/scenarios/" + savedAttackChain.getId() + "/search", input)
          .andExpect(
              jsonPath("$.content.[0].finding_scenario.scenario_id").value(savedAttackChain.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_type").value(savedFinding.getType().getLabel()))
          .andExpect(jsonPath("$.content.[0].finding_value").value("admin:admin"));
    }

    @Test
    @DisplayName("Search findings by endpoint")
    void should_return_findings_by_endpoint() throws Exception {
      Finding savedFinding =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .withAttackChainNode(attackChainNodeWrapper)
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("Finding Text")))
              .persist()
              .get();
      SearchPaginationInput input =
          buildDefaultFilters(
              ContractOutputType.Text, savedFinding, null, null, savedEndpoint, null);

      performCallbackRequest(FINDING_URI + "/endpoints/" + savedEndpoint.getId() + "/search", input)
          .andExpect(
              jsonPath("$.content.[0].finding_assets.[0].asset_id").value(savedEndpoint.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_type").value(savedFinding.getType().getLabel()))
          .andExpect(jsonPath("$.content.[0].finding_value").value("text_value"));
    }

    @Test
    void distinctTypeValueWithFilter_returnsDistinctFindings() {
      // Create two findings with the same type and value (duplicates)
      Finding f1 =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withAttackChainNode(attackChainNodeWrapper)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();

      Finding f2 =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withAttackChainNode(attackChainNodeWrapper2)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();

      // Create a unique finding with different type or value
      Finding f3 =
          findingComposer
              .forFinding(FindingFixture.createDefaultIPV6Finding())
              .withAttackChainNode(attackChainNodeWrapper)
              .persist()
              .get();

      // base specification can be null (no additional filtering)
      Specification<Finding> baseSpec = null;

      Specification<Finding> distinctSpec =
          FindingSpecification.distinctTypeValueWithFilter(baseSpec);

      List<Finding> results = findingRepository.findAll(distinctSpec);

      // Should return only 2 distinct findings (f1/f2 collapse to one)
      assertThat(results).hasSize(2);

      Set<String> distinctPairs =
          results.stream()
              .map(f -> f.getType().getLabel() + "::" + f.getValue())
              .collect(Collectors.toSet());

      assertThat(distinctPairs)
          .containsExactlyInAnyOrder(
              f1.getType().getLabel() + "::" + f1.getValue(),
              f3.getType().getLabel() + "::" + f3.getValue());
    }
  }

  private SearchPaginationInput buildDefaultFilters(
      ContractOutputType type,
      Finding finding,
      AttackChainRun simulation,
      AttackChain attackChain,
      Endpoint endpoint,
      AssetGroup assetGroup) {
    SearchPaginationInput input = new SearchPaginationInput();
    Filters.FilterGroup group = new Filters.FilterGroup();
    group.setMode(Filters.FilterMode.and);

    Instant now = Instant.now().minus(1, ChronoUnit.DAYS);

    List<Filters.Filter> filters = new ArrayList<>();

    filters.add(
        buildFilter("finding_type", Filters.FilterOperator.contains, List.of(type.getLabel())));
    filters.add(
        buildFilter("finding_created_at", Filters.FilterOperator.gt, List.of(now.toString())));
    filters.add(
        buildFilter(
            "finding_tags",
            Filters.FilterOperator.contains,
            List.of(finding.getTags().stream().findFirst().get().getId())));
    filters.add(
        buildFilter(
            "finding_inject_id",
            Filters.FilterOperator.contains,
            List.of(finding.getAttackChainNode().getId())));

    if (assetGroup != null) {
      filters.add(
          buildFilter(
              "finding_asset_groups",
              Filters.FilterOperator.contains,
              List.of(assetGroup.getId())));
    }
    if (endpoint != null) {
      filters.add(
          buildFilter(
              "finding_assets", Filters.FilterOperator.contains, List.of(endpoint.getId())));
    }
    if (simulation != null) {
      filters.add(
          buildFilter(
              "finding_simulation", Filters.FilterOperator.contains, List.of(simulation.getId())));
    }
    if (attackChain != null) {
      filters.add(
          buildFilter(
              "finding_scenario", Filters.FilterOperator.contains, List.of(attackChain.getId())));
    }

    group.setFilters(filters);
    input.setFilterGroup(group);
    return input;
  }

  private Filters.Filter buildFilter(
      String key, Filters.FilterOperator operator, List<String> values) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setMode(Filters.FilterMode.and);
    filter.setOperator(operator);
    filter.setValues(values);
    return filter;
  }

  private ResultActions performCallbackRequest(String uri, SearchPaginationInput input)
      throws Exception {
    return mvc.perform(
            post(uri)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }
}
