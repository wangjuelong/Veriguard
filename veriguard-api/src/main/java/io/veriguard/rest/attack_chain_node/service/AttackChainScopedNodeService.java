package io.veriguard.rest.attack_chain_node.service;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeInput;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeUpdateActivationInput;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.service.attack_chain.AttackChainService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class AttackChainScopedNodeService {

  private final AttackChainService attackChainService;
  private final AttackChainNodeService attackChainNodeService;
  private final AttackChainNodeRepository attackChainNodeRepository;

  // -- READ --

  /** Finds an attackChainNode that belongs to the given attackChain. */
  public AttackChainNode findAttackChainNodeForAttackChain(
      @NotBlank final String attackChainId, @NotBlank final String attackChainNodeId) {
    return attackChainNodeRepository
        .findByIdAndAttackChainId(attackChainNodeId, attackChainId)
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    "Inject not found with id: "
                        + attackChainNodeId
                        + " in scenario: "
                        + attackChainId));
  }

  // -- UPDATE --

  /** Updates an attackChainNode that belongs to the given attackChain. */
  public AttackChainNode updateAttackChainNodeForAttackChain(
      @NotBlank final String attackChainId,
      @NotBlank final String attackChainNodeId,
      @NotNull AttackChainNodeInput input) {
    checkAttackChainNodeForAttackChain(attackChainId, attackChainNodeId);
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    AttackChainNode attackChainNode =
        attackChainNodeService.updateAttackChainNode(attackChainNodeId, input);

    // It should not be possible to add EE executor on attackChainNode when the attackChain is
    // already
    // scheduled.
    if (attackChain.getRecurrenceStart() != null) {
      this.attackChainNodeService.throwIfAttackChainNodeNotLaunchable(attackChainNode);
    }

    // If Documents not yet linked directly to the attackChain, attach them
    attackChainNode
        .getDocuments()
        .forEach(
            document -> {
              if (!document.getDocument().getAttackChains().contains(attackChain)) {
                attackChain.getDocuments().add(document.getDocument());
              }
            });
    this.attackChainService.updateAttackChain(attackChain);
    return attackChainNodeRepository.save(attackChainNode);
  }

  /** Toggles the activation of an attackChainNode that belongs to the given attackChain. */
  public AttackChainNode updateAttackChainNodeActivationForAttackChain(
      @NotBlank final String attackChainId,
      @NotBlank final String attackChainNodeId,
      AttackChainNodeUpdateActivationInput input) {
    checkAttackChainNodeForAttackChain(attackChainId, attackChainNodeId);
    return attackChainNodeService.updateAttackChainNodeActivation(attackChainNodeId, input);
  }

  // -- DELETE --

  /** Deletes an attackChainNode that belongs to the given attackChain. */
  public void deleteAttackChainNode(
      @NotBlank final String attackChainId, @NotBlank final String attackChainNodeId) {
    checkAttackChainNodeForAttackChain(attackChainId, attackChainNodeId);
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    this.attackChainNodeService.delete(attackChainNodeId);
    this.attackChainService.updateAttackChain(attackChain);
  }

  // -- PRIVATE --

  /** Checks that an attackChainNode belongs to the given attackChain. Throws if not found. */
  private void checkAttackChainNodeForAttackChain(
      @NotBlank final String attackChainId, @NotBlank final String attackChainNodeId) {
    if (!attackChainNodeRepository.existsByIdAndAttackChainId(attackChainNodeId, attackChainId)) {
      throw new ElementNotFoundException(
          "Inject not found with id: " + attackChainNodeId + " in scenario: " + attackChainId);
    }
  }
}
