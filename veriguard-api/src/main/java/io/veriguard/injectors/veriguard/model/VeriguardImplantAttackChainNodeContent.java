package io.veriguard.injectors.veriguard.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.model.attack_chain_node.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VeriguardImplantAttackChainNodeContent {

  @JsonProperty("obfuscator")
  private String obfuscator;

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
