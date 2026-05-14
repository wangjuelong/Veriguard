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
 * Serialize / deserialize Veriguard {@code .vpack} JSON envelope (spec §3.5.2).
 *
 * <p>Envelope layout:
 *
 * <pre>
 * {
 *   "schema_version": "1.0",
 *   "format": "vpack",
 *   "metadata_plaintext": {
 *     "pack_id": "<UUID>",
 *     "platform_id": "<string>",
 *     "agent_id": "<string>",
 *     "issued_at": "<ISO-8601 UTC>",
 *     "task_count": <int>,
 *     "schema_version_payload": "<string>",
 *     "exported_by": "<string>"
 *   },
 *   "envelope_encrypted": {
 *     "scheme": "nacl-box",
 *     "kdf": "x25519",
 *     "cipher": "chacha20-poly1305",
 *     "sender_x25519_pub_b64": "<base64>",
 *     "nonce_b64": "<base64>",
 *     "ciphertext_b64": "<base64>"
 *   },
 *   "signature": {
 *     "scheme": "Ed25519",
 *     "signer_pub_b64": "<base64>",
 *     "sig_b64": "<base64>"
 *   }
 * }
 * </pre>
 *
 * <p>Canonical signature input — Jackson with {@link
 * SerializationFeature#ORDER_MAP_ENTRIES_BY_KEYS} enabled, no whitespace, UTF-8. The sign input
 * is a 2-key JSON object {@code { "envelope_encrypted": ..., "metadata_plaintext": ... }} (keys
 * sorted alphabetically). Rust agent 端 serde_json + serde with field-sort 生成同字节序，互验签可行.
 *
 * <p>API：
 *
 * <ul>
 *   <li>{@link #build} — assemble envelope JSON given pre-encrypted ciphertext + platform sign priv
 *   <li>{@link #parse} — verify signature, return parsed payload
 * </ul>
 *
 * <p>Caller responsibility — encryption (X25519BoxService) is performed before {@link #build} and
 * decryption is performed after {@link #parse} (separation of concern: serializer 只管 JSON
 * envelope + 签名层，不做 ChaCha20-Poly1305).
 */
@Component
public class VpackSerializer {

  public static final String SCHEMA_VERSION = "1.0";
  public static final String FORMAT_VPACK = "vpack";
  public static final String ENCRYPT_SCHEME = "nacl-box";
  public static final String ENCRYPT_KDF = "x25519";
  public static final String ENCRYPT_CIPHER = "chacha20-poly1305";
  public static final String SIGN_SCHEME = "Ed25519";

  private final ObjectMapper canonicalMapper;
  private final Ed25519SignatureService ed25519;

  public VpackSerializer(ObjectMapper baseMapper, Ed25519SignatureService ed25519) {
    // Build a clone of the injected mapper with key-sort enabled so the canonical signature input
    // is byte-deterministic for the same logical content.
    this.canonicalMapper = baseMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    this.canonicalMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    this.ed25519 = ed25519;
  }

  /**
   * Build a complete {@code .vpack} envelope bytes from already-encrypted payload + platform
   * sign priv key.
   *
   * @param payload metadata + encrypted envelope (caller pre-runs ChaCha20-Poly1305 seal)
   * @param platformSignPriv 32-byte platform Ed25519 secret seed
   * @return UTF-8 bytes of the canonical JSON envelope (keys sorted, no whitespace)
   */
  public byte[] build(VpackPayload payload, byte[] platformSignPriv) {
    if (payload == null) {
      throw new IllegalArgumentException("payload must not be null");
    }
    byte[] platformSignPub = ed25519.derivePublicKey(platformSignPriv);

    ObjectNode metadataNode = metadataToNode(payload.metadata());
    ObjectNode envelopeNode = encryptedEnvelopeToNode(payload.encryptedEnvelope());
    byte[] signInput = canonicalSignInput(metadataNode, envelopeNode);
    byte[] signature = ed25519.sign(platformSignPriv, signInput);

    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.set("envelope_encrypted", envelopeNode);
    root.put("format", FORMAT_VPACK);
    root.set("metadata_plaintext", metadataNode);
    root.put("schema_version", SCHEMA_VERSION);

    ObjectNode signatureNode = root.objectNode();
    signatureNode.put("scheme", SIGN_SCHEME);
    signatureNode.put("signer_pub_b64", Base64.getEncoder().encodeToString(platformSignPub));
    signatureNode.put("sig_b64", Base64.getEncoder().encodeToString(signature));
    root.set("signature", signatureNode);

    try {
      return canonicalMapper.writeValueAsBytes(root);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize vpack envelope", ex);
    }
  }

  /**
   * Parse a {@code .vpack} envelope, verifying its signature against {@code expectedSignerPub}.
   *
   * @param envelopeBytes UTF-8 bytes of the JSON envelope
   * @param expectedSignerPub 32-byte expected platform Ed25519 public key
   * @throws SchemaVersionException if {@code schema_version} or {@code format} does not match
   * @throws SignatureVerificationException if the {@code signer_pub_b64} does not match {@code
   *     expectedSignerPub} or if the Ed25519 signature is invalid
   * @throws VpackParseException for malformed JSON
   */
  public VpackContents parse(byte[] envelopeBytes, byte[] expectedSignerPub) {
    JsonNode root;
    try {
      root = canonicalMapper.readTree(envelopeBytes);
    } catch (IOException ex) {
      throw new VpackParseException("Malformed JSON envelope", ex);
    }
    if (root == null || !root.isObject()) {
      throw new VpackParseException("Envelope root must be a JSON object", null);
    }

    String schemaVersion = textOrThrow(root, "schema_version");
    String format = textOrThrow(root, "format");
    if (!SCHEMA_VERSION.equals(schemaVersion)) {
      throw new SchemaVersionException(
          "Unsupported schema_version: expected " + SCHEMA_VERSION + ", got " + schemaVersion);
    }
    if (!FORMAT_VPACK.equals(format)) {
      throw new SchemaVersionException(
          "Unsupported format: expected " + FORMAT_VPACK + ", got " + format);
    }

    JsonNode metadataNode = objectOrThrow(root, "metadata_plaintext");
    JsonNode envelopeNode = objectOrThrow(root, "envelope_encrypted");
    JsonNode signatureNode = objectOrThrow(root, "signature");

    String signerPubB64 = textOrThrow(signatureNode, "signer_pub_b64");
    String sigB64 = textOrThrow(signatureNode, "sig_b64");
    byte[] signerPub = Base64.getDecoder().decode(signerPubB64);
    byte[] sig = Base64.getDecoder().decode(sigB64);

    if (!java.util.Arrays.equals(signerPub, expectedSignerPub)) {
      throw new SignatureVerificationException(
          "Signer public key does not match expected platform key");
    }

    byte[] signInput = canonicalSignInput(metadataNode, envelopeNode);
    if (!ed25519.verify(signerPub, signInput, sig)) {
      throw new SignatureVerificationException("Ed25519 signature verification failed");
    }

    VpackMetadata metadata = nodeToMetadata(metadataNode);
    VpackEncryptedEnvelope encEnvelope = nodeToEncryptedEnvelope(envelopeNode);
    return new VpackContents(metadata, encEnvelope, signerPub);
  }

  private byte[] canonicalSignInput(JsonNode metadataNode, JsonNode envelopeNode) {
    ObjectNode signRoot = JsonNodeFactory.instance.objectNode();
    // Keys are emitted in alphabetical order due to ORDER_MAP_ENTRIES_BY_KEYS
    signRoot.set("envelope_encrypted", envelopeNode);
    signRoot.set("metadata_plaintext", metadataNode);
    try {
      return canonicalMapper.writeValueAsBytes(signRoot);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to canonicalize sign input", ex);
    }
  }

  private ObjectNode metadataToNode(VpackMetadata m) {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    n.put("pack_id", m.packId().toString());
    n.put("platform_id", m.platformId());
    n.put("agent_id", m.agentId());
    n.put("issued_at", m.issuedAt().toString());
    n.put("task_count", m.taskCount());
    n.put("schema_version_payload", m.schemaVersionPayload());
    n.put("exported_by", m.exportedBy());
    return n;
  }

  private VpackMetadata nodeToMetadata(JsonNode n) {
    return new VpackMetadata(
        UUID.fromString(textOrThrow(n, "pack_id")),
        textOrThrow(n, "platform_id"),
        textOrThrow(n, "agent_id"),
        Instant.parse(textOrThrow(n, "issued_at")),
        intOrThrow(n, "task_count"),
        textOrThrow(n, "schema_version_payload"),
        textOrThrow(n, "exported_by"));
  }

  private ObjectNode encryptedEnvelopeToNode(VpackEncryptedEnvelope e) {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    n.put("scheme", ENCRYPT_SCHEME);
    n.put("kdf", ENCRYPT_KDF);
    n.put("cipher", ENCRYPT_CIPHER);
    n.put("sender_x25519_pub_b64", Base64.getEncoder().encodeToString(e.senderX25519Pub()));
    n.put("nonce_b64", Base64.getEncoder().encodeToString(e.nonce()));
    n.put("ciphertext_b64", Base64.getEncoder().encodeToString(e.ciphertext()));
    return n;
  }

  private VpackEncryptedEnvelope nodeToEncryptedEnvelope(JsonNode n) {
    byte[] senderPub = Base64.getDecoder().decode(textOrThrow(n, "sender_x25519_pub_b64"));
    byte[] nonce = Base64.getDecoder().decode(textOrThrow(n, "nonce_b64"));
    byte[] ct = Base64.getDecoder().decode(textOrThrow(n, "ciphertext_b64"));
    return new VpackEncryptedEnvelope(senderPub, nonce, ct);
  }

  private static String textOrThrow(JsonNode parent, String field) {
    JsonNode v = parent.get(field);
    if (v == null || !v.isTextual()) {
      throw new VpackParseException("Missing or non-textual field: " + field, null);
    }
    return v.asText();
  }

  private static int intOrThrow(JsonNode parent, String field) {
    JsonNode v = parent.get(field);
    if (v == null || !v.canConvertToInt()) {
      throw new VpackParseException("Missing or non-integer field: " + field, null);
    }
    return v.asInt();
  }

  private static JsonNode objectOrThrow(JsonNode parent, String field) {
    JsonNode v = parent.get(field);
    if (v == null || !v.isObject()) {
      throw new VpackParseException("Missing or non-object field: " + field, null);
    }
    return v;
  }

  // --- Records ---

  /**
   * Full input payload required to build a {@code .vpack} envelope. Caller must run X25519+ChaCha
   * encryption before constructing this record (the {@code ciphertext} field already contains the
   * sealed bytes).
   */
  public record VpackPayload(VpackMetadata metadata, VpackEncryptedEnvelope encryptedEnvelope) {}

  /** Parsed output of {@link #parse}. */
  public record VpackContents(
      VpackMetadata metadata, VpackEncryptedEnvelope encryptedEnvelope, byte[] signerPub) {}

  /** Plaintext metadata (spec §3.5.2). All fields are opaque (no business secrets). */
  public record VpackMetadata(
      UUID packId,
      String platformId,
      String agentId,
      Instant issuedAt,
      int taskCount,
      String schemaVersionPayload,
      String exportedBy) {}

  /**
   * Encrypted envelope structure — caller-provided fields (sender X25519 public key, 12-byte
   * nonce, ChaCha20-Poly1305 ciphertext+tag).
   */
  public record VpackEncryptedEnvelope(byte[] senderX25519Pub, byte[] nonce, byte[] ciphertext) {
    public VpackEncryptedEnvelope {
      if (senderX25519Pub == null || nonce == null || ciphertext == null) {
        throw new IllegalArgumentException(
            "senderX25519Pub / nonce / ciphertext must not be null");
      }
    }
  }

  // --- Exceptions ---

  /** Thrown when {@code schema_version} or {@code format} does not match. */
  public static class SchemaVersionException extends RuntimeException {
    public SchemaVersionException(String message) {
      super(message);
    }
  }

  /** Thrown when Ed25519 verify fails (tamper / forge / wrong signer). */
  public static class SignatureVerificationException extends RuntimeException {
    public SignatureVerificationException(String message) {
      super(message);
    }
  }

  /** Thrown for malformed JSON envelope (missing fields, wrong types). */
  public static class VpackParseException extends RuntimeException {
    public VpackParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }

}
