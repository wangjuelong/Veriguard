package io.veriguard.rest.exercise.form;

import static io.veriguard.config.AppConfig.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.FutureOrPresent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class AttackChainRunUpdateStartDateInput {
  @JsonProperty("exercise_start_date")
  @FutureOrPresent(message = NOW_FUTURE_MESSAGE)
  private Instant start;

  public Instant getStart() {
    return start != null ? start.truncatedTo(ChronoUnit.MINUTES) : null;
  }

  public void setStart(Instant start) {
    this.start = start;
  }
}
