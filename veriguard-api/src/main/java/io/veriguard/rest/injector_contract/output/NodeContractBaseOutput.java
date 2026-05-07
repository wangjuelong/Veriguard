package io.veriguard.rest.injector_contract.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.model.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
@Schema(
    discriminatorProperty = "injector_contract_has_full_details",
    oneOf = {NodeContractBaseOutput.class, NodeContractFullOutput.class},
    discriminatorMapping = {
      @DiscriminatorMapping(value = "false", schema = NodeContractBaseOutput.class),
      @DiscriminatorMapping(value = "true", schema = NodeContractFullOutput.class),
    })
public class NodeContractBaseOutput {
  @Schema(description = "Injector contract Id")
  @JsonProperty("injector_contract_id")
  @NotBlank
  private String id;

  @Schema(description = "Injector contract external Id")
  @JsonProperty("injector_contract_external_id")
  private String externalId;

  @Schema(description = "Timestamp when the injector contract was last updated")
  @JsonProperty("injector_contract_updated_at")
  @NotNull
  private Instant updatedAt;

  @Setter(AccessLevel.PROTECTED)
  @JsonProperty("injector_contract_has_full_details")
  private boolean hasFullDetails;

  public NodeContractBaseOutput(String id, String externalId, Instant updatedAt) {
    this.setId(id);
    this.setExternalId(externalId);
    this.setUpdatedAt(updatedAt);
    this.setHasFullDetails(false);
  }

  public static NodeContractBaseOutput fromNodeContract(NodeContract sourceContract) {
    return new NodeContractBaseOutput(
        sourceContract.getId(), sourceContract.getExternalId(), sourceContract.getUpdatedAt());
  }
}
