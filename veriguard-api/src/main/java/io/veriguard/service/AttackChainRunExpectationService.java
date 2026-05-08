package io.veriguard.service;

import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AttackChainRunExpectationService {

  private final AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  private final AttackChainRunRepository attackChainRunRepository;

  public List<AttackChainNodeExpectation> attackChainNodeExpectations(
      @NotBlank final String attackChainRunId) {
    AttackChainRun attackChainRun =
        this.attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Exercise not found with id: " + attackChainRunId));
    return this.attackChainNodeExpectationRepository.findAllForAttackChainRun(
        attackChainRun.getId());
  }
}
