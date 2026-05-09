package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.raw.RawAttackChainRunTeamUser;
import io.veriguard.helper.MonoIdSerializer;
import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "attack_chain_runs_teams_users")
public class AttackChainRunTeamUser {
  @EmbeddedId @JsonIgnore
  private AttackChainRunTeamUserId compositeId = new AttackChainRunTeamUserId();

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("attackChainRunId")
  @JoinColumn(name = "run_id")
  @JsonProperty("attack_chain_run_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private AttackChainRun attackChainRun;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("teamId")
  @JoinColumn(name = "team_id")
  @JsonProperty("team_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Team team;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("userId")
  @JoinColumn(name = "user_id")
  @JsonProperty("user_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private User user;

  public AttackChainRunTeamUserId getCompositeId() {
    return compositeId;
  }

  public void setCompositeId(AttackChainRunTeamUserId compositeId) {
    this.compositeId = compositeId;
  }

  public AttackChainRun getAttackChainRun() {
    return attackChainRun;
  }

  public void setAttackChainRun(AttackChainRun attackChainRun) {
    this.attackChainRun = attackChainRun;
  }

  public Team getTeam() {
    return team;
  }

  public void setTeam(Team team) {
    this.team = team;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AttackChainRunTeamUser that = (AttackChainRunTeamUser) o;
    return compositeId.equals(that.compositeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(compositeId);
  }

  public static AttackChainRunTeamUser fromRawAttackChainRunTeamUser(
      RawAttackChainRunTeamUser rawAttackChainRunTeamUser) {
    AttackChainRunTeamUser attackChainRunTeamUser = new AttackChainRunTeamUser();
    attackChainRunTeamUser.setTeam(new Team());
    attackChainRunTeamUser.getTeam().setId(rawAttackChainRunTeamUser.getTeam_id());
    attackChainRunTeamUser.setAttackChainRun(new AttackChainRun());
    attackChainRunTeamUser.getAttackChainRun().setId(rawAttackChainRunTeamUser.getExercise_id());
    attackChainRunTeamUser.setUser(new User());
    attackChainRunTeamUser.getUser().setId(rawAttackChainRunTeamUser.getUser_id());
    attackChainRunTeamUser.setCompositeId(new AttackChainRunTeamUserId());
    attackChainRunTeamUser
        .getCompositeId()
        .setAttackChainRunId(rawAttackChainRunTeamUser.getExercise_id());
    attackChainRunTeamUser.getCompositeId().setTeamId(rawAttackChainRunTeamUser.getTeam_id());
    attackChainRunTeamUser.getCompositeId().setUserId(rawAttackChainRunTeamUser.getUser_id());
    return attackChainRunTeamUser;
  }
}
