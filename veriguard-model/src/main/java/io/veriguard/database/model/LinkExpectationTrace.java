package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/**
 * SOC correlation rule 命中追溯（PRD §2.4 / spec §2.2.6）.
 *
 * <p>每条对应 SocAlertConnector.queryCorrelationRule 返回的一条 {@code CorrelationMatch}；写入后累加 {@link
 * AttackChainLinkExpectation#getScore()}.
 */
@Entity
@Table(name = "link_expectation_traces")
@Getter
@Setter
public class LinkExpectationTrace {

  @Id
  @Column(name = "trace_id")
  @GeneratedValue
  @UuidGenerator
  @JsonProperty("trace_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "link_expectation_id", nullable = false)
  @NotNull
  private AttackChainLinkExpectation linkExpectation;

  @Column(name = "incident_id")
  @JsonProperty("incident_id")
  private String incidentId;

  @Column(name = "correlation_rule_name")
  @JsonProperty("correlation_rule_name")
  private String correlationRuleName;

  @Column(name = "triggered_at", nullable = false)
  @JsonProperty("triggered_at")
  @NotNull
  private Instant triggeredAt;

  @Column(name = "score_delta", nullable = false)
  @JsonProperty("score_delta")
  private int scoreDelta;

  @Type(JsonBinaryType.class)
  @Column(name = "raw_payload", columnDefinition = "jsonb")
  @JsonProperty("raw_payload")
  private Map<String, Object> rawPayload;

  @Column(name = "created_at", nullable = false, updatable = false)
  @JsonProperty("created_at")
  private Instant createdAt = Instant.now();
}
