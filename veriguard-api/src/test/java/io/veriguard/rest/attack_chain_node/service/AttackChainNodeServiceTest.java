package io.veriguard.rest.attack_chain_node.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainNodeDocumentRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.AttackChainNodeStatusRepository;
import io.veriguard.database.repository.TeamRepository;
import io.veriguard.executors.utils.ExecutorUtils;
import io.veriguard.healthcheck.dto.HealthCheck;
import io.veriguard.healthcheck.enums.ExternalServiceDependency;
import io.veriguard.healthcheck.utils.HealthCheckUtils;
import io.veriguard.injectors.email.service.ImapService;
import io.veriguard.injectors.email.service.SmtpService;
import io.veriguard.rest.attack_chain_node.form.*;
import io.veriguard.rest.collector.service.CollectorService;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.injector_contract.NodeContractContentUtils;
import io.veriguard.rest.injector_contract.NodeContractService;
import io.veriguard.rest.security.SecurityExpressionHandler;
import io.veriguard.rest.tag.TagService;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.AssetService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.UserService;
import io.veriguard.utils.AttackChainNodeUtils;
import io.veriguard.utils.TargetType;
import io.veriguard.utils.fixtures.AssetGroupFixture;
import io.veriguard.utils.fixtures.AttackChainNodeFixture;
import io.veriguard.utils.fixtures.NodeContractFixture;
import io.veriguard.utils.fixtures.NodeExecutorFixture;
import io.veriguard.utils.mapper.AttackChainNodeExpectationMapper;
import io.veriguard.utils.mapper.AttackChainNodeMapper;
import io.veriguard.utils.mapper.AttackChainNodeStatusMapper;
import io.veriguard.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttackChainNodeServiceTest {

  private static final String INJECT_ID = "injectid";

  @Mock private AttackChainNodeRepository attackChainNodeRepository;

  @Mock private AssetService assetService;

  @Mock private AssetGroupService assetGroupService;

  @Mock private TeamRepository teamRepository;

  @Mock(extraInterfaces = {MethodSecurityExpressionHandler.class})
  private SecurityExpressionHandler methodSecurityExpressionHandler;

  @Mock private AttackChainNodeDocumentRepository attackChainNodeDocumentRepository;

  @Mock private AttackChainNodeStatusRepository attackChainNodeStatusRepository;

  @Mock private AttackChainNodeUtils attackChainNodeUtils;

  @Mock private AttackChainNodeStatusMapper attackChainNodeStatusMapper;

  @Mock private AttackChainNodeExpectationMapper attackChainNodeExpectationMapper;

  @Mock private NodeContractService nodeContractService;

  @Mock private UserService userService;

  @Mock private TagService tagService;

  @Mock private SmtpService smtpService;

  @Mock private ImapService imapService;

  @Mock private CollectorService collectorService;

  @Mock private NodeExecutorService nodeExecutorService;

  @Spy private NodeContractContentUtils nodeContractContentUtils;

  ObjectMapper mapper;

  @InjectMocks private AttackChainNodeService attackChainNodeService;
  @InjectMocks private AttackChainNodeStatusService attackChainNodeStatusService;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    ReflectionTestUtils.setField(attackChainNodeService, "mapper", mapper);
    ReflectionTestUtils.setField(
        attackChainNodeService, "healthCheckUtils", new HealthCheckUtils(new ExecutorUtils()));
    ReflectionTestUtils.setField(
        attackChainNodeService,
        "attackChainNodeMapper",
        new AttackChainNodeMapper(
            attackChainNodeStatusMapper, attackChainNodeExpectationMapper, attackChainNodeUtils));
    ReflectionTestUtils.setField(
        attackChainNodeService, "nodeContractContentUtils", nodeContractContentUtils);
  }

  @Test
  public void testApplyDefaultAssetGroupsToInject_WITH_unexisting_attackChainNode() {
    doReturn(Optional.empty()).when(attackChainNodeRepository).findById(INJECT_ID);
    assertThrows(
        ElementNotFoundException.class,
        () ->
            attackChainNodeService.applyDefaultAssetGroupsToAttackChainNode(INJECT_ID, List.of()));
  }

  @Test
  public void testApplyDefaultAssetGroupsToInject_WITH_default_assets_to_add() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    AssetGroup assetGroup3 = getAssetGroup("assetgroup3");
    AssetGroup assetGroup4 = getAssetGroup("assetgroup4");
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setId(INJECT_ID);
    attackChainNode.setAssetGroups(List.of(assetGroup1, assetGroup2, assetGroup3));
    doReturn(Optional.of(attackChainNode)).when(attackChainNodeRepository).findById(INJECT_ID);

    attackChainNodeService.applyDefaultAssetGroupsToAttackChainNode(
        INJECT_ID, List.of(assetGroup4));

    ArgumentCaptor<AttackChainNode> attackChainNodeCaptor =
        ArgumentCaptor.forClass(AttackChainNode.class);
    verify(attackChainNodeRepository).save(attackChainNodeCaptor.capture());
    AttackChainNode capturedAttackChainNode = attackChainNodeCaptor.getValue();
    assertEquals(INJECT_ID, capturedAttackChainNode.getId());
    assertEquals(
        new HashSet<>(List.of(assetGroup1, assetGroup2, assetGroup3, assetGroup4)),
        new HashSet<>(capturedAttackChainNode.getAssetGroups()));
  }

  @Test
  public void testApplyDefaultAssetGroupsToInject_WITH_no_change() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    AssetGroup assetGroup3 = getAssetGroup("assetgroup3");
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setId(INJECT_ID);
    attackChainNode.setAssetGroups(List.of(assetGroup1, assetGroup2, assetGroup3));
    doReturn(Optional.of(attackChainNode)).when(attackChainNodeRepository).findById(INJECT_ID);

    attackChainNodeService.applyDefaultAssetGroupsToAttackChainNode(
        INJECT_ID, List.of(assetGroup1));

    verify(attackChainNodeRepository, never()).save(any());
  }

  private AssetGroup getAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup(name);
    assetGroup.setId(name);
    return assetGroup;
  }

  @DisplayName("Test get inject specification with valid search input")
  @Test
  void getAttackChainNodeSpecificationWithValidSearchInput() {
    // Arrange
    AttackChainNodeBulkProcessingInput input = new AttackChainNodeBulkProcessingInput();
    input.setSearchPaginationInput(new SearchPaginationInput());
    input.getSearchPaginationInput().setFilterGroup(new Filters.FilterGroup());
    input.getSearchPaginationInput().setTextSearch("test");

    when(userService.currentUser()).thenReturn(new User());

    // Act
    Specification<AttackChainNode> specification =
        attackChainNodeService.getAttackChainNodeSpecification(input, Grant.GRANT_TYPE.OBSERVER);

    // Assert
    assertNotNull(specification);
  }

  @DisplayName("Test get inject specification with inject IDs to process")
  @Test
  void getAttackChainNodeSpecificationWithAttackChainNodeIDsToProcess() {
    // Arrange
    AttackChainNodeBulkProcessingInput input = new AttackChainNodeBulkProcessingInput();
    input.setAttackChainNodeIDsToProcess(List.of("id1", "id2"));

    when(userService.currentUser()).thenReturn(new User());

    // Act
    Specification<AttackChainNode> specification =
        attackChainNodeService.getAttackChainNodeSpecification(input, Grant.GRANT_TYPE.OBSERVER);

    // Assert
    assertNotNull(specification);
  }

  @DisplayName("Test get inject specification with inject IDs to ignore")
  @Test
  void getAttackChainNodeSpecificationWithAttackChainNodeIDsToIgnore() {
    // Arrange
    AttackChainNodeBulkProcessingInput input = new AttackChainNodeBulkProcessingInput();
    input.setAttackChainNodeIDsToProcess(List.of("id1", "id2"));
    input.setAttackChainNodeIDsToIgnore(List.of("id3"));

    when(userService.currentUser()).thenReturn(new User());

    // Act
    Specification<AttackChainNode> specification =
        attackChainNodeService.getAttackChainNodeSpecification(input, Grant.GRANT_TYPE.OBSERVER);

    // Assert
    assertNotNull(specification);
  }

  @DisplayName("Test get inject specification with null input")
  @Test
  void getAttackChainNodeSpecificationWithNullInput() {
    // Arrange
    AttackChainNodeBulkProcessingInput input = new AttackChainNodeBulkProcessingInput();

    // Act & assert
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                attackChainNodeService.getAttackChainNodeSpecification(
                    input, Grant.GRANT_TYPE.OBSERVER));

    // Assert
    assertEquals(
        "Either inject_ids_to_process or search_pagination_input must be provided, and not both at the same time",
        exception.getMessage());
  }

  @DisplayName("Test bulk update injects with valid operations")
  @Test
  void bulkUpdateAttackChainNodesWithValidOperations() {
    // Arrange
    Team t0 = new Team();
    t0.setId("team0");
    Asset a0 = new Asset();
    a0.setId("asset0");
    AttackChainNode i1 = new AttackChainNode();
    i1.setId("inject1");
    AttackChainNode i2 = new AttackChainNode();
    i2.setId("inject2");
    i1.setTeams(new ArrayList<>(List.of(t0)));
    i1.setAssets(new ArrayList<>(List.of(a0)));

    List<AttackChainNode> attackChainNodesToUpdate = List.of(i1, i2);

    AttackChainNodeBulkUpdateOperation ope1 = new AttackChainNodeBulkUpdateOperation();
    ope1.setField(AttackChainNodeBulkUpdateSupportedFields.TEAMS);
    ope1.setOperation(AttackChainNodeBulkUpdateSupportedOperations.ADD);
    ope1.setValues(List.of("team1", "team2"));
    AttackChainNodeBulkUpdateOperation ope2 = new AttackChainNodeBulkUpdateOperation();
    ope2.setField(AttackChainNodeBulkUpdateSupportedFields.ASSETS);
    ope2.setOperation(AttackChainNodeBulkUpdateSupportedOperations.REPLACE);
    ope2.setValues(List.of("asset1", "asset2"));

    List<AttackChainNodeBulkUpdateOperation> operations = List.of(ope1, ope2);

    Team t1 = new Team();
    t1.setId("team1");
    Team t2 = new Team();
    t2.setId("team2");
    List<Team> tList = List.of(t1, t2);

    Asset a1 = new Asset();
    a1.setId("asset1");
    Asset a2 = new Asset();
    a2.setId("asset2");
    List<Asset> aList = List.of(a1, a2);

    when(teamRepository.findAllById(any())).thenReturn(tList);
    when(assetService.assets(any())).thenReturn(aList);

    // Expected results
    AttackChainNode i1updated = new AttackChainNode();
    i1updated.setId("inject1");
    AttackChainNode i2updated = new AttackChainNode();
    i2updated.setId("inject2");
    i1updated.setTeams(new ArrayList<>(List.of(t0)));
    i1updated.getTeams().addAll(tList);
    i1updated.setAssets(aList);
    i2updated.setTeams(tList);
    i2updated.setAssets(aList);

    List<AttackChainNode> expectedUpdatedAttackChainNodes = List.of(i1updated, i2updated);

    when(attackChainNodeRepository.saveAll(expectedUpdatedAttackChainNodes))
        .thenReturn(expectedUpdatedAttackChainNodes);

    // Act
    List<AttackChainNode> updatedAttackChainNodes =
        attackChainNodeService.bulkUpdateAttackChainNode(attackChainNodesToUpdate, operations);

    // Assert
    assertNotNull(updatedAttackChainNodes);
    assertEquals(2, updatedAttackChainNodes.size());
    // test that we added the teams and replaced the assets to the existing lists
    assertEquals(1 + tList.size(), updatedAttackChainNodes.getFirst().getTeams().size());
    assertEquals(aList.size(), updatedAttackChainNodes.getFirst().getAssets().size());
    assertTrue(updatedAttackChainNodes.getFirst().getTeams().containsAll(tList));
    assertTrue(updatedAttackChainNodes.getFirst().getAssets().containsAll(aList));
    assertTrue(updatedAttackChainNodes.get(1).getTeams().containsAll(tList));
    assertTrue(updatedAttackChainNodes.get(1).getAssets().containsAll(aList));
  }

  @DisplayName("Test bulk update injects with empty operations")
  @Test
  void bulkUpdateAttackChainNodesWithEmptyOperations() {
    // Arrange
    List<AttackChainNode> attackChainNodesToUpdate =
        List.of(new AttackChainNode(), new AttackChainNode());
    List<AttackChainNodeBulkUpdateOperation> operations = List.of();

    when(attackChainNodeRepository.saveAll(attackChainNodesToUpdate))
        .thenReturn(attackChainNodesToUpdate);

    // Act
    List<AttackChainNode> updatedAttackChainNodes =
        attackChainNodeService.bulkUpdateAttackChainNode(attackChainNodesToUpdate, operations);

    // Assert
    assertNotNull(updatedAttackChainNodes);
    assertEquals(2, updatedAttackChainNodes.size());
    assertTrue(updatedAttackChainNodes.getFirst().getTeams().isEmpty());
    assertTrue(updatedAttackChainNodes.getFirst().getAssets().isEmpty());
    assertTrue(updatedAttackChainNodes.getFirst().getAssetGroups().isEmpty());
  }

  @DisplayName("Test bulk update injects with non-existing team")
  @Test
  void bulkUpdateAttackChainNodesWithNonExistingEntity() {
    // Arrange
    AttackChainNode i1 = new AttackChainNode();
    i1.setId("inject1");
    AttackChainNode i2 = new AttackChainNode();
    i2.setId("inject2");
    List<AttackChainNode> attackChainNodesToUpdate = List.of(i1, i2);

    AttackChainNodeBulkUpdateOperation ope = new AttackChainNodeBulkUpdateOperation();
    ope.setField(AttackChainNodeBulkUpdateSupportedFields.TEAMS);
    ope.setOperation(AttackChainNodeBulkUpdateSupportedOperations.ADD);
    ope.setValues(List.of("nonExistingTeam"));

    List<AttackChainNodeBulkUpdateOperation> operations = List.of(ope);

    when(teamRepository.findAllById(any())).thenReturn(List.of());

    // Expected results
    AttackChainNode i1updated = new AttackChainNode();
    i1updated.setId("inject1");
    AttackChainNode i2updated = new AttackChainNode();
    i2updated.setId("inject2");

    List<AttackChainNode> expectedUpdatedAttackChainNodes = List.of(i1updated, i2updated);

    when(attackChainNodeRepository.saveAll(expectedUpdatedAttackChainNodes))
        .thenReturn(expectedUpdatedAttackChainNodes);

    // Act
    List<AttackChainNode> updatedAttackChainNodes =
        attackChainNodeService.bulkUpdateAttackChainNode(attackChainNodesToUpdate, operations);

    // Assert
    assertNotNull(updatedAttackChainNodes);
    assertEquals(expectedUpdatedAttackChainNodes.size(), updatedAttackChainNodes.size());
    assertTrue(updatedAttackChainNodes.getFirst().getTeams().isEmpty());
    assertTrue(updatedAttackChainNodes.get(1).getTeams().isEmpty());
  }

  @DisplayName("Test get injects and check is planner with valid input")
  @Test
  void getAttackChainNodesAndCheckPermissionWithValidInput() {
    // Arrange
    AttackChainNodeBulkProcessingInput input = new AttackChainNodeBulkProcessingInput();
    input.setSearchPaginationInput(new SearchPaginationInput());
    input.getSearchPaginationInput().setFilterGroup(new Filters.FilterGroup());
    input.getSearchPaginationInput().setTextSearch("test");

    List<AttackChainNode> attackChainNodes = List.of(new AttackChainNode(), new AttackChainNode());
    //noinspection unchecked
    when(attackChainNodeRepository.findAll(any(Specification.class))).thenReturn(attackChainNodes);

    when(userService.currentUser()).thenReturn(new User());

    // Act
    List<AttackChainNode> result =
        attackChainNodeService.getAttackChainNodesAndCheckPermission(
            input, Grant.GRANT_TYPE.PLANNER);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
  }

  @DisplayName("Test get injects and check is planner with inject IDs to process")
  @Test
  void getAttackChainNodesAndCheckPermissionWithAttackChainNodeIDsToProcess() {
    // Arrange
    AttackChainNodeBulkProcessingInput input = new AttackChainNodeBulkProcessingInput();
    input.setAttackChainNodeIDsToProcess(List.of("id1", "id2"));

    List<AttackChainNode> attackChainNodes = List.of(new AttackChainNode(), new AttackChainNode());

    when(userService.currentUser()).thenReturn(new User());

    //noinspection unchecked
    when(attackChainNodeRepository.findAll(any(Specification.class))).thenReturn(attackChainNodes);

    // Act
    List<AttackChainNode> result =
        attackChainNodeService.getAttackChainNodesAndCheckPermission(
            input, Grant.GRANT_TYPE.PLANNER);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
  }

  @DisplayName("Test get injects and check is planner with inject IDs to ignore")
  @Test
  void getAttackChainNodesAndCheckPermissionWithAttackChainNodeIDsToIgnore() {
    // Arrange
    AttackChainNodeBulkProcessingInput input = new AttackChainNodeBulkProcessingInput();
    input.setAttackChainNodeIDsToProcess(List.of("id1", "id2"));
    input.setAttackChainNodeIDsToIgnore(List.of("id3"));

    when(userService.currentUser()).thenReturn(new User());

    List<AttackChainNode> attackChainNodes = List.of(new AttackChainNode(), new AttackChainNode());

    //noinspection unchecked
    when(attackChainNodeRepository.findAll(any(Specification.class))).thenReturn(attackChainNodes);

    // Act
    List<AttackChainNode> result =
        attackChainNodeService.getAttackChainNodesAndCheckPermission(
            input, Grant.GRANT_TYPE.PLANNER);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
  }

  @DisplayName("Test get injects and check is planner with null input")
  @Test
  void getAttackChainNodesAndCheckPermissionWithNullInput() {
    // Arrange
    AttackChainNodeBulkProcessingInput input = new AttackChainNodeBulkProcessingInput();

    // Act & assert
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                attackChainNodeService.getAttackChainNodesAndCheckPermission(
                    input, Grant.GRANT_TYPE.PLANNER));

    // Assert
    assertEquals(
        "Either inject_ids_to_process or search_pagination_input must be provided, and not both at the same time",
        exception.getMessage());
  }

  @DisplayName("Test delete all injects by valid IDs")
  @Test
  void deleteAllAttackChainNodesByValidIds() {
    // Arrange
    List<String> attackChainNodeIds = List.of("id1", "id2");

    doNothing().when(attackChainNodeRepository).deleteByAllIdsNative(attackChainNodeIds);

    // Act
    attackChainNodeService.deleteAllByIds(attackChainNodeIds);

    // Assert
    verify(attackChainNodeRepository, times(1)).deleteByAllIdsNative(attackChainNodeIds);
  }

  @DisplayName("Test delete all injects by empty IDs list")
  @Test
  void deleteAllAttackChainNodesByEmptyIdsList() {
    // Arrange
    List<String> attackChainNodeIds = List.of();

    // Act
    attackChainNodeService.deleteAllByIds(attackChainNodeIds);

    // Assert
    verify(attackChainNodeRepository, never()).deleteByAllIdsNative(any());
  }

  @DisplayName("Test delete all injects by null IDs list")
  @Test
  void deleteAllAttackChainNodesByNullIdsList() {
    // Arrange
    List<String> attackChainNodeIds = null;

    // Act
    attackChainNodeService.deleteAllByIds(attackChainNodeIds);

    // Assert
    verify(attackChainNodeRepository, never()).deleteByAllIdsNative(any());
  }

  @DisplayName("Test canApplyTargetType with manual inject")
  @Test
  void testCanApplyAssetToInject_WITH_no_assetGroup() throws JsonProcessingException {
    NodeContract nodeContract = new NodeContract();
    nodeContract.setContent(
        "{\"manual\":true,\"fields\":[{\"key\":\"content\",\"label\":\"Content\",\"mandatory\":true,\"readOnly\":false,\"mandatoryGroups\":null,\"linkedFields\":[],\"linkedValues\":[],\"defaultValue\":\"\",\"richText\":false,\"type\":\"textarea\"}]}");
    nodeContract.setConvertedContent((ObjectNode) mapper.readTree(nodeContract.getContent()));
    AttackChainNode attackChainNode = new AttackChainNode();
    doCallRealMethod().when(nodeContractService).checkTargetSupport(any(), any());
    attackChainNode.setNodeContract(nodeContract);

    assertFalse(
        attackChainNodeService.canApplyTargetType(attackChainNode, TargetType.ASSETS_GROUPS));
  }

  @DisplayName("Test canApplyTargetType with inject with asset group")
  @Test
  void testCanApplyAssetGroupToInject_WITH_assets() throws JsonProcessingException {
    NodeContract nodeContract = new NodeContract();
    nodeContract.setContent(
        "{\"manual\":true,\"fields\":[{\"key\":\"assetgroups\",\"label\":\"Content\",\"mandatory\":true,\"readOnly\":false,\"mandatoryGroups\":null,\"linkedFields\":[],\"linkedValues\":[],\"defaultValue\":\"\",\"richText\":false,\"type\":\"asset-group\"}]}");
    nodeContract.setConvertedContent((ObjectNode) mapper.readTree(nodeContract.getContent()));
    AttackChainNode attackChainNode = new AttackChainNode();
    doCallRealMethod().when(nodeContractService).checkTargetSupport(any(), any());
    attackChainNode.setNodeContract(nodeContract);

    assertTrue(
        attackChainNodeService.canApplyTargetType(attackChainNode, TargetType.ASSETS_GROUPS));
  }

  @Test
  void given_valid_input_initializeAttackChainNodeStatus_SHOULD_save_the_attackChainNodestatus() {
    ExecutionStatus executionStatus = ExecutionStatus.EXECUTING;
    String attackChainNodeId = "injectid";
    String attackChainNodeStatusID = "injectStatusID";
    AttackChainNodeStatus attackChainNodeStatus = new AttackChainNodeStatus();
    attackChainNodeStatus.setId(attackChainNodeStatusID);
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setId(attackChainNodeId);
    attackChainNode.setStatus(attackChainNodeStatus);
    attackChainNodeStatus.setAttackChainNode(attackChainNode);
    StatusPayload statusPayload = new StatusPayload();

    when(attackChainNodeUtils.getStatusPayloadFromAttackChainNode(attackChainNode))
        .thenReturn(statusPayload);
    when(attackChainNodeRepository.findById(attackChainNodeId))
        .thenReturn(Optional.of(attackChainNode));

    attackChainNodeStatusService.initializeAttackChainNodeStatus(
        attackChainNodeId, executionStatus);

    ArgumentCaptor<AttackChainNodeStatus> statusCaptor =
        ArgumentCaptor.forClass(AttackChainNodeStatus.class);
    verify(attackChainNodeStatusRepository).save(statusCaptor.capture());
    AttackChainNodeStatus savedStatus = statusCaptor.getValue();
    assertNotNull(savedStatus);
    assertEquals(attackChainNode, savedStatus.getAttackChainNode());
    assertEquals(executionStatus, savedStatus.getName());
    assertEquals(statusPayload, savedStatus.getPayloadOutput());
  }

  @Test
  void given_inject_without_injectcontent_SHOULD_take_default() throws JsonProcessingException {
    AttackChainNodeInput attackChainNodeInput = new AttackChainNodeInput();
    AttackChain attackChain = new AttackChain();
    String nodeContractId = "injectorContractId";
    String nodeContractString =
        """
              {
                "fields": [
                  {
                  "type": "defaultValue1",
                  "key": "value1",
                  "defaultValue": ["defaultValue1"],
                   "cardinality":"1"
                  },
                  {
                  "type": "asset",
                  "key": "value2",
                  "defaultValue": ["defaultValue2"],
                  "cardinality":"1"
                  }
                ]
              }
            """;
    NodeContract nodeContract = new NodeContract();
    nodeContract.setId(nodeContractId);
    nodeContract.setContent(nodeContractString);
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode nodeContractJson = (ObjectNode) mapper.readTree(nodeContractString);

    nodeContract.setConvertedContent(nodeContractJson);

    attackChainNodeInput.setNodeContract(nodeContractId);
    when(nodeContractService.nodeContract(nodeContractId)).thenReturn(nodeContract);

    attackChainNodeService.createAndSaveAttackChainNode(null, attackChain, attackChainNodeInput);

    ArgumentCaptor<AttackChainNode> attackChainNodeCaptor =
        ArgumentCaptor.forClass(AttackChainNode.class);
    verify(attackChainNodeRepository).save(attackChainNodeCaptor.capture());
    AttackChainNode capturedAttackChainNode = attackChainNodeCaptor.getValue();

    assertEquals("defaultValue1", capturedAttackChainNode.getContent().get("value1").asText());
    assertEquals("defaultValue2", capturedAttackChainNode.getContent().get("value2").asText());
  }

  @Test
  public void testRunChecksWhenAttackChainNodeIsNull() {

    // RUN
    List<HealthCheck> healtchChecks = attackChainNodeService.runChecks(null);

    // VERIFY
    assertNull(healtchChecks);
  }

  @Test
  public void testRunChecksForSmtpIssue() throws JsonProcessingException {
    // PREPARE
    AttackChainNode attackChainNode =
        AttackChainNodeFixture.getAttackChainNodeForEmailContract(
            NodeContractFixture.createPayloadNodeContractWithFieldsContent(
                NodeExecutorFixture.createDefaultPayloadNodeExecutor(), null, List.of()));
    attackChainNode
        .getNodeContract()
        .get()
        .getNodeExecutor()
        .setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.SMTP});

    // MOCK
    when(smtpService.isServiceAvailable()).thenReturn(false);
    when(collectorService.securityPlatformCollectors()).thenReturn(List.of());
    when(nodeExecutorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healtchChecks = attackChainNodeService.runChecks(attackChainNode);

    // VERIFY
    assertNotNull(healtchChecks);
    assertFalse(healtchChecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healtchChecks.stream()
            .filter(hc -> HealthCheck.Type.SMTP.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, null));
    assertEquals(HealthCheck.Type.SMTP, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.SERVICE_UNAVAILABLE, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  public void testRunChecksForImapIssue() throws JsonProcessingException {
    // PREPARE
    AttackChainNode attackChainNode =
        AttackChainNodeFixture.getAttackChainNodeForEmailContract(
            NodeContractFixture.createPayloadNodeContractWithFieldsContent(
                NodeExecutorFixture.createDefaultPayloadNodeExecutor(), null, List.of()));
    attackChainNode
        .getNodeContract()
        .get()
        .getNodeExecutor()
        .setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.IMAP});

    // MOCK
    when(imapService.isServiceAvailable()).thenReturn(false);
    when(collectorService.securityPlatformCollectors()).thenReturn(List.of());
    when(nodeExecutorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healtchChecks = attackChainNodeService.runChecks(attackChainNode);
    // VERIFY
    assertNotNull(healtchChecks);
    assertFalse(healtchChecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healtchChecks.stream()
            .filter(hc -> HealthCheck.Type.IMAP.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, null));
    assertEquals(HealthCheck.Type.IMAP, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.SERVICE_UNAVAILABLE, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.WARNING, healthCheckToVerify.getStatus());
  }

  @Test
  public void testRunChecksForExecutorIssue() throws JsonProcessingException {
    // PREPARE
    AttackChainNode attackChainNode =
        AttackChainNodeFixture.getAttackChainNodeForEmailContract(
            NodeContractFixture.createPayloadNodeContractWithFieldsContent(
                NodeExecutorFixture.createDefaultPayloadNodeExecutor(), null, List.of()));
    attackChainNode.getNodeContract().get().setNeedsExecutor(true);

    // MOCK
    when(collectorService.securityPlatformCollectors()).thenReturn(List.of());
    when(nodeExecutorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healtchChecks = attackChainNodeService.runChecks(attackChainNode);

    // VERIFY
    assertNotNull(healtchChecks);
    assertFalse(healtchChecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healtchChecks.stream()
            .filter(hc -> HealthCheck.Type.AGENT_OR_EXECUTOR.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, null));
    assertEquals(HealthCheck.Type.AGENT_OR_EXECUTOR, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.EMPTY, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  public void testRunChecksForCollectorIssue() throws JsonProcessingException {
    // PREPARE
    AttackChainNode attackChainNode =
        AttackChainNodeFixture.getAttackChainNodeForEmailContract(
            NodeContractFixture.createPayloadNodeContractWithFieldsContent(
                NodeExecutorFixture.createDefaultPayloadNodeExecutor(), null, List.of()));

    ObjectNode expectationDetection = mapper.createObjectNode();
    expectationDetection.put("expectation_type", AttackChainNodeExpectation.EXPECTATION_TYPE.DETECTION.toString());

    ObjectNode expectationPrevention = mapper.createObjectNode();
    expectationPrevention.put("expectation_type", AttackChainNodeExpectation.EXPECTATION_TYPE.PREVENTION.toString());

    ArrayNode expectationsArray = mapper.createArrayNode();
    expectationsArray.add(expectationDetection);
    expectationsArray.add(expectationPrevention);

    ObjectNode content = mapper.createObjectNode();
    content.put("expectations", expectationsArray);
    attackChainNode.setContent(content);

    // MOCK
    when(collectorService.securityPlatformCollectors()).thenReturn(List.of());
    when(nodeExecutorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healtchChecks = attackChainNodeService.runChecks(attackChainNode);

    // VERIFY
    assertNotNull(healtchChecks);
    assertFalse(healtchChecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healtchChecks.stream()
            .filter(hc -> HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, null));
    assertEquals(HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.EMPTY, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  public void given_nodeExecutorDependenciesOnNmap_when_nmapIsRegistered_then_noHealtchCheck()
      throws JsonProcessingException {

    // PREPARE
    AttackChainNode attackChainNode =
        AttackChainNodeFixture.getAttackChainNodeForEmailContract(
            NodeContractFixture.createPayloadNodeContractWithFieldsContent(
                NodeExecutorFixture.createDefaultPayloadNodeExecutor(), null, List.of()));
    attackChainNode
        .getNodeContract()
        .get()
        .getNodeExecutor()
        .setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.NMAP});

    // MOCK
    when(collectorService.securityPlatformCollectors()).thenReturn(List.of());
    NodeExecutor nmapNodeExecutor = new NodeExecutor();
    nmapNodeExecutor.setId("testNmap");
    nmapNodeExecutor.setType("veriguard_nmap");
    when(nodeExecutorService.findAll()).thenReturn(List.of(nmapNodeExecutor));

    // RUN
    List<HealthCheck> healtchChecks = attackChainNodeService.runChecks(attackChainNode);
    // VERIFY
    assertTrue(healtchChecks.isEmpty());
  }

  @Test
  public void given_nodeExecutorDependenciesOnNmap_when_nmapIsRegistered_then_healtchCheckCreated()
      throws JsonProcessingException {

    // PREPARE
    AttackChainNode attackChainNode =
        AttackChainNodeFixture.getAttackChainNodeForEmailContract(
            NodeContractFixture.createPayloadNodeContractWithFieldsContent(
                NodeExecutorFixture.createDefaultPayloadNodeExecutor(), null, List.of()));
    attackChainNode
        .getNodeContract()
        .get()
        .getNodeExecutor()
        .setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.NMAP});

    // MOCK
    when(collectorService.securityPlatformCollectors()).thenReturn(List.of());
    when(nodeExecutorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healthChecks = attackChainNodeService.runChecks(attackChainNode);

    // VERIFY
    HealthCheck healthCheckToVerify =
        healthChecks.stream()
            .filter(hc -> HealthCheck.Type.NMAP.equals(hc.getType()))
            .findFirst()
            .orElseThrow();
    assertEquals(HealthCheck.Type.NMAP, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.SERVICE_UNAVAILABLE, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  public void
      given_nodeExecutorDependenciesOnNuclei_when_nucleiIsRegistered_then_healtchCheckCreated()
          throws JsonProcessingException {

    // PREPARE
    AttackChainNode attackChainNode =
        AttackChainNodeFixture.getAttackChainNodeForEmailContract(
            NodeContractFixture.createPayloadNodeContractWithFieldsContent(
                NodeExecutorFixture.createDefaultPayloadNodeExecutor(), null, List.of()));
    attackChainNode
        .getNodeContract()
        .get()
        .getNodeExecutor()
        .setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.NUCLEI});

    // MOCK
    when(collectorService.securityPlatformCollectors()).thenReturn(List.of());
    when(nodeExecutorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healthChecks = attackChainNodeService.runChecks(attackChainNode);

    // VERIFY
    HealthCheck healthCheckToVerify =
        healthChecks.stream()
            .filter(hc -> HealthCheck.Type.NUCLEI.equals(hc.getType()))
            .findFirst()
            .orElseThrow();
    assertEquals(HealthCheck.Type.NUCLEI, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.SERVICE_UNAVAILABLE, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }
}
