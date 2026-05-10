package io.veriguard.rest.attack_chain_run.form;

import static io.veriguard.config.AppConfig.NOW_FUTURE_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAttackChainRunInput extends AttackChainRunInput {

  @Schema(nullable = true)
  @JsonProperty("attack_chain_run_start_date")
  @FutureOrPresent(message = NOW_FUTURE_MESSAGE)
  private Instant start;

  @JsonProperty("attack_chain_run_custom_dashboard")
  private String customDashboard;

  public Instant getStart() {
    return start != null ? start.truncatedTo(ChronoUnit.MINUTES) : null;
  }
}
