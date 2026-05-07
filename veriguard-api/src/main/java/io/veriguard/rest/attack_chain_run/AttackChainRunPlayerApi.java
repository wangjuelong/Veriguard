package io.veriguard.rest.attack_chain_run;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.rest.attack_chain_run.response.PublicAttackChainRun;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttackChainRunPlayerApi extends RestBehavior {

  public static final String EXERCISE_URI = "/api/player/exercises";

  private final UserRepository userRepository;
  private final AttackChainRunRepository attackChainRunRepository;

  @GetMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(skipRBAC = true)
  public PublicAttackChainRun playerAttackChainRun(
      @PathVariable String attackChainRunId, @RequestParam Optional<String> userId) {
    impersonateUser(this.userRepository, userId);
    AttackChainRun attackChainRun =
        this.attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    return new PublicAttackChainRun(attackChainRun);
  }
}
