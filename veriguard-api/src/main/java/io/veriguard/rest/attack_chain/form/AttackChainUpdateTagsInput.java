package io.veriguard.rest.attack_chain.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AttackChainUpdateTagsInput {

  @JsonProperty("attack_chain_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("apply_tag_rule")
  private boolean applyTagRule = false;
}
