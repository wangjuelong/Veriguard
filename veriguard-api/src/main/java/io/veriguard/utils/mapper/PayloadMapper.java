package io.veriguard.utils.mapper;

import static io.veriguard.database.model.Command.COMMAND_TYPE;
import static io.veriguard.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.veriguard.database.model.Executable.EXECUTABLE_TYPE;
import static io.veriguard.database.model.FileDrop.FILE_DROP_TYPE;
import static java.util.Optional.ofNullable;

import io.veriguard.database.model.*;
import io.veriguard.rest.atomic_testing.form.AttackPatternSimple;
import io.veriguard.rest.atomic_testing.form.StatusPayloadOutput;
import io.veriguard.rest.document.form.RelatedEntityOutput;
import io.veriguard.rest.payload.contract_output_element.ContractOutputElementSimple;
import io.veriguard.rest.payload.form.DetectionRemediationOutput;
import io.veriguard.rest.payload.output_parser.OutputParserSimple;
import io.veriguard.rest.payload.regex_group.RegexGroupSimple;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Mapper component for converting Payload entities to output DTOs.
 *
 * <p>Handles complex payload mapping including different payload types (Command, Executable,
 * FileDrop, DnsResolution), status output generation, and detection remediation mapping.
 *
 * @see io.veriguard.database.model.Payload
 * @see io.veriguard.rest.atomic_testing.form.StatusPayloadOutput
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class PayloadMapper {

  private final ApplicationContext context;

  /**
   * Extracts payload output information from an attackChainNode.
   *
   * <p>Determines the appropriate payload representation based on whether the attackChainNode has been
   * executed. For executed attackChainNodes, returns the saved status payload. For pending attackChainNodes,
   * constructs the payload output from the nodeExecutor contract configuration.
   *
   * @param attackChainNode the optional attackChainNode to extract payload from
   * @return the status payload output DTO, or null if no payload available
   */
  public StatusPayloadOutput getStatusPayloadOutputFromAttackChainNode(Optional<AttackChainNode> attackChainNode) {

    if (attackChainNode.isEmpty()) {
      return null;
    }

    AttackChainNode attackChainNodeObj = attackChainNode.get();
    Optional<NodeContract> nodeContractOpt = attackChainNodeObj.getNodeContract();
    if (nodeContractOpt.isEmpty() || nodeContractOpt.get().getPayload() == null) {
      return null;
    }

    NodeContract nodeContract = nodeContractOpt.get();
    StatusPayloadOutput.StatusPayloadOutputBuilder statusPayloadOutputBuilder =
        StatusPayloadOutput.builder();

    if (ofNullable(attackChainNode.get().getContent()).map(c -> c.has("obfuscator")).orElse(Boolean.FALSE)) {
      String obfuscator = attackChainNode.get().getContent().findValue("obfuscator").asText();
      statusPayloadOutputBuilder.obfuscator(obfuscator);
    }

    Optional<AttackChainNodeStatus> attackChainNodeStatusOpt = attackChainNodeObj.getStatus();
    Payload payload = nodeContract.getPayload();

    // Handle the case when attackChainNode has not been executed yet or no payload output exists
    if (attackChainNodeStatusOpt.isEmpty() || attackChainNodeStatusOpt.get().getPayloadOutput() == null) {
      if (payload != null) {
        populatePayloadDetails(statusPayloadOutputBuilder, payload, nodeContract);

        // Handle different payload types
        processPayloadType(statusPayloadOutputBuilder, payload);
        return statusPayloadOutputBuilder.build();
      } else {
        return null;
      }
    }

    // If attackChainNode has been executed, reuse the previous status
    return attackChainNodeStatusOpt
        .map(AttackChainNodeStatus::getPayloadOutput)
        .map(
            statusPayload ->
                populateExecutedPayload(
                    statusPayloadOutputBuilder, statusPayload, nodeContract))
        .orElse(null);
  }

  private Set<RegexGroupSimple> toRegexGroupSimple(Set<RegexGroup> regexGroups) {
    return regexGroups.stream()
        .map(
            regexGroup ->
                RegexGroupSimple.builder()
                    .id(regexGroup.getId())
                    .field(regexGroup.getField())
                    .indexValues(regexGroup.getIndexValues())
                    .build())
        .collect(Collectors.toSet());
  }

  private Set<ContractOutputElementSimple> toContractOutputElementsSimple(
      Set<ContractOutputElement> contractElements) {
    return contractElements.stream()
        .map(
            contractElement ->
                ContractOutputElementSimple.builder()
                    .id(contractElement.getId())
                    .rule(contractElement.getRule())
                    .name(contractElement.getName())
                    .key(contractElement.getKey())
                    .type(contractElement.getType())
                    .tagIds(contractElement.getTags().stream().map(Tag::getId).toList())
                    .regexGroups(toRegexGroupSimple(contractElement.getRegexGroups()))
                    .build())
        .collect(Collectors.toSet());
  }

  private Set<OutputParserSimple> toOutputParsersSimple(Set<OutputParser> outputParsers) {
    return outputParsers.stream()
        .map(
            parser ->
                OutputParserSimple.builder()
                    .id(parser.getId())
                    .mode(parser.getMode())
                    .type(parser.getType())
                    .contractOutputElement(
                        toContractOutputElementsSimple(parser.getContractOutputElements()))
                    .build())
        .collect(Collectors.toSet());
  }

  private void populatePayloadDetails(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder,
      Payload payload,
      NodeContract nodeContract) {
    builder
        .arguments(payload.getArguments())
        .prerequisites(payload.getPrerequisites())
        .externalId(payload.getExternalId())
        .cleanupExecutor(payload.getCleanupExecutor())
        .name(payload.getName())
        .type(payload.getType())
        .collectorType(payload.getCollectorType())
        .description(payload.getDescription())
        .tags(payload.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .platforms(payload.getPlatforms())
        .payloadOutputParsers(toOutputParsersSimple(payload.getOutputParsers()))
        .attackPatterns(toAttackPatternSimples(nodeContract.getAttackPatterns()))
        .executableArch(nodeContract.getArch());
  }

  private void processPayloadType(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder, Payload payload) {
    switch (payload.getType()) {
      case COMMAND_TYPE:
        handleCommandType(builder, (Command) Hibernate.unproxy(payload));
        break;
      case EXECUTABLE_TYPE:
        handleExecutableType(builder, (Executable) Hibernate.unproxy(payload));
        break;
      case FILE_DROP_TYPE:
        handleFileDropType(builder, (FileDrop) Hibernate.unproxy(payload));
        break;
      case DNS_RESOLUTION_TYPE:
        handleDnsResolutionType(builder, (DnsResolution) Hibernate.unproxy(payload));
        break;
      default:
        break;
    }
  }

  private void handleCommandType(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder, Command payloadCommand) {
    List<String> cleanupCommands = new ArrayList<>();
    if (payloadCommand.getCleanupCommand() != null) {
      cleanupCommands.add(payloadCommand.getCleanupCommand());
    }

    PayloadCommandBlock commandBlock =
        new PayloadCommandBlock(
            payloadCommand.getExecutor(), payloadCommand.getContent(), cleanupCommands);
    builder.payloadCommandBlocks(Collections.singletonList(commandBlock));
  }

  private void handleExecutableType(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder, Executable payloadExecutable) {
    builder.executableFile(new StatusPayloadDocument(payloadExecutable.getExecutableFile()));
  }

  private void handleFileDropType(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder, FileDrop payloadFileDrop) {
    builder.fileDropFile(new StatusPayloadDocument(payloadFileDrop.getFileDropFile()));
  }

  private void handleDnsResolutionType(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder, DnsResolution payloadDnsResolution) {
    builder.hostname(payloadDnsResolution.getHostname());
  }

  private StatusPayloadOutput populateExecutedPayload(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder,
      StatusPayload statusPayload,
      NodeContract nodeContract) {
    builder
        .cleanupExecutor(statusPayload.getCleanupExecutor())
        .payloadCommandBlocks(statusPayload.getPayloadCommandBlocks())
        .arguments(statusPayload.getArguments())
        .prerequisites(statusPayload.getPrerequisites())
        .externalId(statusPayload.getExternalId())
        .executableFile(statusPayload.getExecutableFile())
        .fileDropFile(statusPayload.getFileDropFile())
        .hostname(statusPayload.getHostname())
        .attackPatterns(toAttackPatternSimples(nodeContract.getAttackPatterns()))
        .executableArch(nodeContract.getArch())
        .name(statusPayload.getName())
        .type(statusPayload.getType())
        .description(statusPayload.getDescription())
        .platforms(nodeContract.getPlatforms());

    Payload payload = nodeContract.getPayload();
    if (payload != null) {
      builder
          .collectorType(payload.getCollectorType())
          .payloadOutputParsers(toOutputParsersSimple(payload.getOutputParsers()))
          .tags(payload.getTags().stream().map(Tag::getId).collect(Collectors.toSet()));
    }

    return builder.build();
  }

  /**
   * Converts a list of attack patterns to simplified DTOs.
   *
   * @param attackPatterns the attack patterns to convert
   * @return list of simplified attack pattern DTOs
   */
  public List<AttackPatternSimple> toAttackPatternSimples(List<AttackPattern> attackPatterns) {
    return attackPatterns.stream()
        .filter(Objects::nonNull)
        .map(this::toAttackPatternSimple)
        .toList();
  }

  private AttackPatternSimple toAttackPatternSimple(AttackPattern attackPattern) {
    return AttackPatternSimple.builder()
        .id(attackPattern.getId())
        .name(attackPattern.getName())
        .externalId(attackPattern.getExternalId())
        .build();
  }

  /**
   * Converts detection remediations to output DTOs.
   *
   * @param detectionRemediations the detection remediations to convert
   * @return list of detection remediation output DTOs
   */
  public List<DetectionRemediationOutput> toDetectionRemediationOutputs(
      List<DetectionRemediation> detectionRemediations) {
    return detectionRemediations.stream()
        .map(PayloadMapper::toDetectionRemediationOutput)
        .toList();
  }

  public static DetectionRemediationOutput toDetectionRemediationOutput(
      DetectionRemediation detectionRemediation) {
    return DetectionRemediationOutput.builder()
        .id(detectionRemediation.getId())
        .payloadId(detectionRemediation.getPayload().getId())
        .collectorType(detectionRemediation.getCollector().getType())
        .values(detectionRemediation.getValues())
        .authorRule(detectionRemediation.getAuthorRule())
        .build();
  }

  /**
   * Converts a set of payloads to related entity outputs.
   *
   * @param payloads the payloads to convert
   * @return set of related entity output DTOs
   */
  public static Set<RelatedEntityOutput> toRelatedEntityOutputs(Set<Payload> payloads) {
    return payloads.stream().map(PayloadMapper::toRelatedEntityOutput).collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutput(Payload payload) {
    return RelatedEntityOutput.builder().id(payload.getId()).name(payload.getName()).build();
  }
}
