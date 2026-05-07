package io.veriguard.service.targets.search;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainNodeTarget;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.utils.NodeExpectationResultUtils;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import io.veriguard.utils.mapper.AttackChainNodeExpectationMapper;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class HelperTargetSearchAdaptor {

  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final AttackChainNodeExpectationMapper attackChainNodeExpectationMapper;

  public AttackChainNodeTarget buildTargetWithExpectations(
      AttackChainNode attackChainNode, Supplier<AttackChainNodeTarget> targetSupplier, boolean allowVulnerability) {
    AttackChainNodeTarget target = targetSupplier.get();

    List<AttackChainNodeExpectation> mergedExpectationsByAttackChainNodeAndTargetAndTargetType =
        attackChainNodeExpectationService.findMergedExpectationsByAttackChainNodeAndTargetAndTargetType(
            attackChainNode.getId(), target.getId(), target.getTargetType());

    List<ExpectationResultsByType> results =
        attackChainNodeExpectationMapper.extractExpectationResults(
            attackChainNode.getContent(),
            mergedExpectationsByAttackChainNodeAndTargetAndTargetType,
            NodeExpectationResultUtils::getScores);

    for (ExpectationResultsByType result : results) {
      switch (result.type()) {
        case DETECTION -> target.setTargetDetectionStatus(result.avgResult());
        case PREVENTION -> target.setTargetPreventionStatus(result.avgResult());
        case VULNERABILITY -> {
          if (allowVulnerability) {
            target.setTargetVulnerabilityStatus(result.avgResult());
          }
        }
        case HUMAN_RESPONSE -> target.setTargetHumanResponseStatus(result.avgResult());
      }
    }

    return target;
  }
}
