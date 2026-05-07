package io.veriguard.rest.security;

import static io.veriguard.database.model.User.ROLE_ADMIN;

import io.veriguard.config.VeriguardPrincipal;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.User;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.database.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityExpression extends SecurityExpressionRoot
    implements MethodSecurityExpressionOperations {

  private final UserRepository userRepository;
  private final AttackChainRunRepository attackChainRunRepository;

  private Object filterObject;
  private Object returnObject;

  // region utils
  public SecurityExpression(
      Authentication authentication,
      final UserRepository userRepository,
      final AttackChainRunRepository attackChainRunRepository) {
    super(authentication);
    this.attackChainRunRepository = attackChainRunRepository;
    this.userRepository = userRepository;
  }

  private VeriguardPrincipal getUser() {
    return (VeriguardPrincipal)
        SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  public boolean isAdmin() {
    return isUserHasBypass();
  }

  private boolean isUserHasBypass() {
    VeriguardPrincipal principal = getUser();
    return principal != null
        && principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(s -> s.equals(ROLE_ADMIN));
  }

  // endregion

  // region attackChainRun annotations

  /**
   * Check that a user is a planner for a given attackChainRun
   *
   * @deprecated use isSimulationPlanner instead
   * @param attackChainRunId the exercice to search
   * @return true if the user is a planner for given attackChainRun
   */
  @Deprecated(since = "1.11.0", forRemoval = true)
  public boolean isAttackChainRunPlanner(String attackChainRunId) {
    return isSimulationPlanner(attackChainRunId);
  }

  /**
   * Check that a user is a planner for a given simulation
   *
   * @param simulationId the simulation to check
   * @return true if the user is a planner for given simulation
   */
  public boolean isSimulationPlanner(String simulationId) {
    if (isUserHasBypass()) {
      return true;
    }
    AttackChainRun attackChainRun = attackChainRunRepository.findById(simulationId).orElseThrow();
    List<User> planners = attackChainRun.getPlanners();
    Optional<User> planner =
        planners.stream().filter(user -> user.getId().equals(getUser().getId())).findAny();
    return planner.isPresent();
  }

  @Deprecated(since = "1.12.0", forRemoval = true)
  public boolean isAttackChainRunObserver(String attackChainRunId) {
    return isSimulationObserver(attackChainRunId);
  }

  /**
   * Check that a user is a observer for a given simulation
   *
   * @param simulationId the simulation to check
   * @return true if the user is an observer for given simulation
   */
  public boolean isSimulationObserver(String simulationId) {
    if (isUserHasBypass()) {
      return true;
    }
    AttackChainRun attackChainRun = attackChainRunRepository.findById(simulationId).orElseThrow();
    List<User> observers = attackChainRun.getObservers();
    Optional<User> observer =
        observers.stream().filter(user -> user.getId().equals(getUser().getId())).findAny();
    return observer.isPresent();
  }

  public boolean isAttackChainRunPlayer(String attackChainRunId) {
    if (isUserHasBypass()) {
      return true;
    }
    AttackChainRun attackChainRun =
        attackChainRunRepository.findById(attackChainRunId).orElseThrow();
    List<User> players = attackChainRun.getUsers();
    Optional<User> player =
        players.stream().filter(user -> user.getId().equals(getUser().getId())).findAny();
    return player.isPresent();
  }

  // endregion

  // region user annotations
  public boolean isPlanner() {
    if (isUserHasBypass()) {
      return true;
    }
    User user = this.userRepository.findById(getUser().getId()).orElseThrow();
    return user.isPlanner();
  }

  public boolean isObserver() {
    if (isUserHasBypass()) {
      return true;
    }
    User user = userRepository.findById(getUser().getId()).orElseThrow();
    return user.isObserver();
  }

  public boolean isPlayer() {
    User user = userRepository.findById(getUser().getId()).orElseThrow();
    return user.isPlayer();
  }

  // endregion

  // region setters
  @Override
  public Object getFilterObject() {
    return this.filterObject;
  }

  @Override
  public void setFilterObject(Object obj) {
    this.filterObject = obj;
  }

  @Override
  public Object getReturnObject() {
    return this.returnObject;
  }

  @Override
  public void setReturnObject(Object obj) {
    this.returnObject = obj;
  }

  @Override
  public Object getThis() {
    return this;
  }
  // endregion
}
