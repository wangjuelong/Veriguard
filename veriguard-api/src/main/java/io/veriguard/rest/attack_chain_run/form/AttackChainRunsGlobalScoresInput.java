package io.veriguard.rest.attack_chain_run.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AttackChainRunsGlobalScoresInput(
    @JsonProperty("attack_chain_run_ids") @NotNull List<String> attackChainRunIds) {}
