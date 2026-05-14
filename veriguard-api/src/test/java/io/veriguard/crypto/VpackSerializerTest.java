package io.veriguard.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VpackSerializer}.
 *
 * <p>Validates round-trip + 4 reject paths (T-1 tamper, T-2 forge, schema_version mismatch, format
 * mismatch). Cross-language fixture (Rust agent ↔ Java platform) is deferred to C1-Integration
 * phase per the project plan.
 */
class VpackSerializerTest {

  private VpackSerializer serializer;
  private Ed25519SignatureService ed25519;
  private ObjectMapper mapper;

  private byte[] platformSignPriv;
  private byte[] platformSignPub;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ed25519 = new Ed25519SignatureService();
    serializer = new VpackSerializer(mapper, ed25519);

    Ed25519SignatureService.Ed25519KeyPair pair = ed25519.generate();
    platformSignPriv = pair.privateKey();
    platformSignPub = pair.publicKey();
  }

  private VpackSerializer.VpackPayload samplePayload() {
    return new VpackSerializer.VpackPayload(
        new VpackSerializer.VpackMetadata(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            "veriguard-prod-001",
            "8a7b9c1d-2e3f-4a5b-9c8d-1f2e3a4b5c6d",
            Instant.parse("2026-05-14T10:00:00Z"),
            247,
            "1.0",
            "operator-zhang"),
        new VpackSerializer.VpackEncryptedEnvelope(
            new byte[] {1, 2, 3, 4},
            new byte[] {5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
            new byte[] {0x10, 0x20, 0x30, 0x40, 0x50, 0x60}));
  }

  @Test
  void buildParseRoundtrip() {
    VpackSerializer.VpackPayload payload = samplePayload();

    byte[] envelope = serializer.build(payload, platformSignPriv);
    VpackSerializer.VpackContents parsed = serializer.parse(envelope, platformSignPub);

    assertThat(parsed.metadata().packId())
        .isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
    assertThat(parsed.metadata().platformId()).isEqualTo("veriguard-prod-001");
    assertThat(parsed.metadata().agentId()).isEqualTo("8a7b9c1d-2e3f-4a5b-9c8d-1f2e3a4b5c6d");
    assertThat(parsed.metadata().issuedAt()).isEqualTo(Instant.parse("2026-05-14T10:00:00Z"));
    assertThat(parsed.metadata().taskCount()).isEqualTo(247);
    assertThat(parsed.encryptedEnvelope().senderX25519Pub()).containsExactly(1, 2, 3, 4);
    assertThat(parsed.encryptedEnvelope().nonce()).hasSize(12);
    assertThat(parsed.encryptedEnvelope().ciphertext())
        .containsExactly(0x10, 0x20, 0x30, 0x40, 0x50, 0x60);
    assertThat(parsed.signerPub()).isEqualTo(platformSignPub);
  }

  @Test
  void parseRejectsTamperedSignature() throws Exception {
    byte[] envelope = serializer.build(samplePayload(), platformSignPriv);

    // Flip a byte in the signature field
    ObjectNode root = (ObjectNode) mapper.readTree(envelope);
    String sigB64 = root.get("signature").get("sig_b64").asText();
    byte[] sig = java.util.Base64.getDecoder().decode(sigB64);
    sig[0] ^= 0x01;
    ((ObjectNode) root.get("signature"))
        .put("sig_b64", java.util.Base64.getEncoder().encodeToString(sig));
    byte[] tampered = mapper.writeValueAsBytes(root);

    assertThatThrownBy(() -> serializer.parse(tampered, platformSignPub))
        .isInstanceOf(VpackSerializer.SignatureVerificationException.class);
  }

  @Test
  void parseRejectsTamperedCiphertext() throws Exception {
    byte[] envelope = serializer.build(samplePayload(), platformSignPriv);

    ObjectNode root = (ObjectNode) mapper.readTree(envelope);
    String ctB64 = root.get("envelope_encrypted").get("ciphertext_b64").asText();
    byte[] ct = java.util.Base64.getDecoder().decode(ctB64);
    ct[0] ^= 0x01;
    ((ObjectNode) root.get("envelope_encrypted"))
        .put("ciphertext_b64", java.util.Base64.getEncoder().encodeToString(ct));
    byte[] tampered = mapper.writeValueAsBytes(root);

    // Signature is over metadata + envelope — modifying ciphertext breaks it
    assertThatThrownBy(() -> serializer.parse(tampered, platformSignPub))
        .isInstanceOf(VpackSerializer.SignatureVerificationException.class);
  }

  @Test
  void parseRejectsWrongSchemaVersion() throws Exception {
    byte[] envelope = serializer.build(samplePayload(), platformSignPriv);
    ObjectNode root = (ObjectNode) mapper.readTree(envelope);
    root.put("schema_version", "2.0");
    byte[] modified = mapper.writeValueAsBytes(root);

    assertThatThrownBy(() -> serializer.parse(modified, platformSignPub))
        .isInstanceOf(VpackSerializer.SchemaVersionException.class);
  }

  @Test
  void parseRejectsWrongFormat() throws Exception {
    byte[] envelope = serializer.build(samplePayload(), platformSignPriv);
    ObjectNode root = (ObjectNode) mapper.readTree(envelope);
    root.put("format", "vresults");
    byte[] modified = mapper.writeValueAsBytes(root);

    assertThatThrownBy(() -> serializer.parse(modified, platformSignPub))
        .isInstanceOf(VpackSerializer.SchemaVersionException.class);
  }

  @Test
  void parseRejectsForgedSignerKey() {
    byte[] envelope = serializer.build(samplePayload(), platformSignPriv);

    Ed25519SignatureService.Ed25519KeyPair attacker = ed25519.generate();

    assertThatThrownBy(() -> serializer.parse(envelope, attacker.publicKey()))
        .isInstanceOf(VpackSerializer.SignatureVerificationException.class);
  }

  @Test
  void buildProducesValidJsonStructure() throws Exception {
    byte[] envelope = serializer.build(samplePayload(), platformSignPriv);

    String json = new String(envelope, StandardCharsets.UTF_8);
    ObjectNode root = (ObjectNode) mapper.readTree(json);

    assertThat(root.get("schema_version").asText()).isEqualTo("1.0");
    assertThat(root.get("format").asText()).isEqualTo("vpack");
    assertThat(root.get("metadata_plaintext").get("platform_id").asText())
        .isEqualTo("veriguard-prod-001");
    assertThat(root.get("envelope_encrypted").get("scheme").asText()).isEqualTo("nacl-box");
    assertThat(root.get("envelope_encrypted").get("kdf").asText()).isEqualTo("x25519");
    assertThat(root.get("envelope_encrypted").get("cipher").asText())
        .isEqualTo("chacha20-poly1305");
    assertThat(root.get("signature").get("scheme").asText()).isEqualTo("Ed25519");
    assertThat(root.get("signature").get("signer_pub_b64").asText()).isNotBlank();
    assertThat(root.get("signature").get("sig_b64").asText()).isNotBlank();
  }
}
