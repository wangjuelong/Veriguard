package io.veriguard.rest.challenge;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.veriguard.rest.challenge.ChallengeHelper.resolveChallengeIds;
import static io.veriguard.rest.scenario.ScenarioApi.SCENARIO_URI;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.Inject;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.repository.ChallengeRepository;
import io.veriguard.database.repository.InjectRepository;
import io.veriguard.database.specification.InjectSpecification;
import io.veriguard.rest.challenge.output.ChallengeOutput;
import io.veriguard.rest.helper.RestBehavior;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScenarioChallengeApi extends RestBehavior {

  private final InjectRepository injectRepository;
  private final ChallengeRepository challengeRepository;

  @GetMapping(SCENARIO_URI + "/{scenarioId}/challenges")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Iterable<ChallengeOutput> scenarioChallenges(
      @PathVariable @NotBlank final String scenarioId) {
    List<Inject> injects =
        this.injectRepository.findAll(
            InjectSpecification.fromScenario(scenarioId)
                .and(InjectSpecification.fromContract(CHALLENGE_PUBLISH)));
    List<String> challengeIds = resolveChallengeIds(injects, this.mapper);
    return fromIterable(this.challengeRepository.findAllById(challengeIds)).stream()
        .map(ChallengeOutput::from)
        .peek(c -> c.setScenarioIds(List.of(scenarioId)))
        .toList();
  }
}
