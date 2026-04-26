package io.veriguard.rest.scenario;

import static io.veriguard.config.VeriguardAnonymous.ANONYMOUS;
import static io.veriguard.helper.StreamHelper.fromIterable;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.ScenarioRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.rest.challenge.response.ChallengeInformation;
import io.veriguard.rest.challenge.response.ScenarioChallengesReader;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.ChallengeService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScenarioChallengesApi extends RestBehavior {

  private final ScenarioRepository scenarioRepository;
  private final UserRepository userRepository;

  private final DocumentService documentService;
  private final ChallengeService challengeService;

  public List<Document> getScenarioPlayerDocuments(Scenario scenario) {
    List<Article> articles = scenario.getArticles();
    List<Inject> injects = scenario.getInjects();
    return documentService.getPlayerDocuments(articles, injects);
  }

  @GetMapping("/api/player/scenarios/{scenarioId}/documents")
  @RBAC(skipRBAC = true)
  public List<Document> playerDocuments(
      @PathVariable String scenarioId, @RequestParam Optional<String> userId) {
    Optional<Scenario> scenarioOpt = this.scenarioRepository.findById(scenarioId);
    final User user = impersonateUser(userRepository, userId);
    if (user.getId().equals(ANONYMOUS)) {
      throw new UnsupportedOperationException("User must be logged or dynamic player is required");
    }
    if (scenarioOpt.isPresent()) {
      if (!scenarioOpt.get().isUserHasAccess(user)
          && !scenarioOpt.get().getUsers().contains(user)) {
        throw new UnsupportedOperationException("The given player is not in this exercise");
      }
      return getScenarioPlayerDocuments(scenarioOpt.get());
    } else {
      throw new IllegalArgumentException("Scenario ID not found");
    }
  }

  @GetMapping("/api/observer/scenarios/{scenarioId}/challenges")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioChallengesReader observerChallenges(@PathVariable String scenarioId) {
    Scenario scenario =
        scenarioRepository.findById(scenarioId).orElseThrow(ElementNotFoundException::new);
    ScenarioChallengesReader scenarioChallengesReader = new ScenarioChallengesReader(scenario);
    Iterable<Challenge> challenges = challengeService.getScenarioChallenges(scenario);
    scenarioChallengesReader.setScenarioChallenges(
        fromIterable(challenges).stream()
            .map(challenge -> new ChallengeInformation(challenge, null, 0))
            .toList());
    return scenarioChallengesReader;
  }
}
