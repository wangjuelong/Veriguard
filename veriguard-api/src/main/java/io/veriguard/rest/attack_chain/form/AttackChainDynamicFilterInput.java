package io.veriguard.rest.attack_chain.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.Filters.FilterGroup;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/attack_chains/{id}/dynamic_filter 输入 DTO（PRD §2.3 第 4 行）.
 *
 * <p>FilterGroup 序列化由 Jackson 处理，与 attack_chains.dynamic_filter JSONB 列同字段名.
 */
public record AttackChainDynamicFilterInput(
    @JsonProperty("dynamic_filter") @NotNull FilterGroup dynamicFilter) {}
