package io.veriguard.rest.attack_chain_node.service;

import static io.veriguard.database.model.NodeContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR;
import static io.veriguard.executors.Executor.CMD;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.injector_contract.fields.ContractFieldType;
import io.veriguard.injectors.veriguard.model.VeriguardImplantAttackChainNodeContent;
import io.veriguard.injectors.veriguard.util.VeriguardObfuscationMap;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.payload.service.PayloadService;
import io.veriguard.service.AttackChainNodeExpectationService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class ExecutableNodeService {

  private final AttackChainNodeService attackChainNodeService;
  private final DocumentService documentService;
  private final AttackChainNodeStatusService attackChainNodeStatusService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final PayloadService payloadService;

  @Resource protected ObjectMapper mapper;

  private static final Pattern argumentsRegex = Pattern.compile("#\\{([^#{}]+)}");
  private static final Pattern cmdVariablesRegex = Pattern.compile("%(\\w+)%");

  private List<String> getArgumentsFromCommandLines(String command) {
    Matcher matcher = argumentsRegex.matcher(command);
    List<String> commandParameters = new ArrayList<>();

    while (matcher.find()) {
      commandParameters.add(matcher.group(1));
    }

    return commandParameters;
  }

  private String getArgumentValueOrDefault(
      String key, ObjectNode attackChainNodeContent, String defaultValue) {
    return attackChainNodeContent.get(key) != null && !attackChainNodeContent.get(key).asText().isEmpty()
        ? attackChainNodeContent.get(key).asText()
        : defaultValue;
  }

  private String getTargetedAssetArgumentValue(
      String argumentKey,
      ObjectNode attackChainNodeContent,
      PayloadArgument defaultPayloadArgument,
      List<ObjectNode> nodeContractContentFields) {
    Map<String, Endpoint> valuesAssetsMap =
        attackChainNodeService.retrieveValuesOfTargetedAssetFromAttackChainNode(
            nodeContractContentFields, attackChainNodeContent, argumentKey);

    String assetSeparator =
        getArgumentValueOrDefault(
            CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR + "-" + argumentKey,
            attackChainNodeContent,
            defaultPayloadArgument.getSeparator());

    return String.join(assetSeparator, valuesAssetsMap.keySet());
  }

  private String replaceArgumentsByValue(
      String command,
      List<PayloadArgument> defaultPayloadArguments,
      List<ObjectNode> nodeContractContentFields,
      ObjectNode attackChainNodeContent) {

    List<String> argumentKeys = getArgumentsFromCommandLines(command);

    for (String argumentKey : argumentKeys) {
      String value;
      PayloadArgument defaultPayloadArgument =
          defaultPayloadArguments.stream()
              .filter(a -> a.getKey().equals(argumentKey))
              .findFirst()
              .orElse(null);

      // If the argument is a targeted asset, we need to fetch the asset details
      if (defaultPayloadArgument != null
          && ContractFieldType.TargetedAsset.label.equals(defaultPayloadArgument.getType())) {
        value =
            getTargetedAssetArgumentValue(
                argumentKey, attackChainNodeContent, defaultPayloadArgument, nodeContractContentFields);

      } else {
        value =
            getArgumentValueOrDefault(
                argumentKey,
                attackChainNodeContent,
                defaultPayloadArgument != null ? defaultPayloadArgument.getDefaultValue() : "");
        // If arg is a doc, specific handling
        // We need to resolve the doc name and add special prefix #{location} that will be resolved
        // by the implant
        boolean isDocArg =
            defaultPayloadArgument != null
                && defaultPayloadArgument.getType().equalsIgnoreCase("document");
        if (isDocArg && !value.isEmpty()) {
          try {
            Document doc = documentService.document(value);
            value = "#{location}/" + doc.getName();
          } catch (ElementNotFoundException e) {
            log.error("Payload argument target unexisting document", e);
          }
        }
      }

      command = command.replace("#{" + argumentKey + "}", value);
    }
    return command;
  }

  public static String replaceCmdVariables(String cmd) {
    Matcher matcher = cmdVariablesRegex.matcher(cmd);

    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String variableName = matcher.group(1);
      matcher.appendReplacement(result, "!" + variableName + "!");
    }
    matcher.appendTail(result);

    return result.toString();
  }

  public static String formatMultilineCommand(String command) {
    String[] lines = command.split("\n");
    StringBuilder formattedCommand = new StringBuilder();

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmedLine = line.trim();
      if (trimmedLine.isEmpty()) {
        continue;
      }
      formattedCommand.append(trimmedLine);

      boolean isLastLine = (i == lines.length - 1);
      boolean isAfterParentheses = trimmedLine.endsWith("(");
      boolean isBeforeParentheses = !isLastLine && lines[i + 1].trim().startsWith(")");

      if (!isAfterParentheses && !isBeforeParentheses && !isLastLine) {
        formattedCommand.append(" & ");
      } else {
        formattedCommand.append(" ");
      }
    }

    return formattedCommand.toString();
  }

  private String processAndEncodeCommand(
      String command,
      String executor,
      List<PayloadArgument> defaultPayloadArguments,
      ObjectNode attackChainNodeContent,
      List<ObjectNode> nodeContractContentFields,
      String obfuscator) {
    VeriguardObfuscationMap obfuscationMap = new VeriguardObfuscationMap(executor);
    String computedCommand =
        replaceArgumentsByValue(
            command, defaultPayloadArguments, nodeContractContentFields, attackChainNodeContent);

    if (CMD.equals(executor)) {
      computedCommand = replaceCmdVariables(computedCommand);
      computedCommand = formatMultilineCommand(computedCommand);
    }

    computedCommand = obfuscationMap.executeObfuscation(obfuscator, computedCommand, executor);

    return Base64.getEncoder().encodeToString(computedCommand.getBytes());
  }

  public Payload getExecutablePayloadAndUpdateAttackChainNodeStatus(String attackChainNodeId, String agentId)
      throws Exception {
    // Need startTime to be defined before everything else to be the most accurate start time, as
    // this whole process is
    // called at the beginning of the implant execution. A better solution would be to have the
    // implant send the start time
    // but it would require more changes in the implant code and change this endpoint from a get to
    // a post.
    Instant startTime = Instant.now();
    Payload payloadToExecute = getExecutablePayloadAttackChainNode(attackChainNodeId);
    this.attackChainNodeStatusService.addStartImplantExecutionTraceByAttackChainNode(
        attackChainNodeId, agentId, "Implant is up and starting execution", startTime);
    this.attackChainNodeExpectationService.addStartDateSignatureToAttackChainNodeExpectationsByAgent(
        attackChainNodeId, agentId, startTime);
    return payloadToExecute;
  }

  private Payload getExecutablePayloadAttackChainNode(String attackChainNodeId) throws Exception {
    AttackChainNode attackChainNode = attackChainNodeService.attackChainNode(attackChainNodeId);
    NodeContract contract =
        attackChainNode
            .getNodeContract()
            .orElseThrow(() -> new ElementNotFoundException("Inject contract not found"));
    VeriguardImplantAttackChainNodeContent content =
        attackChainNodeService.convertAttackChainNodeContent(attackChainNode, VeriguardImplantAttackChainNodeContent.class);
    String obfuscator = content.getObfuscator() != null ? content.getObfuscator() : "plain-text";

    if (contract.getPayload() == null) {
      throw new ElementNotFoundException("Payload not found");
    }
    Payload payloadToExecute = payloadService.generateDuplicatedPayload(contract.getPayload());
    JsonNode nodeContractFieldsNode = contract.getConvertedContent().get("fields");
    List<ObjectNode> nodeContractFields =
        StreamSupport.stream(nodeContractFieldsNode.spliterator(), false)
            .map(ObjectNode.class::cast)
            .toList();

    // prerequisite
    if (!isEmpty(contract.getPayload().getPrerequisites())) {
      List<PayloadPrerequisite> prerequisiteList = new ArrayList<>();
      contract
          .getPayload()
          .getPrerequisites()
          .forEach(
              prerequisite -> {
                PayloadPrerequisite payload = new PayloadPrerequisite();
                payload.setExecutor(prerequisite.getExecutor());
                if (hasText(prerequisite.getCheckCommand())) {
                  payload.setCheckCommand(
                      processAndEncodeCommand(
                          prerequisite.getCheckCommand(),
                          prerequisite.getExecutor(),
                          contract.getPayload().getArguments(),
                          attackChainNode.getContent(),
                          nodeContractFields,
                          obfuscator));
                }
                if (hasText(prerequisite.getGetCommand())) {
                  payload.setGetCommand(
                      processAndEncodeCommand(
                          prerequisite.getGetCommand(),
                          prerequisite.getExecutor(),
                          contract.getPayload().getArguments(),
                          attackChainNode.getContent(),
                          nodeContractFields,
                          obfuscator));
                }
                prerequisiteList.add(payload);
              });
      payloadToExecute.setPrerequisites(prerequisiteList);
    }

    // cleanup
    if (contract.getPayload().getCleanupCommand() != null) {
      payloadToExecute.setCleanupExecutor(contract.getPayload().getCleanupExecutor());
      payloadToExecute.setCleanupCommand(
          processAndEncodeCommand(
              contract.getPayload().getCleanupCommand(),
              contract.getPayload().getCleanupExecutor(),
              contract.getPayload().getArguments(),
              attackChainNode.getContent(),
              nodeContractFields,
              obfuscator));
    }

    return processPayloadToExecute(
        payloadToExecute, contract, attackChainNode, nodeContractFields, obfuscator);
  }

  private Payload processPayloadToExecute(
      Payload payloadToExecute,
      NodeContract contract,
      AttackChainNode attackChainNode,
      List<ObjectNode> nodeContractFields,
      String obfuscator) {
    switch (contract.getPayload().getTypeEnum()) {
      case PayloadType.COMMAND:
        return processCommandPayload(
            payloadToExecute, contract, attackChainNode, nodeContractFields, obfuscator);
      case PayloadType.DNS_RESOLUTION:
        return processDnsResolutionPayload(payloadToExecute, attackChainNode);
      default:
        // All other payload types are intentionally passed through unchanged.
        return payloadToExecute;
    }
  }

  private Payload processCommandPayload(
      Payload payloadToExecute,
      NodeContract contract,
      AttackChainNode attackChainNode,
      List<ObjectNode> nodeContractFields,
      String obfuscator) {
    Command payloadCommand = (Command) payloadToExecute;
    payloadCommand.setExecutor(((Command) contract.getPayload()).getExecutor());
    payloadCommand.setContent(
        processAndEncodeCommand(
            payloadCommand.getContent(),
            payloadCommand.getExecutor(),
            contract.getPayload().getArguments(),
            attackChainNode.getContent(),
            nodeContractFields,
            obfuscator));
    return payloadCommand;
  }

  private Payload processDnsResolutionPayload(Payload payloadToExecute, AttackChainNode attackChainNode) {
    DnsResolution dnsResolution = (DnsResolution) payloadToExecute;
    dnsResolution.setHostname(
        replaceArgumentsByValue(
            dnsResolution.getHostname(), dnsResolution.getArguments(), null, attackChainNode.getContent()));
    return dnsResolution;
  }
}
