package io.veriguard.rest.payload;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.veriguard.database.model.Command;
import io.veriguard.database.model.PayloadType;
import io.veriguard.database.model.WebAttackPayload;
import io.veriguard.database.model.WebRequestHeaderEntry;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.payload.form.PayloadUpsertInput;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PR B7 — verify PayloadType.WEB_ATTACK wiring, WebAttackPayload JSON wire format,
 * and PayloadUtils.validateWebAttackInput cross-field validation. No Spring context required.
 */
class WebAttackPayloadUnitTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("PayloadType.WEB_ATTACK round-trips via fromString")
  void payloadTypeRoundTrip() {
    PayloadType resolved = PayloadType.fromString("WebAttack");
    assertEquals(PayloadType.WEB_ATTACK, resolved);
    assertEquals(WebAttackPayload.WEB_ATTACK_TYPE, resolved.key);
    assertNotNull(resolved.getPayloadSupplier().get());
    assertInstanceOf(WebAttackPayload.class, resolved.getPayloadSupplier().get());
  }

  @Test
  @DisplayName("WebAttackPayload serializes to spec wire format (snake_case web_request_*)")
  void webAttackPayloadSerializesWireFormat() throws Exception {
    WebAttackPayload payload = new WebAttackPayload("p1", WebAttackPayload.WEB_ATTACK_TYPE, "name");
    payload.setMethod("POST");
    payload.setUrl("/api/transfer");
    payload.setBody("amount=1000");
    payload.setBodyType("form");
    payload.setTimeoutSeconds(30);
    payload.setCookies("a=1; b=2");

    WebRequestHeaderEntry header = new WebRequestHeaderEntry();
    header.setName("Content-Type");
    header.setValue("application/x-www-form-urlencoded");
    payload.setHeaders(List.of(header));

    payload.setExpectedStatusCodes(List.of(403, 401));
    payload.setExpectedBodyRegex("(?i)forbidden");

    String json = objectMapper.writeValueAsString(payload);

    assertTrue(json.contains("\"web_request_method\":\"POST\""));
    assertTrue(json.contains("\"web_request_url\":\"/api/transfer\""));
    assertTrue(json.contains("\"web_request_body\":\"amount=1000\""));
    assertTrue(json.contains("\"web_request_body_type\":\"form\""));
    assertTrue(json.contains("\"web_request_timeout_seconds\":30"));
    assertTrue(json.contains("\"web_request_cookies\":\"a=1; b=2\""));
    assertTrue(json.contains("\"web_request_headers\""));
    assertTrue(json.contains("\"expected_status_codes\":[403,401]"));
    assertTrue(json.contains("\"expected_body_regex\":\"(?i)forbidden\""));
    assertTrue(json.contains("\"payload_type\":\"WebAttack\""));
  }

  @Test
  @DisplayName("PayloadUpsertInput deserializes from B6 dataset wire format")
  void payloadUpsertInputDeserializesB6Wire() throws Exception {
    String b6Json =
        "{\n"
            + "  \"payload_type\": \"WebAttack\",\n"
            + "  \"payload_name\": \"CSRF: form POST without token\",\n"
            + "  \"payload_external_id\": \"B6-CSRF-001\",\n"
            + "  \"payload_source\": \"COMMUNITY\",\n"
            + "  \"payload_status\": \"VERIFIED\",\n"
            + "  \"payload_domains\": [],\n"
            + "  \"web_request_method\": \"POST\",\n"
            + "  \"web_request_url\": \"/api/transfer\",\n"
            + "  \"web_request_body\": \"amount=1000\",\n"
            + "  \"web_request_body_type\": \"form\",\n"
            + "  \"web_request_headers\": [{\"name\":\"X-A\",\"value\":\"1\"}],\n"
            + "  \"web_request_cookies\": \"sid=abc\",\n"
            + "  \"web_request_timeout_seconds\": 600,\n"
            + "  \"expected_status_codes\": [403],\n"
            + "  \"expected_body_regex\": \"forbidden\"\n"
            + "}";

    PayloadUpsertInput input = objectMapper.readValue(b6Json, PayloadUpsertInput.class);

    assertEquals("WebAttack", input.getType());
    assertEquals("POST", input.getMethod());
    assertEquals("/api/transfer", input.getUrl());
    assertEquals("amount=1000", input.getBody());
    assertEquals("form", input.getBodyType());
    assertEquals("sid=abc", input.getCookies());
    assertEquals(600, input.getTimeoutSeconds());
    assertEquals(1, input.getHeaders().size());
    assertEquals("X-A", input.getHeaders().get(0).getName());
    assertEquals(List.of(403), input.getExpectedStatusCodes());
    assertEquals("forbidden", input.getExpectedBodyRegex());
  }

  @Test
  @DisplayName("validateWebAttackInput passes when method+url present")
  void validateWebAttackPasses() {
    PayloadUpsertInput input = new PayloadUpsertInput();
    input.setType("WebAttack");
    input.setMethod("GET");
    input.setUrl("/api/x");
    assertDoesNotThrow(() -> PayloadUtils.validateWebAttackInput(PayloadType.WEB_ATTACK, input));
  }

  @Test
  @DisplayName("validateWebAttackInput rejects missing method")
  void validateWebAttackRejectsMissingMethod() {
    PayloadUpsertInput input = new PayloadUpsertInput();
    input.setType("WebAttack");
    input.setUrl("/api/x");
    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () -> PayloadUtils.validateWebAttackInput(PayloadType.WEB_ATTACK, input));
    assertTrue(ex.getMessage().contains("web_request_method"));
  }

  @Test
  @DisplayName("validateWebAttackInput rejects missing url")
  void validateWebAttackRejectsMissingUrl() {
    PayloadUpsertInput input = new PayloadUpsertInput();
    input.setType("WebAttack");
    input.setMethod("POST");
    input.setUrl("   ");
    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () -> PayloadUtils.validateWebAttackInput(PayloadType.WEB_ATTACK, input));
    assertTrue(ex.getMessage().contains("web_request_url"));
  }

  @Test
  @DisplayName("validateWebAttackInput is no-op for non-WebAttack types (Command)")
  void validateWebAttackNoOpForCommand() {
    PayloadUpsertInput input = new PayloadUpsertInput();
    input.setType(Command.COMMAND_TYPE);
    // method/url null but type=Command → should not throw
    assertDoesNotThrow(() -> PayloadUtils.validateWebAttackInput(PayloadType.COMMAND, input));
  }
}
