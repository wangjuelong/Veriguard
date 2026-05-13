package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.Data;

/**
 * 攻击编排节点级设置输入（PRD §2.4 / spec §6.3.1 NodeEditDrawer）.
 *
 * <p>对应前端 NodeEditDrawer 提交体；和既有 {@link AttackChainNodeInput}（节点全量字段）
 * 区分开 —— 后者承载 inject_content / target / contract 等执行性字段，本类专攻 V3
 * 攻击编排扩展：节点级默认参数集覆盖 + 重复次数 + 重复间隔。
 *
 * <p>字段名与 {@link io.veriguard.database.model.AttackChainNode} 实体的同名属性 1:1 对齐，
 * {@link io.veriguard.database.model.Base#setUpdateAttributes(Object)} 通过
 * Spring {@code BeanUtils.copyProperties} 直接拷贝；title / description 留给原 PUT
 * 节点端点处理，避免把基础信息和编排策略混到一个端点上。
 */
@Data
public class AttackChainNodeSettingsInput {

  @JsonProperty("node_title")
  private String title;

  @Nullable
  @JsonProperty("node_description")
  private String description;

  @Nullable
  @JsonProperty("node_validation_parameter_set_id")
  private UUID validationParameterSetId;

  /**
   * 重复执行次数（PR C5 / 招标 §3.3 ★1 + §4.2 ★3 稳定性引擎）.
   *
   * <p>N=1 → 普通验证；2 ≤ N ≤ 100 → 启用稳定性引擎，节点终态时产出 stability_trend_snapshot 行.
   */
  @Min(value = 1L)
  @Max(value = 100L)
  @JsonProperty("node_repeat_count")
  private int repeatCount = 1;

  @Min(value = 0L)
  @JsonProperty("node_repeat_interval_seconds")
  private long repeatIntervalSeconds = 0L;
}
