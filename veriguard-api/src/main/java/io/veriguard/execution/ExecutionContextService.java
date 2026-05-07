package io.veriguard.execution;

import static io.veriguard.injector_contract.variables.VariableHelper.*;

import io.veriguard.config.VeriguardConfig;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.Injection;
import io.veriguard.database.model.User;
import io.veriguard.database.model.Variable;
import io.veriguard.service.VariableService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ExecutionContextService {

  @Resource private final VeriguardConfig veriguardCOnfig;

  private final VariableService variableService;

  public ExecutionContext executionContext(
      @NotNull final User user, Injection injection, String team) {
    return this.executionContext(user, injection, List.of(team));
  }

  public ExecutionContext executionContext(
      @NotNull final User user, Injection injection, List<String> teams) {
    ExecutionContext executionContext = new ExecutionContext(user, teams);
    if (injection.getAttackChainRun() != null) {
      String attackChainRunId = injection.getAttackChainRun().getId();
      String queryParams = "?user=" + user.getId() + "&inject=" + injection.getId();
      String baseUrl = this.veriguardCOnfig.getBaseUrl();
      executionContext.put(PLAYER_URI, baseUrl + "/private/" + attackChainRunId + queryParams);
      executionContext.put(CHALLENGES_URI, baseUrl + "/challenges/" + attackChainRunId + queryParams);
      executionContext.put(SCOREBOARD_URI, baseUrl + "/scoreboard/" + attackChainRunId + queryParams);
      executionContext.put(
          LESSONS_URI, baseUrl + "/lessons/simulation/" + attackChainRunId + queryParams);
      executionContext.put(EXERCISE, injection.getAttackChainRun());
      fillDynamicSimulationVariable(executionContext, attackChainRunId);
    } else if (injection.getAttackChain() != null) {
      fillDynamicAttackChainVariable(executionContext, injection.getAttackChain().getId());
    }

    return executionContext;
  }

  public ExecutionContext executionContext(
      @NotNull final User user, AttackChainRun attackChainRun, String team) {
    ExecutionContext executionContext = new ExecutionContext(user, List.of(team));
    if (attackChainRun != null) {
      fillDynamicSimulationVariable(executionContext, attackChainRun.getId());
    }
    return executionContext;
  }

  // -- PRIVATE --

  private void fillDynamicSimulationVariable(
      @NotNull ExecutionContext executionContext, @NotBlank final String attackChainRunId) {
    List<Variable> variables = this.variableService.variablesFromAttackChainRun(attackChainRunId);
    variables.forEach((v) -> executionContext.put(v.getKey(), v.getValue()));
  }

  private void fillDynamicAttackChainVariable(
      @NotNull ExecutionContext executionContext, @NotBlank final String attackChainId) {
    List<Variable> variables = this.variableService.variablesFromAttackChain(attackChainId);
    variables.forEach((v) -> executionContext.put(v.getKey(), v.getValue()));
  }
}
