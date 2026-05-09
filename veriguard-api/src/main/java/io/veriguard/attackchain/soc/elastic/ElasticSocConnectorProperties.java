package io.veriguard.attackchain.soc.elastic;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code veriguard.soc.elastic.*} 配置（spec §4.4）.
 *
 * <p>凭证仅来自 {@code application.properties} + 环境变量；<b>不入数据库</b>（spec §4.5）。{@code enabled=false} 时
 * {@link ElasticSocConnector} 不会被 {@code @ConditionalOnProperty} 实例化。
 */
@ConfigurationProperties(prefix = "veriguard.soc.elastic")
@Getter
@Setter
public class ElasticSocConnectorProperties {

  /** 是否启用 Elastic SOC connector。默认 {@code false}（生产 ops 在 docker compose env 里打开）。 */
  private boolean enabled = false;

  /** Elastic 集群 URL，例如 {@code https://elastic.internal:9200}。 */
  private String url;

  /** Elastic API key（推荐）；如果走 username/password 仅用于本地调试。 */
  private String apiKey;

  /** 告警索引 pattern，默认 {@code .alerts-security.alerts-*}（Kibana Security 默认）。 */
  private String alertIndex = ".alerts-security.alerts-*";

  /** Kibana Detection Engine API 路径，默认 {@code /api/detection_engine/rules/_find}。 */
  private String detectionRulesApi = "/api/detection_engine/rules/_find";

  /** 单次查询超时，秒。 */
  private int queryTimeoutSeconds = 10;

  /** {@code listAvailableRules()} 本地缓存 TTL，秒；默认 5 分钟。 */
  private int rulesCacheTtlSeconds = 300;
}
