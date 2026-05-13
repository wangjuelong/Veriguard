package io.veriguard.database.model.combination;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.database.model.Base;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import static java.time.Instant.now;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 攻击组合聚类结果 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR D3.
 *
 * <p>每行 = 一次 (run × cluster_dim × cluster_key) 的聚类汇总. 聚类对象仅
 * {@code hit_state = miss}（未防护）的 result 行；hit / timeout / failed 不入 miss_count 但参与
 * total_in_cluster.
 *
 * <p>对应 Flyway V13 表 {@code attack_combination_clusters}.
 */
@Getter
@Setter
@Entity
@Table(name = "attack_combination_clusters")
@EntityListeners(ModelBaseListener.class)
public class AttackCombinationCluster implements Base {

  @Id
  @Column(name = "attack_combination_cluster_id")
  @JsonProperty("attack_combination_cluster_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_cluster_run_id")
  @JsonProperty("attack_combination_cluster_run_id")
  @NotBlank
  private String runId;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_cluster_dim")
  @Enumerated(EnumType.STRING)
  @JsonProperty("attack_combination_cluster_dim")
  @NotNull
  private AttackCombinationClusterDim clusterDim;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_cluster_key")
  @JsonProperty("attack_combination_cluster_key")
  @NotBlank
  private String clusterKey;

  @Column(name = "attack_combination_cluster_label")
  @JsonProperty("attack_combination_cluster_label")
  private String clusterLabel;

  @Queryable(sortable = true)
  @Column(name = "attack_combination_cluster_miss_count")
  @JsonProperty("attack_combination_cluster_miss_count")
  private int missCount;

  @Column(name = "attack_combination_cluster_total_in_cluster")
  @JsonProperty("attack_combination_cluster_total_in_cluster")
  private int totalInCluster;

  @Type(JsonType.class)
  @Column(name = "attack_combination_cluster_payload_samples", columnDefinition = "jsonb")
  @JsonProperty("attack_combination_cluster_payload_samples")
  @NotNull
  private List<String> payloadSamples = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "attack_combination_cluster_top_base_attack_types", columnDefinition = "jsonb")
  @JsonProperty("attack_combination_cluster_top_base_attack_types")
  @NotNull
  private List<Map<String, Object>> topBaseAttackTypes = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "attack_combination_cluster_top_bypass_dimensions", columnDefinition = "jsonb")
  @JsonProperty("attack_combination_cluster_top_bypass_dimensions")
  @NotNull
  private List<Map<String, Object>> topBypassDimensions = new ArrayList<>();

  @Column(name = "attack_combination_cluster_computed_at")
  @JsonProperty("attack_combination_cluster_computed_at")
  @NotNull
  private Instant computedAt = now();

  @CreationTimestamp
  @Column(name = "attack_combination_cluster_created_at")
  @JsonProperty("attack_combination_cluster_created_at")
  @NotNull
  private Instant createdAt = now();

  @UpdateTimestamp
  @Column(name = "attack_combination_cluster_updated_at")
  @JsonProperty("attack_combination_cluster_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
