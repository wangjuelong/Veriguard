package io.veriguard.database.model;

import static io.veriguard.database.model.CollectExecutionStatus.COLLECTING;
import static io.veriguard.database.specification.AttackChainNodeSpecification.VALID_TESTABLE_TYPES;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.database.converter.ContentConverter;
import io.veriguard.helper.MonoIdSerializer;
import io.veriguard.helper.MultiIdListSerializer;
import io.veriguard.helper.MultiIdSetSerializer;
import io.veriguard.helper.MultiModelSerializer;
import io.veriguard.helper.NodeModelHelper;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Entity
@Table(name = "attack_chain_nodes")
@EntityListeners(ModelBaseListener.class)
@Slf4j
@Grantable(Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING)
public class AttackChainNode implements GrantableBase, Injection {

  public static final int SPEED_STANDARD = 1; // Standard speed define by the user.
  public static final String ID_COLUMN_NAME = "node_id";
  public static final String ID_FIELD_NAME = "id";

  public static final Comparator<AttackChainNode> executionComparator =
      (o1, o2) -> {
        if (o1.getDate().isPresent() && o2.getDate().isPresent()) {
          return o1.getDate().get().compareTo(o2.getDate().get());
        }
        if (o1.getId() != null && o2.getId() != null) {
          return o1.getId().compareTo(o2.getId());
        }
        return 0;
      };

  @Getter
  @Id
  @Column(name = ID_COLUMN_NAME)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("node_id")
  @NotBlank
  private String id;

  @Getter
  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "node_title")
  @JsonProperty("node_title")
  @NotBlank
  private String title;

  @Getter
  @Column(name = "node_description")
  @JsonProperty("node_description")
  private String description;

  @Getter
  @Column(name = "node_country")
  @JsonProperty("node_country")
  private String country;

  @Getter
  @Column(name = "node_city")
  @JsonProperty("node_city")
  private String city;

  @Getter
  @Column(name = "node_enabled")
  @JsonProperty("node_enabled")
  private boolean enabled = true;

  @Getter
  @Column(name = "node_trigger_now_date")
  @JsonProperty("node_trigger_now_date")
  private Instant triggerNowDate;

  @Getter
  @Column(name = "node_content")
  @Convert(converter = ContentConverter.class)
  @JsonProperty("node_content")
  private ObjectNode content;

  @Getter
  @Column(name = "node_created_at")
  @JsonProperty("node_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Getter
  @Column(name = "node_updated_at")
  @Queryable(filterable = true, sortable = true)
  @JsonProperty("node_updated_at")
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Getter
  @Column(name = "node_all_teams")
  @JsonProperty("node_all_teams")
  private boolean allTeams;

  @Getter
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "node_attack_chain_run_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("node_attack_chain_run")
  @Schema(type = "string")
  private AttackChainRun attackChainRun;

  @Getter
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "node_attack_chain_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("node_attack_chain")
  @Schema(type = "string")
  private AttackChain attackChain;

  @Getter
  @OneToMany(
      mappedBy = "attackChainNodeChildren",
      fetch = FetchType.EAGER,
      orphanRemoval = true,
      cascade = CascadeType.ALL)
  @JsonProperty("node_depends_on")
  private List<AttackChainEdge> dependsOn = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setDependsOn(List<AttackChainEdge> dependsOn) {
    this.updatedAt = now();
    this.dependsOn = dependsOn;
  }

  @Getter
  @Column(name = "node_depends_duration")
  @JsonProperty("node_depends_duration")
  @NotNull
  @Min(value = 0L, message = "The value must be positive")
  @Queryable(sortable = true)
  private Long dependsDuration;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "node_contract_id")
  @JsonProperty("node_injector_contract")
  @Queryable(filterable = true, dynamicValues = true, path = "nodeContract.nodeExecutor.id")
  private NodeContract nodeContract;

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "node_user")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("node_user")
  @Schema(type = "string")
  private User user;

  // CascadeType.ALL is required here because attackChainNode status are embedded
  @OneToOne(mappedBy = "attackChainNode", cascade = CascadeType.ALL, orphanRemoval = true)
  @JsonProperty("node_status")
  @Queryable(filterable = true, sortable = true)
  private AttackChainNodeStatus status;

  @Column(name = "node_collect_status", nullable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("node_collect_status")
  @Getter
  private CollectExecutionStatus collectExecutionStatus = COLLECTING;

  // -- 攻击编排（PRD §2.4 二开新增）--

  /** Phase 12c-Biii: 动态节点标记. true 表示此节点由 dynamic_filter 派生，run 结束后自动 cleanup. */
  @Getter
  @Column(name = "is_dynamic", nullable = false)
  @JsonProperty("node_is_dynamic")
  private boolean isDynamic = false;

  @Getter
  @Column(name = "repeat_count", nullable = false)
  @JsonProperty("node_repeat_count")
  @Min(value = 1L)
  private int repeatCount = 1;

  @Getter
  @Column(name = "repeat_interval_seconds", nullable = false)
  @JsonProperty("node_repeat_interval_seconds")
  @Min(value = 0L)
  private long repeatIntervalSeconds = 0L;

  @Getter
  @Column(name = "validation_parameter_set_id")
  @JsonProperty("node_validation_parameter_set_id")
  private UUID validationParameterSetId;

  @Getter
  @Column(name = "node_state")
  @Enumerated(EnumType.STRING)
  @JsonProperty("node_node_state")
  private NodeState nodeState;

  @Getter
  @Column(name = "current_iteration", nullable = false)
  @JsonProperty("node_current_iteration")
  @Min(value = 0L)
  private int currentIteration = 0;

  // UpdatedAt now used to sync with linked object
  public void setStatus(AttackChainNodeStatus status) {
    this.updatedAt = now();
    this.status = status;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "attack_chain_nodes_tags",
      joinColumns = @JoinColumn(name = "node_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("node_tags")
  @Queryable(filterable = true, dynamicValues = true)
  private Set<Tag> tags = new HashSet<>();

  // UpdatedAt now used to sync with linked object
  public void setTags(Set<Tag> tags) {
    this.updatedAt = now();
    this.tags = tags;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "attack_chain_nodes_teams",
      joinColumns = @JoinColumn(name = "node_id"),
      inverseJoinColumns = @JoinColumn(name = "team_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("node_teams")
  @Queryable(filterable = true, dynamicValues = true, path = "teams.id")
  private List<Team> teams = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setTeams(List<Team> teams) {
    this.updatedAt = now();
    this.teams = teams;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "attack_chain_nodes_assets",
      joinColumns = @JoinColumn(name = "node_id"),
      inverseJoinColumns = @JoinColumn(name = "asset_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("node_assets")
  @Queryable(filterable = true, dynamicValues = true, path = "assets.id")
  private List<Asset> assets = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setAssets(List<Asset> assets) {
    this.updatedAt = now();
    this.assets = assets;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "attack_chain_nodes_asset_groups",
      joinColumns = @JoinColumn(name = "node_id"),
      inverseJoinColumns = @JoinColumn(name = "asset_group_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("node_asset_groups")
  @Queryable(filterable = true, dynamicValues = true, path = "assetGroups.id")
  private List<AssetGroup> assetGroups = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setAssetGroups(List<AssetGroup> assetGroups) {
    this.updatedAt = now();
    this.assetGroups = assetGroups;
  }

  // CascadeType.ALL is required here because of complex relationships
  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @OneToMany(
      mappedBy = "attackChainNode",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("node_documents")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<AttackChainNodeDocument> documents = new ArrayList<>();

  // CascadeType.ALL is required here because communications are embedded
  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @OneToMany(
      mappedBy = "attackChainNode",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("node_communications")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<Communication> communications = new ArrayList<>();

  // CascadeType.ALL is required here because expectations are embedded
  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @OneToMany(
      mappedBy = "attackChainNode",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("node_expectations")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<AttackChainNodeExpectation> expectations = new ArrayList<>();

  @JsonIgnore
  @Getter
  @OneToMany(mappedBy = "attackChainNode", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Finding> findings = new ArrayList<>();

  @Getter @Setter @Transient private boolean isListened = true;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.INJECT;

  @Getter
  @OneToMany
  @JoinColumn(
      name = "grant_resource",
      referencedColumnName = "node_id",
      insertable = false,
      updatable = false)
  @SQLRestriction(
      "grant_resource_type = 'ATOMIC_TESTING'") // Must be present in Grant.GRANT_RESOURCE_TYPE
  @JsonIgnore
  private List<Grant> grants = new ArrayList<>();

  // region transient
  @Transient
  public String getHeader() {
    return ofNullable(this.getAttackChainRun()).map(AttackChainRun::getHeader).orElse("");
  }

  @Transient
  public String getFooter() {
    return ofNullable(this.getAttackChainRun()).map(AttackChainRun::getFooter).orElse("");
  }

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    if (this.getAttackChainRun() != null) {
      return this.getAttackChainRun().isUserHasAccess(user);
    }
    if (this.getAttackChain() != null) {
      return this.getAttackChain().isUserHasAccess(user);
    }
    // For atomic testing, only admins or planners have access
    return user.isAdmin() || user.isPlanner();
  }

  @JsonIgnore
  public void clean() {
    this.status = null;
    this.communications.clear();
    this.expectations.clear();
    this.findings.clear();
    this.setCollectExecutionStatus(COLLECTING);
  }

  @JsonProperty("node_users_number")
  public long getNumberOfTargetUsers() {
    if (this.getAttackChainRun() == null) {
      return 0L;
    }
    if (this.isAllTeams()) {
      return this.getAttackChainRun().usersNumber();
    }
    return getTeams().stream()
        .map(team -> team.getUsersNumberInAttackChainRun(getAttackChainRun().getId()))
        .reduce(Long::sum)
        .orElse(0L);
  }

  @JsonProperty("node_ready")
  public boolean isReady() {
    return NodeModelHelper.isReady(
        getNodeContract().orElse(null),
        getContent(),
        isAllTeams(),
        getTeams().stream().map(Team::getId).collect(Collectors.toList()),
        getAssets().stream().map(Asset::getId).collect(Collectors.toList()),
        getAssetGroups().stream().map(AssetGroup::getId).collect(Collectors.toList()));
  }

  @JsonIgnore
  public Instant computeAttackChainNodeDate(Instant source, int speed) {
    return NodeModelHelper.computeAttackChainNodeDate(
        source, speed, getDependsDuration(), getAttackChainRun());
  }

  @JsonProperty("node_date")
  public Optional<Instant> getDate() {
    // If a trigger now was executed for this attackChainNode linked to an attackChainRun, we ignore
    // pauses and we
    // set attackChainNode inside of a range of execution
    if (getAttackChainRun() != null && triggerNowDate != null) {
      Optional<Instant> attackChainRunStartOpt = getAttackChainRun().getStart();
      if (attackChainRunStartOpt.isPresent()
          && (attackChainRunStartOpt.get().equals(triggerNowDate)
              || attackChainRunStartOpt.get().isBefore(triggerNowDate))) {
        return Optional.of(now().minusSeconds(60));
      }
    }
    return NodeModelHelper.getDate(getAttackChainRun(), getAttackChain(), getDependsDuration());
  }

  @JsonIgnore
  public AttackChainNode getAttackChainNode() {
    return this;
  }

  @JsonIgnore
  public boolean isNotExecuted() {
    return this.getStatus().isEmpty();
  }

  @JsonIgnore
  public boolean isPastAttackChainNode() {
    return this.getDate().map(date -> date.isBefore(now())).orElse(false);
  }

  @JsonIgnore
  public boolean isFutureAttackChainNode() {
    return this.getDate().map(date -> date.isAfter(now())).orElse(false);
  }

  // endregion

  public Optional<NodeContract> getNodeContract() {
    return Optional.ofNullable(this.nodeContract);
  }

  public Optional<AttackChainNodeStatus> getStatus() {
    return ofNullable(this.status);
  }

  @JsonProperty("node_communications_number")
  public long getCommunicationsNumber() {
    return this.getCommunications().size();
  }

  @JsonProperty("node_communications_not_ack_number")
  public long getCommunicationsNotAckNumber() {
    return this.getCommunications().stream()
        .filter(communication -> !communication.getAck())
        .count();
  }

  @JsonProperty("node_sent_at")
  public Instant getSentAt() {
    return NodeModelHelper.getSentAt(this.getStatus());
  }

  @JsonProperty("node_kill_chain_phases")
  @Queryable(
      filterable = true,
      dynamicValues = true,
      path = "nodeContract.attackPatterns.killChainPhases.id")
  public List<KillChainPhase> getKillChainPhases() {
    return getNodeContract()
        .map(
            ic ->
                ic.getAttackPatterns().stream()
                    .flatMap(attackPattern -> attackPattern.getKillChainPhases().stream())
                    .distinct()
                    .collect(Collectors.toList()))
        .orElseGet(ArrayList::new);
  }

  @JsonProperty("node_attack_patterns")
  @Queryable(filterable = true, dynamicValues = true, path = "nodeContract.attackPatterns.id")
  public List<AttackPattern> getAttackPatterns() {
    return getNodeContract().map(NodeContract::getAttackPatterns).orElseGet(ArrayList::new);
  }

  @JsonProperty("node_type")
  @Queryable(filterable = true, path = "nodeContract.labels", clazz = Map.class)
  private String getType() {
    return getNodeContract()
        .map(NodeContract::getNodeExecutor)
        .map(NodeExecutor::getType)
        .orElse(null);
  }

  @JsonIgnore
  @JsonProperty("node_platforms")
  @Queryable(filterable = true, path = "nodeContract.platforms", clazz = String[].class)
  private Endpoint.PLATFORM_TYPE[] getPlatforms() {
    return getNodeContract().map(NodeContract::getPlatforms).orElse(new Endpoint.PLATFORM_TYPE[0]);
  }

  @JsonProperty("node_contract_domains")
  @Queryable(
      filterable = true,
      paths = {"injectorContract.domains.id", "injectorContract.payload.domains.id"},
      dynamicValues = true,
      clazz = String[].class)
  private Set<Domain> getDomains() {
    return getNodeContract().map(NodeContract::getDomains).orElseGet(HashSet::new);
  }

  @JsonIgnore
  public boolean isAtomicTesting() {
    return this.attackChainRun == null && this.attackChain == null;
  }

  @JsonProperty("node_testable")
  public boolean getAttackChainNodeTestable() {
    return VALID_TESTABLE_TYPES.contains(this.getType());
  }

  @JsonIgnore
  public Optional<Payload> getPayload() {
    return Optional.ofNullable(
        this.getNodeContract().isPresent() ? this.getNodeContract().get().getPayload() : null);
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
    if (base.getId() == null || this.getId() == null) {
      return false;
    }
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @JsonIgnore
  public String getParentResourceId() {
    return this.getAttackChain() != null
        ? this.getAttackChain().getId()
        : this.getAttackChainRun() != null ? this.getAttackChainRun().getId() : this.getId();
  }

  @JsonIgnore
  public ResourceType getParentResourceType() {
    return this.getAttackChain() != null
        ? ResourceType.SCENARIO
        : this.getAttackChainRun() != null ? ResourceType.SIMULATION : ResourceType.ATOMIC_TESTING;
  }
}
