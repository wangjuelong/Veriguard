package io.veriguard.rest.attack_chain.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Data;

@Data
public class AttackChainRecurrenceInput {

  @JsonProperty("attack_chain_recurrence")
  private String recurrence;

  @JsonProperty("attack_chain_recurrence_start")
  private Instant recurrenceStart;

  @JsonProperty("attack_chain_recurrence_end")
  private Instant recurrenceEnd;
}
