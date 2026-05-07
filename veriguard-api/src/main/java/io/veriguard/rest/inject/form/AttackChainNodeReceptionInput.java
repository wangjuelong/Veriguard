package io.veriguard.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AttackChainNodeReceptionInput {

  @JsonProperty("tracking_total_count")
  private int trackingTotalCount;
}
