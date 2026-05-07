package io.veriguard.database.model;

import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AttackChainRunTeamUserId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String attackChainRunId;
  private String teamId;
  private String userId;

  public AttackChainRunTeamUserId() {
    // Default constructor
  }

  public String getAttackChainRunId() {
    return attackChainRunId;
  }

  public void setAttackChainRunId(String attackChainRunId) {
    this.attackChainRunId = attackChainRunId;
  }

  public String getTeamId() {
    return teamId;
  }

  public void setTeamId(String teamId) {
    this.teamId = teamId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AttackChainRunTeamUserId that = (AttackChainRunTeamUserId) o;
    return attackChainRunId.equals(that.attackChainRunId)
        && teamId.equals(that.teamId)
        && userId.equals(that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attackChainRunId, teamId, userId);
  }
}
