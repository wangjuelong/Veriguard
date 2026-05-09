package io.veriguard.rest;

import static io.veriguard.rest.inject_expectation_trace.NodeExpectationTraceApi.INJECT_EXPECTATION_TRACES_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE;
import io.veriguard.database.repository.*;
import io.veriguard.helper.StreamHelper;
import io.veriguard.rest.inject_expectation_trace.form.NodeExpectationTraceBulkInsertInput;
import io.veriguard.rest.inject_expectation_trace.form.NodeExpectationTraceInput;
import io.veriguard.utils.fixtures.AssetFixture;
import io.veriguard.utils.fixtures.AttackChainNodeExpectationFixture;
import io.veriguard.utils.fixtures.AttackChainNodeFixture;
import io.veriguard.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class NodeExpectationTraceApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AttackChainNodeRepository attackChainNodeRepository;
  @Autowired private CollectorRepository collectorRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  @Autowired private NodeExpectationTraceRepository nodeExpectationTraceRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private ObjectMapper mapper;

  private Collector savedCollector;
  private AttackChainNode savedAttackChainNode;
  private AttackChainNodeExpectation savedAttackChainNodeExpectation;
  private Asset savedAsset;
  private SecurityPlatform savedSecurityPlatform;
  private NodeExpectationTrace savedNodeExpectationTrace1;
  private NodeExpectationTrace savedNodeExpectationTrace2;
  private NodeExpectationTrace savedNodeExpectationTrace3Dupe;

  @BeforeEach
  void beforeEach() {
    savedAsset = assetRepository.save(AssetFixture.createDefaultAsset("test"));

    SecurityPlatform sp = new SecurityPlatform();
    sp.setExternalReference(UUID.randomUUID().toString());
    sp.setName("sp-name");
    sp.setSecurityPlatformType(SECURITY_PLATFORM_TYPE.SIEM);
    savedSecurityPlatform = securityPlatformRepository.save(sp);

    Collector collector = new Collector();
    collector.setId(UUID.randomUUID().toString());
    collector.setName("collector-name");
    collector.setSecurityPlatform(savedSecurityPlatform);
    collector.setType("type");
    collector.setExternal(true);
    savedCollector = collectorRepository.save(collector);

    AttackChainNode i = AttackChainNodeFixture.getDefaultAttackChainNode();
    i.setAssets(List.of(savedAsset));
    savedAttackChainNode = attackChainNodeRepository.save(i);

    AttackChainNodeExpectation ie =
        AttackChainNodeExpectationFixture.createDetectionAttackChainNodeExpectation(
            savedAttackChainNode, null);
    ie.setAsset(savedAsset);
    savedAttackChainNodeExpectation = attackChainNodeExpectationRepository.save(ie);

    NodeExpectationTrace iet1 = new NodeExpectationTrace();
    iet1.setAttackChainNodeExpectation(savedAttackChainNodeExpectation);
    iet1.setSecurityPlatform(savedSecurityPlatform);
    iet1.setAlertDate(Instant.now().minus(1, ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS));
    iet1.setAlertLink("http://test-link.com/1");
    iet1.setAlertName("Test Alert 1");
    savedNodeExpectationTrace1 = nodeExpectationTraceRepository.save(iet1);

    NodeExpectationTrace iet2 = new NodeExpectationTrace();
    iet2.setAttackChainNodeExpectation(savedAttackChainNodeExpectation);
    iet2.setSecurityPlatform(savedSecurityPlatform);
    iet2.setAlertDate(Instant.now().minus(1, ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS));
    iet2.setAlertLink("http://test-link.com/2");
    iet2.setAlertName("Test Alert 2");
    savedNodeExpectationTrace2 = nodeExpectationTraceRepository.save(iet2);

    // Insert input3 duplicate
    savedNodeExpectationTrace3Dupe = new NodeExpectationTrace();
    savedNodeExpectationTrace3Dupe.setAttackChainNodeExpectation(savedAttackChainNodeExpectation);
    savedNodeExpectationTrace3Dupe.setAlertDate(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    savedNodeExpectationTrace3Dupe.setAlertLink("http://fake-link.com/bulk3");
    savedNodeExpectationTrace3Dupe.setSecurityPlatform(savedSecurityPlatform);
    savedNodeExpectationTrace3Dupe.setAlertName("Test Alert Bulk 3 for duplicate test");
    nodeExpectationTraceRepository.save(savedNodeExpectationTrace3Dupe);
  }

  @DisplayName("Create an inject expectation trace for a collector")
  @Test
  @WithMockUser(isAdmin = true)
  void createNodeExpectationTraceForCollector_Success() throws Exception {
    // --PREPARE--
    NodeExpectationTraceInput input = new NodeExpectationTraceInput();
    input.setAttackChainNodeExpectationId(savedAttackChainNodeExpectation.getId());
    input.setAlertDate(Instant.now());
    input.setAlertLink("http://fake-link.com");
    input.setSourceId(savedCollector.getId());
    input.setAlertName("Test Alert");

    // --EXECUTE--
    String response =
        mvc.perform(
                post(INJECT_EXPECTATION_TRACES_URI)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(
        savedAttackChainNodeExpectation.getId(),
        JsonPath.read(response, "$.node_expectation_trace_expectation"));
    assertEquals(
        savedSecurityPlatform.getId(),
        JsonPath.read(response, "$.node_expectation_trace_source_id"));
  }

  @DisplayName("Get the traces for a collector")
  @Test
  @WithMockUser(isAdmin = true)
  void getNodeExpectationTracesForCollector() throws Exception {
    // --EXECUTE--
    String response =
        mvc.perform(
                get(INJECT_EXPECTATION_TRACES_URI
                        + "?attackChainNodeExpectationId="
                        + savedAttackChainNodeExpectation.getId()
                        + "&sourceId="
                        + savedCollector.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    String savedNodeExpectationTrace1Json = mapper.writeValueAsString(savedNodeExpectationTrace1);
    String savedNodeExpectationTrace2Json = mapper.writeValueAsString(savedNodeExpectationTrace2);
    String savedNodeExpectationTrace3DupeJson =
        mapper.writeValueAsString(savedNodeExpectationTrace3Dupe);
    assertThatJson(response)
        .when(IGNORING_ARRAY_ORDER)
        .whenIgnoringPaths(
            "inject_expectation_trace_created_at", "inject_expectation_trace_updated_at")
        .isArray()
        .containsAll(
            List.of(
                savedNodeExpectationTrace1Json,
                savedNodeExpectationTrace2Json,
                savedNodeExpectationTrace3DupeJson));
  }

  @DisplayName("Count expectation traces for a collector")
  @Test
  @WithMockUser(isAdmin = true)
  void countNodeExpectationTracesForCollector() throws Exception {
    // --EXECUTE--
    String response =
        mvc.perform(
                get(INJECT_EXPECTATION_TRACES_URI
                        + "/count?attackChainNodeExpectationId="
                        + savedAttackChainNodeExpectation.getId()
                        + "&sourceId="
                        + savedSecurityPlatform.getExternalReference()
                        + "&expectationResultSourceType=collector")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(3, Integer.parseInt(response));
  }

  @DisplayName(
      "Count expectation traces for other source than a collector, with an ivalid sourceId given")
  @Test
  @WithMockUser(isAdmin = true)
  void countNodeExpectationTracesForOthers_0() throws Exception {
    // --EXECUTE--
    String response =
        mvc.perform(
                get(INJECT_EXPECTATION_TRACES_URI
                        + "/count?attackChainNodeExpectationId="
                        + savedAttackChainNodeExpectation.getId()
                        + "&sourceId="
                        + savedSecurityPlatform.getExternalReference()
                        + "&expectationResultSourceType=other")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(0, Integer.parseInt(response));
  }

  @DisplayName(
      "Count expectation traces for other source than a collector, with a valid sourceId given")
  @Test
  @WithMockUser(isAdmin = true)
  void countNodeExpectationTracesForOthers() throws Exception {
    // --EXECUTE--
    String response =
        mvc.perform(
                get(INJECT_EXPECTATION_TRACES_URI
                        + "/count?attackChainNodeExpectationId="
                        + savedAttackChainNodeExpectation.getId()
                        + "&sourceId="
                        + savedSecurityPlatform.getId()
                        + "&expectationResultSourceType=other")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(3, Integer.parseInt(response));
  }

  @DisplayName("Bulk insert of 1 inject expectation trace for a collector")
  @Test
  @WithMockUser(isAdmin = true)
  void bulkInsertNodeExpectationTraceForCollector_Success() throws Exception {
    // --PREPARE--
    NodeExpectationTraceInput input = new NodeExpectationTraceInput();
    input.setAttackChainNodeExpectationId(savedAttackChainNodeExpectation.getId());
    input.setAlertDate(Instant.now());
    input.setAlertLink("http://fake-link.com");
    input.setSourceId(savedCollector.getId());
    input.setAlertName("Test Alert Bulk");

    NodeExpectationTraceBulkInsertInput inputBulk = new NodeExpectationTraceBulkInsertInput();
    inputBulk.setExpectationTraces(List.of(input));

    // --EXECUTE--
    mvc.perform(
            post(INJECT_EXPECTATION_TRACES_URI + "/bulk")
                .content(asJsonString(inputBulk))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    // --ASSERT--
    List<NodeExpectationTrace> results =
        StreamHelper.fromIterable(
            nodeExpectationTraceRepository.findAll(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.and(criteriaBuilder.like(root.get("alertName"), "%Bulk%"))));
    assertFalse(results.isEmpty());
    assertEquals(2, results.size());
    assertTrue(
        results.stream()
            .anyMatch(
                nodeExpectationTrace ->
                    savedNodeExpectationTrace3Dupe
                        .getAlertName()
                        .equals(nodeExpectationTrace.getAlertName())));
    assertTrue(
        results.stream()
            .anyMatch(
                nodeExpectationTrace ->
                    input.getAlertName().equals(nodeExpectationTrace.getAlertName())));
  }

  @DisplayName("Bulk insert of multiple inject expectation trace for a collector")
  @Test
  @WithMockUser(isAdmin = true)
  void bulkInsertMultipleNodeExpectationTraceForCollector_Success() throws Exception {
    // --PREPARE--
    NodeExpectationTraceInput input = new NodeExpectationTraceInput();
    input.setAttackChainNodeExpectationId(savedAttackChainNodeExpectation.getId());
    input.setAlertDate(Instant.now());
    input.setAlertLink("http://fake-link.com/bulk");
    input.setSourceId(savedCollector.getId());
    input.setAlertName("Test Alert Bulk");

    NodeExpectationTraceInput input2 = new NodeExpectationTraceInput();
    input2.setAttackChainNodeExpectationId(savedAttackChainNodeExpectation.getId());
    input2.setAlertDate(Instant.now());
    input2.setAlertLink("http://fake-link.com/bulk2");
    input2.setSourceId(savedCollector.getId());
    input2.setAlertName("Test Alert Bulk 2");

    NodeExpectationTraceBulkInsertInput inputBulk = new NodeExpectationTraceBulkInsertInput();
    inputBulk.setExpectationTraces(List.of(input, input2));

    // --EXECUTE--
    mvc.perform(
            post(INJECT_EXPECTATION_TRACES_URI + "/bulk")
                .content(asJsonString(inputBulk))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    // --ASSERT--
    List<NodeExpectationTrace> results =
        StreamHelper.fromIterable(
            nodeExpectationTraceRepository.findAll(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.and(criteriaBuilder.like(root.get("alertName"), "%Bulk%"))));
    assertFalse(results.isEmpty());
    assertEquals(3, results.size());
  }

  @DisplayName("Bulk insert inject expectation traces for a collector with duplicates")
  @Test
  @WithMockUser(isAdmin = true)
  void bulkInsertNodeExpectationTraceForCollector_SuccessWithDuped() throws Exception {
    // --PREPARE--
    NodeExpectationTraceInput input = new NodeExpectationTraceInput();
    input.setAttackChainNodeExpectationId(savedAttackChainNodeExpectation.getId());
    input.setAlertDate(Instant.now());
    input.setAlertLink("http://fake-link.com/bulk");
    input.setSourceId(savedCollector.getId());
    input.setAlertName("Test Alert Bulk");

    NodeExpectationTraceInput input2 = new NodeExpectationTraceInput();
    input2.setAttackChainNodeExpectationId(savedAttackChainNodeExpectation.getId());
    input2.setAlertDate(Instant.now());
    input2.setAlertLink("http://fake-link.com/bulk2");
    input2.setSourceId(savedCollector.getId());
    input2.setAlertName("Test Alert Bulk 2");

    NodeExpectationTraceInput input3 = new NodeExpectationTraceInput();
    input3.setAttackChainNodeExpectationId(
        savedNodeExpectationTrace3Dupe.getAttackChainNodeExpectation().getId());
    input3.setAlertDate(savedNodeExpectationTrace3Dupe.getAlertDate());
    input3.setAlertLink(savedNodeExpectationTrace3Dupe.getAlertLink());
    input3.setSourceId(savedNodeExpectationTrace3Dupe.getSecurityPlatform().getId());
    input3.setAlertName(savedNodeExpectationTrace3Dupe.getAlertName());

    NodeExpectationTraceBulkInsertInput inputBulk = new NodeExpectationTraceBulkInsertInput();
    inputBulk.setExpectationTraces(List.of(input, input2, input3));

    // --EXECUTE--
    mvc.perform(
            post(INJECT_EXPECTATION_TRACES_URI + "/bulk")
                .content(asJsonString(inputBulk))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    // --ASSERT--
    List<NodeExpectationTrace> results =
        StreamHelper.fromIterable(
            nodeExpectationTraceRepository.findAll(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.and(criteriaBuilder.like(root.get("alertName"), "%Bulk%"))));
    assertFalse(results.isEmpty());
    assertEquals(3, results.size());
    assertEquals(
        1,
        results.stream()
            .filter(
                nodeExpectationTrace ->
                    nodeExpectationTrace
                        .getAlertName()
                        .equals(savedNodeExpectationTrace3Dupe.getAlertName()))
            .count());
  }
}
