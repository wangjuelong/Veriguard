package io.veriguard.utils;

import static io.veriguard.database.model.Command.COMMAND_TYPE;
import static io.veriguard.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.veriguard.database.model.Executable.EXECUTABLE_TYPE;
import static io.veriguard.database.model.FileDrop.FILE_DROP_TYPE;
import static io.veriguard.database.model.NetworkTraffic.NETWORK_TRAFFIC_TYPE;
import static io.veriguard.utils.ExpectationUtils.isAssetExpectation;
import static io.veriguard.utils.ExpectationUtils.isAssetGroupExpectation;
import static java.util.Collections.emptyList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.helper.ObjectMapperHelper;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/**
 * Component providing utility methods for attackChainNode operations.
 *
 * <p>Handles payload extraction, expectation filtering, row validation for imports, and attackChainNode
 * duplication. This component is central to attackChainNode processing workflows including execution,
 * import, and cloning operations.
 *
 * @see io.veriguard.database.model.AttackChainNode
 * @see io.veriguard.database.model.StatusPayload
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class AttackChainNodeUtils {

  /**
   * Extracts the payload information from an attackChainNode.
   *
   * <p>Determines the appropriate payload based on the attackChainNode's execution status or nodeExecutor
   * contract. Supports multiple payload types including:
   *
   * <ul>
   *   <li>Command payloads (shell commands)
   *   <li>Executable payloads (binary files)
   *   <li>File drop payloads (file deployment)
   *   <li>DNS resolution payloads (DNS queries)
   *   <li>Network traffic payloads (network operations)
   * </ul>
   *
   * <p>If the attackChainNode has been executed, returns the saved payload output. Otherwise, constructs the
   * payload from the nodeExecutor contract configuration.
   *
   * @param attackChainNode the attackChainNode to extract payload from
   * @return the status payload, or {@code null} if no payload can be determined
   */
  public StatusPayload getStatusPayloadFromAttackChainNode(final AttackChainNode attackChainNode) {
    if (attackChainNode == null) {
      return null;
    }

    if (attackChainNode.getStatus().isPresent() && attackChainNode.getStatus().get().getPayloadOutput() != null) {
      // Commands lines saved because attackChainNode has been executed
      return attackChainNode.getStatus().get().getPayloadOutput();
    }

    NodeContract nodeContract = attackChainNode.getNodeContract().orElse(null);

    if (nodeContract == null || attackChainNode.getNodeContract().get().getPayload() == null) {
      return null;
    }

    Payload payload = nodeContract.getPayload();

    if (COMMAND_TYPE.equals(nodeContract.getPayload().getType())) {
      // AttackChainNode has a command payload
      Command payloadCommand = (Command) Hibernate.unproxy(payload);
      PayloadCommandBlock payloadCommandBlock =
          new PayloadCommandBlock(payloadCommand.getExecutor(), payloadCommand.getContent(), null);
      if (payloadCommand.getCleanupCommand() != null) {
        payloadCommandBlock.setCleanupCommand(List.of(payloadCommand.getCleanupCommand()));
      }
      return new StatusPayload(
          payloadCommand.getName(),
          payloadCommand.getDescription(),
          COMMAND_TYPE,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          payloadCommand.getExternalId(),
          payloadCommand.getPrerequisites(),
          payloadCommand.getArguments(),
          List.of(payloadCommandBlock),
          payloadCommand.getCleanupExecutor());
    }
    if (EXECUTABLE_TYPE.equals(nodeContract.getPayload().getType())) {
      // AttackChainNode has a command payload
      Executable payloadExecutable = (Executable) Hibernate.unproxy(payload);
      return new StatusPayload(
          payloadExecutable.getName(),
          payloadExecutable.getDescription(),
          EXECUTABLE_TYPE,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          payloadExecutable.getExecutableFile(),
          null,
          null,
          null,
          emptyList(),
          null);
    }
    if (FILE_DROP_TYPE.equals(nodeContract.getPayload().getType())) {
      // AttackChainNode has a command payload
      FileDrop payloadFileDrop = (FileDrop) Hibernate.unproxy(payload);
      return new StatusPayload(
          payloadFileDrop.getName(),
          payloadFileDrop.getDescription(),
          FILE_DROP_TYPE,
          null,
          null,
          null,
          null,
          null,
          null,
          payloadFileDrop.getFileDropFile(),
          null,
          null,
          null,
          null,
          emptyList(),
          null);
    }
    if (DNS_RESOLUTION_TYPE.equals(nodeContract.getPayload().getType())) {
      // AttackChainNode has a command payload
      DnsResolution payloadDnsResolution = (DnsResolution) Hibernate.unproxy(payload);
      return new StatusPayload(
          payloadDnsResolution.getName(),
          payloadDnsResolution.getDescription(),
          DNS_RESOLUTION_TYPE,
          null,
          null,
          null,
          null,
          null,
          payloadDnsResolution.getHostname(),
          null,
          null,
          null,
          null,
          null,
          emptyList(),
          null);
    }
    if (NETWORK_TRAFFIC_TYPE.equals(nodeContract.getPayload().getType())) {
      // AttackChainNode has a command payload
      NetworkTraffic payloadNetworkTraffic = (NetworkTraffic) Hibernate.unproxy(payload);
      return new StatusPayload(
          payloadNetworkTraffic.getName(),
          payloadNetworkTraffic.getDescription(),
          NETWORK_TRAFFIC_TYPE,
          payloadNetworkTraffic.getProtocol(),
          payloadNetworkTraffic.getPortDst(),
          payloadNetworkTraffic.getPortSrc(),
          payloadNetworkTraffic.getIpDst(),
          payloadNetworkTraffic.getIpSrc(),
          null,
          null,
          null,
          null,
          null,
          null,
          emptyList(),
          null);
    }

    return null;
  }

  /**
   * Retrieves the primary expectations from an attackChainNode.
   *
   * <p>Primary expectations are those directly associated with the attackChainNode's targets (teams, assets,
   * or asset groups). This filters out derived or secondary expectations that may exist for
   * sub-targets.
   *
   * @param attackChainNode the attackChainNode to get expectations from
   * @return a list of expectations matching the attackChainNode's direct targets
   */
  public List<AttackChainNodeExpectation> getPrimaryExpectations(AttackChainNode attackChainNode) {
    List<String> firstIds = new ArrayList<>();

    firstIds.addAll(attackChainNode.getTeams().stream().map(Team::getId).toList());
    firstIds.addAll(attackChainNode.getAssets().stream().map(Asset::getId).toList());
    firstIds.addAll(attackChainNode.getAssetGroups().stream().map(AssetGroup::getId).toList());

    // Reject expectations if none of the team, asset, or assetGroup IDs exist in firstIds
    return attackChainNode.getExpectations().stream()
        .filter(
            expectation -> {
              boolean teamMatch =
                  expectation.getTeam() != null && firstIds.contains(expectation.getTeam().getId());
              boolean assetMatch =
                  isAssetExpectation(expectation)
                      && firstIds.contains(expectation.getAsset().getId());
              boolean assetGroupMatch =
                  isAssetGroupExpectation(expectation)
                      && firstIds.contains(expectation.getAssetGroup().getId());
              return teamMatch || assetMatch || assetGroupMatch;
            })
        .collect(Collectors.toList());
  }

  /**
   * Checks if an Excel row is empty or contains only blank cells.
   *
   * <p>Used during XLS import operations to skip empty rows. A row is considered empty if:
   *
   * <ul>
   *   <li>The row is null
   *   <li>The row has no cells
   *   <li>All cells are blank or contain only whitespace
   * </ul>
   *
   * @param row the Excel row to check
   * @return {@code true} if the row is empty, {@code false} otherwise
   */
  public static boolean checkIfRowIsEmpty(Row row) {
    if (row == null) {
      return true;
    }
    if (row.getLastCellNum() <= 0) {
      return true;
    }
    for (int cellNum = row.getFirstCellNum(); cellNum < row.getLastCellNum(); cellNum++) {
      Cell cell = row.getCell(cellNum);
      if (cell != null
          && cell.getCellType() != CellType.BLANK
          && StringUtils.isNotBlank(cell.toString())) {
        return false;
      }
    }
    return true;
  }

  /** Shared ObjectMapper instance for JSON processing in attackChainNode duplication. */
  private static final ObjectMapper STATIC_MAPPER = ObjectMapperHelper.veriguardJsonMapper();

  /**
   * Creates a deep copy of an attackChainNode.
   *
   * <p>Duplicates all properties of the source attackChainNode including content, teams, assets, asset
   * groups, tags, and dependencies. The content is deep-copied to ensure modifications to the
   * duplicate don't affect the original.
   *
   * <p>The duplicated attackChainNode maintains references to the same attackChainRun/attackChain and nodeExecutor
   * contract as the original.
   *
   * @param attackChainNodeOrigin the attackChainNode to duplicate (must not be null)
   * @return a new attackChainNode instance with copied properties
   * @throws RuntimeException if the content cannot be serialized/deserialized
   */
  public static AttackChainNode duplicateAttackChainNode(@NotNull AttackChainNode attackChainNodeOrigin) {
    AttackChainNode duplicatedAttackChainNode = new AttackChainNode();
    duplicatedAttackChainNode.setUser(attackChainNodeOrigin.getUser());
    duplicatedAttackChainNode.setTitle(attackChainNodeOrigin.getTitle());
    duplicatedAttackChainNode.setDescription(attackChainNodeOrigin.getDescription());
    try {
      ObjectNode content =
          STATIC_MAPPER.readValue(
              STATIC_MAPPER.writeValueAsString(attackChainNodeOrigin.getContent()), ObjectNode.class);
      duplicatedAttackChainNode.setContent(content);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    duplicatedAttackChainNode.setAllTeams(attackChainNodeOrigin.isAllTeams());
    duplicatedAttackChainNode.setTeams(new ArrayList<>(attackChainNodeOrigin.getTeams()));
    duplicatedAttackChainNode.setEnabled(attackChainNodeOrigin.isEnabled());
    duplicatedAttackChainNode.setDependsDuration(attackChainNodeOrigin.getDependsDuration());
    if (attackChainNodeOrigin.getDependsOn() != null) {
      duplicatedAttackChainNode.setDependsOn(new ArrayList<>(attackChainNodeOrigin.getDependsOn()));
    }
    duplicatedAttackChainNode.setCountry(attackChainNodeOrigin.getCountry());
    duplicatedAttackChainNode.setCity(attackChainNodeOrigin.getCity());
    duplicatedAttackChainNode.setNodeContract(attackChainNodeOrigin.getNodeContract().orElse(null));
    duplicatedAttackChainNode.setAssetGroups(new ArrayList<>(attackChainNodeOrigin.getAssetGroups()));
    duplicatedAttackChainNode.setAssets(new ArrayList<>(attackChainNodeOrigin.getAssets()));
    duplicatedAttackChainNode.setCommunications(new ArrayList<>(attackChainNodeOrigin.getCommunications()));
    duplicatedAttackChainNode.setTags(new HashSet<>(attackChainNodeOrigin.getTags()));

    duplicatedAttackChainNode.setAttackChainRun(attackChainNodeOrigin.getAttackChainRun());
    duplicatedAttackChainNode.setAttackChain(attackChainNodeOrigin.getAttackChain());
    return duplicatedAttackChainNode;
  }

  /**
   * Retrieve all attackChainNode expectations from a list of attackChainNodes
   *
   * @param attackChainNodes to retrive all attackChainNode expectations
   * @return a stream of all retrieve attackChainNode expectations
   */
  public static Stream<AttackChainNodeExpectation> extractAttackChainNodeExpectationsFromAttackChainNodes(
      List<AttackChainNode> attackChainNodes) {
    return attackChainNodes.stream().flatMap(attackChainNode -> attackChainNode.getExpectations().stream());
  }
}
