package io.veriguard.rest.injector_contract.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.Domain;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodeContractDomainDTO {
  @JsonProperty("domain_id")
  @NotBlank
  private String id;

  @JsonProperty("domain_name")
  @NotBlank
  private String name;

  @JsonProperty("domain_color")
  @NotBlank
  private String color;

  public static NodeContractDomainDTO fromDomain(Domain domain) {
    NodeContractDomainDTO dto = new NodeContractDomainDTO();
    dto.setId(domain.getId());
    dto.setName(domain.getName());
    dto.setColor(domain.getColor());
    return dto;
  }
}
