package io.veriguard.collectors.expectations_expiration_manager.service;

import static io.veriguard.collectors.expectations_expiration_manager.utils.ExpectationUtils.*;
import static io.veriguard.service.AttackChainNodeExpectationUtils.FAILED_SCORE_VALUE;
import static io.veriguard.utils.ExpectationUtils.HUMAN_EXPECTATION;
import static io.veriguard.utils.inject_expectation_result.ExpectationResultBuilder.expireEmptyResults;

import io.veriguard.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.veriguard.database.model.Collector;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.expectation.ExpectationType;
import io.veriguard.rest.collector.service.CollectorService;
import io.veriguard.rest.inject.form.AttackChainNodeExpectationUpdateInput;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.utils.ExpectationUtils;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class ExpectationsExpirationManagerService {

  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final ExpectationsExpirationManagerConfig config;
  private final CollectorService collectorService;

  public static final String EXPIRED = "Expired";

  @Transactional(rollbackFor = Exception.class)
  public void computeExpectations() {
    Collector collector = this.collectorService.collector(config.getId());
    // Get all the expectations we will update (max of 10k)
    Page<AttackChainNodeExpectation> expectations = this.attackChainNodeExpectationService.expectationsNotFill();
    // We're making a loop on 10 calls max to avoid staying in an infinite loop
    for (int i = 1; i < 10 && expectations.getTotalElements() > 0; i++) {
      List<AttackChainNodeExpectation> updated = new ArrayList<>();
      this.processAgentExpectations(expectations.toList(), collector);
      this.processRemainingExpectations(expectations.toList(), collector, updated);

      // Updating all the expectations following the process
      this.attackChainNodeExpectationService.updateAll(updated);

      // Get the next expectations that need to be processed (still max of 10k)
      expectations = this.attackChainNodeExpectationService.expectationsNotFill();
    }
  }

  // -- PRIVATE --
  private void processAgentExpectations(
      @NotNull final List<AttackChainNodeExpectation> expectations, @NotNull final Collector collector) {
    List<AttackChainNodeExpectation> expectationAgents =
        expectations.stream().filter(ExpectationUtils::isAgentExpectation).toList();
    expectationAgents.forEach(
        expectation -> {
          if (isExpired(expectation)) {
            AttackChainNodeExpectationUpdateInput input = new AttackChainNodeExpectationUpdateInput();
            if (ExpectationType.VULNERABILITY.toString().equals(expectation.getType().toString())) {
              input.setIsSuccess(true);
              input.setResult(computeSuccessMessage(expectation.getType()));
              expireEmptyResults(expectation.getResults(), expectation.getExpectedScore(), EXPIRED);
            } else {
              input.setIsSuccess(false);
              input.setResult(computeFailedMessage(expectation.getType()));
              expireEmptyResults(expectation.getResults(), FAILED_SCORE_VALUE, EXPIRED);
            }
            this.attackChainNodeExpectationService.computeTechnicalExpectation(
                expectation, collector, input, true);
          }
        });
  }

  private void processRemainingExpectations(
      @NotNull final List<AttackChainNodeExpectation> expectations,
      @NotNull final Collector collector,
      @NotNull final List<AttackChainNodeExpectation> updated) {
    List<AttackChainNodeExpectation> remainingExpectations =
        expectations.stream().filter(exp -> exp.getScore() == null).toList();
    remainingExpectations.forEach(
        expectation -> {
          if (isExpired(expectation)) {
            AttackChainNodeExpectationUpdateInput input = new AttackChainNodeExpectationUpdateInput();
            input.setIsSuccess(false);
            input.setResult(computeFailedMessage(expectation.getType()));
            expireEmptyResults(expectation.getResults(), FAILED_SCORE_VALUE, EXPIRED);
            if (HUMAN_EXPECTATION.contains(expectation.getType())) {
              updated.add(
                  attackChainNodeExpectationService.computeAttackChainNodeExpectationForHumanResponse(
                      expectation, input, collector));
            } else {
              updated.add(
                  attackChainNodeExpectationService.computeAttackChainNodeExpectationForAgentOrAssetAgentless(
                      expectation, input, collector));
            }
          }
        });
  }
}
