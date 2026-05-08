package io.veriguard.rest;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.rest.injector.NodeExecutorApi.INJECT0R_URI;
import static io.veriguard.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.veriguard.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.veriguard.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static io.veriguard.utils.fixtures.NodeExecutorFixture.createDefaultNodeExecutor;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.NodeExecutorRepository;
import io.veriguard.utils.AgentUtils;
import io.veriguard.utils.HashUtils;
import io.veriguard.utils.fixtures.AgentFixture;
import io.veriguard.utils.fixtures.AttackChainNodeFixture;
import io.veriguard.utils.fixtures.EndpointFixture;
import io.veriguard.utils.fixtures.composers.*;
import io.veriguard.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Injector Api Integration Tests")
@WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_SETTINGS})
public class NodeExecutorApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;

  @Autowired private NodeExecutorRepository nodeExecutorRepository;
  @Autowired private AttackChainNodeComposer attackChainNodeComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

  private ConnectorInstancePersisted getNodeExecutorInstance(
      String nodeExecutorId, String nodeExecutorName) throws JsonProcessingException {
    return connectorInstanceComposer
        .forConnectorInstance(createDefaultConnectorInstance())
        .withCatalogConnector(
            catalogConnectorComposer.forCatalogConnector(
                createDefaultCatalogConnectorManagedByXtmComposer(
                    nodeExecutorName, ConnectorType.INJECTOR)))
        .withConnectorInstanceConfiguration(
            connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                createConnectorInstanceConfiguration("INJECTOR_ID", nodeExecutorId)))
        .persist()
        .get();
  }

  private NodeExecutor getNodeExecutor(String nodeExecutorName) {
    NodeExecutor nodeExecutor = createDefaultNodeExecutor(nodeExecutorName);
    return nodeExecutorRepository.save(nodeExecutor);
  }

  @Nested
  @DisplayName("Retrieve injectors")
  class GetNodeExecutors {
    @Test
    @DisplayName("Should retrieve all injectors")
    void shouldRetrieveAllNodeExecutors() throws Exception {
      NodeExecutor nodeExecutor = getNodeExecutor("nuclei");
      List<NodeExecutor> existingNodeExecutors = fromIterable(nodeExecutorRepository.findAll());
      getNodeExecutorInstance("PENDING_INJECTOR_ID", "Pending injector");
      ConnectorInstancePersisted connectorInstanceLinkToCreatedNodeExecutor =
          getNodeExecutorInstance(nodeExecutor.getId(), nodeExecutor.getName());

      String response =
          mvc.perform(
                  get(INJECT0R_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isArray().size().isEqualTo(existingNodeExecutors.size());

      assertThatJson(response)
          .inPath("[*].injector_id")
          .isArray()
          .containsExactlyInAnyOrderElementsOf(
              existingNodeExecutors.stream().map(NodeExecutor::getId).toList());

      String path = "$[?(@.injector_id == '" + nodeExecutor.getId() + "')]";

      assertThatJson(response)
          .inPath(path + ".catalog.catalog_connector_id")
          .isArray()
          .containsExactly(
              connectorInstanceLinkToCreatedNodeExecutor.getCatalogConnector().getId());

      assertThatJson(response).inPath(path + ".is_verified").isArray().containsExactly(true);
    }

    @Test
    @DisplayName(
        "Given queryParams include_next to true should retrieve all injectors and and pending injectors")
    void givenQueryParamsIncludeNextToTrue_shouldRetrieveAllNodeExecutorsAndPendingNodeExecutors()
        throws Exception {
      getNodeExecutor("Mitre Attack");
      List<NodeExecutor> existingNodeExecutors = fromIterable(nodeExecutorRepository.findAll());
      String pendingNodeExecutorId = "PENDING_INJECTOR_ID";
      ConnectorInstancePersisted pendingNodeExecutorInstance =
          getNodeExecutorInstance(pendingNodeExecutorId, "PENDING INJECTOR");

      String response =
          mvc.perform(
                  get(INJECT0R_URI + "?include_next=true")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isArray().size().isEqualTo(existingNodeExecutors.size() + 1);

      assertThatJson(response)
          .inPath("[*].injector_id")
          .isArray()
          .containsExactlyInAnyOrderElementsOf(
              Stream.concat(
                      existingNodeExecutors.stream().map(NodeExecutor::getId),
                      Stream.of(pendingNodeExecutorId))
                  .toList());
      String path = "$[?(@.injector_id == '" + pendingNodeExecutorId + "')]";

      assertThatJson(response)
          .inPath(path + ".catalog.catalog_connector_id")
          .isArray()
          .containsExactly(pendingNodeExecutorInstance.getCatalogConnector().getId());

      assertThatJson(response).inPath(path + ".is_verified").isArray().containsExactly(true);
    }
  }

  @Nested
  @DisplayName("Related injectors ids")
  class GetRelatedNodeExecutorIds {
    @Test
    @DisplayName(
        "Given injector managed by XTM Composer, should return linked connector instance ID and catalog ID")
    void givenLinkedInjector_shouldReturnInstanceAndCatalogId() throws Exception {
      NodeExecutor nodeExecutor = getNodeExecutor("nmap");
      ConnectorInstancePersisted instance =
          getNodeExecutorInstance(nodeExecutor.getId(), nodeExecutor.getName());
      String response =
          mvc.perform(
                  get(INJECT0R_URI + "/" + nodeExecutor.getId() + "/related-ids")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response).inPath("connector_instance_id").isEqualTo(instance.getId());
      assertThatJson(response)
          .inPath("catalog_connector_id")
          .isEqualTo(instance.getCatalogConnector().getId());
    }

    @Test
    @DisplayName(
        "Given injector matching a catalog type, should return matching catalog ID without connector instance ID")
    void givenNodeExecutorWithType_shouldReturnCatalogWithMatchingSlug() throws Exception {
      NodeExecutor nodeExecutor = getNodeExecutor("nmap-injector");
      CatalogConnector catalogConnector =
          catalogConnectorComposer
              .forCatalogConnector(
                  createDefaultCatalogConnectorManagedByXtmComposer("nmap-injector"))
              .persist()
              .get();

      String response =
          mvc.perform(
                  get(INJECT0R_URI + "/" + nodeExecutor.getId() + "/related-ids")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response).inPath("connector_instance_id").isEqualTo(null);
      assertThatJson(response).inPath("catalog_connector_id").isEqualTo(catalogConnector.getId());
    }

    @Test
    @DisplayName("Given unlinked injector, should return empty catalog ID and empty instance ID")
    void givenUnlinkedInjector_shouldReturnEmptyInstanceAndCatalogId() throws Exception {
      NodeExecutor nodeExecutor = getNodeExecutor("http-query-injector");
      String response =
          mvc.perform(
                  get(INJECT0R_URI + "/" + nodeExecutor.getId() + "/related-ids")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response).inPath("connector_instance_id").isEqualTo(null);
      assertThatJson(response).inPath("catalog_connector_id").isEqualTo(null);
    }
  }

  @Nested
  @DisplayName("Implant downloads")
  public class ImplantDownloadsTest {
    private static Stream<Arguments> platformArchCombinationsImplantSuccess() {
      return Stream.of(
          Arguments.of(Endpoint.PLATFORM_TYPE.MacOS.name(), Endpoint.PLATFORM_ARCH.arm64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.MacOS.name(), Endpoint.PLATFORM_ARCH.x86_64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.Linux.name(), Endpoint.PLATFORM_ARCH.arm64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.Linux.name(), Endpoint.PLATFORM_ARCH.x86_64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.Windows.name(), Endpoint.PLATFORM_ARCH.arm64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.Windows.name(), Endpoint.PLATFORM_ARCH.x86_64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.MacOS.name(), "Aarch64"),
          Arguments.of(Endpoint.PLATFORM_TYPE.Linux.name(), "Aarch64"),
          Arguments.of(Endpoint.PLATFORM_TYPE.Windows.name(), "Aarch64"));
    }

    @ParameterizedTest(name = "GET implant for platform \"{0}\" arch \"{1}\" should succeed")
    @MethodSource("platformArchCombinationsImplantSuccess")
    public void given_platformAndArch_then_downloadExecutableSucceeds(String platform, String arch)
        throws Exception {
      AttackChainNodeComposer.Composer attackChainNodeWrapper =
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .persist();
      AgentComposer.Composer agentWrapper =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      endpointComposer
          .forEndpoint(EndpointFixture.createEndpoint())
          .withAgent(agentWrapper)
          .persist();
      byte[] agentBytes =
          mvc.perform(
                  get("/api/implant/veriguard/%s/%s".formatted(platform, arch))
                      .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                      .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                      .queryParam("injectId", attackChainNodeWrapper.get().getId())
                      .queryParam("agentId", agentWrapper.get().getId()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      String baseFilename = "veriguard-implant-Testing";
      String filename =
          switch (platform) {
            case "Windows" -> "%s.exe".formatted(baseFilename);
            default -> baseFilename;
          };
      assertThat(HashUtils.getSha256HexDigest(agentBytes))
          .isEqualTo(
              HashUtils.getSha256HexDigest(
                  "/implants/veriguard-implant/%s/%s/%s"
                      .formatted(
                          platform.toLowerCase(),
                          AgentUtils.getCanonicalArchitectureString(arch.toLowerCase()),
                          filename)));
    }

    private static Stream<Arguments> platformArchCombinationsImplantFailure() {
      return Stream.of(
          Arguments.of(Endpoint.PLATFORM_TYPE.MacOS.name(), "not an arch"),
          Arguments.of(Endpoint.PLATFORM_TYPE.Linux.name(), "not an arch"),
          Arguments.of(Endpoint.PLATFORM_TYPE.Windows.name(), "not an arch"));
    }

    @ParameterizedTest(name = "GET implant for platform \"{0}\" arch \"{1}\" should fail")
    @MethodSource("platformArchCombinationsImplantFailure")
    public void given_platformAndArch_then_downloadExecutableFails(String platform, String arch) {
      AttackChainNodeComposer.Composer attackChainNodeWrapper =
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .persist();
      AgentComposer.Composer agentWrapper =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      endpointComposer
          .forEndpoint(EndpointFixture.createEndpoint())
          .withAgent(agentWrapper)
          .persist();
      assertThatThrownBy(
              () ->
                  mvc.perform(
                      get("/api/implant/veriguard/%s/%s".formatted(platform, arch))
                          .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                          .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                          .queryParam("injectId", attackChainNodeWrapper.get().getId())
                          .queryParam("agentId", agentWrapper.get().getId())))
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }
}
