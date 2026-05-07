package io.veriguard.rest.inject.service;

import static io.veriguard.database.specification.CommunicationSpecification.fromAttackChainNode;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static java.time.Instant.now;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.CommunicationRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.TeamRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exercise.service.AttackChainRunService;
import io.veriguard.rest.inject.form.AttackChainNodeTeamsInput;
import io.veriguard.rest.inject.form.AttackChainNodeUpdateActivationInput;
import io.veriguard.rest.inject.form.AttackChainNodeUpdateStatusInput;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class SimulationAttackChainNodeService {

  private final AttackChainRunService attackChainRunService;
  private final AttackChainNodeService attackChainNodeService;
  private final AttackChainNodeStatusService attackChainNodeStatusService;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final CommunicationRepository communicationRepository;
  private final TeamRepository teamRepository;

  // -- READ --

  /** Finds an attackChainNode that belongs to the given simulation. */
  public AttackChainNode findAttackChainNodeForSimulation(
      @NotBlank final String simulationId, @NotBlank final String attackChainNodeId) {
    return attackChainNodeRepository
        .findByIdAndAttackChainRunId(attackChainNodeId, simulationId)
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    "Inject not found with id: " + attackChainNodeId + " in simulation: " + simulationId));
  }

  /** Returns the teams assigned to an attackChainNode that belongs to the given simulation. */
  @Transactional(readOnly = true)
  public List<Team> findAttackChainNodeTeamsForSimulation(
      @NotBlank final String simulationId, @NotBlank final String attackChainNodeId) {
    return findAttackChainNodeForSimulation(simulationId, attackChainNodeId).getTeams();
  }

  /**
   * Returns and acknowledges all communications for an attackChainNode that belongs to the given simulation.
   */
  public Iterable<Communication> findAndAckCommunicationsForSimulation(
      @NotBlank final String simulationId, @NotBlank final String attackChainNodeId) {
    checkAttackChainNodeForSimulation(simulationId, attackChainNodeId);
    List<Communication> coms =
        communicationRepository.findAll(
            fromAttackChainNode(attackChainNodeId), Sort.by(Sort.Direction.DESC, "receivedAt"));
    coms.forEach(com -> com.setAck(true));
    return communicationRepository.saveAll(coms);
  }

  // -- UPDATE --

  /** Toggles the activation of an attackChainNode that belongs to the given simulation. */
  public AttackChainNode updateAttackChainNodeActivationForSimulation(
      @NotBlank final String simulationId,
      @NotBlank final String attackChainNodeId,
      AttackChainNodeUpdateActivationInput input) {
    checkAttackChainNodeForSimulation(simulationId, attackChainNodeId);
    return attackChainNodeService.updateAttackChainNodeActivation(attackChainNodeId, input);
  }

  /** Triggers immediate execution of an attackChainNode that belongs to the given simulation. */
  public AttackChainNode triggerAttackChainNodeForSimulation(
      @NotBlank final String simulationId, @NotBlank final String attackChainNodeId) {
    AttackChainNode attackChainNode = findAttackChainNodeForSimulation(simulationId, attackChainNodeId);
    attackChainNode.setTriggerNowDate(now());
    attackChainNode.setUpdatedAt(now());
    return attackChainNodeRepository.save(attackChainNode);
  }

  /** Sets the status of an attackChainNode that belongs to the given simulation. */
  public AttackChainNode setAttackChainNodeStatusForSimulation(
      @NotBlank final String simulationId,
      @NotBlank final String attackChainNodeId,
      AttackChainNodeUpdateStatusInput input) {
    checkAttackChainNodeForSimulation(simulationId, attackChainNodeId);
    return attackChainNodeStatusService.updateAttackChainNodeStatus(attackChainNodeId, input);
  }

  /** Replaces the teams assigned to an attackChainNode that belongs to the given simulation. */
  public AttackChainNode updateAttackChainNodeTeamsForSimulation(
      @NotBlank final String simulationId,
      @NotBlank final String attackChainNodeId,
      AttackChainNodeTeamsInput input) {
    AttackChainNode attackChainNode = findAttackChainNodeForSimulation(simulationId, attackChainNodeId);
    Iterable<Team> attackChainNodeTeams = teamRepository.findAllById(input.getTeamIds());
    attackChainNode.setTeams(fromIterable(attackChainNodeTeams));
    return attackChainNodeRepository.save(attackChainNode);
  }

  // -- DELETE --

  /** Deletes an attackChainNode that belongs to the given simulation. */
  public void deleteAttackChainNode(@NotBlank final String simulationId, @NotBlank final String attackChainNodeId) {
    checkAttackChainNodeForSimulation(simulationId, attackChainNodeId);
    AttackChainRun attackChainRun = this.attackChainRunService.attackChainRun(simulationId);
    attackChainNodeService.delete(attackChainNodeId);
    this.attackChainRunService.updateAttackChainRun(attackChainRun);
  }

  // -- PRIVATE --

  /** Checks that an attackChainNode belongs to the given simulation. Throws if not found. */
  private void checkAttackChainNodeForSimulation(
      @NotBlank final String simulationId, @NotBlank final String attackChainNodeId) {
    if (!attackChainNodeRepository.existsByIdAndAttackChainRunId(attackChainNodeId, simulationId)) {
      throw new ElementNotFoundException(
          "Inject not found with id: " + attackChainNodeId + " in simulation: " + simulationId);
    }
  }
}
