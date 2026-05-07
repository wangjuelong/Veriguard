package io.veriguard.rest.attack_chain.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.Tag;
import io.veriguard.database.raw.RawAttackChainSimple;
import io.veriguard.helper.MultiIdSetSerializer;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class AttackChainSimple {

  @JsonProperty("scenario_id")
  private String id;

  @JsonProperty("scenario_name")
  private String name;

  @JsonProperty("scenario_subtitle")
  private String subtitle;

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("scenario_tags")
  private Set<Tag> tags = new HashSet<>();

  public static AttackChainSimple fromAttackChain(@NotNull final AttackChain attackChain) {
    AttackChainSimple simple = new AttackChainSimple();
    BeanUtils.copyProperties(attackChain, simple);
    return simple;
  }

  public static AttackChainSimple fromRawAttackChain(
      @NotNull final RawAttackChainSimple attackChain) {
    AttackChainSimple simple = new AttackChainSimple();
    simple.setId(attackChain.getScenario_id());
    simple.setName(attackChain.getScenario_name());
    simple.setSubtitle(attackChain.getScenario_subtitle());
    if (attackChain.getScenario_tags() != null) {
      simple.setTags(
          attackChain.getScenario_tags().stream()
              .map(
                  (tagId) -> {
                    Tag tag = new Tag();
                    tag.setId(tagId);
                    return tag;
                  })
              .collect(Collectors.toSet()));
    } else {
      simple.setTags(new HashSet<>());
    }
    return simple;
  }
}
