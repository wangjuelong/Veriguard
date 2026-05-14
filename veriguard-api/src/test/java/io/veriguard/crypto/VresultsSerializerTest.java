package io.veriguard.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VresultsSerializer} — mirror of {@link VpackSerializer} but agent-signed
 * and platform-decrypted.
 */
class VresultsSerializerTest {

  private VresultsSerializer serializer;
  private Ed25519SignatureService ed25519;
  private ObjectMapper mapper;

  private byte[] agentSignPriv;
  private byte[] agentSignPub;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ed25519 = new Ed25519SignatureService();
    serializer = new VresultsSerializer(mapper, ed25519);

    Ed25519SignatureService.Ed25519KeyPair pair = ed25519.generate();
    agentSignPriv = pair.privateKey();
    agentSignPub = pair.publicKey();
  }

  private VresultsSerializer.VresultsPayload samplePayload() {
    return new VresultsSerializer.VresultsPayload(
        new VresultsSerializer.VresultsMetadata(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            "8a7b9c1d-2e3f-4a5b-9c8d-1f2e3a4b5c6d",
            Instant.parse("2026-05-14T15:00:00Z"),
            247),
        new VresultsSerializer.VresultsEncryptedEnvelope(
            new byte[] {0x21, 0x22, 0x23, 0x24},
            new byte[] {0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x3b},
            new byte[] {(byte) 0xab, (byte) 0xcd, (byte) 0xef, 0x01}));
  }

  @Test
  void buildParseRoundtrip() {
    VresultsSerializer.VresultsPayload payload = samplePayload();

    byte[] envelope = serializer.build(payload, agentSignPriv);
    VresultsSerializer.VresultsContents parsed = serializer.parse(envelope, agentSignPub);

    assertThat(parsed.metadata().packId())
        .isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
    assertThat(parsed.metadata().agentId()).isEqualTo("8a7b9c1d-2e3f-4a5b-9c8d-1f2e3a4b5c6d");
    assertThat(parsed.metadata().executedAt()).isEqualTo(Instant.parse("2026-05-14T15:00:00Z"));
    assertThat(parsed.metadata().resultCount()).isEqualTo(247);
    assertThat(parsed.signerPub()).isEqualTo(agentSignPub);
  }

  @Test
  void parseRejectsTamperedSignature() throws Exception {
    byte[] envelope = serializer.build(samplePayload(), agentSignPriv);
    ObjectNode root = (ObjectNode) mapper.readTree(envelope);
    String sigB64 = root.get("signature").get("sig_b64").asText();
    byte[] sig = java.util.Base64.getDecoder().decode(sigB64);
    sig[0] ^= 0x01;
    ((ObjectNode) root.get("signature"))
        .put("sig_b64", java.util.Base64.getEncoder().encodeToString(sig));
    byte[] tampered = mapper.writeValueAsBytes(root);

    assertThatThrownBy(() -> serializer.parse(tampered, agentSignPub))
        .isInstanceOf(VpackSerializer.SignatureVerificationException.class);
  }

  @Test
  void parseRejectsTamperedCiphertext() throws Exception {
    byte[] envelope = serializer.build(samplePayload(), agentSignPriv);
    ObjectNode root = (ObjectNode) mapper.readTree(envelope);
    String ctB64 = root.get("envelope_encrypted").get("ciphertext_b64").asText();
    byte[] ct = java.util.Base64.getDecoder().decode(ctB64);
    ct[0] ^= 0x01;
    ((ObjectNode) root.get("envelope_encrypted"))
        .put("ciphertext_b64", java.util.Base64.getEncoder().encodeToString(ct));
    byte[] tampered = mapper.writeValueAsBytes(root);

    assertThatThrownBy(() -> serializer.parse(tampered, agentSignPub))
        .isInstanceOf(VpackSerializer.SignatureVerificationException.class);
  }

  @Test
  void parseRejectsWrongFormat() throws Exception {
    byte[] envelope = serializer.build(samplePayload(), agentSignPriv);
    ObjectNode root = (ObjectNode) mapper.readTree(envelope);
    root.put("format", "vpack");
    byte[] modified = mapper.writeValueAsBytes(root);

    assertThatThrownBy(() -> serializer.parse(modified, agentSignPub))
        .isInstanceOf(VpackSerializer.SchemaVersionException.class);
  }

  @Test
  void parseRejectsForgedSignerKey() {
    byte[] envelope = serializer.build(samplePayload(), agentSignPriv);
    Ed25519SignatureService.Ed25519KeyPair attacker = ed25519.generate();

    assertThatThrownBy(() -> serializer.parse(envelope, attacker.publicKey()))
        .isInstanceOf(VpackSerializer.SignatureVerificationException.class);
  }

  @Test
  void parseRejectsMalformedBase64() throws Exception {
    byte[] envelope = serializer.build(samplePayload(), agentSignPriv);
    ObjectNode root = (ObjectNode) mapper.readTree(envelope);
    // sig_b64 is decoded before signature verification, so malformed input here exercises the
    // decodeBase64OrFail path rather than tripping the SignatureVerificationException first.
    ((ObjectNode) root.get("signature")).put("sig_b64", "!not-valid-base64!");
    byte[] malformed = mapper.writeValueAsBytes(root);

    assertThatThrownBy(() -> serializer.parse(malformed, agentSignPub))
        .isInstanceOf(VpackSerializer.VpackParseException.class)
        .hasMessageContaining("sig_b64");
  }

  @Test
  void buildProducesValidJsonStructure() throws Exception {
    byte[] envelope = serializer.build(samplePayload(), agentSignPriv);
    ObjectNode root = (ObjectNode) mapper.readTree(envelope);

    assertThat(root.get("schema_version").asText()).isEqualTo("1.0");
    assertThat(root.get("format").asText()).isEqualTo("vresults");
    assertThat(root.get("metadata_plaintext").get("agent_id").asText())
        .isEqualTo("8a7b9c1d-2e3f-4a5b-9c8d-1f2e3a4b5c6d");
    assertThat(root.get("envelope_encrypted").get("scheme").asText()).isEqualTo("nacl-box");
    assertThat(root.get("signature").get("scheme").asText()).isEqualTo("Ed25519");
  }
}
