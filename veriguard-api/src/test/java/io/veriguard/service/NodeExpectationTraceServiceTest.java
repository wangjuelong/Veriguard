package io.veriguard.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.NodeExpectationTrace;
import io.veriguard.database.model.SecurityPlatform;
import io.veriguard.database.repository.CollectorRepository;
import io.veriguard.database.repository.NodeExpectationTraceRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeExpectationTraceServiceTest {

  @Mock private NodeExpectationTraceRepository nodeExpectationTraceRepository;
  @Mock private CollectorRepository collectorRepository;

  @InjectMocks private NodeExpectationTraceService nodeExpectationTraceService;

  private NodeExpectationTrace nodeExpectationTrace;
  private AttackChainNodeExpectation attackChainNodeExpectation;
  private SecurityPlatform securityPlatform;
  private String attackChainNodeExpectationId;
  private String securityPlatformId;
  private String expectationResultSourceType;

  @BeforeEach
  void setUp() {
    attackChainNodeExpectationId = UUID.randomUUID().toString();
    securityPlatformId = UUID.randomUUID().toString();
    expectationResultSourceType = "TYPE";

    attackChainNodeExpectation = new AttackChainNodeExpectation();
    attackChainNodeExpectation.setId(attackChainNodeExpectationId);

    securityPlatform = new SecurityPlatform();
    securityPlatform.setId(securityPlatformId);

    nodeExpectationTrace = new NodeExpectationTrace();
    nodeExpectationTrace.setId(UUID.randomUUID().toString());
    nodeExpectationTrace.setAttackChainNodeExpectation(attackChainNodeExpectation);
    nodeExpectationTrace.setSecurityPlatform(securityPlatform);
    nodeExpectationTrace.setAlertDate(Instant.now());
    nodeExpectationTrace.setAlertLink("http://test-link.com");
    nodeExpectationTrace.setAlertName("Test Alert");
  }

  @Test
  void getNodeExpectationTracesFromCollector_Success() {
    // Arrange
    List<NodeExpectationTrace> expectedTraces = Collections.singletonList(nodeExpectationTrace);
    when(nodeExpectationTraceRepository.findByExpectationAndSecurityPlatform(
            anyString(), anyString()))
        .thenReturn(expectedTraces);

    // Act
    List<NodeExpectationTrace> result =
        nodeExpectationTraceService.getNodeExpectationTracesFromCollector(
            attackChainNodeExpectationId, securityPlatformId);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(nodeExpectationTrace, result.get(0));
    verify(nodeExpectationTraceRepository)
        .findByExpectationAndSecurityPlatform(attackChainNodeExpectationId, securityPlatformId);
  }

  @Test
  void getNodeExpectationTracesFromCollector_EmptyResult() {
    // Arrange
    when(nodeExpectationTraceRepository.findByExpectationAndSecurityPlatform(
            anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    // Act
    List<NodeExpectationTrace> result =
        nodeExpectationTraceService.getNodeExpectationTracesFromCollector(
            attackChainNodeExpectationId, securityPlatformId);

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty());
    verify(nodeExpectationTraceRepository)
        .findByExpectationAndSecurityPlatform(attackChainNodeExpectationId, securityPlatformId);
  }

  @Test
  void getAlertLinksNumber_Success() {
    // Arrange
    long expectedCount = 5L;
    when(nodeExpectationTraceRepository.countAlerts(anyString(), anyString()))
        .thenReturn(expectedCount);

    // Act
    long result =
        nodeExpectationTraceService.getAlertLinksNumber(
            attackChainNodeExpectationId, securityPlatformId, expectationResultSourceType);

    // Assert
    assertEquals(expectedCount, result);
    verify(nodeExpectationTraceRepository)
        .countAlerts(attackChainNodeExpectationId, securityPlatformId);
  }

  @Test
  void getAlertLinksNumber_ZeroCount() {
    // Arrange
    when(nodeExpectationTraceRepository.countAlerts(anyString(), anyString())).thenReturn(0L);

    // Act
    long result =
        nodeExpectationTraceService.getAlertLinksNumber(
            attackChainNodeExpectationId, securityPlatformId, expectationResultSourceType);

    // Assert
    assertEquals(0L, result);
    verify(nodeExpectationTraceRepository)
        .countAlerts(attackChainNodeExpectationId, securityPlatformId);
  }

  @Test
  void createNodeExpectationTrace_WithNullTrace() {
    // Act & Assert
    nodeExpectationTraceService.bulkInsertNodeExpectationTraces(List.of());
    verify(collectorRepository, never()).save(any());
    verify(nodeExpectationTraceRepository, never()).save(any());
  }
}
