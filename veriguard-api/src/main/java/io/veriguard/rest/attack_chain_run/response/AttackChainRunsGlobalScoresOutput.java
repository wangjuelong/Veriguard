package io.veriguard.rest.attack_chain_run.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record AttackChainRunsGlobalScoresOutput(
    @JsonProperty("global_scores_by_attack_chain_run_ids") @NotNull
        Map<String, List<ExpectationResultsByType>> globalScoresByAttackChainRunIds) {}
