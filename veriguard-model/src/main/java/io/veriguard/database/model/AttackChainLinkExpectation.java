package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/**
 * 链路级 SOC DETECTION 期望（PRD §2.4 / spec §2.2.6）.
 *
 * <p>每个 {@link SocCorrelationRuleRef}（来自 {@link AttackChain#getSocCorrelationRules()}）在 run 启动时
 * 实例化为一条本表记录。链路结束、所有节点 SETTLED 后，{@code LinkExpectationService} 调用 {@code
 * SocAlertConnector.queryCorrelationRule} 查询，把命中写为 {@link LinkExpectationTrace}，按 score 累加 + 与
 * expectedScore 比较得出最终 {@link #status}。
 */
@Entity
@Table(name = "attack_chain_link_expectations")
@Getter
@Setter
public class AttackChainLinkExpectation {

  @Id
  @Column(name = "link_expectation_id")
  @GeneratedValue
  @UuidGenerator
  @JsonProperty("link_expectation_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attack_chain_run_id", nullable = false)
  @NotNull
  private AttackChainRun attackChainRun;

  @Type(JsonBinaryType.class)
  @Column(name = "soc_rule_ref", columnDefinition = "jsonb", nullable = false)
  @JsonProperty("soc_rule_ref")
  @NotNull
  private SocCorrelationRuleRef socRuleRef;

  @Column(name = "score", nullable = false)
  @JsonProperty("score")
  private int score = 0;

  @Column(name = "expected_score", nullable = false)
  @JsonProperty("expected_score")
  private int expectedScore = 100;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("status")
  @NotNull
  private LinkExpectationStatus status = LinkExpectationStatus.PENDING;

  @Column(name = "expiration_time", nullable = false)
  @JsonProperty("expiration_time")
  @NotNull
  private Instant expirationTime;

  @Column(name = "created_at", nullable = false, updatable = false)
  @JsonProperty("created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @JsonProperty("updated_at")
  private Instant updatedAt = Instant.now();

  @OneToMany(
      mappedBy = "linkExpectation",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @JsonProperty("traces")
  private List<LinkExpectationTrace> traces = new ArrayList<>();
}
