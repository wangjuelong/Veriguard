package io.veriguard.database.model;

import static io.veriguard.database.model.Grant.GRANT_TYPE.OBSERVER;
import static io.veriguard.database.model.Grant.GRANT_TYPE.PLANNER;
import static io.veriguard.helper.UserHelper.getUsersByType;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.database.model.AttackChain.SEVERITY;
import io.veriguard.database.model.Endpoint.PLATFORM_TYPE;
import io.veriguard.helper.AttackChainNodeStatisticsHelper;
import io.veriguard.helper.MonoIdSerializer;
import io.veriguard.helper.MultiIdListSerializer;
import io.veriguard.helper.MultiIdSetSerializer;
import io.veriguard.helper.MultiModelSerializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Entity
@Table(name = "attack_chain_runs")
@EntityListeners(ModelBaseListener.class)
@Grantable(Grant.GRANT_RESOURCE_TYPE.SIMULATION)
public class AttackChainRun implements GrantableBase {

  @Getter
  @Id
  @Column(name = "run_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("attack_chain_run_id")
  @NotBlank
  private String id;

  @Getter
  @Column(name = "run_name")
  @JsonProperty("attack_chain_run_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @NotBlank
  private String name;

  @Getter
  @Column(name = "run_description")
  @JsonProperty("attack_chain_run_description")
  private String description;

  @Getter
  @Column(name = "run_status")
  @JsonProperty("attack_chain_run_status")
  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true)
  @NotNull
  private AttackChainRunStatus status = AttackChainRunStatus.SCHEDULED;

  @Getter
  @Column(name = "run_subtitle")
  @JsonProperty("attack_chain_run_subtitle")
  private String subtitle;

  @Getter
  @Column(name = "run_category")
  @JsonProperty("attack_chain_run_category")
  private String category;

  @Getter
  @Column(name = "run_main_focus")
  @JsonProperty("attack_chain_run_main_focus")
  private String mainFocus;

  @Getter
  @Column(name = "run_severity")
  @Enumerated(EnumType.STRING)
  @JsonProperty("attack_chain_run_severity")
  private SEVERITY severity;

  @Column(name = "run_pause_date")
  @JsonIgnore
  private Instant currentPause;

  @Column(name = "run_start_date")
  @JsonProperty("attack_chain_run_start_date")
  @Queryable(filterable = true, sortable = true)
  private Instant start;

  @Column(name = "run_launch_order", insertable = false, updatable = false)
  @JsonIgnore
  @Getter
  @Setter(NONE)
  private Long launchOrder;

  @Column(name = "run_end_date")
  @JsonProperty("attack_chain_run_end_date")
  @Queryable(filterable = true, sortable = true)
  private Instant end;

  // -- 邮件演练遗留字段（V3 已 drop DB 列；Java 字段保留为 @Transient 维持 API 兼容）--
  // TODO Phase 2+: 彻底删除 + 移除 AttackChainRunFactory / V1_DataImporter / NotificationRule 等引用

  @Transient @JsonIgnore @Getter private String header = "SIMULATION HEADER";

  @Transient @JsonIgnore @Getter private String footer = "SIMULATION FOOTER";

  @Transient @JsonIgnore @Getter private String from;

  @Transient @JsonIgnore @Getter private List<String> replyTos = new ArrayList<>();

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "run_logo_dark")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("attack_chain_run_logo_dark")
  @Schema(type = "string")
  private Document logoDark;

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "run_logo_light")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("attack_chain_run_logo_light")
  @Schema(type = "string")
  private Document logoLight;

  @Getter
  @Column(name = "run_lessons_anonymized")
  @JsonProperty("attack_chain_run_lessons_anonymized")
  private boolean lessonsAnonymized = false;

  // -- SCENARIO --

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinTable(
      name = "attack_chains_runs",
      joinColumns = @JoinColumn(name = "run_id"),
      inverseJoinColumns = @JoinColumn(name = "attack_chain_id"))
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("attack_chain_run_attack_chain")
  @Queryable(filterable = true, dynamicValues = true)
  @Schema(type = "string")
  @Setter(NONE)
  private AttackChain attackChain;

  public void setAttackChain(AttackChain attackChain) {
    if (attackChain != null) attackChain.setUpdatedAt(now());
    this.attackChain = attackChain;
    this.setUpdatedAt(now());
  }

  // STIX
  @Getter
  @ManyToOne
  @JoinColumn(name = "run_security_coverage")
  @JsonIgnore
  private SecurityCoverage securityCoverage;

  // -- AUDIT --

  @Getter
  @Column(name = "run_created_at")
  @JsonProperty("attack_chain_run_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Getter
  @Column(name = "run_updated_at")
  @JsonProperty("attack_chain_run_updated_at")
  @NotNull
  @Queryable(filterable = true, sortable = true)
  @UpdateTimestamp
  private Instant updatedAt = now();

  // -- 攻击编排（PRD §2.4 二开新增 verdict）--

  @Getter
  @Column(name = "verdict_prevention")
  @Enumerated(EnumType.STRING)
  @JsonProperty("attack_chain_run_verdict_prevention")
  private LinkVerdict verdictPrevention;

  @Getter
  @Column(name = "verdict_detection")
  @Enumerated(EnumType.STRING)
  @JsonProperty("attack_chain_run_verdict_detection")
  private LinkVerdict verdictDetection;

  @Getter
  @Column(name = "verdict_computed_at")
  @JsonProperty("attack_chain_run_verdict_computed_at")
  private Instant verdictComputedAt;

  // -- RELATION --

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "run_custom_dashboard")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("attack_chain_run_custom_dashboard")
  @Schema(type = "string")
  private CustomDashboard customDashboard;

  @Getter
  @OneToMany(fetch = FetchType.EAGER)
  @JoinColumn(
      name = "grant_resource",
      referencedColumnName = "run_id",
      insertable = false,
      updatable = false)
  @SQLRestriction(
      "grant_resource_type = 'SIMULATION'") // Must be present in Grant.GRANT_RESOURCE_TYPE
  @JsonIgnore
  private List<Grant> grants = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(mappedBy = "attackChainRun", fetch = FetchType.LAZY)
  @JsonProperty("attack_chain_run_nodes")
  @JsonSerialize(using = MultiIdListSerializer.class)
  private List<AttackChainNode> attackChainNodes = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setAttackChainNodes(List<AttackChainNode> attackChainNodes) {
    this.updatedAt = now();
    this.attackChainNodes = attackChainNodes;
  }

  @Getter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "attack_chain_runs_teams",
      joinColumns = @JoinColumn(name = "run_id"),
      inverseJoinColumns = @JoinColumn(name = "team_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("attack_chain_run_teams")
  @ArraySchema(schema = @Schema(type = "string"))
  private List<Team> teams = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setTeams(List<Team> teams) {
    this.updatedAt = now();
    this.teams = teams;
  }

  @Getter
  @OneToMany(
      mappedBy = "attackChainRun",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("attack_chain_run_teams_users")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<AttackChainRunTeamUser> teamUsers = new ArrayList<>();

  @Getter
  @OneToMany(mappedBy = "attackChainRun", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonIgnore
  private List<Objective> objectives = new ArrayList<>();

  @Getter
  @OneToMany(mappedBy = "attackChainRun", fetch = FetchType.LAZY)
  @JsonIgnore
  private List<Log> logs = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @OneToMany(mappedBy = "attackChainRun", fetch = FetchType.LAZY)
  @JsonProperty("attack_chain_run_pauses")
  @JsonSerialize(using = MultiIdListSerializer.class)
  private List<Pause> pauses = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "attack_chain_runs_tags",
      joinColumns = @JoinColumn(name = "run_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("attack_chain_run_tags")
  @Queryable(filterable = true, dynamicValues = true, path = "tags.id")
  private Set<Tag> tags = new HashSet<>();

  // UpdatedAt now used to sync with linked object
  public void setTags(Set<Tag> tags) {
    this.updatedAt = now();
    this.tags = tags;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "attack_chain_runs_documents",
      joinColumns = @JoinColumn(name = "run_id"),
      inverseJoinColumns = @JoinColumn(name = "document_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("attack_chain_run_documents")
  private List<Document> documents = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @OneToMany(mappedBy = "attackChainRun", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("attack_chain_run_lessons_categories")
  private List<LessonsCategory> lessonsCategories = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @OneToMany(mappedBy = "attackChainRun", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("attack_chain_run_variables")
  private List<Variable> variables = new ArrayList<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.SIMULATION;

  // region transient
  @JsonProperty("attack_chain_run_nodes_statistics")
  public Map<String, Long> getAttackChainNodeStatistics() {
    return AttackChainNodeStatisticsHelper.getAttackChainNodeStatistics(this.getAttackChainNodes());
  }

  @JsonProperty("attack_chain_run_lessons_answers_number")
  public Long getLessonsAnswersNumbers() {
    return getLessonsCategories().stream()
        .flatMap(
            lessonsCategory ->
                lessonsCategory.getQuestions().stream()
                    .flatMap(lessonsQuestion -> lessonsQuestion.getAnswers().stream()))
        .count();
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("attack_chain_run_planners")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<User> getPlanners() {
    return getUsersByType(this.getGrants(), PLANNER);
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("attack_chain_run_observers")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<User> getObservers() {
    return getUsersByType(this.getGrants(), PLANNER, OBSERVER);
  }

  @JsonProperty("attack_chain_run_next_node_date")
  public Optional<Instant> getNextAttackChainNodeExecution() {
    return getAttackChainNodes().stream()
        .filter(attackChainNode -> attackChainNode.getStatus().isEmpty())
        .filter(attackChainNode -> attackChainNode.getDate().isPresent())
        .filter(attackChainNode -> attackChainNode.getDate().get().isAfter(now()))
        .findFirst()
        .flatMap(AttackChainNode::getDate);
  }

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin() || getObservers().contains(user);
  }

  @JsonProperty("attack_chain_run_all_users_number")
  public long usersAllNumber() {
    return getTeams().stream().mapToLong(Team::getUsersNumber).sum();
  }

  @JsonProperty("attack_chain_run_users_number")
  public long usersNumber() {
    return getTeamUsers().stream().map(AttackChainRunTeamUser::getUser).distinct().count();
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("attack_chain_run_users")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<User> getUsers() {
    return getTeamUsers().stream().map(AttackChainRunTeamUser::getUser).distinct().toList();
  }

  @JsonProperty("attack_chain_run_score")
  public Double getEvaluationAverage() {
    double evaluationAverage =
        getObjectives().stream().mapToDouble(Objective::getEvaluationAverage).average().orElse(0D);
    return Math.round(evaluationAverage * 100.0) / 100.0;
  }

  @JsonProperty("attack_chain_run_logs_number")
  public long getLogsNumber() {
    return getLogs().size();
  }

  @JsonProperty("attack_chain_run_communications_number")
  public long getCommunicationsNumber() {
    return getAttackChainNodes().stream().mapToLong(AttackChainNode::getCommunicationsNumber).sum();
  }

  // -- PLATFORMS --
  @JsonProperty("attack_chain_run_platforms")
  public List<PLATFORM_TYPE> getPlatforms() {
    return getAttackChainNodes().stream()
        .flatMap(
            attackChainNode ->
                attackChainNode.getNodeContract().map(NodeContract::getPlatforms).stream()
                    .flatMap(Arrays::stream))
        .distinct()
        .toList();
  }

  // -- KILL CHAIN PHASES --
  @JsonProperty("attack_chain_run_kill_chain_phases")
  @Queryable(
      filterable = true,
      dynamicValues = true,
      path = "attackChainNodes.nodeContract.attackPatterns.killChainPhases.id")
  public List<KillChainPhase> getKillChainPhases() {
    return getAttackChainNodes().stream()
        .flatMap(
            attackChainNode ->
                attackChainNode.getNodeContract().map(NodeContract::getAttackPatterns).stream()
                    .flatMap(Collection::stream)
                    .flatMap(attackPattern -> attackPattern.getKillChainPhases().stream()))
        .distinct()
        .toList();
  }

  @JsonProperty("attack_chain_run_next_possible_status")
  public List<AttackChainRunStatus> nextPossibleStatus() {
    if (AttackChainRunStatus.CANCELED.equals(status)) {
      return List.of(AttackChainRunStatus.SCHEDULED); // Via reset
    }
    if (AttackChainRunStatus.FINISHED.equals(status)) {
      return List.of(AttackChainRunStatus.SCHEDULED); // Via reset
    }
    if (AttackChainRunStatus.SCHEDULED.equals(status)) {
      return List.of(AttackChainRunStatus.RUNNING);
    }
    if (AttackChainRunStatus.RUNNING.equals(status)) {
      return List.of(AttackChainRunStatus.CANCELED, AttackChainRunStatus.PAUSED);
    }
    if (AttackChainRunStatus.PAUSED.equals(status)) {
      return List.of(AttackChainRunStatus.CANCELED, AttackChainRunStatus.RUNNING);
    }
    return List.of();
  }

  // endregion

  public Optional<Instant> getStart() {
    return ofNullable(start);
  }

  public Optional<Instant> getEnd() {
    return ofNullable(end);
  }

  public Optional<Instant> getCurrentPause() {
    return ofNullable(currentPause);
  }

  public List<AttackChainNode> getAttackChainNodes() {
    return attackChainNodes.stream()
        .sorted(AttackChainNode.executionComparator)
        .collect(Collectors.toList()); // Should be modifiable
  }

  public void addReplyTos(List<String> replyTos) {
    getReplyTos().addAll(replyTos);
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
