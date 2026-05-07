package io.veriguard.rest.exercise.response;

import io.veriguard.database.model.AttackChainRun;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublicAttackChainRun {

  private String id;
  private String name;
  private String description;

  public PublicAttackChainRun(AttackChainRun attackChainRun) {
    this.id = attackChainRun.getId();
    this.name = attackChainRun.getName();
    this.description = attackChainRun.getDescription();
  }
}
