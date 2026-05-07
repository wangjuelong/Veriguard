package io.veriguard.rest.attack_chain_node.service;

import static io.veriguard.utils.StringUtils.duplicateString;

import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.database.repository.AttackChainNodeDocumentRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.AttackChainRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.utils.AttackChainNodeUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class AttackChainNodeDuplicateService {

  private final AttackChainRunRepository attackChainRunRepository;
  private final AttackChainRepository attackChainRepository;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final AttackChainNodeDocumentRepository attackChainNodeDocumentRepository;

  @Transactional
  public AttackChainNode duplicateAttackChainNodeForAttackChain(AttackChain attackChain, AttackChainNode attackChainNode) {
    AttackChainNode duplicatedAttackChainNode = getDuplicatedAttackChainNodeWithAttackChain(attackChain, attackChainNode);
    return saveAttackChainNode(attackChainNode, duplicatedAttackChainNode);
  }

  @Transactional
  public AttackChainNode duplicateAttackChainNodeForAttackChainWithDuplicateWordInTitle(
      final String attackChainId, final String attackChainNodeId) {
    AttackChain attackChain =
        attackChainRepository.findById(attackChainId).orElseThrow(ElementNotFoundException::new);
    AttackChainNode attackChainNode = attackChainNodeRepository.findById(attackChainNodeId).orElseThrow(ElementNotFoundException::new);
    AttackChainNode duplicatedAttackChainNode = getDuplicatedAttackChainNodeWithAttackChain(attackChain, attackChainNode);
    duplicatedAttackChainNode.setTitle(duplicateString(duplicatedAttackChainNode.getTitle()));
    return saveAttackChainNode(attackChainNode, duplicatedAttackChainNode);
  }

  @Transactional
  public AttackChainNode duplicateAttackChainNodeForAttackChainRun(AttackChainRun attackChainRun, AttackChainNode attackChainNode) {
    AttackChainNode duplicatedAttackChainNode = getDuplicatedAttackChainNodeWithAttackChainRun(attackChainRun, attackChainNode);
    return saveAttackChainNode(attackChainNode, duplicatedAttackChainNode);
  }

  @Transactional
  public AttackChainNode duplicateAttackChainNodeForAttackChainRunWithDuplicateWordInTitle(
      final String attackChainRunId, final String attackChainNodeId) {
    AttackChainRun attackChainRun =
        attackChainRunRepository.findById(attackChainRunId).orElseThrow(ElementNotFoundException::new);
    AttackChainNode attackChainNode = attackChainNodeRepository.findById(attackChainNodeId).orElseThrow(ElementNotFoundException::new);
    AttackChainNode duplicatedAttackChainNode = getDuplicatedAttackChainNodeWithAttackChainRun(attackChainRun, attackChainNode);
    duplicatedAttackChainNode.setTitle(duplicateString(duplicatedAttackChainNode.getTitle()));
    return saveAttackChainNode(attackChainNode, duplicatedAttackChainNode);
  }

  private AttackChainNode getDuplicatedAttackChainNodeWithAttackChain(AttackChain attackChain, AttackChainNode attackChainNode) {
    AttackChainNode duplicatedAttackChainNode = AttackChainNodeUtils.duplicateAttackChainNode(attackChainNode);
    duplicatedAttackChainNode.setAttackChain(attackChain);
    duplicatedAttackChainNode.setAttackChainRun(null);
    return duplicatedAttackChainNode;
  }

  private AttackChainNode getDuplicatedAttackChainNodeWithAttackChainRun(AttackChainRun attackChainRun, AttackChainNode attackChainNode) {
    AttackChainNode duplicatedAttackChainNode = AttackChainNodeUtils.duplicateAttackChainNode(attackChainNode);
    duplicatedAttackChainNode.setAttackChainRun(attackChainRun);
    duplicatedAttackChainNode.setAttackChain(null);
    return duplicatedAttackChainNode;
  }

  private AttackChainNode saveAttackChainNode(AttackChainNode attackChainNode, AttackChainNode duplicatedAttackChainNode) {
    AttackChainNode savedAttackChainNode = attackChainNodeRepository.save(duplicatedAttackChainNode);
    addAttackChainNodeDocumentsToDuplicatedAttackChainNode(attackChainNode, savedAttackChainNode);
    return savedAttackChainNode;
  }

  private void addAttackChainNodeDocumentsToDuplicatedAttackChainNode(AttackChainNode attackChainNodeOrigin, AttackChainNode duplicatedAttackChainNode) {
    attackChainNodeOrigin
        .getDocuments()
        .forEach(
            attackChainNodeDocument -> {
              String documentId = attackChainNodeDocument.getDocument().getId();
              attackChainNodeDocumentRepository.addAttackChainNodeDoc(
                  duplicatedAttackChainNode.getId(), documentId, attackChainNodeDocument.isAttached());
            });
  }
}
