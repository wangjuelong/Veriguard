package io.veriguard.service;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.AttackChainNodeTestStatusRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.database.specification.AttackChainNodeTestSpecification;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.execution.ExecutionContext;
import io.veriguard.execution.ExecutionContextService;
import io.veriguard.integration.ManagerFactory;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.attack_chain_node.output.AttackChainNodeTestStatusOutput;
import io.veriguard.utils.mapper.AttackChainNodeStatusMapper;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AttackChainNodeTestStatusService {

  private ApplicationContext context;
  private final UserRepository userRepository;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final ExecutionContextService executionContextService;
  private final AttackChainNodeTestStatusRepository attackChainNodeTestStatusRepository;
  private final AttackChainNodeStatusMapper attackChainNodeStatusMapper;
  private final ManagerFactory managerFactory;

  @Autowired
  public void setContext(ApplicationContext context) {
    this.context = context;
  }

  public AttackChainNodeTestStatusOutput testAttackChainNode(String attackChainNodeId) {
    AttackChainNode attackChainNode =
        this.attackChainNodeRepository
            .findById(attackChainNodeId)
            .orElseThrow(() -> new EntityNotFoundException("Inject not found"));

    if (!attackChainNode.getAttackChainNodeTestable()) {
      throw new IllegalArgumentException("Inject: " + attackChainNodeId + " is not testable");
    }

    User user =
        this.userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    AttackChainNodeTestStatus attackChainNodeStatus = testAttackChainNode(attackChainNode, user);
    return attackChainNodeStatusMapper.toAttackChainNodeTestStatusOutput(attackChainNodeStatus);
  }

  /**
   * Bulk tests of attackChainNodes
   *
   * @param searchSpecifications the criteria to search attackChainNodes to test
   * @return the list of attackChainNode test status
   */
  public List<AttackChainNodeTestStatusOutput> bulkTestAttackChainNodes(
      final Specification<AttackChainNode> searchSpecifications) {
    List<AttackChainNode> searchResult = this.attackChainNodeRepository.findAll(searchSpecifications);
    if (searchResult.isEmpty()) {
      throw new BadRequestException("No inject ID is testable");
    }
    User user =
        this.userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    List<AttackChainNodeTestStatus> results = new ArrayList<>();
    searchResult.forEach(attackChainNode -> results.add(testAttackChainNode(attackChainNode, user)));
    return results.stream().map(attackChainNodeStatusMapper::toAttackChainNodeTestStatusOutput).toList();
  }

  public Page<AttackChainNodeTestStatusOutput> findAllAttackChainNodeTestsByAttackChainRunId(
      String attackChainRunId, SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
            (Specification<AttackChainNodeTestStatus> specification, Pageable pageable) ->
                attackChainNodeTestStatusRepository.findAll(
                    AttackChainNodeTestSpecification.findAttackChainNodeTestInAttackChainRun(attackChainRunId).and(specification),
                    pageable),
            searchPaginationInput,
            AttackChainNodeTestStatus.class)
        .map(attackChainNodeStatusMapper::toAttackChainNodeTestStatusOutput);
  }

  public Page<AttackChainNodeTestStatusOutput> findAllAttackChainNodeTestsByAttackChainId(
      String attackChainId, SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
            (Specification<AttackChainNodeTestStatus> specification, Pageable pageable) ->
                attackChainNodeTestStatusRepository.findAll(
                    AttackChainNodeTestSpecification.findAttackChainNodeTestInAttackChain(attackChainId).and(specification),
                    pageable),
            searchPaginationInput,
            AttackChainNodeTestStatus.class)
        .map(attackChainNodeStatusMapper::toAttackChainNodeTestStatusOutput);
  }

  public AttackChainNodeTestStatusOutput findAttackChainNodeTestStatusById(String testId) {
    return attackChainNodeStatusMapper.toAttackChainNodeTestStatusOutput(
        attackChainNodeTestStatusRepository.findById(testId).orElseThrow());
  }

  // -- PRIVATE --
  private AttackChainNodeTestStatus testAttackChainNode(AttackChainNode attackChainNode, User user) {
    ExecutionContext userAttackChainNodeContext =
        this.executionContextService.executionContext(user, attackChainNode, "Direct test");

    String nodeExecutorType =
        attackChainNode
            .getNodeContract()
            .map(contract -> contract.getNodeExecutor().getType())
            .orElseThrow(() -> new EntityNotFoundException("Injector contract not found"));

    io.veriguard.executors.NodeExecutor executor =
        managerFactory.getManager().requestNodeExecutorExecutorByType(nodeExecutorType);

    ExecutableNode injection =
        new ExecutableNode(
            false,
            true,
            attackChainNode,
            List.of(),
            attackChainNode.getAssets(),
            attackChainNode.getAssetGroups(),
            List.of(userAttackChainNodeContext));
    Execution execution = executor.executeInjection(injection);

    AttackChainNodeTestStatus attackChainNodeTestStatus =
        this.attackChainNodeTestStatusRepository
            .findByAttackChainNode(attackChainNode)
            .map(
                existingStatus -> {
                  AttackChainNodeTestStatus updatedStatus = AttackChainNodeTestStatus.fromExecutionTest(execution);
                  updatedStatus.setId(existingStatus.getId());
                  updatedStatus.setTestCreationDate(existingStatus.getTestCreationDate());
                  updatedStatus.setAttackChainNode(attackChainNode);
                  return updatedStatus;
                })
            .orElseGet(
                () -> {
                  AttackChainNodeTestStatus newStatus = AttackChainNodeTestStatus.fromExecutionTest(execution);
                  newStatus.setAttackChainNode(attackChainNode);
                  return newStatus;
                });

    return this.attackChainNodeTestStatusRepository.save(attackChainNodeTestStatus);
  }

  public void deleteAttackChainNodeTest(String testId) {
    attackChainNodeTestStatusRepository.deleteById(testId);
  }
}
