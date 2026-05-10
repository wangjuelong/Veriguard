package io.veriguard.rest.attack_chain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record GlobalScoreBySimulationEndDate(
    @JsonProperty("attack_chain_run_end_date") @NotNull Instant simulationEndDate,
    @JsonProperty("global_score_success_percentage") @NotNull float globalScoreSuccessPercentage) {}
