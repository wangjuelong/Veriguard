package io.veriguard.utils.mapper;

import static io.veriguard.utils.mapper.AttackChainMapper.toRelatedEntityOutputsForChain;
import static io.veriguard.utils.mapper.AttackChainNodeMapper.toRelatedEntityOutputs;
import static io.veriguard.utils.mapper.AttackChainRunMapper.toRelatedEntityOutputs;
import static io.veriguard.utils.mapper.AttackChainRunMapper.toSimulationAttackChainNodes;
import static io.veriguard.utils.mapper.PayloadMapper.toRelatedEntityOutputs;
import static io.veriguard.utils.mapper.SecurityPlatformMapper.toRelatedEntityOutputs;

import io.veriguard.database.model.*;
import io.veriguard.rest.document.form.DocumentRelationsOutput;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class DocumentMapper {

  public static DocumentRelationsOutput toDocumentRelationsOutput(Document document) {
    Set<AttackChainNode> attackChainNodes =
        document.getAttackChainNodeDocuments().stream()
            .map(AttackChainNodeDocument::getAttackChainNode)
            .collect(Collectors.toSet());

    Set<AttackChainNode> atomics =
        attackChainNodes.stream()
            .filter(
                attackChainNode ->
                    attackChainNode.getAttackChain() == null
                        && attackChainNode.getAttackChainRun() == null)
            .collect(Collectors.toSet());

    Set<AttackChainNode> chainNodes =
        attackChainNodes.stream()
            .filter(attackChainNode -> attackChainNode.getAttackChain() != null)
            .collect(Collectors.toSet());

    Set<AttackChainNode> simulationAttackChainNodes =
        attackChainNodes.stream()
            .filter(attackChainNode -> attackChainNode.getAttackChainRun() != null)
            .collect(Collectors.toSet());

    Set<AttackChainRun> simulations =
        Stream.concat(
                document.getSimulationsByLogoDark().stream(),
                document.getSimulationsByLogoLight().stream())
            .collect(Collectors.toSet());

    Set<SecurityPlatform> securityPlatforms =
        Stream.concat(
                document.getSecurityPlatformsByLogoDark().stream(),
                document.getSecurityPlatformsByLogoLight().stream())
            .collect(Collectors.toSet());

    Set<Payload> payloads =
        Stream.concat(
                document.getPayloadsByFileDrop().stream(),
                document.getPayloadsByExecutableFile().stream())
            .collect(Collectors.toSet());

    return DocumentRelationsOutput.builder()
        .simulations(toRelatedEntityOutputs(simulations))
        .securityPlatforms(toRelatedEntityOutputs(securityPlatforms))
        .payloads(toRelatedEntityOutputs(payloads))
        .atomicTestings(toRelatedEntityOutputs(atomics))
        .chainNodes(toRelatedEntityOutputsForChain(chainNodes))
        .simulationAttackChainNodes(toSimulationAttackChainNodes(simulationAttackChainNodes))
        .build();
  }
}
