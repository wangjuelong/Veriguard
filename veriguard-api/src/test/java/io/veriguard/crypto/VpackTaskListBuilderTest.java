package io.veriguard.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.veriguard.rest.agent.AgentDtos;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VpackTaskListBuilder}.
 *
 * <p>Verifies the end-to-end build pipeline: Jackson serialize {@code List<AgentTask>} →
 * X25519+ChaCha seal → {@link VpackSerializer} envelope → bytes. Each test reverses the pipeline
 * (using the low-level services directly) to assert byte-level round-trip integrity.
 */
class VpackTaskListBuilderTest {

  private VpackTaskListBuilder builder;
  private VpackSerializer vpackSerializer;
  private X25519BoxService x25519Box;
  private Ed25519SignatureService ed25519;
  private ObjectMapper objectMapper;

  // Test keypairs — regenerated each test method via @BeforeEach so no shared mutable state.
  private byte[] platformSignPriv;
  private byte[] platformSignPub;
  private byte[] platformEncPriv;
  private byte[] agentEncPriv;
  private byte[] agentEncPub;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ed25519 = new Ed25519SignatureService();
    x25519Box = new X25519BoxService();
    vpackSerializer = new VpackSerializer(objectMapper, ed25519);
    builder = new VpackTaskListBuilder(vpackSerializer, x25519Box, objectMapper);

    Ed25519SignatureService.Ed25519KeyPair signPair = ed25519.generate();
    platformSignPriv = signPair.privateKey();
    platformSignPub = signPair.publicKey();

    X25519BoxService.X25519KeyPair platformEnc = x25519Box.generate();
    platformEncPriv = platformEnc.privateKey();

    X25519BoxService.X25519KeyPair agentEnc = x25519Box.generate();
    agentEncPriv = agentEnc.privateKey();
    agentEncPub = agentEnc.publicKey();
  }

  private VpackTaskListBuilder.BuildInput sampleInput(List<AgentDtos.AgentTask> tasks) {
    return new VpackTaskListBuilder.BuildInput(
        tasks,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
        "veriguard-prod-001",
        "agent-prod-01",
        Instant.parse("2026-05-15T10:30:00Z"),
        "1.0",
        "alice@platform",
        agentEncPub,
        platformEncPriv,
        platformSignPriv);
  }

  private List<AgentDtos.AgentTask> threeSampleTasks() {
    return List.of(
        new AgentDtos.AgentTask(
            "t-001", "http_attack", "http_get", "{\"url\":\"https://target/path1\"}", List.of()),
        new AgentDtos.AgentTask(
            "t-002",
            "pcap_replay",
            "pcap_file",
            "{\"path\":\"/captures/p1.pcap\"}",
            List.of("exp-1")),
        new AgentDtos.AgentTask(
            "t-003", "command_inject", "shell", "{\"cmd\":\"id\"}", List.of("exp-2", "exp-3")));
  }

  /**
   * Reverse the build pipeline using the low-level services and decode the encrypted body back into
   * the original task list. The canonical proof that build ⇋ parse forms a bijection.
   */
  private List<AgentDtos.AgentTask> openAndDecode(byte[] envelopeBytes) throws Exception {
    VpackSerializer.VpackContents contents = vpackSerializer.parse(envelopeBytes, platformSignPub);
    byte[] plaintext =
        x25519Box.open(
            contents.encryptedEnvelope().ciphertext(),
            contents.encryptedEnvelope().nonce(),
            contents.encryptedEnvelope().senderX25519Pub(),
            agentEncPriv);
    return objectMapper.readValue(plaintext, new TypeReference<List<AgentDtos.AgentTask>>() {});
  }

  @Test
  void buildAndReverseRoundTripThreeTasks() throws Exception {
    List<AgentDtos.AgentTask> tasks = threeSampleTasks();

    byte[] envelope = builder.build(sampleInput(tasks));
    assertThat(envelope).isNotEmpty();

    VpackSerializer.VpackContents contents = vpackSerializer.parse(envelope, platformSignPub);
    assertThat(contents.metadata().taskCount()).isEqualTo(3);
    assertThat(contents.metadata().packId().toString())
        .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    assertThat(contents.metadata().agentId()).isEqualTo("agent-prod-01");
    assertThat(contents.metadata().platformId()).isEqualTo("veriguard-prod-001");
    assertThat(contents.metadata().exportedBy()).isEqualTo("alice@platform");

    List<AgentDtos.AgentTask> decoded = openAndDecode(envelope);
    assertThat(decoded).isEqualTo(tasks);
  }

  @Test
  void buildAndReverseRoundTripEmptyTaskList() throws Exception {
    byte[] envelope = builder.build(sampleInput(List.of()));

    VpackSerializer.VpackContents contents = vpackSerializer.parse(envelope, platformSignPub);
    assertThat(contents.metadata().taskCount()).isZero();

    List<AgentDtos.AgentTask> decoded = openAndDecode(envelope);
    assertThat(decoded).isEmpty();
  }

  @Test
  void buildAndReverseRoundTripSingleTask() throws Exception {
    AgentDtos.AgentTask only = new AgentDtos.AgentTask("only", "http_attack", "x", "{}", List.of());
    byte[] envelope = builder.build(sampleInput(List.of(only)));

    List<AgentDtos.AgentTask> decoded = openAndDecode(envelope);
    assertThat(decoded).hasSize(1);
    assertThat(decoded.get(0)).isEqualTo(only);
  }

  @Test
  void buildPreservesTaskOrderInEncryptedBody() throws Exception {
    // Order is load-bearing: the agent emits the result list in the same order, and the platform
    // correlates by index.  This test pins the contract.
    List<AgentDtos.AgentTask> tasks = threeSampleTasks();

    byte[] envelope = builder.build(sampleInput(tasks));
    List<AgentDtos.AgentTask> decoded = openAndDecode(envelope);

    assertThat(decoded.stream().map(AgentDtos.AgentTask::taskId).toList())
        .containsExactly("t-001", "t-002", "t-003");
  }

  @Test
  void buildEmitsSnakeCaseFieldNamesInEncryptedBody() throws Exception {
    byte[] envelope = builder.build(sampleInput(threeSampleTasks()));
    VpackSerializer.VpackContents contents = vpackSerializer.parse(envelope, platformSignPub);
    byte[] plaintext =
        x25519Box.open(
            contents.encryptedEnvelope().ciphertext(),
            contents.encryptedEnvelope().nonce(),
            contents.encryptedEnvelope().senderX25519Pub(),
            agentEncPriv);
    String plaintextJson = new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);

    // These exact snake_case keys are the contract with Rust transport::poll::Task.
    assertThat(plaintextJson).contains("\"task_id\"");
    assertThat(plaintextJson).contains("\"capability\"");
    assertThat(plaintextJson).contains("\"injector_type\"");
    assertThat(plaintextJson).contains("\"payload\"");
    assertThat(plaintextJson).contains("\"expectations\"");
    // No camelCase leakage from Java field accessors.
    assertThat(plaintextJson).doesNotContain("\"taskId\"");
    assertThat(plaintextJson).doesNotContain("\"injectorType\"");
  }

  @Test
  void buildEnvelopeContainsMetadataMatchingInput() {
    VpackTaskListBuilder.BuildInput input = sampleInput(threeSampleTasks());
    byte[] envelope = builder.build(input);

    VpackSerializer.VpackContents contents = vpackSerializer.parse(envelope, platformSignPub);
    assertThat(contents.metadata().issuedAt()).isEqualTo(input.issuedAt());
    assertThat(contents.metadata().schemaVersionPayload()).isEqualTo("1.0");
    assertThat(contents.metadata().taskCount()).isEqualTo(input.tasks().size());
  }

  @Test
  void buildRejectsNullInput() {
    assertThatThrownBy(() -> builder.build(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("input");
  }

  @Test
  void buildInputRejectsNullTasks() {
    assertThatThrownBy(
            () ->
                new VpackTaskListBuilder.BuildInput(
                    null,
                    UUID.randomUUID(),
                    "p",
                    "a",
                    Instant.now(),
                    "1.0",
                    "x",
                    agentEncPub,
                    platformEncPriv,
                    platformSignPriv))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tasks");
  }

  @Test
  void buildInputRejectsNullAgentEncPub() {
    assertThatThrownBy(
            () ->
                new VpackTaskListBuilder.BuildInput(
                    List.of(),
                    UUID.randomUUID(),
                    "p",
                    "a",
                    Instant.now(),
                    "1.0",
                    "x",
                    null,
                    platformEncPriv,
                    platformSignPriv))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("agentEncPub");
  }

  @Test
  void buildInputRejectsNullPlatformSignPriv() {
    assertThatThrownBy(
            () ->
                new VpackTaskListBuilder.BuildInput(
                    List.of(),
                    UUID.randomUUID(),
                    "p",
                    "a",
                    Instant.now(),
                    "1.0",
                    "x",
                    agentEncPub,
                    platformEncPriv,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("platformSignPriv");
  }

  @Test
  void parsedSignatureMatchesPlatformSignPub() {
    byte[] envelope = builder.build(sampleInput(threeSampleTasks()));
    VpackSerializer.VpackContents contents = vpackSerializer.parse(envelope, platformSignPub);
    assertThat(contents.signerPub()).isEqualTo(platformSignPub);
  }

  @Test
  void wrongPlatformSignPubRejectsAtParseTime() {
    byte[] envelope = builder.build(sampleInput(threeSampleTasks()));
    byte[] otherPub = ed25519.generate().publicKey();
    assertThatThrownBy(() -> vpackSerializer.parse(envelope, otherPub))
        .isInstanceOf(VpackSerializer.SignatureVerificationException.class);
  }

  @Test
  void wrongAgentEncPrivCannotOpenEncryptedBody() {
    byte[] envelope = builder.build(sampleInput(threeSampleTasks()));
    VpackSerializer.VpackContents contents = vpackSerializer.parse(envelope, platformSignPub);

    byte[] otherAgentPriv = x25519Box.generate().privateKey();
    assertThatThrownBy(
            () ->
                x25519Box.open(
                    contents.encryptedEnvelope().ciphertext(),
                    contents.encryptedEnvelope().nonce(),
                    contents.encryptedEnvelope().senderX25519Pub(),
                    otherAgentPriv))
        .isInstanceOf(X25519BoxService.BoxOpenException.class);
  }
}
