package io.veriguard.rest.injector_contract.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.utils.pagination.SearchPaginationInput;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class NodeContractSearchPaginationInput extends SearchPaginationInput {
  @JsonProperty("include_full_details")
  private boolean includeFullDetails = true;
}
