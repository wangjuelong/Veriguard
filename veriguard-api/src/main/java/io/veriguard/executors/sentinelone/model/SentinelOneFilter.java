package io.veriguard.executors.sentinelone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SentinelOneFilter {

  private List<String> uuids;
  private boolean tenant = true;
}
