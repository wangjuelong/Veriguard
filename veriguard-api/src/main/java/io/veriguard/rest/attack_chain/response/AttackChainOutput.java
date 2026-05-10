package io.veriguard.rest.attack_chain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.rest.kill_chain_phase.response.KillChainPhaseOutput;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttackChainOutput {

  @JsonProperty("attack_chain_id")
  @NotBlank
  @Schema(description = "ID of the scenario")
  private String id;

  @JsonProperty("attack_chain_name")
  @NotBlank
  @Schema(description = "Name of the scenario")
  private String name;

  @JsonProperty("attack_chain_description")
  @Schema(description = "Description of the scenario")
  private String description;

  @JsonProperty("attack_chain_subtitle")
  @Schema(description = "Subtitle of the scenario")
  private String subtitle;

  @JsonProperty("attack_chain_category")
  @Schema(description = "Category of the scenario")
  private String category;

  @JsonProperty("attack_chain_main_focus")
  @Schema(description = "Main focus value of the scenario")
  private String mainFocus;

  @JsonProperty("attack_chain_severity")
  @Schema(description = "Severity of the scenario")
  private String severity;

  @JsonProperty("attack_chain_type_affinity")
  @Schema(description = "Type affinity of the scenario")
  private String typeAffinity;

  @JsonProperty("attack_chain_external_url")
  @Schema(description = "External URL of the scenario")
  private String externalUrl;

  @JsonProperty("attack_chain_recurrence")
  @Schema(description = "Recurrence of the scenario")
  private String recurrence;

  @JsonProperty("attack_chain_recurrence_start")
  @Schema(description = "Recurrence start date of the scenario")
  private Instant recurrenceStart;

  @JsonProperty("attack_chain_recurrence_end")
  @Schema(description = "Recurrence end date of the scenario")
  private Instant recurrenceEnd;

  @JsonProperty("attack_chain_message_header")
  @Schema(description = "Header of the scenario")
  private String header;

  @JsonProperty("attack_chain_message_footer")
  @Schema(description = "Footer of the scenario")
  private String footer;

  @JsonProperty("attack_chain_mail_from")
  @NotBlank
  @Schema(description = "From value of the scenario")
  private String from;

  @JsonProperty("attack_chain_created_at")
  @NotNull
  @Schema(description = "Creation date of the scenario")
  private Instant createdAt;

  @JsonProperty("attack_chain_updated_at")
  @NotNull
  @Schema(description = "Update date of the scenario")
  private Instant updatedAt;

  @JsonProperty("attack_chain_custom_dashboard")
  @Schema(description = "Custom dashboard of the scenario")
  private String customDashboard;

  @JsonProperty("attack_chain_teams_users")
  @ArraySchema(schema = @Schema(description = "Enabled users of the scenario"))
  private Set<AttackChainTeamUserOutput> teamUsers;

  @JsonProperty("attack_chain_tags")
  @ArraySchema(schema = @Schema(description = "Tags ids of the scenario"))
  private Set<String> tags;

  @JsonProperty("attack_chain_runs")
  @ArraySchema(schema = @Schema(description = "Exercises ids of the scenario"))
  private Set<String> attackChainRuns;

  @Column(name = "attack_chain_lessons_anonymized")
  @Schema(description = "Lesson anonymized state of the scenario")
  private boolean lessonsAnonymized;

  @JsonProperty("attack_chain_dependencies")
  @ArraySchema(schema = @Schema(description = "Dependencies of the scenario"))
  private Set<String> dependencies;

  @JsonProperty("attack_chain_kill_chain_phases")
  @ArraySchema(schema = @Schema(description = "Kill chain phases of the scenario"))
  private Set<KillChainPhaseOutput> killChainPhases;

  @JsonProperty("attack_chain_platforms")
  @ArraySchema(schema = @Schema(description = "Platforms of the scenario"))
  private Set<String> platforms;

  @JsonProperty("attack_chain_users_number")
  @Schema(description = "Active total number of users of the scenario")
  private long attackChainUsersNumber;

  @JsonProperty("attack_chain_all_users_number")
  @Schema(description = "Total number of users of the scenario")
  private long attackChainAllUsersNumber;
}
