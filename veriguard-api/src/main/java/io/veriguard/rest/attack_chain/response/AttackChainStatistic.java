package io.veriguard.rest.attack_chain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record AttackChainStatistic(
    @JsonProperty("simulations_results_latest") @NotNull
        SimulationsResultsLatest simulationsResultsLatest) {}
