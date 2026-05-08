package io.veriguard.utils.helpers;

import static io.veriguard.config.SessionHelper.currentUser;

import io.veriguard.config.VeriguardPrincipal;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.GrantRepository;
import io.veriguard.database.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GrantHelper {
  @Autowired private GrantRepository grantRepository;
  @Autowired private UserRepository userRepository;

  private List<Group> getAmbientSecurityContextGroups() {
    VeriguardPrincipal principal = currentUser();
    Optional<User> user = this.userRepository.findById(principal.getId());

    if (user.isEmpty()) {
      throw new IllegalStateException("No user found");
    }

    return user.get().getGroups();
  }

  private Grant createGrantForGroup(Group group) {
    Grant grant = new Grant();
    grant.setGroup(group);
    return grant;
  }

  public void grantAttackChainRunObserver(AttackChainRun attackChainRun) {
    for (Group group : getAmbientSecurityContextGroups()) {
      Grant grant = createGrantForGroup(group);
      grant.setResourceId(attackChainRun.getId());
      grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
      grant.setName(Grant.GRANT_TYPE.OBSERVER);
      this.grantRepository.save(grant);
    }
  }

  public void grantAttackChainRunPlanner(AttackChainRun attackChainRun) {
    for (Group group : getAmbientSecurityContextGroups()) {
      Grant grant = createGrantForGroup(group);
      grant.setResourceId(attackChainRun.getId());
      grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
      grant.setName(Grant.GRANT_TYPE.PLANNER);
      this.grantRepository.save(grant);
    }
  }

  public void grantAttackChainObserver(AttackChain attackChain) {
    for (Group group : getAmbientSecurityContextGroups()) {
      Grant grant = createGrantForGroup(group);
      grant.setResourceId(attackChain.getId());
      grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
      grant.setName(Grant.GRANT_TYPE.OBSERVER);
      this.grantRepository.save(grant);
    }
  }

  public void grantAttackChainPlanner(AttackChain attackChain) {
    for (Group group : getAmbientSecurityContextGroups()) {
      Grant grant = createGrantForGroup(group);
      grant.setResourceId(attackChain.getId());
      grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
      grant.setName(Grant.GRANT_TYPE.PLANNER);
      this.grantRepository.save(grant);
    }
  }
}
