package io.veriguard.rest.attack_chain_run.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AttackChainRunUpdateTagsInput {

  @JsonProperty("exercise_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("apply_tag_rule")
  private boolean applyTagRule = false;
}
