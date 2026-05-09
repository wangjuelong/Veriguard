package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.helper.MonoIdSerializer;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "attack_chains_teams_users")
public class AttackChainTeamUser {

  @EmbeddedId @JsonIgnore private AttackChainTeamUserId compositeId = new AttackChainTeamUserId();

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("attackChainId")
  @JoinColumn(name = "attack_chain_id")
  @JsonProperty("attack_chain_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private AttackChain attackChain;

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
}
