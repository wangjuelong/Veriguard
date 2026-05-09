package io.veriguard.database.raw;

import io.veriguard.database.model.AttackChain.SEVERITY;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Data
public class RawPaginationAttackChain {

  private String attack_chain_id;
  private String attack_chain_name;
  private String attack_chain_description;
  private SEVERITY attack_chain_severity;
  private String attack_chain_category;
  private String attack_chain_recurrence;
  private Instant attack_chain_updated_at;
  private Set<String> attack_chain_tags;
  private Set<String> attack_chain_platforms;

  public RawPaginationAttackChain(
      String id,
      String name,
      String description,
      SEVERITY severity,
      String category,
      String recurrence,
      Instant updatedAt,
      String[] tags,
      String[] platforms) {
    this.attack_chain_id = id;
    this.attack_chain_name = name;
    this.attack_chain_description = description;
    this.attack_chain_severity = severity;
    this.attack_chain_category = category;
    this.attack_chain_recurrence = recurrence;
    this.attack_chain_updated_at = updatedAt;
    this.attack_chain_tags = tags != null ? new HashSet<>(Arrays.asList(tags)) : new HashSet<>();
    this.attack_chain_platforms =
        platforms != null ? new HashSet<>(Arrays.asList(platforms)) : new HashSet<>();
  }
}
