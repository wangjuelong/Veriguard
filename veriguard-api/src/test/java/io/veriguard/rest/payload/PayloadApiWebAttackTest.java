package io.veriguard.rest.payload;

import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.Capability;
import io.veriguard.database.model.Domain;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.Payload;
import io.veriguard.database.model.WebAttackPayload;
import io.veriguard.database.model.WebRequestHeaderEntry;
import io.veriguard.database.repository.PayloadRepository;
import io.veriguard.rest.injector_contract.form.NodeContractDomainDTO;
import io.veriguard.rest.payload.form.PayloadUpsertInput;
import io.veriguard.utils.fixtures.DomainFixture;
import io.veriguard.utils.fixtures.composers.DomainComposer;
import io.veriguard.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PR B7 — Integration tests proving WebAttack payloads round-trip through {@code POST
 * /api/payloads/upsert} into the {@code payloads} table (V18 columns) and back out via JSON.
 *
 * <p>Unblocks the B6 importer (155 boundary-attack samples) which previously hit a 400 because
 * PayloadType lacked WEB_ATTACK.
 */
@TestInstance(PER_CLASS)
class PayloadApiWebAttackTest extends IntegrationTest {

  private static final String PAYLOAD_URI = "/api/payloads";

  @Autowired private MockMvc mvc;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private DomainComposer domainComposer;

  @AfterAll
  void afterAll() {
    payloadRepository.deleteAll();
  }

  private PayloadUpsertInput buildWebAttackUpsertInput(String externalId, Set<Domain> domains) {
    PayloadUpsertInput input = new PayloadUpsertInput();
    input.setType(WebAttackPayload.WEB_ATTACK_TYPE);
    input.setName("CSRF: form POST without token");
    input.setDescription("WAF/IPS should detect missing CSRF token.");
    input.setSource(Payload.PAYLOAD_SOURCE.COMMUNITY);
    input.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    input.setExternalId(externalId);
    input.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Generic});
    input.setMethod("POST");
    input.setUrl("/api/transfer");
    input.setBody("amount=1000&to=attacker_account");
    input.setBodyType("form");
    input.setTimeoutSeconds(600);
    input.setCookies("csrf_token=cookie_token_xyz");

    WebRequestHeaderEntry h = new WebRequestHeaderEntry();
    h.setName("Content-Type");
    h.setValue("application/x-www-form-urlencoded");
    input.setHeaders(List.of(h));

    input.setExpectedStatusCodes(List.of(403, 401));
    input.setExpectedBodyRegex("(?i)(csrf|forbidden)");

    input.setDomains(
        domains.stream().map(NodeContractDomainDTO::fromDomain).collect(Collectors.toSet()));
    return input;
  }

  @Test
  @DisplayName("Upsert WebAttack payload — creates row and round-trips wire fields")
  @WithMockUser(withCapabilities = {Capability.MANAGE_PAYLOADS})
  void upsertWebAttackCreatesAndRoundTrips() throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    String extId = "B7-IT-" + UUID.randomUUID();
    PayloadUpsertInput input = buildWebAttackUpsertInput(extId, Set.of(domain));

    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload_type").value(WebAttackPayload.WEB_ATTACK_TYPE))
        .andExpect(jsonPath("$.web_request_method").value("POST"))
        .andExpect(jsonPath("$.web_request_url").value("/api/transfer"))
        .andExpect(jsonPath("$.web_request_body").value("amount=1000&to=attacker_account"))
        .andExpect(jsonPath("$.web_request_body_type").value("form"))
        .andExpect(jsonPath("$.web_request_timeout_seconds").value(600))
        .andExpect(jsonPath("$.web_request_cookies").value("csrf_token=cookie_token_xyz"))
        .andExpect(jsonPath("$.web_request_headers[0].name").value("Content-Type"))
        .andExpect(jsonPath("$.expected_status_codes[0]").value(403))
        .andExpect(jsonPath("$.expected_body_regex").value("(?i)(csrf|forbidden)"));

    Optional<Payload> persisted = payloadRepository.findByExternalId(extId);
    assertTrue(persisted.isPresent());
    assertInstanceOf(WebAttackPayload.class, persisted.get());
    WebAttackPayload wp = (WebAttackPayload) persisted.get();
    assertEquals("POST", wp.getMethod());
    assertEquals(2, wp.getExpectedStatusCodes().size());
    assertEquals(1, wp.getHeaders().size());
  }

  @Test
  @DisplayName(
      "Upsert WebAttack payload — second call updates same row (idempotent by external_id)")
  @WithMockUser(withCapabilities = {Capability.MANAGE_PAYLOADS})
  void upsertWebAttackIsIdempotent() throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    String extId = "B7-IT-" + UUID.randomUUID();
    PayloadUpsertInput first = buildWebAttackUpsertInput(extId, Set.of(domain));
    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(first))
                .with(csrf()))
        .andExpect(status().isOk());

    PayloadUpsertInput second = buildWebAttackUpsertInput(extId, Set.of(domain));
    second.setUrl("/api/transfer/v2");
    second.setExpectedStatusCodes(List.of(404));
    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(second))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.web_request_url").value("/api/transfer/v2"))
        .andExpect(jsonPath("$.expected_status_codes[0]").value(404));

    Optional<Payload> persisted = payloadRepository.findByExternalId(extId);
    assertTrue(persisted.isPresent(), "external_id duplicate must update, not insert");
    assertEquals("/api/transfer/v2", ((WebAttackPayload) persisted.get()).getUrl());
  }

  @Test
  @DisplayName("Upsert WebAttack payload — missing web_request_method → 400")
  @WithMockUser(withCapabilities = {Capability.MANAGE_PAYLOADS})
  void upsertWebAttackMissingMethodReturns400() throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadUpsertInput input =
        buildWebAttackUpsertInput("B7-IT-" + UUID.randomUUID(), Set.of(domain));
    input.setMethod(null);

    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }
}
