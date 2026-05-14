package io.veriguard.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Serialize / deserialize Veriguard {@code .vresults} JSON envelope (spec §3.5.3).
 *
 * <p>Mirror of {@link VpackSerializer}: this is the agent-signed, platform-decrypted direction.
 * Wire schema:
 *
 * <pre>
 * {
 *   "schema_version": "1.0",
 *   "format": "vresults",
 *   "metadata_plaintext": {
 *     "pack_id": "<UUID>",
 *     "agent_id": "<string>",
 *     "executed_at": "<ISO-8601 UTC>",
 *     "result_count": <int>
 *   },
 *   "envelope_encrypted": { ... },
 *   "signature": { "scheme": "Ed25519", "signer_pub_b64": "<agent A_sign_pub>", ... }
 * }
 * </pre>
 *
 * <p>Re-uses {@link VpackSerializer} exception hierarchy ({@code SchemaVersionException}, {@code
 * SignatureVerificationException}, {@code VpackParseException}) for consistency — caller handles
 * both serializers' failures uniformly.
 */
@Component
public class VresultsSerializer {

  public static final String SCHEMA_VERSION = "1.0";
  public static final String FORMAT_VRESULTS = "vresults";
  public static final String ENCRYPT_SCHEME = "nacl-box";
  public static final String ENCRYPT_KDF = "x25519";
  public static final String ENCRYPT_CIPHER = "chacha20-poly1305";
  public static final String SIGN_SCHEME = "Ed25519";

  private final ObjectMapper canonicalMapper;
  private final Ed25519SignatureService ed25519;

  public VresultsSerializer(ObjectMapper baseMapper, Ed25519SignatureService ed25519) {
    this.canonicalMapper =
        baseMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    this.canonicalMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    this.ed25519 = ed25519;
  }

  /**
   * Build a {@code .vresults} envelope from already-encrypted payload + agent sign priv key.
   *
   * @param payload metadata + encrypted envelope
   * @param agentSignPriv 32-byte agent Ed25519 secret seed
   * @return UTF-8 bytes of the canonical JSON envelope
   */
  public byte[] build(VresultsPayload payload, byte[] agentSignPriv) {
    if (payload == null) {
      throw new IllegalArgumentException("payload must not be null");
    }
    byte[] agentSignPub = ed25519.derivePublicKey(agentSignPriv);

    ObjectNode metadataNode = metadataToNode(payload.metadata());
    ObjectNode envelopeNode = encryptedEnvelopeToNode(payload.encryptedEnvelope());
    byte[] signInput = canonicalSignInput(metadataNode, envelopeNode);
    byte[] signature = ed25519.sign(agentSignPriv, signInput);

    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.set("envelope_encrypted", envelopeNode);
    root.put("format", FORMAT_VRESULTS);
    root.set("metadata_plaintext", metadataNode);
    root.put("schema_version", SCHEMA_VERSION);

    ObjectNode signatureNode = root.objectNode();
    signatureNode.put("scheme", SIGN_SCHEME);
    signatureNode.put("signer_pub_b64", Base64.getEncoder().encodeToString(agentSignPub));
    signatureNode.put("sig_b64", Base64.getEncoder().encodeToString(signature));
    root.set("signature", signatureNode);

    try {
      return canonicalMapper.writeValueAsBytes(root);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize vresults envelope", ex);
    }
  }

  /**
   * Parse a {@code .vresults} envelope, verifying its signature against the agent's expected public
   * key.
   */
  public VresultsContents parse(byte[] envelopeBytes, byte[] expectedAgentSignerPub) {
    JsonNode root;
    try {
      root = canonicalMapper.readTree(envelopeBytes);
    } catch (IOException ex) {
      throw new VpackSerializer.VpackParseException("Malformed JSON envelope", ex);
    }
    if (root == null || !root.isObject()) {
      throw new VpackSerializer.VpackParseException("Envelope root must be a JSON object", null);
    }

    String schemaVersion = textOrThrow(root, "schema_version");
    String format = textOrThrow(root, "format");
    if (!SCHEMA_VERSION.equals(schemaVersion)) {
      throw new VpackSerializer.SchemaVersionException(
          "Unsupported schema_version: expected " + SCHEMA_VERSION + ", got " + schemaVersion);
    }
    if (!FORMAT_VRESULTS.equals(format)) {
      throw new VpackSerializer.SchemaVersionException(
          "Unsupported format: expected " + FORMAT_VRESULTS + ", got " + format);
    }

    JsonNode metadataNode = objectOrThrow(root, "metadata_plaintext");
    JsonNode envelopeNode = objectOrThrow(root, "envelope_encrypted");
    JsonNode signatureNode = objectOrThrow(root, "signature");

    String signerPubB64 = textOrThrow(signatureNode, "signer_pub_b64");
    String sigB64 = textOrThrow(signatureNode, "sig_b64");
    byte[] signerPub = Base64.getDecoder().decode(signerPubB64);
    byte[] sig = Base64.getDecoder().decode(sigB64);

    if (!java.util.Arrays.equals(signerPub, expectedAgentSignerPub)) {
      throw new VpackSerializer.SignatureVerificationException(
          "Signer public key does not match expected agent key");
    }

    byte[] signInput = canonicalSignInput(metadataNode, envelopeNode);
    if (!ed25519.verify(signerPub, signInput, sig)) {
      throw new VpackSerializer.SignatureVerificationException(
          "Ed25519 signature verification failed");
    }

    VresultsMetadata metadata = nodeToMetadata(metadataNode);
    VresultsEncryptedEnvelope encEnvelope = nodeToEncryptedEnvelope(envelopeNode);
    return new VresultsContents(metadata, encEnvelope, signerPub);
  }

  private byte[] canonicalSignInput(JsonNode metadataNode, JsonNode envelopeNode) {
    ObjectNode signRoot = JsonNodeFactory.instance.objectNode();
    signRoot.set("envelope_encrypted", envelopeNode);
    signRoot.set("metadata_plaintext", metadataNode);
    try {
      return canonicalMapper.writeValueAsBytes(signRoot);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to canonicalize sign input", ex);
    }
  }

  private ObjectNode metadataToNode(VresultsMetadata m) {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    n.put("pack_id", m.packId().toString());
    n.put("agent_id", m.agentId());
    n.put("executed_at", m.executedAt().toString());
    n.put("result_count", m.resultCount());
    return n;
  }

  private VresultsMetadata nodeToMetadata(JsonNode n) {
    return new VresultsMetadata(
        UUID.fromString(textOrThrow(n, "pack_id")),
        textOrThrow(n, "agent_id"),
        Instant.parse(textOrThrow(n, "executed_at")),
        intOrThrow(n, "result_count"));
  }

  private ObjectNode encryptedEnvelopeToNode(VresultsEncryptedEnvelope e) {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    n.put("scheme", ENCRYPT_SCHEME);
    n.put("kdf", ENCRYPT_KDF);
    n.put("cipher", ENCRYPT_CIPHER);
    n.put("sender_x25519_pub_b64", Base64.getEncoder().encodeToString(e.senderX25519Pub()));
    n.put("nonce_b64", Base64.getEncoder().encodeToString(e.nonce()));
    n.put("ciphertext_b64", Base64.getEncoder().encodeToString(e.ciphertext()));
    return n;
  }

  private VresultsEncryptedEnvelope nodeToEncryptedEnvelope(JsonNode n) {
    byte[] senderPub = Base64.getDecoder().decode(textOrThrow(n, "sender_x25519_pub_b64"));
    byte[] nonce = Base64.getDecoder().decode(textOrThrow(n, "nonce_b64"));
    byte[] ct = Base64.getDecoder().decode(textOrThrow(n, "ciphertext_b64"));
    return new VresultsEncryptedEnvelope(senderPub, nonce, ct);
  }

  private static String textOrThrow(JsonNode parent, String field) {
    JsonNode v = parent.get(field);
    if (v == null || !v.isTextual()) {
      throw new VpackSerializer.VpackParseException("Missing or non-textual field: " + field, null);
    }
    return v.asText();
  }

  private static int intOrThrow(JsonNode parent, String field) {
    JsonNode v = parent.get(field);
    if (v == null || !v.canConvertToInt()) {
      throw new VpackSerializer.VpackParseException("Missing or non-integer field: " + field, null);
    }
    return v.asInt();
  }

  private static JsonNode objectOrThrow(JsonNode parent, String field) {
    JsonNode v = parent.get(field);
    if (v == null || !v.isObject()) {
      throw new VpackSerializer.VpackParseException("Missing or non-object field: " + field, null);
    }
    return v;
  }

  // --- Records ---

  public record VresultsPayload(
      VresultsMetadata metadata, VresultsEncryptedEnvelope encryptedEnvelope) {}

  public record VresultsContents(
      VresultsMetadata metadata, VresultsEncryptedEnvelope encryptedEnvelope, byte[] signerPub) {}

  public record VresultsMetadata(
      UUID packId, String agentId, Instant executedAt, int resultCount) {}

  public record VresultsEncryptedEnvelope(byte[] senderX25519Pub, byte[] nonce, byte[] ciphertext) {
    public VresultsEncryptedEnvelope {
      if (senderX25519Pub == null || nonce == null || ciphertext == null) {
        throw new IllegalArgumentException("senderX25519Pub / nonce / ciphertext must not be null");
      }
    }
  }
}
