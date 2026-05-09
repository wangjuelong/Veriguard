package io.veriguard.attackchain.soc.elastic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.attackchain.soc.AvailableRule;
import io.veriguard.attackchain.soc.CorrelationMatch;
import io.veriguard.attackchain.soc.CorrelationRuleQuery;
import io.veriguard.attackchain.soc.DetectionMatch;
import io.veriguard.attackchain.soc.HealthCheckResult;
import io.veriguard.attackchain.soc.NodeAlertQuery;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ElasticSocConnectorTest {

  private ElasticSocConnectorProperties props;
  private HttpClient httpClient;
  private ObjectMapper mapper;
  private ElasticSocConnector connector;

  @BeforeEach
  void setUp() {
    props = new ElasticSocConnectorProperties();
    props.setEnabled(true);
    props.setUrl("https://elastic.test:9200");
    props.setApiKey("test-key");
    props.setQueryTimeoutSeconds(5);
    props.setRulesCacheTtlSeconds(60);
    httpClient = mock(HttpClient.class);
    mapper = new ObjectMapper();
    connector = new ElasticSocConnector(props, httpClient, mapper);
  }

  // ---- 启动校验 ----

  @Test
  @DisplayName("缺 URL → 启动失败")
  void blank_url_fails_fast() {
    props.setUrl("");
    assertThatThrownBy(() -> new ElasticSocConnector(props, httpClient, mapper))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("url");
  }

  @Test
  @DisplayName("缺 apiKey → 启动失败")
  void blank_api_key_fails_fast() {
    props.setApiKey(null);
    assertThatThrownBy(() -> new ElasticSocConnector(props, httpClient, mapper))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("api-key");
  }

  // ---- 节点级查询 ----

  @Test
  @DisplayName("queryNodeAlert: 拼带 host.ip 的 bool 查询，解析 hits")
  void query_node_alert_with_ips() throws IOException, InterruptedException {
    stubResponse(
        200,
        """
        {"hits":{"hits":[
          {"_id":"a1","_source":{"signal":{"original_time":"2026-05-01T00:00:00Z","rule":{"name":"shell","risk_score":73}}}},
          {"_id":"a2","_source":{"signal":{"original_time":"2026-05-01T00:01:00Z","rule":{"name":"shell","risk_score":50}}}}
        ]}}
        """);

    Instant base = Instant.parse("2026-05-01T00:00:00Z");
    List<DetectionMatch> matches =
        connector.queryNodeAlert(
            new NodeAlertQuery(
                "n1",
                base,
                base.plusSeconds(60),
                Set.of("10.0.0.1", "10.0.0.2"),
                Set.of("T1059"),
                Map.of()));

    assertThat(matches).hasSize(2);
    assertThat(matches.get(0).alertId()).isEqualTo("a1");
    assertThat(matches.get(0).score()).isEqualTo(73);

    HttpRequest sentRequest = capturedRequest();
    assertThat(sentRequest.uri().toString()).contains("/_search").contains(props.getAlertIndex());
    String body = readBody(sentRequest);
    assertThat(body).contains("event.type", "host.ip", "10.0.0.1", "10.0.0.2");
    assertThat(sentRequest.headers().firstValue("Authorization")).contains("ApiKey test-key");
  }

  @Test
  @DisplayName("queryNodeAlert: 空 IP → 不带 host.ip filter")
  void query_node_alert_no_ips() throws IOException, InterruptedException {
    stubResponse(200, "{\"hits\":{\"hits\":[]}}");
    Instant base = Instant.parse("2026-05-01T00:00:00Z");

    connector.queryNodeAlert(
        new NodeAlertQuery("n1", base, base.plusSeconds(60), Set.of(), Set.of(), Map.of()));

    String body = readBody(capturedRequest());
    assertThat(body).doesNotContain("host.ip");
  }

  // ---- 链路级查询 ----

  @Test
  @DisplayName("queryCorrelationRule: 按 ruleId + 时间窗过滤")
  void query_correlation_rule() throws IOException, InterruptedException {
    stubResponse(
        200,
        """
        {"hits":{"hits":[
          {"_id":"i1","_source":{"@timestamp":"2026-05-01T00:30:00Z","signal":{"rule":{"name":"chain-detection","risk_score":80}}}}
        ]}}
        """);

    Instant base = Instant.parse("2026-05-01T00:00:00Z");
    List<CorrelationMatch> matches =
        connector.queryCorrelationRule(
            new CorrelationRuleQuery("run-1", base, base.plusSeconds(7200), "rule-abc", Map.of()));

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).incidentId()).isEqualTo("i1");
    assertThat(matches.get(0).correlationRuleName()).isEqualTo("chain-detection");
    assertThat(matches.get(0).score()).isEqualTo(80);

    String body = readBody(capturedRequest());
    assertThat(body).contains("rule-abc", "@timestamp", "signal");
  }

  // ---- listAvailableRules + cache ----

  @Test
  @DisplayName("listAvailableRules: 解析 Kibana 返回 + 5 分钟缓存复用")
  void list_rules_caches() throws IOException, InterruptedException {
    stubResponse(
        200,
        """
        {"data":[
          {"rule_id":"r1","name":"Rule One","description":"desc","type":"correlation"},
          {"id":"r2","name":"Rule Two"}
        ]}
        """);

    List<AvailableRule> first = connector.listAvailableRules();
    List<AvailableRule> second = connector.listAvailableRules();

    assertThat(first).hasSize(2);
    assertThat(first.get(0).ruleId()).isEqualTo("r1");
    assertThat(first.get(0).description()).isEqualTo("desc");
    assertThat(first.get(0).category()).isEqualTo("correlation");
    assertThat(first.get(1).description()).isNull(); // 空字符串 → null

    // 第二次调用复用缓存，HttpClient 仅被调一次
    verify(httpClient, org.mockito.Mockito.times(1))
        .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    assertThat(second).isEqualTo(first);
  }

  // ---- checkHealth ----

  @Test
  @DisplayName("checkHealth: green → HEALTHY")
  void health_green() throws IOException, InterruptedException {
    stubResponse(200, "{\"status\":\"green\"}");
    assertThat(connector.checkHealth().status()).isEqualTo(HealthCheckResult.Status.HEALTHY);
  }

  @Test
  @DisplayName("checkHealth: yellow → DEGRADED")
  void health_yellow() throws IOException, InterruptedException {
    stubResponse(200, "{\"status\":\"yellow\"}");
    assertThat(connector.checkHealth().status()).isEqualTo(HealthCheckResult.Status.DEGRADED);
  }

  @Test
  @DisplayName("checkHealth: red 或 4xx → UNHEALTHY，message 包含原因")
  void health_unhealthy() throws IOException, InterruptedException {
    stubResponse(200, "{\"status\":\"red\"}");
    HealthCheckResult red = connector.checkHealth();
    assertThat(red.status()).isEqualTo(HealthCheckResult.Status.UNHEALTHY);
    assertThat(red.message()).contains("red");

    stubResponse(401, "{\"error\":\"unauthorized\"}");
    HealthCheckResult auth = connector.checkHealth();
    assertThat(auth.status()).isEqualTo(HealthCheckResult.Status.UNHEALTHY);
  }

  // ---- 错误传播 ----

  @Test
  @DisplayName("HTTP 5xx → ElasticSocException 抛上去（spec 3.6 失败显式）")
  void server_error_propagates() throws IOException, InterruptedException {
    stubResponse(503, "{\"error\":\"service unavailable\"}");
    Instant base = Instant.parse("2026-05-01T00:00:00Z");

    assertThatThrownBy(
            () ->
                connector.queryCorrelationRule(
                    new CorrelationRuleQuery("r", base, base.plusSeconds(60), "rule-1", null)))
        .isInstanceOf(ElasticSocConnector.ElasticSocException.class)
        .hasMessageContaining("503");
  }

  @Test
  @DisplayName("IOException → ElasticSocException 抛上去")
  void io_exception_propagates() throws IOException, InterruptedException {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("boom"));
    Instant base = Instant.parse("2026-05-01T00:00:00Z");

    assertThatThrownBy(
            () ->
                connector.queryNodeAlert(
                    new NodeAlertQuery("n", base, base.plusSeconds(60), null, null, null)))
        .isInstanceOf(ElasticSocConnector.ElasticSocException.class)
        .hasMessageContaining("boom");
  }

  // ---- helpers ----

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void stubResponse(int status, String body) throws IOException, InterruptedException {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn((HttpResponse) response);
  }

  private HttpRequest capturedRequest() throws IOException, InterruptedException {
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, org.mockito.Mockito.atLeastOnce())
        .send(captor.capture(), any(HttpResponse.BodyHandler.class));
    return captor.getValue();
  }

  private static String readBody(HttpRequest request) {
    return request
        .bodyPublisher()
        .map(
            publisher -> {
              var subscriber = new BodyAccumulator();
              publisher.subscribe(subscriber);
              return subscriber.toString();
            })
        .orElse("");
  }

  /** 同步累积 HttpRequest body 为 String，便于断言（仅测试用）。 */
  private static class BodyAccumulator
      implements java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
    private final StringBuilder buffer = new StringBuilder();

    @Override
    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(java.nio.ByteBuffer item) {
      byte[] bytes = new byte[item.remaining()];
      item.get(bytes);
      buffer.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public void onError(Throwable throwable) {}

    @Override
    public void onComplete() {}

    @Override
    public String toString() {
      return buffer.toString();
    }
  }
}
