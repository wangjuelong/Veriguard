package io.veriguard.engine.api;

import io.veriguard.database.model.CustomDashboardParameters;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Runtime {

  protected Map<String, String> parameters;
  protected Map<String, CustomDashboardParameters> definitionParameters;
}
