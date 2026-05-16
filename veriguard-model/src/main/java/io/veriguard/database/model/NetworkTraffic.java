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

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(NetworkTraffic.NETWORK_TRAFFIC_TYPE)
@EntityListeners(ModelBaseListener.class)
public class NetworkTraffic extends Payload {

  public static final String NETWORK_TRAFFIC_TYPE = "NetworkTraffic";

  @JsonProperty("payload_type")
  private String type = NETWORK_TRAFFIC_TYPE;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "network_traffic_ip_src")
  @JsonProperty("network_traffic_ip_src")
  @NotNull
  private String ipSrc;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "network_traffic_ip_dst")
  @JsonProperty("network_traffic_ip_dst")
  @NotNull
  private String ipDst;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "network_traffic_port_src")
  @JsonProperty("network_traffic_port_src")
  @NotNull
  private Integer portSrc;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "network_traffic_port_dst")
  @JsonProperty("network_traffic_port_dst")
  @NotNull
  private Integer portDst;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "network_traffic_protocol")
  @JsonProperty("network_traffic_protocol")
  @NotNull
  private String protocol;

  /**
   * 额外四元组列表 —— IPv6 安全验证系统招标 §4 "支持同一个流量安全验证用例中，包含多个端口不同的四元组".
   *
   * <p>主四元组继续由上方 5 个标量列承载（向后兼容、可索引、可过滤）。本列承载额外四元组，旧 payload 此列为 null / 空列表，新 payload 可任意追加。
   *
   * <p>读侧统一通过 {@link #allTuples()} 拿到主 + 额外的合并列表，无需区分两层来源。
   */
  @Type(JsonType.class)
  @Column(name = "network_traffic_extra_tuples", columnDefinition = "jsonb")
  @JsonProperty("network_traffic_extra_tuples")
  private List<NetworkTrafficTuple> extraTuples = new ArrayList<>();

  public NetworkTraffic() {}

  public NetworkTraffic(String id, String type, String name) {
    super(id, type, name);
  }

  /**
   * 返回主四元组 + 额外四元组的合并列表（主在首位）。调用方据此遍历单条 payload 声明的全部 (src/dst/port/proto) 组合，无需关心主/额外的存储差异。
   *
   * <p>主四元组若任一字段为 null（理论不可达，受 {@link NotNull} 约束），不进入返回列表 —— 这与未来 PR 中将主元组改为可选时的行为保持一致。
   */
  public List<NetworkTrafficTuple> allTuples() {
    List<NetworkTrafficTuple> result = new ArrayList<>();
    if (ipSrc != null && ipDst != null && portSrc != null && portDst != null && protocol != null) {
      result.add(new NetworkTrafficTuple(ipSrc, ipDst, portSrc, portDst, protocol));
    }
    if (extraTuples != null) {
      result.addAll(extraTuples);
    }
    return result;
  }
}
