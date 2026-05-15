package io.veriguard.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.veriguard.rest.agent.AgentDtos;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VresultsTaskResultParser}.
 *
 * <p>Each test builds a {@code .vresults} byte stream using the low-level {@link
 * VresultsSerializer} + {@link X25519BoxService} services (so we control the plaintext and crypto
 * material), then asserts the parser reverses the pipeline to the exact original {@link
 * AgentDtos.ResultInput} list.
 */
class VresultsTaskResultParserTest {

  private VresultsTaskResultParser parser;
  private VresultsSerializer vresultsSerializer;
  private X25519BoxService x25519Box;
  private Ed25519SignatureService ed25519;
  private ObjectMapper objectMapper;

  private byte[] agentSignPriv;
  private byte[] agentSignPub;
  private byte[] platformEncPriv;
  private byte[] platformEncPub;
  private byte[] agentEncPriv;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ed25519 = new Ed25519SignatureService();
    x25519Box = new X25519BoxService();
    vresultsSerializer = new VresultsSerializer(objectMapper, ed25519);
    parser = new VresultsTaskResultParser(vresultsSerializer, x25519Box, objectMapper);

    Ed25519SignatureService.Ed25519KeyPair signPair = ed25519.generate();
    agentSignPriv = signPair.privateKey();
    agentSignPub = signPair.publicKey();

    X25519BoxService.X25519KeyPair platformEnc = x25519Box.generate();
    platformEncPriv = platformEnc.privateKey();
    platformEncPub = platformEnc.publicKey();

    X25519BoxService.X25519KeyPair agentEnc = x25519Box.generate();
    agentEncPriv = agentEnc.privateKey();
  }

  /**
   * Helper: build a {@code .vresults} envelope from a fixture {@code List<ResultInput>}, sealing it
   * to the platform's enc public key and signing with the agent's sign private key. Mirrors what
   * the Rust agent's {@code executor.rs} produces.
   */
  private byte[] buildVresultsFor(List<AgentDtos.ResultInput> results, UUID packId)
      throws Exception {
    byte[] plaintext = objectMapper.writeValueAsBytes(results);
    X25519BoxService.SealedBox sealed = x25519Box.seal(plaintext, platformEncPub, agentEncPriv);
    byte[] senderPub = x25519Box.derivePublicKey(agentEncPriv);

    VresultsSerializer.VresultsEncryptedEnvelope envelope =
        new VresultsSerializer.VresultsEncryptedEnvelope(
            senderPub, sealed.nonce(), sealed.ciphertext());
    VresultsSerializer.VresultsMetadata metadata =
        new VresultsSerializer.VresultsMetadata(
            packId, "agent-prod-01", Instant.parse("2026-05-15T11:00:00Z"), results.size());
    return vresultsSerializer.build(
        new VresultsSerializer.VresultsPayload(metadata, envelope), agentSignPriv);
  }

  private List<AgentDtos.ResultInput> threeSampleResults() {
    return List.of(
        new AgentDtos.ResultInput("SUCCESS", 0, "ok-a", null, null, null, null),
        new AgentDtos.ResultInput(
            "FAILED", 1, null, "boom", "2026-05-15T11:00:01Z", "2026-05-15T11:00:02Z", "kaboom"),
        new AgentDtos.ResultInput("SUCCESS", 0, "ok-c", null, null, null, null));
  }

  @Test
  void parseRoundTripThreeResults() throws Exception {
    List<AgentDtos.ResultInput> input = threeSampleResults();
    UUID packId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    byte[] envelope = buildVresultsFor(input, packId);

    VresultsTaskResultParser.ParsedTaskResults out =
        parser.parse(envelope, agentSignPub, platformEncPriv);

    assertThat(out.results()).isEqualTo(input);
    assertThat(out.metadata().packId()).isEqualTo(packId);
    assertThat(out.metadata().agentId()).isEqualTo("agent-prod-01");
    assertThat(out.metadata().resultCount()).isEqualTo(3);
    assertThat(out.metadata().executedAt()).isEqualTo(Instant.parse("2026-05-15T11:00:00Z"));
  }

  @Test
  void parseRoundTripEmptyList() throws Exception {
    UUID packId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    byte[] envelope = buildVresultsFor(List.of(), packId);

    VresultsTaskResultParser.ParsedTaskResults out =
        parser.parse(envelope, agentSignPub, platformEncPriv);
    assertThat(out.results()).isEmpty();
    assertThat(out.metadata().resultCount()).isZero();
  }

  @Test
  void parsePreservesResultOrder() throws Exception {
    List<AgentDtos.ResultInput> input = threeSampleResults();
    byte[] envelope = buildVresultsFor(input, UUID.randomUUID());

    VresultsTaskResultParser.ParsedTaskResults out =
        parser.parse(envelope, agentSignPub, platformEncPriv);

    assertThat(out.results().stream().map(AgentDtos.ResultInput::status).toList())
        .containsExactly("SUCCESS", "FAILED", "SUCCESS");
    assertThat(out.results().stream().map(AgentDtos.ResultInput::exitCode).toList())
        .containsExactly(0, 1, 0);
  }

  @Test
  void parsePreservesNullableFields() throws Exception {
    // ResultInput's nullable fields (stdout/stderr/started_at/finished_at/error_message) MUST
    // round-trip null without coercion to empty string.  This is the Rust-side contract.
    AgentDtos.ResultInput allNulls =
        new AgentDtos.ResultInput("SUCCESS", 0, null, null, null, null, null);
    byte[] envelope = buildVresultsFor(List.of(allNulls), UUID.randomUUID());

    VresultsTaskResultParser.ParsedTaskResults out =
        parser.parse(envelope, agentSignPub, platformEncPriv);
    AgentDtos.ResultInput got = out.results().get(0);
    assertThat(got.stdout()).isNull();
    assertThat(got.stderr()).isNull();
    assertThat(got.startedAt()).isNull();
    assertThat(got.finishedAt()).isNull();
    assertThat(got.errorMessage()).isNull();
  }

  @Test
  void parseRejectsWrongAgentSignerPub() throws Exception {
    byte[] envelope = buildVresultsFor(threeSampleResults(), UUID.randomUUID());
    byte[] otherPub = ed25519.generate().publicKey();

    assertThatThrownBy(() -> parser.parse(envelope, otherPub, platformEncPriv))
        .isInstanceOf(VpackSerializer.SignatureVerificationException.class);
  }

  @Test
  void parseRejectsWrongPlatformEncPriv() throws Exception {
    byte[] envelope = buildVresultsFor(threeSampleResults(), UUID.randomUUID());
    byte[] otherPlatformPriv = x25519Box.generate().privateKey();

    assertThatThrownBy(() -> parser.parse(envelope, agentSignPub, otherPlatformPriv))
        .isInstanceOf(X25519BoxService.BoxOpenException.class);
  }

  @Test
  void parseRejectsMalformedEnvelopeJson() {
    byte[] garbage = "not a valid vresults envelope".getBytes();
    assertThatThrownBy(() -> parser.parse(garbage, agentSignPub, platformEncPriv))
        .isInstanceOf(VpackSerializer.VpackParseException.class);
  }

  @Test
  void parseRejectsTamperedSignature() throws Exception {
    byte[] envelope = buildVresultsFor(threeSampleResults(), UUID.randomUUID());
    // Flip one byte deep inside the sig_b64 area (well past the JSON-prefix bytes); whichever byte
    // we hit, the Ed25519 verify must fail (or the envelope structure parse fails, also rejection).
    byte[] tampered = envelope.clone();
    int targetIdx = tampered.length - 50;
    tampered[targetIdx] = (byte) (tampered[targetIdx] ^ 0x01);

    assertThatThrownBy(() -> parser.parse(tampered, agentSignPub, platformEncPriv))
        .isInstanceOfAny(
            VpackSerializer.SignatureVerificationException.class,
            VpackSerializer.VpackParseException.class);
  }

  @Test
  void parseRejectsNullEnvelopeBytes() {
    assertThatThrownBy(() -> parser.parse(null, agentSignPub, platformEncPriv))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("envelopeBytes");
  }

  @Test
  void parseRejectsNullAgentSignerPub() {
    assertThatThrownBy(() -> parser.parse(new byte[] {1, 2, 3}, null, platformEncPriv))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expectedAgentSignPub");
  }

  @Test
  void parseRejectsNullPlatformEncPriv() {
    assertThatThrownBy(() -> parser.parse(new byte[] {1, 2, 3}, agentSignPub, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("platformEncPriv");
  }

  @Test
  void parseRejectsMalformedPlaintextBody() {
    // Craft a valid envelope whose decrypted body is NOT a JSON array of ResultInput.
    byte[] plaintextNotArray = "{\"this\": \"is not an array\"}".getBytes();
    X25519BoxService.SealedBox sealed =
        x25519Box.seal(plaintextNotArray, platformEncPub, agentEncPriv);
    byte[] senderPub = x25519Box.derivePublicKey(agentEncPriv);
    VresultsSerializer.VresultsEncryptedEnvelope env =
        new VresultsSerializer.VresultsEncryptedEnvelope(
            senderPub, sealed.nonce(), sealed.ciphertext());
    VresultsSerializer.VresultsMetadata meta =
        new VresultsSerializer.VresultsMetadata(
            UUID.randomUUID(), "agent-x", Instant.parse("2026-05-15T11:00:00Z"), 0);
    byte[] envelope =
        vresultsSerializer.build(new VresultsSerializer.VresultsPayload(meta, env), agentSignPriv);

    assertThatThrownBy(() -> parser.parse(envelope, agentSignPub, platformEncPriv))
        .isInstanceOf(VresultsTaskResultParser.ResultListParseException.class);
  }
}
