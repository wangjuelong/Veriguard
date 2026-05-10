package io.veriguard.rest.attack_chain_node.output;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(NON_NULL)
public class AttackChainNodeSimple {

  @Schema(description = "Inject Id")
  @JsonProperty("node_id")
  @NotBlank
  private String id;

  @Schema(description = "Inject Title")
  @JsonProperty("node_title")
  @NotBlank
  private String title;
}
