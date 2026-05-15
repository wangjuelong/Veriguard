package io.veriguard.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.rest.agent.AgentDtos;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Build a complete {@code .vpack} envelope containing an encrypted JSON array of {@link
 * AgentDtos.AgentTask} records — the Mode C offline-pack equivalent of the Mode A {@code
 * /api/agent/poll} response body (spec §3.5.2).
 *
 * <p>This is the platform-side counterpart to the Rust agent's {@code
 * veriguard-agent/src/pack/executor.rs::execute_vpack} which reverses this exact pipeline:
 *
 * <ol>
 *   <li>Jackson serialize {@code List<AgentTask>} → UTF-8 JSON bytes (Jackson default — include
 *       null fields so {@code expectations: null} round-trips identically to {@code expectations:
 *       []} on the Rust side via {@code #[serde(default)]})
 *   <li>{@link X25519BoxService#seal} → 12-byte random IETF nonce + ChaCha20-Poly1305 ciphertext +
 *       Poly1305 tag (raw X25519 ECDH as the AEAD key, no KDF — matches Rust {@code
 *       crypto::x25519_box::seal_box})
 *   <li>Wrap in {@link VpackSerializer.VpackEncryptedEnvelope} + {@link
 *       VpackSerializer.VpackMetadata}
 *   <li>{@link VpackSerializer#build} signs with the platform Ed25519 priv and emits canonical
 *       2-key sorted-keys JSON envelope (byte-aligned with Rust {@code pack::vpack::build_vpack})
 * </ol>
 *
 * <h2>Plaintext schema (Rust-locked — A.7.3 commit {@code e1877d3})</h2>
 *
 * The plaintext inside the {@code ciphertext_b64} field is a JSON <b>array</b> of {@link
 * AgentDtos.AgentTask}; the field names are <em>identical</em> to the Rust {@code
 * transport::poll::Task} struct via the {@code @JsonProperty} annotations on {@link
 * AgentDtos.AgentTask} (snake_case wire form: {@code task_id / capability / injector_type / payload
 * / expectations}).
 *
 * <p>Order in the array <b>is</b> meaningful — the Rust agent emits the corresponding {@code
 * TaskResult} list in the same index order, and the platform correlates request → response by index
 * (no {@code task_id} field on the response side).
 *
 * <p>See {@code project_veriguard_wire_contract_locked.md} memory for the byte-level invariants
 * pinned during C1-Agent-2 and C1-Agent-3 review.
 */
@Component
public class VpackTaskListBuilder {

  private final VpackSerializer vpackSerializer;
  private final X25519BoxService x25519Box;
  private final ObjectMapper objectMapper;

  public VpackTaskListBuilder(
      VpackSerializer vpackSerializer, X25519BoxService x25519Box, ObjectMapper objectMapper) {
    this.vpackSerializer = vpackSerializer;
    this.x25519Box = x25519Box;
    this.objectMapper = objectMapper;
  }

  /**
   * Build a {@code .vpack} envelope from the given task list + crypto material.
   *
   * @param input bundle of plaintext + keys; see {@link BuildInput} for field-by-field detail
   * @return UTF-8 bytes of the canonical .vpack JSON envelope; written verbatim to disk by the
   *     operator and consumed by {@code veriguard-agent pack --input <file>}
   * @throws IllegalArgumentException if {@code input} or any nested required field is {@code null}
   * @throws IllegalStateException if Jackson fails to serialize {@link AgentDtos.AgentTask} records
   *     (should not happen for valid records)
   */
  public byte[] build(BuildInput input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }

    // 1. Serialize the task list to canonical JSON bytes.  Jackson default behaviour matches
    //    Rust serde_json default — null fields emit as null, lists emit without trailing commas.
    byte[] plaintext;
    try {
      plaintext = objectMapper.writeValueAsBytes(input.tasks());
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize task list to JSON", ex);
    }

    // 2. Seal: X25519 ECDH(platform_enc_priv, agent_enc_pub) → 32-byte shared secret used directly
    //    as the ChaCha20-Poly1305 key (no KDF, matches Rust).
    X25519BoxService.SealedBox sealed =
        x25519Box.seal(plaintext, input.agentEncPub(), input.platformEncPriv());
    byte[] senderX25519Pub = x25519Box.derivePublicKey(input.platformEncPriv());

    // 3. Wrap in envelope record + metadata, hand off to the envelope-layer serializer.
    VpackSerializer.VpackEncryptedEnvelope envelope =
        new VpackSerializer.VpackEncryptedEnvelope(
            senderX25519Pub, sealed.nonce(), sealed.ciphertext());
    VpackSerializer.VpackMetadata metadata =
        new VpackSerializer.VpackMetadata(
            input.packId(),
            input.platformId(),
            input.agentId(),
            input.issuedAt(),
            input.tasks().size(),
            input.schemaVersionPayload(),
            input.exportedBy());
    VpackSerializer.VpackPayload payload = new VpackSerializer.VpackPayload(metadata, envelope);

    // 4. Sign + emit canonical envelope bytes.
    return vpackSerializer.build(payload, input.platformSignPriv());
  }

  /**
   * Input bundle for {@link #build}.
   *
   * <p>Using a record rather than a 10-arg positional method keeps call sites self-documenting and
   * makes future additions (e.g. an {@code Authorization} field) non-breaking.
   *
   * @param tasks Tasks to encrypt inside the {@code .vpack} body, in execution order.
   * @param packId Pack identifier (lowercase UUID) — also used by the agent's replay blacklist.
   * @param platformId Opaque platform identifier (e.g. {@code "veriguard-prod-1"}).
   * @param agentId Agent the pack is targeted at; must match the agent's install pack {@code
   *     agent_label}.
   * @param issuedAt When the platform built the pack (ISO-8601 UTC; serialized via {@link
   *     Instant#toString()}).
   * @param schemaVersionPayload Payload schema version (currently {@code "1.0"}).
   * @param exportedBy Operator label (e.g. {@code "alice@platform"}); appears in metadata for
   *     audit.
   * @param agentEncPub Agent's 32-byte X25519 public key (recipient of the sealed body).
   * @param platformEncPriv Platform's 32-byte X25519 private scalar (sender of the sealed body).
   * @param platformSignPriv Platform's 32-byte Ed25519 private seed (signer of the envelope).
   */
  public record BuildInput(
      List<AgentDtos.AgentTask> tasks,
      UUID packId,
      String platformId,
      String agentId,
      Instant issuedAt,
      String schemaVersionPayload,
      String exportedBy,
      byte[] agentEncPub,
      byte[] platformEncPriv,
      byte[] platformSignPriv) {

    /**
     * Defensive null-check on every field; the cryptography below assumes all material is present.
     */
    public BuildInput {
      if (tasks == null) {
        throw new IllegalArgumentException("tasks must not be null");
      }
      if (packId == null) {
        throw new IllegalArgumentException("packId must not be null");
      }
      if (platformId == null) {
        throw new IllegalArgumentException("platformId must not be null");
      }
      if (agentId == null) {
        throw new IllegalArgumentException("agentId must not be null");
      }
      if (issuedAt == null) {
        throw new IllegalArgumentException("issuedAt must not be null");
      }
      if (schemaVersionPayload == null) {
        throw new IllegalArgumentException("schemaVersionPayload must not be null");
      }
      if (exportedBy == null) {
        throw new IllegalArgumentException("exportedBy must not be null");
      }
      if (agentEncPub == null) {
        throw new IllegalArgumentException("agentEncPub must not be null");
      }
      if (platformEncPriv == null) {
        throw new IllegalArgumentException("platformEncPriv must not be null");
      }
      if (platformSignPriv == null) {
        throw new IllegalArgumentException("platformSignPriv must not be null");
      }
    }
  }
}
