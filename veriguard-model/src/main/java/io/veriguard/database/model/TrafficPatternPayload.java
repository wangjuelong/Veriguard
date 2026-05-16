package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Persistence form of a Veriguard "TrafficPattern" payload (P1.2 — NTA 网络流量行为分析验证).
 *
 * <p>Wire-format mirrors the NTA importer JSON (datasets/importer/nta_p1/*.json) so {@code POST
 * /api/payloads/upsert} round-trips without field renames. Columns are added in {@code
 * V25__host_attack_traffic_pattern_payload_columns.sql}.
 *
 * <p>Multi-tuple 四元组 metadata continues to live on {@link NetworkTraffic} (V24); TrafficPattern
 * carries higher-level signature / category fields used by the NTA dataset.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(TrafficPatternPayload.TRAFFIC_PATTERN_TYPE)
@EntityListeners(ModelBaseListener.class)
public class TrafficPatternPayload extends Payload {

  public static final String TRAFFIC_PATTERN_TYPE = "TrafficPattern";

  @JsonProperty("payload_type")
  private String type = TRAFFIC_PATTERN_TYPE;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_nta_category")
  @JsonProperty("nta_category")
  @NotNull
  private String ntaCategory;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_nta_protocol")
  @JsonProperty("nta_protocol")
  @NotNull
  private String ntaProtocol;

  @Column(name = "payload_nta_signature")
  @JsonProperty("nta_signature")
  @NotNull
  private String ntaSignature;

  @Column(name = "payload_nta_pcap_path")
  @JsonProperty("nta_pcap_path")
  private String ntaPcapPath;

  @Column(name = "payload_nta_detection_hint")
  @JsonProperty("nta_detection_hint")
  private String ntaDetectionHint;

  public TrafficPatternPayload() {}

  public TrafficPatternPayload(String id, String type, String name) {
    super(id, type, name);
  }
}
