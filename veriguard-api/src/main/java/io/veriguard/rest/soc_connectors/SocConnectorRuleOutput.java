package io.veriguard.rest.soc_connectors;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SOC connector 内单条 correlation rule 描述 DTO（spec §6.3.6 / §4.2）.
 *
 * <p>给前端 {@code SocConnectorRulePicker} 下拉填数据，与 {@link
 * io.veriguard.attackchain.soc.AvailableRule} 对齐但走 snake_case 线协议。
 */
public record SocConnectorRuleOutput(
    @JsonProperty("rule_id") String ruleId,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("description") String description,
    @JsonProperty("category") String category) {}
