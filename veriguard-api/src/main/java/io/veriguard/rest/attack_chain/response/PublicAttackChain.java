package io.veriguard.rest.attack_chain.response;

import io.veriguard.database.model.AttackChain;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublicAttackChain {

  private String id;
  private String name;
  private String description;

  public PublicAttackChain(AttackChain attackChain) {
    this.id = attackChain.getId();
    this.name = attackChain.getName();
    this.description = attackChain.getDescription();
  }
}
