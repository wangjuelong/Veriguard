package io.veriguard.rest.attack_chain_node.service;

import io.veriguard.database.model.AttackChainNode;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class AttackChainNodeAuthorisationResult {
  private List<AttackChainNode> authorised = new ArrayList<>();
  private List<AttackChainNode> unauthorised = new ArrayList<>();

  public void addAuthorised(AttackChainNode attackChainNode) {
    authorised.add(attackChainNode);
  }

  public void addUnauthorised(AttackChainNode attackChainNode) {
    unauthorised.add(attackChainNode);
  }
}
