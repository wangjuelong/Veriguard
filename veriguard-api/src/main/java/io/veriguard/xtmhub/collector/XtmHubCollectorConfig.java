package io.veriguard.xtmhub.collector;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class XtmHubCollectorConfig {

  @Value("${openbas.xtm.hub.collector.enable:${veriguard.xtm.hub.collector.enable:false}}")
  private boolean enable;

  @NotBlank
  @Value("${openbas.xtm.hub.collector.id:${veriguard.xtm.hub.collector.id:#{null}}}")
  private String id;

  // period between two connectivity checks, default to 1 hour in milliseconds.
  @NotBlank
  @Value(
      "${openbas.xtm.hub.collector.connectivity-check-interval:${veriguard.xtm.hub.collector.connectivity-check-interval:3600000}}")
  private Integer connectivityCheckInterval;
}
