package io.veriguard.rest.attack_chain_run.form;

import static io.veriguard.database.model.AttackChainRunStatus.valueOf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.AttackChain.SEVERITY;
import io.veriguard.database.model.AttackChainRunStatus;
import io.veriguard.database.model.AttackChainRunTeamUser;
import io.veriguard.database.model.KillChainPhase;
import io.veriguard.database.model.Objective;
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

  @JsonProperty("attack_chain_run_id")
  @NotBlank
  private String id;

  @JsonProperty("attack_chain_run_name")
  @NotBlank
  private String name;

  @JsonProperty("attack_chain_run_description")
  private String description;

  @JsonProperty("attack_chain_run_status")
  @NotNull
  private AttackChainRunStatus status;

  @JsonProperty("attack_chain_run_subtitle")
  private String subtitle;

  @JsonProperty("attack_chain_run_category")
  private String category;

  @JsonProperty("attack_chain_run_main_focus")
  private String mainFocus;

  @JsonProperty("attack_chain_run_severity")
  private SEVERITY severity;

  @JsonProperty("attack_chain_run_start_date")
  private Instant start;

  @JsonProperty("attack_chain_run_end_date")
  private Instant end;

  @JsonProperty("attack_chain_run_message_header")
  private String header;

  @JsonProperty("attack_chain_run_message_footer")
  private String footer;

  @JsonProperty("attack_chain_run_mail_from")
  @NotBlank
  private String from;

  @JsonProperty("attack_chain_run_mails_reply_to")
  private List<String> replyTo;

  @JsonProperty("attack_chain_run_lessons_anonymized")
  private boolean lessonsAnonymized;

  // -- SCENARIO --

  @JsonProperty("attack_chain_run_attack_chain")
  private String attackChain;

  // -- AUDIT --

  @JsonProperty("attack_chain_run_created_at")
  private Instant createAt;

  @JsonProperty("attack_chain_run_updated_at")
  private Instant updatedAt;

  // -- RELATION --

  @JsonProperty("attack_chain_run_teams_users")
  private Set<AttackChainRunTeamUser> attackChainRunTeamUsers;

  @JsonProperty("attack_chain_run_tags")
  private Set<String> tags = new HashSet<>();

  @JsonProperty("attack_chain_run_users")
  private Set<String> users = new HashSet<>();

  @JsonProperty("attack_chain_run_observers")
  private Set<String> observers = new HashSet<>();

  @JsonProperty("attack_chain_run_lessons_answers_number")
  private long lessonsAnswersNumber;

  @JsonProperty("attack_chain_run_planners")
  private Set<String> planners = new HashSet<>();

  @JsonProperty("attack_chain_run_all_users_number")
  private long allUsersNumber;

  @JsonProperty("attack_chain_run_users_number")
  private long usersNumber;

  @JsonProperty("attack_chain_run_logs_number")
  private long logsNumber;

  @JsonProperty("attack_chain_run_communications_number")
  public long communicationsNumber;

  @JsonProperty("attack_chain_run_custom_dashboard")
  private String customDashboard;

  // -- PLATFORMS --

  @JsonProperty("attack_chain_run_platforms")
  public List<String> platforms;

  // -- KILL CHAIN PHASES --

  @JsonProperty("attack_chain_run_kill_chain_phases")
  public List<KillChainPhase> killChainPhases;

  @JsonProperty("attack_chain_run_score")
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
            .id(attackChainRun.getAttack_chain_run_id())
            .name(attackChainRun.getAttack_chain_run_name())
            .description(attackChainRun.getAttack_chain_run_description())
            .status(valueOf(attackChainRun.getAttack_chain_run_status()))
            .subtitle(attackChainRun.getAttack_chain_run_subtitle())
            .category(attackChainRun.getAttack_chain_run_category())
            .mainFocus(attackChainRun.getAttack_chain_run_main_focus())
            .customDashboard(attackChainRun.getAttack_chain_run_custom_dashboard());

    if (attackChainRun.getAttack_chain_run_severity() != null) {
      details.severity(SEVERITY.valueOf(attackChainRun.getAttack_chain_run_severity()));
    }
    details
        .start(attackChainRun.getAttack_chain_run_start_date())
        .end(attackChainRun.getAttack_chain_run_end_date())
        .header(attackChainRun.getAttack_chain_run_message_header())
        .footer(attackChainRun.getAttack_chain_run_message_footer())
        .from(attackChainRun.getAttack_chain_run_mail_from());
    if (attackChainRun.getAttack_chain_run_reply_to() != null) {
      details.replyTo(attackChainRun.getAttack_chain_run_reply_to().stream().toList());
    }
    details
        .lessonsAnonymized(attackChainRun.getAttack_chain_run_lessons_anonymized())
        .attackChain(attackChainRun.getAttack_chain_id())
        .createAt(attackChainRun.getAttack_chain_run_created_at())
        .updatedAt(attackChainRun.getAttack_chain_run_updated_at());
    if (attackChainRunTeamsUsers != null) {
      details
          .attackChainRunTeamUsers(new HashSet<>(attackChainRunTeamsUsers))
          .usersNumber(
              attackChainRunTeamsUsers.stream()
                  .map(AttackChainRunTeamUser::getUser)
                  .distinct()
                  .count());
    }
    details
        .tags(new HashSet<>(attackChainRun.getAttack_chain_run_tags()))
        .users(attackChainRun.getAttack_chain_run_users())
        .objectives(objectives)
        .lessonsAnswersNumber(
            attackChainRun.getLessons_answers().stream().distinct().toList().size())
        .allUsersNumber(attackChainRun.getAttack_chain_run_users().stream().distinct().toList().size())
        .logsNumber(attackChainRun.getLogs().stream().distinct().toList().size());

    return details.build();
  }
}
