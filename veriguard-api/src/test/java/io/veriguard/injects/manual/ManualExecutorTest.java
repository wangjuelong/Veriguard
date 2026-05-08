package io.veriguard.injects.manual;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.Execution;
import io.veriguard.database.model.Injection;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.manual.ManualExecutor;
import io.veriguard.injectors.manual.model.ManualContent;
import io.veriguard.model.expectation.ManualExpectation;
import io.veriguard.model.attack_chain_node.form.Expectation;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class ManualExecutorTest extends IntegrationTest {

  @Mock AttackChainNodeExpectationService attackChainNodeExpectationService;

  @Mock ObjectMapper mapper;
  @InjectMocks private NodeExecutorContext nodeExecutorContext;

  @Test
  void process() throws Exception {

    // mock input
    Expectation expectation = new Expectation();
    expectation.setExpectationGroup(false);
    expectation.setName("Expectation 1");
    expectation.setDescription("Expectation 1");
    expectation.setType(AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL);
    expectation.setScore(80D);
    expectation.setExpirationTime(Instant.now().toEpochMilli());
    ManualContent manualContent = new ManualContent();
    manualContent.setExpectations(List.of(expectation));
    Execution execution = mock(Execution.class);
    ExecutableNode executableAttackChainNode = mock(ExecutableNode.class);
    Injection injection = mock(Injection.class);
    AttackChainNode attackChainNode = mock(AttackChainNode.class);
    ObjectNode content = mock(ObjectNode.class);
    when(attackChainNode.getContent()).thenReturn(content);
    when(injection.getAttackChainNode()).thenReturn(attackChainNode);
    when(executableAttackChainNode.getInjection()).thenReturn(injection);
    when(mapper.treeToValue(content, ManualContent.class)).thenReturn(manualContent);

    ManualExecutor executor =
        new ManualExecutor(nodeExecutorContext, attackChainNodeExpectationService);
    executor.process(execution, executableAttackChainNode);

    // verify that the expectations are saved
    verify(attackChainNodeExpectationService)
        .buildAndSaveAttackChainNodeExpectations(
            executableAttackChainNode, List.of(new ManualExpectation(expectation)));
  }
}
