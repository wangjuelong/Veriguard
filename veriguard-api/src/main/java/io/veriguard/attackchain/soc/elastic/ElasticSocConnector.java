package io.veriguard.attackchain.soc.elastic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.attackchain.soc.AvailableRule;
import io.veriguard.attackchain.soc.CorrelationMatch;
import io.veriguard.attackchain.soc.CorrelationRuleQuery;
import io.veriguard.attackchain.soc.DetectionMatch;
import io.veriguard.attackchain.soc.HealthCheckResult;
import io.veriguard.attackchain.soc.NodeAlertQuery;
import io.veriguard.attackchain.soc.SocAlertConnector;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elastic SIEM 参考实现（spec §4.4）.
 *
 * <p>配置见 {@link ElasticSocConnectorProperties}；通过 {@code veriguard.soc.elastic.enabled=true} 启用，
 * 否则不被 Spring 实例化（test / 开发环境默认禁用，避免连不存在的 Elastic）。
 *
 * <p>失败显式：HTTP 错误向上抛 {@link RuntimeException}，由调用方决定 retry / UNKNOWN trace（spec §3.6）。
 */
@Component
@ConditionalOnProperty(name = "veriguard.soc.elastic.enabled", havingValue = "true")
@EnableConfigurationProperties(ElasticSocConnectorProperties.class)
@Slf4j
public class ElasticSocConnector implements SocAlertConnector {

  private static final String CONNECTOR_ID = "elastic";
  private static final String DISPLAY_NAME = "Elastic SIEM";

  private final ElasticSocConnectorProperties props;
  private final HttpClient httpClient;
  private final ObjectMapper mapper;

  private final AtomicReference<CachedRules> rulesCache = new AtomicReference<>();

  public ElasticSocConnector(ElasticSocConnectorProperties props) {
    this(props, defaultHttpClient(props), new ObjectMapper());
  }

  /** 测试可注入 HttpClient + ObjectMapper 替身。 */
  ElasticSocConnector(
      ElasticSocConnectorProperties props, HttpClient httpClient, ObjectMapper mapper) {
    if (props.getUrl() == null || props.getUrl().isBlank()) {
      throw new IllegalStateException(
          "veriguard.soc.elastic.enabled=true but veriguard.soc.elastic.url is missing");
    }
    if (props.getApiKey() == null || props.getApiKey().isBlank()) {
      throw new IllegalStateException(
          "veriguard.soc.elastic.enabled=true but veriguard.soc.elastic.api-key is missing");
    }
    this.props = props;
    this.httpClient = httpClient;
    this.mapper = mapper;
  }

  private static HttpClient defaultHttpClient(ElasticSocConnectorProperties props) {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(props.getQueryTimeoutSeconds()))
        .build();
  }

  @Override
  public String getConnectorId() {
    return CONNECTOR_ID;
  }

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public List<DetectionMatch> queryNodeAlert(NodeAlertQuery query) {
    ObjectNode esQuery = mapper.createObjectNode();
    ArrayNode must = esQuery.putObject("query").putObject("bool").putArray("must");
    must.addObject().putObject("term").put("event.type", "alert");
    must.addObject()
        .putObject("range")
        .putObject("signal.original_time")
        .put("gte", query.injectExecutedAt().toString())
        .put("lte", query.queryWindowEnd().toString());
    if (!query.targetIps().isEmpty()) {
      ArrayNode ips = mapper.createArrayNode();
      query.targetIps().forEach(ips::add);
      must.addObject().putObject("terms").set("host.ip", ips);
    }
    JsonNode response = postJson(props.getAlertIndex() + "/_search", esQuery);
    return parseDetectionHits(response);
  }

  @Override
  public List<CorrelationMatch> queryCorrelationRule(CorrelationRuleQuery query) {
    ObjectNode esQuery = mapper.createObjectNode();
    ArrayNode must = esQuery.putObject("query").putObject("bool").putArray("must");
    must.addObject().putObject("term").put("event.type", "signal");
    must.addObject().putObject("term").put("signal.rule.id", query.ruleId());
    must.addObject()
        .putObject("range")
        .putObject("@timestamp")
        .put("gte", query.runStartedAt().toString())
        .put("lte", query.queryWindowEnd().toString());
    JsonNode response = postJson(props.getAlertIndex() + "/_search", esQuery);
    return parseCorrelationHits(response, query.ruleId());
  }

  @Override
  public List<AvailableRule> listAvailableRules() {
    CachedRules cached = rulesCache.get();
    Instant now = Instant.now();
    if (cached != null
        && cached.fetchedAt.plusSeconds(props.getRulesCacheTtlSeconds()).isAfter(now)) {
      return cached.rules;
    }
    JsonNode response = getJson(props.getDetectionRulesApi() + "?per_page=200");
    List<AvailableRule> rules = new ArrayList<>();
    JsonNode data = response.path("data");
    if (data.isArray()) {
      for (JsonNode rule : data) {
        String ruleId = rule.path("rule_id").asText(rule.path("id").asText(""));
        String name = rule.path("name").asText("");
        if (ruleId.isBlank() || name.isBlank()) {
          continue;
        }
        rules.add(
            new AvailableRule(
                ruleId,
                name,
                emptyToNull(rule.path("description").asText("")),
                emptyToNull(rule.path("type").asText(""))));
      }
    }
    rulesCache.set(new CachedRules(now, List.copyOf(rules)));
    return rules;
  }

  @Override
  public HealthCheckResult checkHealth() {
    try {
      JsonNode health = getJson("/_cluster/health");
      String status = health.path("status").asText("");
      if (status.isBlank()) {
        return HealthCheckResult.unhealthy("missing cluster status");
      }
      return switch (status) {
        case "green" -> HealthCheckResult.healthy();
        case "yellow" -> HealthCheckResult.degraded("cluster yellow");
        default -> HealthCheckResult.unhealthy("cluster " + status);
      };
    } catch (RuntimeException e) {
      log.warn("Elastic SOC connector health check failed: {}", e.getMessage());
      return HealthCheckResult.unhealthy(extractReason(e));
    }
  }

  // --- HTTP helpers ---

  private JsonNode postJson(String path, JsonNode body) {
    HttpRequest request =
        baseRequest(path)
            .POST(HttpRequest.BodyPublishers.ofString(serialize(body)))
            .header("Content-Type", "application/json")
            .build();
    return execute(request);
  }

  private JsonNode getJson(String path) {
    HttpRequest request = baseRequest(path).GET().build();
    return execute(request);
  }

  private HttpRequest.Builder baseRequest(String path) {
    String base = props.getUrl().replaceAll("/+$", "");
    String suffix = path.startsWith("/") ? path : "/" + path;
    return HttpRequest.newBuilder()
        .uri(URI.create(base + suffix))
        .timeout(Duration.ofSeconds(props.getQueryTimeoutSeconds()))
        .header("Authorization", "ApiKey " + props.getApiKey())
        .header("Accept", "application/json");
  }

  private JsonNode execute(HttpRequest request) {
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new ElasticSocException(
            "HTTP " + response.statusCode() + " from Elastic: " + truncate(response.body()));
      }
      return mapper.readTree(response.body());
    } catch (IOException e) {
      throw new ElasticSocException("I/O calling Elastic: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ElasticSocException("Interrupted calling Elastic", e);
    }
  }

  private String serialize(JsonNode node) {
    try {
      return mapper.writeValueAsString(node);
    } catch (IOException e) {
      throw new ElasticSocException("JSON serialize failure", e);
    }
  }

  private List<DetectionMatch> parseDetectionHits(JsonNode response) {
    List<DetectionMatch> matches = new ArrayList<>();
    JsonNode hits = response.path("hits").path("hits");
    if (!hits.isArray()) {
      return matches;
    }
    for (JsonNode hit : hits) {
      JsonNode src = hit.path("_source");
      String alertId = hit.path("_id").asText("");
      String ruleName = src.path("signal").path("rule").path("name").asText("");
      Instant triggeredAt = parseInstant(src.path("signal").path("original_time").asText(""));
      int score = src.path("signal").path("rule").path("risk_score").asInt(0);
      matches.add(new DetectionMatch(alertId, ruleName, triggeredAt, score, toMap(src)));
    }
    return matches;
  }

  private List<CorrelationMatch> parseCorrelationHits(JsonNode response, String ruleId) {
    List<CorrelationMatch> matches = new ArrayList<>();
    JsonNode hits = response.path("hits").path("hits");
    if (!hits.isArray()) {
      return matches;
    }
    for (JsonNode hit : hits) {
      JsonNode src = hit.path("_source");
      String incidentId = hit.path("_id").asText("");
      String name = src.path("signal").path("rule").path("name").asText(ruleId);
      Instant triggeredAt = parseInstant(src.path("@timestamp").asText(""));
      int score = src.path("signal").path("rule").path("risk_score").asInt(0);
      matches.add(new CorrelationMatch(incidentId, name, triggeredAt, score, toMap(src)));
    }
    return matches;
  }

  private static Instant parseInstant(String iso) {
    if (iso == null || iso.isBlank()) {
      return Instant.EPOCH;
    }
    return Instant.parse(iso);
  }

  private Map<String, Object> toMap(JsonNode node) {
    if (!node.isObject()) {
      return Map.of();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    node.fieldNames()
        .forEachRemaining(
            name -> result.put(name, mapper.convertValue(node.get(name), Object.class)));
    return Map.copyOf(result);
  }

  private static String emptyToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  private static String truncate(String body) {
    if (body == null) {
      return "";
    }
    return body.length() > 200 ? body.substring(0, 200) + "..." : body;
  }

  private static String extractReason(Throwable e) {
    if (e.getMessage() == null) {
      return e.getClass().getSimpleName();
    }
    return truncate(e.getMessage());
  }

  /** {@link #listAvailableRules()} 的 5 分钟缓存条目。 */
  private record CachedRules(Instant fetchedAt, List<AvailableRule> rules) {}

  /** Elastic 调用包装异常；调用方一律按 {@link RuntimeException} 处理（spec §3.6）。 */
  static class ElasticSocException extends RuntimeException {
    ElasticSocException(String message) {
      super(message);
    }

    ElasticSocException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  // 便于测试访问内部缓存（package-private）
  void clearRulesCache() {
    rulesCache.set(null);
  }

  Map<String, Object> snapshotConfig() {
    Map<String, Object> snap = new HashMap<>();
    snap.put("url", props.getUrl());
    snap.put("alertIndex", props.getAlertIndex());
    snap.put("rulesCacheTtlSeconds", props.getRulesCacheTtlSeconds());
    return Map.copyOf(snap);
  }
}
