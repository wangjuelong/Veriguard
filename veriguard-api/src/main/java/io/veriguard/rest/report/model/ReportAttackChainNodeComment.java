package io.veriguard.rest.report.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.helper.MonoIdSerializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
@Table(name = "report_inject_comment")
public class ReportAttackChainNodeComment {
  @EmbeddedId @JsonIgnore
  private ReportAttackChainNodeCommentId compositeId = new ReportAttackChainNodeCommentId();

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("attackChainNodeId")
  @JoinColumn(name = "inject_id")
  @JsonIgnore // Ignore AttackChainNode object in JSON
  @JsonSerialize(using = MonoIdSerializer.class)
  @NotNull
  @Schema(type = "string")
  private AttackChainNode attackChainNode;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("reportId")
  @JoinColumn(name = "report_id")
  @JsonIgnore // Ignore AttackChainNode object in JSON
  @JsonSerialize(using = MonoIdSerializer.class)
  @NotNull
  @Schema(type = "string")
  private Report report;

  @Column(name = "comment")
  @JsonProperty("report_node_comment")
  private String comment;

  @JsonProperty("node_id")
  public String getAttackChainNodeId() {
    return attackChainNode != null
        ? attackChainNode.getId()
        : null; // Customize serialization to return ID
  }

  @JsonProperty("report_id")
  public String getReportId() {
    return report != null ? report.getId() : null; // Customize serialization to return ID
  }
}
