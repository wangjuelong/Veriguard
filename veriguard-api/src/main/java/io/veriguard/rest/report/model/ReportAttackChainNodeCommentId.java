package io.veriguard.rest.report.model;

import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;

@Data
@Embeddable
public class ReportAttackChainNodeCommentId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String attackChainNodeId;
  private UUID reportId;

  public ReportAttackChainNodeCommentId() {
    // Default constructor
  }
}
