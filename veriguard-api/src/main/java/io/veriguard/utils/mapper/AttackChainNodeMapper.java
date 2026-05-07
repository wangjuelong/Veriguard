package io.veriguard.utils.mapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.helper.NodeModelHelper;
import io.veriguard.rest.atomic_testing.form.*;
import io.veriguard.rest.document.form.RelatedEntityOutput;
import io.veriguard.rest.attack_chain_node.output.AttackChainNodeOutput;
import io.veriguard.rest.attack_chain_node.output.AttackChainNodeSimple;
import io.veriguard.rest.payload.output.PayloadSimple;
import io.veriguard.utils.NodeExpectationResultUtils;
import io.veriguard.utils.AttackChainNodeUtils;
import io.veriguard.utils.TargetType;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mapper component for converting AttackChainNode entities to various output DTOs.
 *
 * <p>Provides comprehensive mapping methods for transforming attackChainNode domain objects into API
 * response objects, including result overviews, simple representations, and target mappings.
 *
 * @see io.veriguard.database.model.AttackChainNode
 * @see io.veriguard.rest.attack_chain_node.output.AttackChainNodeOutput
 */
@Component
@RequiredArgsConstructor
public class AttackChainNodeMapper {

  private final AttackChainNodeStatusMapper attackChainNodeStatusMapper;
  private final AttackChainNodeExpectationMapper attackChainNodeExpectationMapper;
  private final AttackChainNodeUtils attackChainNodeUtils;

  /**
   * Converts an attackChainNode to a result overview output containing full execution details.
   *
   * <p>Includes attackChainNode metadata, status, expectations, kill chain phases, and aggregated
   * expectation results by type.
   *
   * @param attackChainNode the attackChainNode to convert
   * @return the attackChainNode result overview output DTO
   */
  public AttackChainNodeResultOverviewOutput toAttackChainNodeResultOverviewOutput(AttackChainNode attackChainNode) {
    // --
    Optional<NodeContract> nodeContract = attackChainNode.getNodeContract();

    List<String> documentIds =
        attackChainNode.getDocuments().stream()
            .map(AttackChainNodeDocument::getDocument)
            .map(Document::getId)
            .toList();

    return AttackChainNodeResultOverviewOutput.builder()
        .id(attackChainNode.getId())
        .title(attackChainNode.getTitle())
        .description(attackChainNode.getDescription())
        .content(attackChainNode.getContent())
        .type(nodeContract.map(contract -> contract.getNodeExecutor().getType()).orElse(null))
        .tagIds(attackChainNode.getTags().stream().map(Tag::getId).toList())
        .documentIds(documentIds)
        .nodeContract(toNodeContractOutput(nodeContract))
        .status(attackChainNodeStatusMapper.toAttackChainNodeStatusSimple(attackChainNode.getStatus()))
        .expectations(toAttackChainNodeExpectationSimples(attackChainNode.getExpectations()))
        .killChainPhases(toKillChainPhasesSimples(attackChainNode.getKillChainPhases()))
        .tags(attackChainNode.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .expectationResultByTypes(
            attackChainNodeExpectationMapper.extractExpectationResults(
                attackChainNode.getContent(),
                attackChainNodeUtils.getPrimaryExpectations(attackChainNode),
                NodeExpectationResultUtils::getScores))
        .isReady(attackChainNode.isReady())
        .updatedAt(attackChainNode.getUpdatedAt())
        .build();
  }

  // -- OBJECT[] to TARGETSIMPLE --

  /**
   * Converts raw database result arrays to target simple DTOs.
   *
   * @param targets the raw query results containing target data
   * @param type the type of targets being converted
   * @return list of target simple DTOs
   */
  public List<TargetSimple> toTargetSimple(List<Object[]> targets, TargetType type) {
    return targets.stream()
        .filter(Objects::nonNull)
        .map(target -> toTargetSimple(target, type))
        .toList();
  }

  /**
   * Converts a single raw database result array to a target simple DTO.
   *
   * @param target array containing [attackChainRunId, targetId, targetName]
   * @param type the type of target
   * @return the target simple DTO
   */
  public TargetSimple toTargetSimple(Object[] target, TargetType type) {
    return TargetSimple.builder()
        .id((String) target[1])
        .name((String) target[2])
        .type(type)
        .build();
  }

  // -- INJECTORCONTRACT to INJECTORCONTRACT SIMPLE --

  /**
   * Converts an optional nodeExecutor contract to its output representation.
   *
   * @param nodeContract the optional nodeExecutor contract
   * @return the nodeExecutor contract output DTO, or null if not present
   */
  public AtomicNodeContractOutput toNodeContractOutput(
      Optional<NodeContract> nodeContract) {
    return nodeContract
        .map(
            contract ->
                AtomicNodeContractOutput.builder()
                    .id(contract.getId())
                    .content(contract.getContent())
                    .convertedContent(contract.getConvertedContent())
                    .platforms(contract.getPlatforms())
                    .payload(toPayloadSimple(Optional.ofNullable(contract.getPayload())))
                    .labels(contract.getLabels())
                    .build())
        .orElse(null);
  }

  private PayloadSimple toPayloadSimple(Optional<Payload> payload) {
    return payload
        .map(
            payloadToSimple ->
                PayloadSimple.builder()
                    .id(payloadToSimple.getId())
                    .type(payloadToSimple.getType())
                    .collectorType(payloadToSimple.getCollectorType())
                    .domains(
                        payloadToSimple.getDomains().stream()
                            .map(Domain::getId)
                            .toArray(String[]::new))
                    .build())
        .orElse(null);
  }

  // -- EXPECTATIONS to EXPECTATIONSIMPLE

  /**
   * Converts a list of attackChainNode expectations to simplified DTOs.
   *
   * @param expectations the expectations to convert
   * @return list of simplified expectation DTOs
   */
  public List<AttackChainNodeExpectationSimple> toAttackChainNodeExpectationSimples(
      List<AttackChainNodeExpectation> expectations) {
    return expectations.stream().filter(Objects::nonNull).map(this::toExpectationSimple).toList();
  }

  private AttackChainNodeExpectationSimple toExpectationSimple(AttackChainNodeExpectation expectation) {
    return AttackChainNodeExpectationSimple.builder()
        .id(expectation.getId())
        .name(expectation.getName())
        .build();
  }

  // -- KILLCHAINPHASES to KILLCHAINPHASESSIMPLE

  /**
   * Converts a list of kill chain phases to simplified DTOs.
   *
   * @param killChainPhases the kill chain phases to convert
   * @return list of simplified kill chain phase DTOs
   */
  public List<KillChainPhaseSimple> toKillChainPhasesSimples(List<KillChainPhase> killChainPhases) {
    return killChainPhases.stream()
        .filter(Objects::nonNull)
        .map(this::toKillChainPhasesSimple)
        .toList();
  }

  private KillChainPhaseSimple toKillChainPhasesSimple(KillChainPhase killChainPhase) {
    return KillChainPhaseSimple.builder()
        .id(killChainPhase.getId())
        .name(killChainPhase.getName())
        .build();
  }

  /**
   * Converts an attackChainNode to a simplified representation.
   *
   * @param attackChainNode the attackChainNode to convert
   * @return the simplified attackChainNode DTO
   */
  public AttackChainNodeSimple toAttackChainNodeSimple(AttackChainNode attackChainNode) {
    return AttackChainNodeSimple.builder().id(attackChainNode.getId()).title(attackChainNode.getTitle()).build();
  }

  /**
   * Converts a set of attackChainNodes to related entity outputs.
   *
   * <p>Used for showing attackChainNode references in document or other entity contexts.
   *
   * @param attackChainNodes the attackChainNodes to convert
   * @return set of related entity output DTOs
   */
  public static Set<RelatedEntityOutput> toRelatedEntityOutputs(Set<AttackChainNode> attackChainNodes) {
    return attackChainNodes.stream()
        .map(attackChainNode -> toRelatedEntityOutput(attackChainNode))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutput(AttackChainNode attackChainNode) {
    return RelatedEntityOutput.builder().id(attackChainNode.getId()).name(attackChainNode.getTitle()).build();
  }

  /**
   * Creates an attackChainNode output DTO from individual components.
   *
   * <p>Assembles an attackChainNode output from raw data components, typically from database query results.
   * Calculates readiness based on contract requirements and target assignments.
   *
   * @param id the attackChainNode ID
   * @param title the attackChainNode title
   * @param enabled whether the attackChainNode is enabled
   * @param content the attackChainNode content as JSON
   * @param allTeams whether all teams are targeted
   * @param attackChainRunId the parent attackChainRun ID
   * @param attackChainId the parent attackChain ID
   * @param dependsDuration the duration dependency
   * @param nodeContract the nodeExecutor contract
   * @param tags array of tag IDs
   * @param teams array of team IDs
   * @param assets array of asset IDs
   * @param assetGroups array of asset group IDs
   * @param attackChainNodeType the attackChainNode type identifier
   * @param attackChainEdge the attackChainNode dependency if any
   * @return the assembled attackChainNode output DTO
   */
  public AttackChainNodeOutput toAttackChainNodeOutput(
      String id,
      String title,
      boolean enabled,
      ObjectNode content,
      boolean allTeams,
      String attackChainRunId,
      String attackChainId,
      Long dependsDuration,
      NodeContract nodeContract,
      String[] tags,
      String[] teams,
      String[] assets,
      String[] assetGroups,
      String attackChainNodeType,
      AttackChainEdge attackChainEdge) {
    AttackChainNodeOutput attackChainNodeOutput = new AttackChainNodeOutput();
    attackChainNodeOutput.setId(id);
    attackChainNodeOutput.setTitle(title);
    attackChainNodeOutput.setEnabled(enabled);
    attackChainNodeOutput.setAttackChainRun(attackChainRunId);
    attackChainNodeOutput.setAttackChain(attackChainId);
    attackChainNodeOutput.setDependsDuration(dependsDuration);
    attackChainNodeOutput.setNodeContract(nodeContract);
    attackChainNodeOutput.setTags(tags != null ? new HashSet<>(Arrays.asList(tags)) : new HashSet<>());
    attackChainNodeOutput.setTeams(
        teams != null ? new ArrayList<>(Arrays.asList(teams)) : new ArrayList<>());
    attackChainNodeOutput.setAssets(
        assets != null ? new ArrayList<>(Arrays.asList(assets)) : new ArrayList<>());
    attackChainNodeOutput.setAssetGroups(
        assetGroups != null ? new ArrayList<>(Arrays.asList(assetGroups)) : new ArrayList<>());
    attackChainNodeOutput.setReady(
        NodeModelHelper.isReady(
            nodeContract,
            content,
            allTeams,
            attackChainNodeOutput.getTeams(),
            attackChainNodeOutput.getAssets(),
            attackChainNodeOutput.getAssetGroups()));
    attackChainNodeOutput.setAttackChainNodeType(attackChainNodeType);
    attackChainNodeOutput.setContent(content);
    if (attackChainEdge != null) {
      attackChainNodeOutput.setDependsOn(List.of(attackChainEdge));
    }
    return attackChainNodeOutput;
  }
}
