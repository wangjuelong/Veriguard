package io.veriguard.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record AttackChainStatistic(
    @JsonProperty("simulations_results_latest") @NotNull
        SimulationsResultsLatest simulationsResultsLatest) {}
