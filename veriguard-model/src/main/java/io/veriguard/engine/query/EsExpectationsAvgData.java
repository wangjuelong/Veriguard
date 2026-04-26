package io.veriguard.engine.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class EsExpectationsAvgData {

  private String key;
  private Double avg;
}
