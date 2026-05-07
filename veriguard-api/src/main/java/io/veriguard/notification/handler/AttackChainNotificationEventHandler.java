package io.veriguard.notification.handler;

import io.veriguard.config.VeriguardConfig;
import io.veriguard.database.model.*;
import io.veriguard.expectation.ExpectationType;
import io.veriguard.notification.model.NotificationEvent;
import io.veriguard.notification.model.NotificationEventType;
import io.veriguard.rest.exercise.service.AttackChainRunService;
import io.veriguard.rest.scenario.service.AttackChainStatisticService;
import io.veriguard.service.NotificationRuleService;
import io.veriguard.service.scenario.AttackChainService;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttackChainNotificationEventHandler implements NotificationEventHandler {
  private final VeriguardConfig veriguardConfig;
  private final AttackChainRunService attackChainRunService;
  private final AttackChainService attackChainService;
  private final NotificationRuleService notificationRuleService;

  @Override
  public void handle(NotificationEvent event) {
    if (NotificationEventType.SIMULATION_COMPLETED.equals(event.getEventType())) {
      // get the last 2 simulations
      AttackChainRun lastSimulation =
          attackChainRunService.previousFinishedSimulation(event.getResourceId(), event.getTimestamp());
      if (lastSimulation == null || lastSimulation.getEnd().isEmpty()) {
        return;
      }
      AttackChainRun secondLastSimulation =
          attackChainRunService.previousFinishedSimulation(
              event.getResourceId(), lastSimulation.getEnd().get());
      if (secondLastSimulation == null) {
        return;
      }

      // create map with the results to facilitate the computing of the score difference
      // TODO update attackChainRunService to return a map with result
      Map<ExpectationType, ExpectationResultsByType> lastSimulationResultsMap =
          attackChainRunService.getGlobalResults(lastSimulation.getId()).stream()
              .collect(Collectors.toMap(ExpectationResultsByType::type, Function.identity()));
      Map<ExpectationType, ExpectationResultsByType> secondLastSimulationResultsMap =
          attackChainRunService.getGlobalResults(secondLastSimulation.getId()).stream()
              .collect(Collectors.toMap(ExpectationResultsByType::type, Function.identity()));

      if (attackChainRunService.isThereAScoreDegradation(
          lastSimulationResultsMap, secondLastSimulationResultsMap)) {

        // notify
        notificationRuleService.activateNotificationRules(
            lastSimulation.getAttackChain().getId(),
            NotificationRuleTrigger.DIFFERENCE,
            buildAttackChainNotificationData(
                lastSimulation.getAttackChain().getId(),
                lastSimulation,
                secondLastSimulation,
                lastSimulationResultsMap,
                secondLastSimulationResultsMap));
      }
    }
  }

  private Map<String, String> buildAttackChainNotificationData(
      @NotNull final String attackChainId,
      @NotNull final AttackChainRun lastSimulation,
      @NotNull final AttackChainRun secondLastSimulation,
      @NotNull final Map<ExpectationType, ExpectationResultsByType> lastSimulationResultsMap,
      @NotNull
          final Map<ExpectationType, ExpectationResultsByType> secondLastSimulationResultsMap) {
    // TODO handle date format dynamically
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault());

    AttackChain attackChain = attackChainService.attackChain(attackChainId);
    String url = veriguardConfig.getBaseUrl();
    float lastSimulationPrevScore =
        AttackChainStatisticService.getRoundedPercentage(
            lastSimulationResultsMap.get(ExpectationType.PREVENTION));
    float lastSimulationDetectScore =
        AttackChainStatisticService.getRoundedPercentage(
            lastSimulationResultsMap.get(ExpectationType.DETECTION));
    float secondLastSimulationPrevScore =
        AttackChainStatisticService.getRoundedPercentage(
            secondLastSimulationResultsMap.get(ExpectationType.PREVENTION));
    float secondLastSimulationDetectScore =
        AttackChainStatisticService.getRoundedPercentage(
            secondLastSimulationResultsMap.get(ExpectationType.DETECTION));
    float decreasePrev = secondLastSimulationPrevScore - lastSimulationPrevScore;
    float decreaseDetect = secondLastSimulationDetectScore - lastSimulationDetectScore;

    Map<String, String> data = new HashMap<>();
    data.put("decrease_prev", Float.toString(decreasePrev));
    data.put("decrease_detect", Float.toString(decreaseDetect));
    data.put(
        "prev_simulation_date", secondLastSimulation.getEnd().map(formatter::format).orElse("NA"));
    data.put("prev_percentage_detection", Float.toString(secondLastSimulationDetectScore));
    data.put("prev_percentage_prevention", Float.toString(secondLastSimulationPrevScore));
    data.put("new_simulation_date", lastSimulation.getEnd().map(formatter::format).orElse("NA"));
    data.put("new_percentage_detection", Float.toString(lastSimulationDetectScore));
    data.put("new_percentage_prevention", Float.toString(lastSimulationPrevScore));
    data.put("scenarioLink", String.format("%s/admin/scenarios/%s", url, attackChainId));
    data.put("instanceLink", url);
    data.put("scenario_name", attackChain.getName());
    return data;
  }
}
