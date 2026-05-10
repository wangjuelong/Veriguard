package io.veriguard.rest.attack_chain.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.ExecutionMode;
import io.veriguard.database.model.SocCorrelationRuleRef;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * 攻击编排链路级设置输入（PRD §2.4 / spec §6.3.4）.
 *
 * <p>对应 AttackChainSettingsDrawer 提交体；与 {@link AttackChainInput} 区分开 ——
 * 后者承载链路基本信息（名称 / 描述 / 标签 / 邮件模板 等），本类专攻 V3 攻击编排
 * 三个字段：执行模式、默认验证参数集、链路级 SOC 关联规则。
 *
 * <p>字段名与 {@link io.veriguard.database.model.AttackChain} 实体的同名属性 1:1 对齐，
 * 便于 {@link io.veriguard.database.model.Base#setUpdateAttributes(Object)} 通过
 * Spring {@code BeanUtils.copyProperties} 直接拷贝。
 */
@Data
public class AttackChainSettingsInput {

  @NotNull
  @JsonProperty("attack_chain_execution_mode")
  private ExecutionMode executionMode = ExecutionMode.STOP_ON_BLOCK;

  @Nullable
  @JsonProperty("attack_chain_validation_parameter_set_id")
  private UUID validationParameterSetId;

  @JsonProperty("attack_chain_soc_correlation_rules")
  private List<SocCorrelationRuleRef> socCorrelationRules = new ArrayList<>();
}
