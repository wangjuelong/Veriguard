package io.veriguard.database.model;

import static io.veriguard.database.model.Grant.GRANT_TYPE.OBSERVER;
import static io.veriguard.database.model.Grant.GRANT_TYPE.PLANNER;
import static io.veriguard.helper.UserHelper.getUsersByType;
import static java.time.Instant.now;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.database.model.Endpoint.PLATFORM_TYPE;
import io.veriguard.helper.AttackChainNodeStatisticsHelper;
import io.veriguard.helper.MonoIdSerializer;
import io.veriguard.helper.MultiIdListSerializer;
import io.veriguard.helper.MultiIdSetSerializer;
import io.veriguard.helper.MultiModelSerializer;
import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;

/**
 * Entity representing a simulation attackChain in Veriguard.
 *
 * <p>A AttackChain is a reusable template that defines:
 *
 * <ul>
 *   <li>A collection of attackChainNodes (attack simulations) to execute
 *   <li>Target teams and their configurations
 *   <li>Documentation (articles, documents)
 *   <li>Recurrence settings for automated execution
 *   <li>Default validation parameter set + execution mode + SOC correlation rules（PRD §2.4 二开）
 * </ul>
 *
 * <p>AttackChains can be instantiated into {@link AttackChainRun} instances for actual execution.
 *
 * @see AttackChainRun
 * @see AttackChainNode
 * @see Team
 */
@Data
@Entity
@Table(name = "attack_chains")
@EntityListeners(ModelBaseListener.class)
@NamedEntityGraphs({
  @NamedEntityGraph(
      name = "Scenario.tags-injects",
      attributeNodes = {@NamedAttributeNode("tags"), @NamedAttributeNode("attackChainNodes")})
})
@Grantable(Grant.GRANT_RESOURCE_TYPE.SCENARIO)
public class AttackChain implements GrantableBase {

  /** Status of attackChain recurrence scheduling. */
  public enum RECURRENCE_STATUS {
    /** AttackChain has scheduled recurrence. */
    SCHEDULED,
    /** AttackChain has no planned recurrence. */
    NOT_PLANNED,
  }

  /** Dependency sources for attackChains. */
  public enum Dependency {
    /** AttackChain originated from a starter pack. */
    @JsonProperty("STARTERPACK")
    STARTERPACK
  }

  /** Severity levels for attackChain classification. */
  public enum SEVERITY {
    /** Low severity attackChain. */
    @JsonProperty("low")
    low,
    /** Medium severity attackChain. */
    @JsonProperty("medium")
    medium,
    /** High severity attackChain. */
    @JsonProperty("high")
    high,
    /** Critical severity attackChain. */
    @JsonProperty("critical")
    critical,
  }

  /** Main focus: Incident Response attackChainRuns. */
  public static final String MAIN_FOCUS_INCIDENT_RESPONSE = "incident-response";

  /** Main focus: Endpoint Protection validation. */
  public static final String MAIN_FOCUS_ENDPOINT_PROTECTION = "endpoint-protection";

  /** Main focus: Web Filtering effectiveness. */
  public static final String MAIN_FOCUS_WEB_FILTERING = "web-filtering";

  /** Main focus: Standard Operating Procedure testing. */
  public static final String MAIN_FOCUS_STANDARD_OPERATING_PROCEDURE =
      "standard-operating-procedure";

  /** Main focus: Crisis Communication drills. */
  public static final String MAIN_FOCUS_CRISIS_COMMUNICATION = "crisis-communication";

  /** Main focus: Strategic Reaction capabilities. */
  public static final String MAIN_FOCUS_STRATEGIC_REACTION = "strategic-reaction";

  @Id
  @UuidGenerator
  @Column(name = "attack_chain_id")
  @JsonProperty("scenario_id")
  @NotBlank
  private String id;

  @Column(name = "attack_chain_name")
  @JsonProperty("scenario_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @NotBlank
  private String name;

  @Column(name = "attack_chain_description")
  @JsonProperty("scenario_description")
  private String description;

  @Column(name = "attack_chain_subtitle")
  @JsonProperty("scenario_subtitle")
  private String subtitle;

  @Column(name = "attack_chain_category")
  @JsonProperty("scenario_category")
  @Queryable(filterable = true, sortable = true, dynamicValues = true)
  private String category;

  @Column(name = "attack_chain_main_focus")
  @JsonProperty("scenario_main_focus")
  private String mainFocus;

  @Column(name = "attack_chain_severity")
  @Enumerated(EnumType.STRING)
  @JsonProperty("scenario_severity")
  @Queryable(filterable = true, sortable = true)
  private SEVERITY severity;

  @Column(name = "attack_chain_type_affinity")
  @JsonProperty("scenario_type_affinity")
  private String typeAffinity;

  // -- OCTI GENERATION SCENARIO FROM HTTP CALL--

  @Column(name = "attack_chain_external_reference")
  @JsonProperty("scenario_external_reference")
  private String externalReference;

  @Column(name = "attack_chain_external_url")
  @JsonProperty("scenario_external_url")
  private String externalUrl;

  // -- OCTI GENERATION SCENARIO FROM STIX --

  @OneToOne(mappedBy = "attackChain")
  @JsonProperty("scenario_security_coverage")
  @JsonIgnore
  private SecurityCoverage securityCoverage;

  // -- RECURRENCE --

  @Column(name = "attack_chain_recurrence")
  @JsonProperty("scenario_recurrence")
  @Queryable(filterable = true)
  private String recurrence; // cron expression

  @Column(name = "attack_chain_recurrence_start")
  @JsonProperty("scenario_recurrence_start")
  private Instant recurrenceStart;

  @Column(name = "attack_chain_recurrence_end")
  @JsonProperty("scenario_recurrence_end")
  private Instant recurrenceEnd;

  // -- 邮件演练遗留字段（V3 已 drop DB 列；Java 字段保留为 @Transient 以维持 API 兼容）--
  // TODO Phase 2+：彻底删除 + 移除 AttackChainService / AttackChainRunFactory / V1_DataImporter 中的引用

  @Transient
  @JsonIgnore
  private String header = "SIMULATION HEADER";

  @Transient
  @JsonIgnore
  private String footer = "SIMULATION FOOTER";

  @Transient
  @JsonIgnore
  private String from;

  @Transient
  @JsonIgnore
  private List<String> replyTos = new ArrayList<>();

  // -- 攻击编排（PRD §2.4 二开新增）--

  @Column(name = "execution_mode")
  @Enumerated(EnumType.STRING)
  @JsonProperty("scenario_execution_mode")
  @NotNull
  private ExecutionMode executionMode = ExecutionMode.STOP_ON_BLOCK;

  @Column(name = "validation_parameter_set_id")
  @JsonProperty("scenario_validation_parameter_set_id")
  private UUID validationParameterSetId;

  @Type(JsonBinaryType.class)
  @Column(name = "soc_correlation_rules", columnDefinition = "jsonb")
  @JsonProperty("scenario_soc_correlation_rules")
  private List<SocCorrelationRuleRef> socCorrelationRules = new ArrayList<>();

  // -- AUDIT --

  @Column(name = "attack_chain_created_at")
  @JsonProperty("scenario_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Column(name = "attack_chain_updated_at")
  @JsonProperty("scenario_updated_at")
  @NotNull
  @Queryable(filterable = true, sortable = true)
  @UpdateTimestamp
  private Instant updatedAt = now();

  // -- RELATION --

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attack_chain_custom_dashboard")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("scenario_custom_dashboard")
  @Schema(type = "string")
  private CustomDashboard customDashboard;

  @Getter
  @OneToMany(fetch = FetchType.EAGER)
  @JoinColumn(
      name = "grant_resource",
      referencedColumnName = "attack_chain_id",
      insertable = false,
      updatable = false)
  @SQLRestriction(
      "grant_resource_type = 'SCENARIO'") // Must be present in Grant.GRANT_RESOURCE_TYPE
  @JsonIgnore
  private List<Grant> grants = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(mappedBy = "attackChain", fetch = FetchType.LAZY)
  @JsonProperty("scenario_injects")
  @JsonSerialize(using = MultiIdListSerializer.class)
  @Getter(NONE)
  private Set<AttackChainNode> attackChainNodes = new HashSet<>();

  // UpdatedAt now used to sync with linked object
  public void setAttackChainNodes(Set<AttackChainNode> attackChainNodes) {
    this.updatedAt = now();
    this.attackChainNodes = attackChainNodes;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "attack_chains_teams",
      joinColumns = @JoinColumn(name = "attack_chain_id"),
      inverseJoinColumns = @JoinColumn(name = "team_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("scenario_teams")
  private List<Team> teams = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setTeams(List<Team> teams) {
    this.updatedAt = now();
    this.teams = teams;
  }

  @OneToMany(
      mappedBy = "attackChain",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("scenario_teams_users")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<AttackChainTeamUser> teamUsers = new ArrayList<>();

  @OneToMany(mappedBy = "attackChain", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonIgnore
  private List<Objective> objectives = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "attack_chains_tags",
      joinColumns = @JoinColumn(name = "attack_chain_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("scenario_tags")
  @Queryable(filterable = true, dynamicValues = true, path = "tags.id")
  private Set<Tag> tags = new HashSet<>();

  // UpdatedAt now used to sync with linked object
  public void setTags(Set<Tag> tags) {
    this.updatedAt = now();
    this.tags = tags;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "attack_chains_documents",
      joinColumns = @JoinColumn(name = "attack_chain_id"),
      inverseJoinColumns = @JoinColumn(name = "document_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("scenario_documents")
  private List<Document> documents = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(mappedBy = "attackChain", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("scenario_lessons_categories")
  private List<LessonsCategory> lessonsCategories = new ArrayList<>();

  @Getter
  @OneToMany(mappedBy = "attackChain")
  @JsonIgnore
  public List<Variable> variables = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "attack_chains_runs",
      joinColumns = @JoinColumn(name = "attack_chain_id"),
      inverseJoinColumns = @JoinColumn(name = "run_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("scenario_exercises")
  @Setter(NONE)
  private List<AttackChainRun> attackChainRuns;

  public void setAttackChainRuns(List<AttackChainRun> attackChainRuns) {
    if (attackChainRuns != null) {
      for (AttackChainRun attackChainRun : attackChainRuns) {
        if (attackChainRun != null) attackChainRun.setUpdatedAt(now());
      }
    }
    this.attackChainRuns = attackChainRuns;
    this.setUpdatedAt(now());
  }

  @Getter
  @Column(name = "attack_chain_lessons_anonymized")
  @JsonProperty("scenario_lessons_anonymized")
  private boolean lessonsAnonymized = false;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.SCENARIO;

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin() || getObservers().contains(user);
  }

  // -- LESSONS --

  public List<AttackChainNode> getAttackChainNodes() {
    return new ArrayList<>(this.attackChainNodes);
  }

  // -- SECURITY --

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("scenario_planners")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<User> getPlanners() {
    return getUsersByType(this.getGrants(), PLANNER);
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("scenario_observers")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<User> getObservers() {
    return getUsersByType(this.getGrants(), PLANNER, OBSERVER);
  }

  // -- STATISTICS --

  @JsonProperty("scenario_injects_statistics")
  public Map<String, Long> getAttackChainNodeStatistics() {
    return AttackChainNodeStatisticsHelper.getAttackChainNodeStatistics(this.getAttackChainNodes());
  }

  @JsonProperty("scenario_all_users_number")
  public long usersAllNumber() {
    return getTeams().stream().mapToLong(Team::getUsersNumber).sum();
  }

  @JsonProperty("scenario_users_number")
  public long usersNumber() {
    return getTeamUsers().stream().map(AttackChainTeamUser::getUser).distinct().count();
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("scenario_users")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<User> getUsers() {
    return getTeamUsers().stream().map(AttackChainTeamUser::getUser).distinct().toList();
  }

  @JsonProperty("scenario_communications_number")
  public long getCommunicationsNumber() {
    return getAttackChainNodes().stream().mapToLong(AttackChainNode::getCommunicationsNumber).sum();
  }

  // -- PLATFORMS --
  @JsonProperty("scenario_platforms")
  @Queryable(
      filterable = true,
      path = "attackChainNodes.nodeContract.platforms",
      clazz = String[].class)
  public List<PLATFORM_TYPE> getPlatforms() {
    return getAttackChainNodes().stream()
        .flatMap(
            attackChainNode ->
                attackChainNode.getNodeContract().map(NodeContract::getPlatforms).stream())
        .flatMap(Arrays::stream)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  // -- KILL CHAIN PHASES --
  @JsonProperty("scenario_kill_chain_phases")
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

  @Type(StringArrayType.class)
  @Column(name = "attack_chain_dependencies", columnDefinition = "text[]")
  @JsonProperty("scenario_dependencies")
  @Queryable(filterable = true, searchable = true, sortable = true)
  private Dependency[] dependencies;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
