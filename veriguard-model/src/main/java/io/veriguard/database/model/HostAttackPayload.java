package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

/**
 * Persistence form of a Veriguard "HostAttack" payload (P1.1 — HIDS 主机入侵检测验证).
 *
 * <p>Wire-format mirrors the HIDS importer JSON (datasets/importer/hids_p1/*.json) so {@code POST
 * /api/payloads/upsert} round-trips without field renames. Columns are added in {@code
 * V25__host_attack_traffic_pattern_payload_columns.sql}.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(HostAttackPayload.HOST_ATTACK_TYPE)
@EntityListeners(ModelBaseListener.class)
public class HostAttackPayload extends Payload {

  public static final String HOST_ATTACK_TYPE = "HostAttack";

  @JsonProperty("payload_type")
  private String type = HOST_ATTACK_TYPE;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_hids_category")
  @JsonProperty("hids_category")
  @NotNull
  private String hidsCategory;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_hids_execution_mode")
  @JsonProperty("hids_execution_mode")
  @NotNull
  private String hidsExecutionMode;

  @Column(name = "payload_hids_command_template")
  @JsonProperty("hids_command_template")
  @NotNull
  private String hidsCommandTemplate;

  @Column(name = "payload_hids_artifact_path")
  @JsonProperty("hids_artifact_path")
  private String hidsArtifactPath;

  @Type(JsonType.class)
  @Column(name = "payload_hids_expected_artifacts", columnDefinition = "jsonb")
  @JsonProperty("hids_expected_artifacts")
  private List<String> hidsExpectedArtifacts = new ArrayList<>();

  public HostAttackPayload() {}

  public HostAttackPayload(String id, String type, String name) {
    super(id, type, name);
  }
}
