package io.veriguard.database.model;

import static io.veriguard.helper.AttackChainNodeExpectationHelper.computeStatus;
import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.helper.MonoIdSerializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(name = "attack_chain_node_expectations")
@EntityListeners(ModelBaseListener.class)
public class AttackChainNodeExpectation implements Base, Cloneable {

  /**
   * Creates a shallow clone of this AttackChainNodeExpectation with deep-copied collections.
   *
   * <p>The following collections are deep-copied to prevent shared mutable state:
   *
   * <ul>
   *   <li>{@code signatures} - copied to new ArrayList
   *   <li>{@code results} - copied to new ArrayList
   * </ul>
   *
   * <p><strong>Important:</strong> The {@code traces} collection is intentionally NOT copied and
   * will be empty in the clone. Traces represent execution history that belongs to the original
   * expectation instance and should not be duplicated when cloning for a new execution context.
   *
   * @return a new AttackChainNodeExpectation with copied signatures and results, but empty traces
   */
  @Override
  public AttackChainNodeExpectation clone() {
    try {
      AttackChainNodeExpectation clone = (AttackChainNodeExpectation) super.clone();
      clone.signatures =
          this.signatures != null ? new ArrayList<>(this.signatures) : new ArrayList<>();
      clone.results = this.results != null ? new ArrayList<>(this.results) : new ArrayList<>();
      clone.traces = new ArrayList<>();
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone should be supported for Cloneable objects", e);
    }
  }

  public enum EXPECTATION_TYPE {
    TEXT,
    DOCUMENT,
    ARTICLE,
    CHALLENGE,
    MANUAL,
    PREVENTION,
    DETECTION,
    VULNERABILITY
  }

  public enum EXPECTATION_STATUS {
    FAILED,
    PENDING,
    PARTIAL,
    UNKNOWN,
    SUCCESS
  }

  @Queryable(filterable = true, label = "inject expectation type")
  @Setter
  @Column(name = "node_expectation_type")
  @JsonProperty("inject_expectation_type")
  @Enumerated(EnumType.STRING)
  @NotNull
  private EXPECTATION_TYPE type;

  // region basic
  @Id
  @NotBlank
  @Setter
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "node_expectation_id")
  @JsonProperty("inject_expectation_id")
  private String id;

  @Setter
  @Column(name = "node_expectation_name")
  @JsonProperty("inject_expectation_name")
  private String name;

  @Setter
  @Column(name = "node_expectation_description")
  @JsonProperty("inject_expectation_description")
  private String description;

  @Setter
  @Type(JsonType.class)
  @Column(name = "node_expectation_signatures")
  @JsonProperty("inject_expectation_signatures")
  private List<NodeExpectationSignature> signatures = new ArrayList<>();

  @Setter
  @Type(JsonType.class)
  @Column(name = "node_expectation_results")
  @JsonProperty("inject_expectation_results")
  private List<NodeExpectationResult> results = new ArrayList<>();

  @Setter
  @Column(name = "node_expectation_score")
  @JsonProperty("inject_expectation_score")
  private Double score;

  @JsonProperty("inject_expectation_status")
  public EXPECTATION_STATUS getResponse() {
    return computeStatus(this.getScore(), this.getExpectedScore());
  }

  @Setter
  @Column(name = "node_expectation_expected_score")
  @JsonProperty("inject_expectation_expected_score")
  @NotNull
  private Double expectedScore;

  /** Expiration time in seconds */
  @Setter
  @Column(name = "node_expectation_expiration_time")
  @JsonProperty("inject_expiration_time")
  @NotNull
  private Long expirationTime;

  @Queryable(filterable = true, label = "created at")
  @Setter
  @Column(name = "node_expectation_created_at")
  @JsonProperty("inject_expectation_created_at")
  @CreationTimestamp
  private Instant createdAt = now();

  @Queryable(filterable = true, label = "updated at")
  @Setter
  @Column(name = "node_expectation_updated_at")
  @JsonProperty("inject_expectation_updated_at")
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Setter
  @Column(name = "node_expectation_group")
  @JsonProperty("inject_expectation_group")
  private boolean expectationGroup;

  // endregion

  // region contextual relations
  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "run_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_exercise")
  @Schema(type = "string")
  private AttackChainRun attackChainRun;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "node_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_inject")
  @Schema(type = "string")
  private AttackChainNode attackChainNode;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_user")
  @Schema(type = "string")
  private User user;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "team_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_team")
  @Schema(type = "string")
  private Team team;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "agent_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_agent")
  @Schema(type = "string")
  private Agent agent;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_asset")
  @Schema(type = "string")
  private Asset asset;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_group_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_asset_group")
  @Schema(type = "string")
  private AssetGroup assetGroup;

  // endregion

  @OneToMany(
      mappedBy = "attackChainNodeExpectation",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @JsonProperty("inject_expectation_traces")
  private List<NodeExpectationTrace> traces = new ArrayList<>();

  public void setManual(final Agent agent, final Asset asset, final AssetGroup assetGroup) {
    this.type = EXPECTATION_TYPE.MANUAL;
    this.agent = agent;
    this.asset = asset;
    this.assetGroup = assetGroup;
  }

  public void setPrevention(final Agent agent, final Asset asset, final AssetGroup assetGroup) {
    this.type = EXPECTATION_TYPE.PREVENTION;
    this.agent = agent;
    this.asset = asset;
    this.assetGroup = assetGroup;
  }

  public void setDetection(final Agent agent, final Asset asset, final AssetGroup assetGroup) {
    this.type = EXPECTATION_TYPE.DETECTION;
    this.agent = agent;
    this.asset = asset;
    this.assetGroup = assetGroup;
  }

  public void setVulnerability(final Agent agent, final Asset asset, final AssetGroup assetGroup) {
    this.type = EXPECTATION_TYPE.VULNERABILITY;
    this.agent = agent;
    this.asset = asset;
    this.assetGroup = assetGroup;
  }

  public boolean isUserHasAccess(User user) {
    if (getAttackChainRun() != null) {
      return getAttackChainRun().isUserHasAccess(user);
    }
    return user.isAdmin();
  }

  @JsonProperty("target_id")
  public String getTargetId() {
    if (user != null) {
      return user.getId();
    } else if (team != null) {
      return team.getId();
    } else if (agent != null) {
      return agent.getId();
    } else if (asset != null) {
      return asset.getId();
    } else if (assetGroup != null) {
      return assetGroup.getId();
    } else {
      throw new IllegalStateException(
          "InjectExpectation must have at least one target (user, team, agent, asset, or assetGroup)");
    }
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
