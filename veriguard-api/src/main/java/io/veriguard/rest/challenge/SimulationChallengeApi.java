package io.veriguard.rest.challenge;

import static io.veriguard.config.VeriguardAnonymous.ANONYMOUS;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.veriguard.rest.challenge.ChallengeHelper.resolveChallengeIds;
import static io.veriguard.rest.exercise.ExerciseApi.EXERCISE_URI;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.ChallengeRepository;
import io.veriguard.database.repository.ExerciseRepository;
import io.veriguard.database.repository.InjectRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.database.specification.InjectSpecification;
import io.veriguard.rest.challenge.form.ChallengeTryInput;
import io.veriguard.rest.challenge.output.ChallengeOutput;
import io.veriguard.rest.challenge.response.ChallengeInformation;
import io.veriguard.rest.challenge.response.SimulationChallengesReader;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.InputValidationException;
import io.veriguard.rest.exercise.service.ExerciseService;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.ChallengeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SimulationChallengeApi extends RestBehavior {

  private final InjectRepository injectRepository;
  private final ChallengeRepository challengeRepository;
  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;

  private final ChallengeService challengeService;
  private final ExerciseService exerciseService;

  @GetMapping(EXERCISE_URI + "/{exerciseId}/challenges")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Iterable<ChallengeOutput> exerciseChallenges(
      @PathVariable @NotBlank final String exerciseId) {
    List<Inject> injects =
        this.injectRepository.findAll(
            InjectSpecification.fromSimulation(exerciseId)
                .and(InjectSpecification.fromContract(CHALLENGE_PUBLISH)));
    List<String> challengeIds = resolveChallengeIds(injects, this.mapper);
    return fromIterable(this.challengeRepository.findAllById(challengeIds)).stream()
        .map(ChallengeOutput::from)
        .peek(c -> c.setExerciseIds(List.of(exerciseId)))
        .toList();
  }

  @PostMapping("/api/player/challenges/{exerciseId}/{challengeId}/validate")
  @RBAC(skipRBAC = true)
  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  public SimulationChallengesReader validateChallenge(
      @PathVariable String exerciseId,
      @PathVariable String challengeId,
      @Valid @RequestBody ChallengeTryInput input,
      @RequestParam Optional<String> userId)
      throws InputValidationException {
    validateUUID(exerciseId);
    validateUUID(challengeId);

    final User user = impersonateUser(userRepository, userId);
    if (user.getId().equals(ANONYMOUS)) {
      throw new UnsupportedOperationException("User must be logged or dynamic player is required");
    }
    return challengeService.validateChallenge(exerciseId, challengeId, input, user);
  }

  @GetMapping("/api/player/simulations/{simulationId}/documents")
  @RBAC(skipRBAC = true)
  public List<Document> playerDocuments(
      @PathVariable String simulationId, @RequestParam Optional<String> userId) {
    Optional<Exercise> exerciseOpt = this.exerciseRepository.findById(simulationId);
    final User user = impersonateUser(userRepository, userId);
    if (user.getId().equals(ANONYMOUS)) {
      throw new UnsupportedOperationException("User must be logged or dynamic player is required");
    }
    if (exerciseOpt.isPresent()) {
      if (!exerciseOpt.get().isUserHasAccess(user)
          && !exerciseOpt.get().getUsers().contains(user)) {
        throw new UnsupportedOperationException("The given player is not in this exercise");
      }
      return exerciseService.getExercisePlayerDocuments(exerciseOpt.get());
    } else {
      throw new IllegalArgumentException("Simulation ID not found");
    }
  }

  @GetMapping("/api/observer/simulations/{simulationId}/challenges")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public SimulationChallengesReader observerChallenges(@PathVariable String simulationId) {
    Exercise exercise =
        exerciseRepository.findById(simulationId).orElseThrow(ElementNotFoundException::new);
    SimulationChallengesReader simulationChallengesReader =
        new SimulationChallengesReader(exercise);
    Iterable<Challenge> challenges = challengeService.getExerciseChallenges(simulationId);
    simulationChallengesReader.setExerciseChallenges(
        fromIterable(challenges).stream()
            .map(challenge -> new ChallengeInformation(challenge, null, 0))
            .toList());
    return simulationChallengesReader;
  }

  @GetMapping("/api/player/simulations/{simulationId}/challenges")
  @RBAC(skipRBAC = true)
  public SimulationChallengesReader playerChallenges(
      @PathVariable String simulationId, @RequestParam Optional<String> userId) {
    final User user = impersonateUser(userRepository, userId);
    if (user.getId().equals(ANONYMOUS)) {
      throw new UnsupportedOperationException("User must be logged or dynamic player is required");
    }
    return challengeService.playerChallenges(simulationId, user);
  }
}
