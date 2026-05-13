package io.veriguard.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.helper.MonoIdSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * 稳定性趋势快照（PR C5 / 招标 §3.3 ★1 边界设备 + §4.2 ★3 流量设备）.
 *
 * <p>每个 {@code repeat_count > 1} 的 AttackChainNode 在终态时（FINALIZE / FINALIZE_BLOCKED）产出一行，
 * 用于绘制"任务 × 时间"的稳定性趋势折线图，悬停展示 N + 命中率 + 命中明细.
 *
 * <p>命中语义：PREVENTION expectation = SUCCESS 即"攻击被防御工具识别 / 拦截"——这与
 * {@link io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS#SUCCESS} 在 PREVENTION
 * 维度上的现有约定一致.
 *
 * <p>{@code hit_rate = hit_count / total_count}，在 Java 层计算并落库，便于 SQL 直接按 device_id 聚合.
 */
@Setter
@Getter
@Entity
@Table(name = "stability_trend_snapshots")
@EntityListeners(ModelBaseListener.class)
public class StabilityTrendSnapshot implements Base {

  public static final String ID_COLUMN_NAME = "snapshot_id";

  @Id
  @Column(name = ID_COLUMN_NAME)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("snapshot_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "snapshot_run_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("snapshot_run")
  @Schema(type = "string")
  @Queryable(filterable = true)
  private AttackChainRun attackChainRun;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "snapshot_node_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("snapshot_node")
  @Schema(type = "string")
  @Queryable(filterable = true)
  private AttackChainNode attackChainNode;

  /**
   * 关联设备 ID（asset_id 或外部设备 ID）. 当节点未绑定 Asset 时为 null. 招标 §3.3 边界设备 / §4.2 流量设备 —— 不强约束 FK 便于未来对接外部
   * 设备元数据系统.
   */
  @Column(name = "snapshot_device_id")
  @JsonProperty("snapshot_device_id")
  @Queryable(filterable = true)
  private String deviceId;

  /** 基线 ID. 招标尚未引入"基线"概念，预留 NULL 字段以支持未来"基线漂移检测". */
  @Column(name = "snapshot_baseline_id")
  @JsonProperty("snapshot_baseline_id")
  @Queryable(filterable = true)
  private String baselineId;

  /** 命中次数 —— 累计 PREVENTION expectation = SUCCESS 的次数. */
  @Column(name = "snapshot_hit_count", nullable = false)
  @JsonProperty("snapshot_hit_count")
  @NotNull
  @Min(0)
  private int hitCount;

  /** 实际执行迭代数 —— 通常等于 repeat_count，STOP_ON_BLOCK 早停时小于 repeat_count. */
  @Column(name = "snapshot_total_count", nullable = false)
  @JsonProperty("snapshot_total_count")
  @NotNull
  @Min(1)
  @Max(100)
  private int totalCount;

  /** 命中率 = hit_count / total_count，落库时计算. */
  @Column(name = "snapshot_hit_rate", nullable = false, precision = 5, scale = 4)
  @JsonProperty("snapshot_hit_rate")
  @NotNull
  private BigDecimal hitRate;

  @Column(name = "snapshot_captured_at", nullable = false)
  @JsonProperty("snapshot_captured_at")
  @NotNull
  @Queryable(filterable = true, sortable = true)
  private Instant capturedAt = now();

  @Column(name = "snapshot_created_at")
  @JsonProperty("snapshot_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Column(name = "snapshot_updated_at")
  @JsonProperty("snapshot_updated_at")
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    if (base.getId() == null || this.getId() == null) {
      return false;
    }
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
