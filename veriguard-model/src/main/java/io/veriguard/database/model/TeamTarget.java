package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.annotation.Queryable;
import java.util.Set;
import lombok.Data;

@Data
public class TeamTarget extends AttackChainNodeTarget {
  public TeamTarget(String id, String name, Set<String> tags) {
    this.setId(id);
    this.setName(name);
    this.setTags(tags);
    this.setTargetType("TEAMS");
  }

  @JsonProperty("target_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  private String name;

  @Override
  protected String getTargetSubtype() {
    return "N/A";
  }
}
