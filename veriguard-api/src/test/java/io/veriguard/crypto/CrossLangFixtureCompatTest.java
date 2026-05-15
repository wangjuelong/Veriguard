package io.veriguard.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.rest.agent.AgentDtos;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Byte-level cross-language compatibility test for the Veriguard Mode C wire format.
 *
 * <p>Loads two binary fixtures produced by the Rust agent's {@code src/tests/cross_lang_fixture.rs}
 * (see {@code veriguard-agent} PR introducing {@code seal_box_with_nonce}). Each fixture is a
 * canonical UTF-8 JSON envelope frozen at a specific (keys + nonce + plaintext + metadata) input.
 * This Java test re-derives the same four keypairs from the same seed convention, then parses +
 * decrypts each envelope through the production {@link VpackSerializer} / {@link
 * VresultsSerializer} / {@link X25519BoxService} code paths and asserts the recovered plaintext
 * matches the expected JSON in {@code expected.json}.
 *
 * <p>Two independent codepaths (Rust agent + Java platform) agreeing on the same byte sequence is
 * byte-level proof that the wire contract is interoperable — addresses the {@code
 * project-veriguard-wire-contract-locked} memory's outstanding "cross-language fixture exchange"
 * GAP.
 *
 * <h2>Regeneration recipe</h2>
 *
 * If the wire format intentionally changes:
 *
 * <ol>
 *   <li>Update the Rust fixture generator and capture the new base64 strings (see Rust test doc).
 *   <li>Replace the bytes in {@code .bin} resources by base64-decoding the new constants.
 *   <li>Re-sync {@code expected.json} to mirror any changed plaintext / metadata fields.
 *   <li>Run this test — it must pass without further changes (no business assertions are
 *       date-bound).
 * </ol>
 */
class CrossLangFixtureCompatTest {

  private static final String FIXTURE_DIR = "/fixtures/cross-lang-c1";
  private static final String VPACK_BIN = FIXTURE_DIR + "/vpack_v1_2tasks.bin";
  private static final String VRESULTS_BIN = FIXTURE_DIR + "/vresults_v1_2results.bin";
  private static final String EXPECTED_JSON = FIXTURE_DIR + "/expected.json";

  // Seed convention — must match veriguard-agent/src/tests/cross_lang_fixture.rs.
  private static final byte PLATFORM_SIGN_SEED_FILL = 0x01;
  private static final byte PLATFORM_ENC_SCALAR_FILL = 0x02;
  private static final byte AGENT_SIGN_SEED_FILL = 0x03;
  private static final byte AGENT_ENC_SCALAR_FILL = 0x04;

  private Ed25519SignatureService ed25519;
  private X25519BoxService x25519;
  private VpackSerializer vpackSerializer;
  private VresultsSerializer vresultsSerializer;
  private ObjectMapper objectMapper;

  private byte[] platformSignSeed;
  private byte[] platformEncScalar;
  private byte[] agentSignSeed;
  private byte[] agentEncScalar;

  private byte[] platformSignPub;
  private byte[] agentSignPub;
  private byte[] platformEncPub;
  private byte[] agentEncPub;

  private JsonNode expected;

  @BeforeEach
  void setUp() throws IOException {
    objectMapper = new ObjectMapper();
    ed25519 = new Ed25519SignatureService();
    x25519 = new X25519BoxService();
    vpackSerializer = new VpackSerializer(objectMapper, ed25519);
    vresultsSerializer = new VresultsSerializer(objectMapper, ed25519);

    platformSignSeed = filled(PLATFORM_SIGN_SEED_FILL);
    platformEncScalar = filled(PLATFORM_ENC_SCALAR_FILL);
    agentSignSeed = filled(AGENT_SIGN_SEED_FILL);
    agentEncScalar = filled(AGENT_ENC_SCALAR_FILL);

    platformSignPub = ed25519.derivePublicKey(platformSignSeed);
    agentSignPub = ed25519.derivePublicKey(agentSignSeed);
    platformEncPub = x25519.derivePublicKey(platformEncScalar);
    agentEncPub = x25519.derivePublicKey(agentEncScalar);

    expected = objectMapper.readTree(readResource(EXPECTED_JSON));
  }

  @Test
  void derived_public_keys_match_rust_side_constants() {
    // First-line sanity: if Rust and Java derive different public keys from the same scalar,
    // every downstream signature / decryption assertion will fire confusingly. Pinning the
    // derived publics here gives a single clear failure point for "the two stacks disagree
    // on Ed25519 / X25519 key derivation".
    JsonNode pubs = expected.get("derived_public_keys_b64");
    assertThat(b64(platformSignPub)).isEqualTo(pubs.get("platform_sign_pub").asText());
    assertThat(b64(agentSignPub)).isEqualTo(pubs.get("agent_sign_pub").asText());
    assertThat(b64(platformEncPub)).isEqualTo(pubs.get("platform_enc_pub").asText());
    assertThat(b64(agentEncPub)).isEqualTo(pubs.get("agent_enc_pub").asText());
  }

  @Test
  void vpack_fixture_parses_and_decrypts_to_expected_task_list() throws IOException {
    byte[] envelopeBytes = readResource(VPACK_BIN);

    // 1. Verify the envelope Ed25519 signature using the platform sign pub we re-derived.
    VpackSerializer.VpackContents contents = vpackSerializer.parse(envelopeBytes, platformSignPub);

    // 2. Metadata fields must match the expected.json — same UUID, same agent, etc.
    JsonNode meta = expected.get("vpack_metadata");
    assertThat(contents.metadata().packId().toString()).isEqualTo(meta.get("pack_id").asText());
    assertThat(contents.metadata().platformId()).isEqualTo(meta.get("platform_id").asText());
    assertThat(contents.metadata().agentId()).isEqualTo(meta.get("agent_id").asText());
    assertThat(contents.metadata().issuedAt().toString()).isEqualTo(meta.get("issued_at").asText());
    assertThat(contents.metadata().taskCount()).isEqualTo(meta.get("task_count").asInt());
    assertThat(contents.metadata().schemaVersionPayload())
        .isEqualTo(meta.get("schema_version_payload").asText());
    assertThat(contents.metadata().exportedBy()).isEqualTo(meta.get("exported_by").asText());

    // 3. Envelope nonce + sender pub are stable.
    assertThat(b64(contents.encryptedEnvelope().nonce()))
        .isEqualTo(expected.get("vpack_envelope_nonce_b64").asText());
    assertThat(b64(contents.encryptedEnvelope().senderX25519Pub())).isEqualTo(b64(platformEncPub));

    // 4. Decrypt the sealed body — sender = platform, recipient = agent. Java acts as the agent
    //    for this leg of the fixture.
    byte[] plaintext =
        x25519.open(
            contents.encryptedEnvelope().ciphertext(),
            contents.encryptedEnvelope().nonce(),
            contents.encryptedEnvelope().senderX25519Pub(),
            agentEncScalar);

    // 5. Decode the plaintext as a JSON array of AgentTask and assert it equals expected.
    List<AgentDtos.AgentTask> tasks =
        Arrays.asList(objectMapper.readValue(plaintext, AgentDtos.AgentTask[].class));
    JsonNode expectedTasks = expected.get("vpack_plaintext_tasks");
    assertThat(tasks).hasSize(expectedTasks.size());
    for (int i = 0; i < tasks.size(); i++) {
      JsonNode e = expectedTasks.get(i);
      AgentDtos.AgentTask t = tasks.get(i);
      assertThat(t.taskId()).isEqualTo(e.get("task_id").asText());
      assertThat(t.capability()).isEqualTo(e.get("capability").asText());
      assertThat(t.injectorType()).isEqualTo(e.get("injector_type").asText());
      assertThat(t.payloadJson()).isEqualTo(e.get("payload").asText());
      assertThat(t.expectations()).isEqualTo(textArrayToList(e.get("expectations")));
    }
  }

  @Test
  void vresults_fixture_parses_and_decrypts_to_expected_result_list() throws IOException {
    byte[] envelopeBytes = readResource(VRESULTS_BIN);

    // 1. Verify the envelope Ed25519 signature using the agent sign pub we re-derived.
    VresultsSerializer.VresultsContents contents =
        vresultsSerializer.parse(envelopeBytes, agentSignPub);

    // 2. Metadata fields.
    JsonNode meta = expected.get("vresults_metadata");
    assertThat(contents.metadata().packId().toString()).isEqualTo(meta.get("pack_id").asText());
    assertThat(contents.metadata().agentId()).isEqualTo(meta.get("agent_id").asText());
    assertThat(contents.metadata().executedAt().toString())
        .isEqualTo(meta.get("executed_at").asText());
    assertThat(contents.metadata().resultCount()).isEqualTo(meta.get("result_count").asInt());

    assertThat(b64(contents.encryptedEnvelope().nonce()))
        .isEqualTo(expected.get("vresults_envelope_nonce_b64").asText());
    assertThat(b64(contents.encryptedEnvelope().senderX25519Pub())).isEqualTo(b64(agentEncPub));

    // 3. Decrypt — sender = agent, recipient = platform. Java IS the platform.
    byte[] plaintext =
        x25519.open(
            contents.encryptedEnvelope().ciphertext(),
            contents.encryptedEnvelope().nonce(),
            contents.encryptedEnvelope().senderX25519Pub(),
            platformEncScalar);

    List<AgentDtos.ResultInput> results =
        Arrays.asList(objectMapper.readValue(plaintext, AgentDtos.ResultInput[].class));
    JsonNode expectedResults = expected.get("vresults_plaintext_results");
    assertThat(results).hasSize(expectedResults.size());
    for (int i = 0; i < results.size(); i++) {
      JsonNode e = expectedResults.get(i);
      AgentDtos.ResultInput r = results.get(i);
      assertThat(r.status()).isEqualTo(e.get("status").asText());
      assertThat(r.exitCode()).isEqualTo(e.get("exit_code").asInt());
      assertThat(r.stdout()).isEqualTo(e.get("stdout").asText());
      assertThat(r.stderr()).isEqualTo(e.get("stderr").asText());
      assertThat(r.startedAt()).isEqualTo(e.get("started_at").asText());
      assertThat(r.finishedAt()).isEqualTo(e.get("finished_at").asText());
      // error_message is nullable — Jackson maps JSON null → Java null.
      if (e.get("error_message").isNull()) {
        assertThat(r.errorMessage()).isNull();
      } else {
        assertThat(r.errorMessage()).isEqualTo(e.get("error_message").asText());
      }
    }
  }

  // ---- helpers ----

  private static byte[] readResource(String path) throws IOException {
    try (InputStream in = CrossLangFixtureCompatTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IOException("test resource not found on classpath: " + path);
      }
      return in.readAllBytes();
    }
  }

  private static byte[] filled(byte fill) {
    byte[] arr = new byte[32];
    Arrays.fill(arr, fill);
    return arr;
  }

  private static String b64(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  private static List<String> textArrayToList(JsonNode arrNode) {
    List<String> out = new ArrayList<>(arrNode.size());
    arrNode.forEach(n -> out.add(n.asText()));
    return out;
  }
}
