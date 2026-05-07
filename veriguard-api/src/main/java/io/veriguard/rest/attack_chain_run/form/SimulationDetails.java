package io.veriguard.rest.attack_chain_run.form;

import static io.veriguard.database.model.AttackChainRunStatus.valueOf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.AttackChainRunStatus;
import io.veriguard.database.model.AttackChainRunTeamUser;
import io.veriguard.database.model.KillChainPhase;
import io.veriguard.database.model.Objective;
import io.veriguard.database.model.AttackChain.SEVERITY;
import io.veriguard.database.raw.RawSimulation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class SimulationDetails {

  @JsonProperty("exercise_id")
  @NotBlank
  private String id;

  @JsonProperty("exercise_name")
  @NotBlank
  private String name;

  @JsonProperty("exercise_description")
  private String description;

  @JsonProperty("exercise_status")
  @NotNull
  private AttackChainRunStatus status;

  @JsonProperty("exercise_subtitle")
  private String subtitle;

  @JsonProperty("exercise_category")
  private String category;

  @JsonProperty("exercise_main_focus")
  private String mainFocus;

  @JsonProperty("exercise_severity")
  private SEVERITY severity;

  @JsonProperty("exercise_start_date")
  private Instant start;

  @JsonProperty("exercise_end_date")
  private Instant end;

  @JsonProperty("exercise_message_header")
  private String header;

  @JsonProperty("exercise_message_footer")
  private String footer;

  @JsonProperty("exercise_mail_from")
  @NotBlank
  private String from;

  @JsonProperty("exercise_mails_reply_to")
  private List<String> replyTo;

  @JsonProperty("exercise_lessons_anonymized")
  private boolean lessonsAnonymized;

  // -- SCENARIO --

  @JsonProperty("exercise_scenario")
  private String attackChain;

  // -- AUDIT --

  @JsonProperty("exercise_created_at")
  private Instant createAt;

  @JsonProperty("exercise_updated_at")
  private Instant updatedAt;

  // -- RELATION --

  @JsonProperty("exercise_teams_users")
  private Set<AttackChainRunTeamUser> attackChainRunTeamUsers;

  @JsonProperty("exercise_tags")
  private Set<String> tags = new HashSet<>();

  @JsonProperty("exercise_users")
  private Set<String> users = new HashSet<>();

  @JsonProperty("exercise_observers")
  private Set<String> observers = new HashSet<>();

  @JsonProperty("exercise_lessons_answers_number")
  private long lessonsAnswersNumber;

  @JsonProperty("exercise_planners")
  private Set<String> planners = new HashSet<>();

  @JsonProperty("exercise_all_users_number")
  private long allUsersNumber;

  @JsonProperty("exercise_users_number")
  private long usersNumber;

  @JsonProperty("exercise_logs_number")
  private long logsNumber;

  @JsonProperty("exercise_communications_number")
  public long communicationsNumber;

  @JsonProperty("exercise_custom_dashboard")
  private String customDashboard;

  // -- PLATFORMS --

  @JsonProperty("exercise_platforms")
  public List<String> platforms;

  // -- KILL CHAIN PHASES --

  @JsonProperty("exercise_kill_chain_phases")
  public List<KillChainPhase> killChainPhases;

  @JsonProperty("exercise_score")
  public Double getEvaluationAverage() {
    double evaluationAverage =
        getObjectives().stream().mapToDouble(Objective::getEvaluationAverage).average().orElse(0D);
    return Math.round(evaluationAverage * 100.0) / 100.0;
  }

  @JsonIgnore private List<Objective> objectives;

  /**
   * Create an AttackChainRun Details object different from the one used in the lists from a Raw one
   *
   * @param attackChainRun the raw attackChainRun
   * @return an AttackChainRun Simple object
   */
  public static SimulationDetails fromRawAttackChainRun(
      RawSimulation attackChainRun,
      List<AttackChainRunTeamUser> attackChainRunTeamsUsers,
      List<Objective> objectives) {
    SimulationDetailsBuilder details =
        SimulationDetails.builder()
            .id(attackChainRun.getExercise_id())
            .name(attackChainRun.getExercise_name())
            .description(attackChainRun.getExercise_description())
            .status(valueOf(attackChainRun.getExercise_status()))
            .subtitle(attackChainRun.getExercise_subtitle())
            .category(attackChainRun.getExercise_category())
            .mainFocus(attackChainRun.getExercise_main_focus())
            .customDashboard(attackChainRun.getExercise_custom_dashboard());

    if (attackChainRun.getExercise_severity() != null) {
      details.severity(SEVERITY.valueOf(attackChainRun.getExercise_severity()));
    }
    details
        .start(attackChainRun.getExercise_start_date())
        .end(attackChainRun.getExercise_end_date())
        .header(attackChainRun.getExercise_message_header())
        .footer(attackChainRun.getExercise_message_footer())
        .from(attackChainRun.getExercise_mail_from());
    if (attackChainRun.getExercise_reply_to() != null) {
      details.replyTo(attackChainRun.getExercise_reply_to().stream().toList());
    }
    details
        .lessonsAnonymized(attackChainRun.getExercise_lessons_anonymized())
        .attackChain(attackChainRun.getScenario_id())
        .createAt(attackChainRun.getExercise_created_at())
        .updatedAt(attackChainRun.getExercise_updated_at());
    if (attackChainRunTeamsUsers != null) {
      details
          .attackChainRunTeamUsers(new HashSet<>(attackChainRunTeamsUsers))
          .usersNumber(
              attackChainRunTeamsUsers.stream().map(AttackChainRunTeamUser::getUser).distinct().count());
    }
    details
        .tags(new HashSet<>(attackChainRun.getExercise_tags()))
        .users(attackChainRun.getExercise_users())
        .objectives(objectives)
        .lessonsAnswersNumber(attackChainRun.getLessons_answers().stream().distinct().toList().size())
        .allUsersNumber(attackChainRun.getExercise_users().stream().distinct().toList().size())
        .logsNumber(attackChainRun.getLogs().stream().distinct().toList().size());

    return details.build();
  }
}
