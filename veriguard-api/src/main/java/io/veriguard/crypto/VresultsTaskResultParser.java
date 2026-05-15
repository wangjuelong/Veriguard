package io.veriguard.crypto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.rest.agent.AgentDtos;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Parse a {@code .vresults} envelope back into the plaintext list of {@link AgentDtos.ResultInput}
 * records — the platform-side counterpart to the Rust agent's {@code
 * veriguard-agent/src/pack/executor.rs::execute_vpack} which emits this exact wire form (spec
 * §3.5.3).
 *
 * <p>This is the inverse of {@link VpackTaskListBuilder}: where the builder seals a list of {@link
 * AgentDtos.AgentTask}, this parser opens a list of {@link AgentDtos.ResultInput} produced by an
 * agent that has finished executing the corresponding {@code .vpack}.
 *
 * <p>Pipeline:
 *
 * <ol>
 *   <li>{@link VresultsSerializer#parse} verifies the Ed25519 signature against the caller-supplied
 *       agent signing public key and returns the parsed metadata + encrypted envelope (still
 *       ciphertext).
 *   <li>{@link X25519BoxService#open} decrypts the body using the platform X25519 private scalar +
 *       the sender X25519 public key embedded in the envelope (no KDF, raw ECDH — matches Rust
 *       {@code crypto::x25519_box::open_box}).
 *   <li>Jackson parses the resulting UTF-8 JSON bytes as a {@code List<ResultInput>}. Field order
 *       matches the input task list 1-to-1 (the platform correlates request → response by index, no
 *       {@code task_id} field on the response side).
 * </ol>
 *
 * <h2>Wire schema (Rust-locked — A.7.3 commit {@code e1877d3})</h2>
 *
 * The plaintext is a JSON <b>array</b> of {@link AgentDtos.ResultInput}; the field names are
 * identical to the Rust {@code transport::poll::TaskResult} struct via {@code @JsonProperty}
 * annotations (snake_case wire form: {@code status / exit_code / stdout / stderr / started_at /
 * finished_at / error_message}). All-null fields are emitted as JSON {@code null} on both sides;
 * the Rust agent never strips them.
 */
@Component
public class VresultsTaskResultParser {

  private static final TypeReference<List<AgentDtos.ResultInput>> RESULT_LIST_TYPE =
      new TypeReference<>() {};

  private final VresultsSerializer vresultsSerializer;
  private final X25519BoxService x25519Box;
  private final ObjectMapper objectMapper;

  public VresultsTaskResultParser(
      VresultsSerializer vresultsSerializer,
      X25519BoxService x25519Box,
      ObjectMapper objectMapper) {
    this.vresultsSerializer = vresultsSerializer;
    this.x25519Box = x25519Box;
    this.objectMapper = objectMapper;
  }

  /**
   * Verify + decrypt + decode a {@code .vresults} envelope.
   *
   * @param envelopeBytes UTF-8 bytes of the canonical {@code .vresults} JSON envelope (typically
   *     {@code fs.read} of the file the agent emitted)
   * @param expectedAgentSignPub 32-byte Ed25519 public key of the agent that signed the envelope
   *     (looked up from the platform's agents table by {@code agent_id})
   * @param platformEncPriv 32-byte X25519 private scalar of the platform (recipient of the sealed
   *     body)
   * @return parsed metadata + decoded result list; the result list length equals {@code
   *     metadata.resultCount()} and matches the input task list 1-to-1 by index
   * @throws VpackSerializer.SchemaVersionException if {@code schema_version} or {@code format} is
   *     wrong
   * @throws VpackSerializer.SignatureVerificationException if the signer public key does not match
   *     {@code expectedAgentSignPub} or Ed25519 verify fails
   * @throws VpackSerializer.VpackParseException for malformed JSON envelopes
   * @throws X25519BoxService.BoxOpenException if Poly1305 authentication fails (tamper / wrong
   *     recipient key)
   * @throws ResultListParseException if the decrypted bytes are not a valid JSON array of {@link
   *     AgentDtos.ResultInput}
   */
  public ParsedTaskResults parse(
      byte[] envelopeBytes, byte[] expectedAgentSignPub, byte[] platformEncPriv) {
    if (envelopeBytes == null) {
      throw new IllegalArgumentException("envelopeBytes must not be null");
    }
    if (expectedAgentSignPub == null) {
      throw new IllegalArgumentException("expectedAgentSignPub must not be null");
    }
    if (platformEncPriv == null) {
      throw new IllegalArgumentException("platformEncPriv must not be null");
    }

    // 1. Verify envelope signature + extract metadata + ciphertext.
    VresultsSerializer.VresultsContents contents =
        vresultsSerializer.parse(envelopeBytes, expectedAgentSignPub);

    // 2. Decrypt the sealed body.  Sender pub comes from the envelope (the agent's static or
    //    ephemeral X25519 public key for this pack); recipient is the platform's static priv.
    VresultsSerializer.VresultsEncryptedEnvelope env = contents.encryptedEnvelope();
    byte[] plaintext =
        x25519Box.open(env.ciphertext(), env.nonce(), env.senderX25519Pub(), platformEncPriv);

    // 3. Parse the decrypted JSON bytes as a List<ResultInput>.
    List<AgentDtos.ResultInput> results;
    try {
      results = objectMapper.readValue(plaintext, RESULT_LIST_TYPE);
    } catch (IOException ex) {
      throw new ResultListParseException(
          "Failed to parse decrypted .vresults body as List<ResultInput>", ex);
    }

    return new ParsedTaskResults(contents.metadata(), results);
  }

  /** Successful parse: envelope metadata + decoded result list. */
  public record ParsedTaskResults(
      VresultsSerializer.VresultsMetadata metadata, List<AgentDtos.ResultInput> results) {}

  /**
   * Thrown when the decrypted ciphertext body is not a valid JSON array of {@link
   * AgentDtos.ResultInput}. Distinct from {@link VpackSerializer.VpackParseException} which applies
   * to the envelope JSON, not the encrypted body.
   */
  public static class ResultListParseException extends RuntimeException {
    public ResultListParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
