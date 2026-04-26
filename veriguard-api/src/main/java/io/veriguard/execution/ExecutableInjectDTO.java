package io.veriguard.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.Injection;
import io.veriguard.rest.asset.endpoint.output.EndpointTargetOutput;
import io.veriguard.rest.asset_group.form.AssetGroupSimple;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExecutableInjectDTO {

  @JsonProperty("injection")
  private final Injection injection;

  @JsonProperty("assets")
  private final Set<EndpointTargetOutput> assets;

  @JsonProperty("assetGroups")
  private final Set<AssetGroupSimple> assetGroups;
}
