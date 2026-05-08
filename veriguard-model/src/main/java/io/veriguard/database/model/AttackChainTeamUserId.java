package io.veriguard.database.model;

import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
@Embeddable
public class AttackChainTeamUserId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String attackChainId;
  private String teamId;
  private String userId;

  public AttackChainTeamUserId() {
    // Default constructor
  }
}
