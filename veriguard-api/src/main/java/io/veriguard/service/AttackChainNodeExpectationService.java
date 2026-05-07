package io.veriguard.service;

import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE.*;
import static io.veriguard.database.model.NodeExpectationSignature.EXPECTATION_SIGNATURE_TYPE_END_DATE;
import static io.veriguard.database.model.NodeExpectationSignature.EXPECTATION_SIGNATURE_TYPE_START_DATE;
import static io.veriguard.expectation.ExpectationType.VULNERABILITY;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.service.AttackChainNodeExpectationUtils.computeScores;
import static io.veriguard.service.AttackChainNodeExpectationUtils.expectationConverter;
import static io.veriguard.utils.AgentUtils.getPrimaryAgents;
import static io.veriguard.utils.ExpectationUtils.*;
import static io.veriguard.utils.inject_expectation_result.ExpectationResultBuilder.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.database.specification.AttackChainNodeExpectationSpecification;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.expectation.ExpectationPropertiesConfig;
import io.veriguard.expectation.ExpectationType;
import io.veriguard.model.Expectation;
import io.veriguard.rest.atomic_testing.form.AttackChainNodeExpectationAgentOutput;
import io.veriguard.rest.collector.service.CollectorService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.attack_chain_run.form.ExpectationUpdateInput;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExpectationUpdateInput;
import io.veriguard.rest.attack_chain_node.service.ExecutionProcessingContext;
import io.veriguard.utils.ExpectationUtils;
import io.veriguard.utils.TargetType;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class AttackChainNodeExpectationService {

  public static final String SUCCESS = "Success";
  public static final String PENDING = "Pending";
  public static final String COLLECTOR = "collector";
  private final AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  private final CollectorService collectorService;
  @Resource private ExpectationPropertiesConfig expectationPropertiesConfig;
  private final SecurityCoverageSendJobService securityCoverageSendJobService;

  @Resource protected ObjectMapper mapper;

  // -- CRUD --

  public AttackChainNodeExpectation findAttackChainNodeExpectation(@NotBlank final String attackChainNodeExpectationId) {
    return this.attackChainNodeExpectationRepository
        .findById(attackChainNodeExpectationId)
        .orElseThrow(ElementNotFoundException::new);
  }

  // -- UPDATE FROM UI --

  public AttackChainNodeExpectation updateAttackChainNodeExpectation(
      @NotBlank final String expectationId, @NotNull final ExpectationUpdateInput input) {
    AttackChainNodeExpectation attackChainNodeExpectation = this.findAttackChainNodeExpectation(expectationId);

    if (HUMAN_EXPECTATION.contains(attackChainNodeExpectation.getType())) {
      String result =
          ExpectationType.label(
              attackChainNodeExpectation.getType(), attackChainNodeExpectation.getExpectedScore(), input.getScore());
      computeAttackChainNodeExpectationForHumanResponse(attackChainNodeExpectation, input, result);
      AttackChainNodeExpectation updated = this.attackChainNodeExpectationRepository.save(attackChainNodeExpectation);
      propagateHumanResponseExpectation(updated, result);
      return updated;
    } else if (List.of(DETECTION, PREVENTION).contains(attackChainNodeExpectation.getType())) {
      // Block down computation on asset group
      if (isAssetGroupExpectation(attackChainNodeExpectation)) {
        throw new IllegalArgumentException("Not possible to update Asset Group directly");
      }
      // Allow down computation on asset
      Endpoint endpoint = (Endpoint) Hibernate.unproxy(attackChainNodeExpectation.getAsset());
      List<Agent> agents = getPrimaryAgents(endpoint);
      boolean isAgentless = agents.isEmpty();
      if (isAssetExpectation(attackChainNodeExpectation) && !isAgentless) {
        List<AttackChainNodeExpectation> expectationsForAgents =
            getExpectationsAgentsForAsset(attackChainNodeExpectation);
        expectationsForAgents.forEach(
            e -> computeAttackChainNodeExpectationForAgentOrAssetAgentless(e, input));
        this.attackChainNodeExpectationRepository.saveAll(expectationsForAgents);
        propagateTechnicalExpectation(attackChainNodeExpectation, isAgentless, null);
        return attackChainNodeExpectation;
        // Computation on agent or asset agentless
      } else {
        computeAttackChainNodeExpectationForAgentOrAssetAgentless(attackChainNodeExpectation, input);
        AttackChainNodeExpectation updated = this.attackChainNodeExpectationRepository.save(attackChainNodeExpectation);
        propagateTechnicalExpectation(updated, isAgentless, null);
        return updated;
      }
    }
    return attackChainNodeExpectation;
  }

  // -- DELETE RESULT FROM UI --

  public AttackChainNodeExpectation deleteNodeExpectationResult(
      @NotBlank final String expectationId, @NotBlank final String sourceId) {
    AttackChainNodeExpectation attackChainNodeExpectation =
        this.attackChainNodeExpectationRepository.findById(expectationId).orElseThrow();
    deleteResult(attackChainNodeExpectation, sourceId);
    AttackChainNodeExpectation updated = this.attackChainNodeExpectationRepository.save(attackChainNodeExpectation);
    if (HUMAN_EXPECTATION.contains(attackChainNodeExpectation.getType())) {
      propagateHumanResponseExpectation(updated, null);
    } else if (List.of(DETECTION, PREVENTION).contains(attackChainNodeExpectation.getType())) {
      // Block down computation
      // Not asset group
      if (isAssetGroupExpectation(attackChainNodeExpectation)) {
        throw new IllegalArgumentException("Not possible to update Asset Group directly");
      }
      // Not Endpoint if no agentless
      Endpoint endpoint = (Endpoint) Hibernate.unproxy(attackChainNodeExpectation.getAsset());
      List<Agent> agents = getPrimaryAgents(endpoint);
      boolean isAgentless = agents.isEmpty();
      if (isAssetExpectation(attackChainNodeExpectation) && !isAgentless) {
        throw new IllegalArgumentException(
            "Not possible to update Asset directly on Asset with Agent");
      }
      propagateTechnicalExpectation(updated, isAgentless, null);
    }

    return updated;
  }

  //  -- HUMAN RESPONSE --

  private void computeAttackChainNodeExpectationForHumanResponse(
      @NotNull AttackChainNodeExpectation attackChainNodeExpectation,
      @NotNull final ExpectationUpdateInput input,
      @NotBlank final String result) {
    // Keep only one result
    attackChainNodeExpectation.getResults().clear();
    addResult(attackChainNodeExpectation, input, result);
    final Double score = computeScore(attackChainNodeExpectation.getResults(), attackChainNodeExpectation);
    attackChainNodeExpectation.setScore(score);
  }

  public AttackChainNodeExpectation computeAttackChainNodeExpectationForHumanResponse(
      @NotNull AttackChainNodeExpectation attackChainNodeExpectation,
      @NotNull final AttackChainNodeExpectationUpdateInput input,
      @NotNull final Collector collector) {
    // Keep only one result
    attackChainNodeExpectation.getResults().clear();
    addResult(attackChainNodeExpectation, input, collector);
    final Double score = computeScore(attackChainNodeExpectation.getResults(), attackChainNodeExpectation);
    attackChainNodeExpectation.setScore(score);
    return attackChainNodeExpectation;
  }

  private void propagateHumanResponseExpectation(
      @NotNull AttackChainNodeExpectation attackChainNodeExpectation, @Nullable final String result) {
    // If the updated expectation was a player expectation, We have to update the team expectation
    // using player expectations (based on validation type)
    List<AttackChainNodeExpectation> expectations = new ArrayList<>();
    if (attackChainNodeExpectation.getUser() != null) {
      expectations.addAll(propagateToTeam(attackChainNodeExpectation, result));
    } else {
      expectations.addAll(propagateToPlayers(attackChainNodeExpectation, result));
    }
    this.attackChainNodeExpectationRepository.saveAll(expectations);

    // Security coverage job creation
    List<AttackChainRun> attackChainRuns = new ArrayList<>();
    attackChainRuns.add(attackChainNodeExpectation.getAttackChainNode().getAttackChainRun());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(attackChainRuns);
  }

  private List<AttackChainNodeExpectation> propagateToPlayers(
      @NotNull final AttackChainNodeExpectation attackChainNodeExpectation, @Nullable final String result) {
    // If I update the expectation team: What happens with children? -> update expectation score
    // for all children -> set score from AttackChainNodeExpectation
    List<AttackChainNodeExpectation> expectationsForPlayers =
        getExpectationsPlayersForTeam(attackChainNodeExpectation);
    for (AttackChainNodeExpectation expectationsForPlayer : expectationsForPlayers) {
      expectationsForPlayer.getResults().clear();
      if (result != null) {
        expectationsForPlayer
            .getResults()
            .add(buildForTeamManualValidation(result, attackChainNodeExpectation.getScore()));
      }
      expectationsForPlayer.setScore(attackChainNodeExpectation.getScore());
    }
    return expectationsForPlayers;
  }

  private List<AttackChainNodeExpectation> propagateToTeam(
      @NotNull final AttackChainNodeExpectation attackChainNodeExpectation, @Nullable final String result) {
    List<AttackChainNodeExpectation> expectationsForPlayers =
        getExpectationsPlayersForTeam(attackChainNodeExpectation);
    List<AttackChainNodeExpectation> expectationForTeams = getExpectationTeams(attackChainNodeExpectation);
    computeScores(
        expectationsForPlayers,
        expectationForTeams,
        attackChainNodeExpectation,
        score -> buildForPlayerManualValidation(result, score));
    return expectationForTeams;
  }

  // -- TECHNICAL --

  private void computeAttackChainNodeExpectationForAgentOrAssetAgentless(
      @NotNull final AttackChainNodeExpectation attackChainNodeExpectation,
      @NotNull final ExpectationUpdateInput input) {
    String result =
        ExpectationType.label(
            attackChainNodeExpectation.getType(), attackChainNodeExpectation.getExpectedScore(), input.getScore());
    addResult(attackChainNodeExpectation, input, result);
    final Double score = computeScore(attackChainNodeExpectation.getResults(), attackChainNodeExpectation);
    attackChainNodeExpectation.setScore(score);
  }

  private void propagateTechnicalExpectation(
      @NotNull final AttackChainNodeExpectation attackChainNodeExpectation,
      final boolean isAgentless,
      @Nullable final Function<Double, NodeExpectationResult> addResult) {
    List<AttackChainNodeExpectation> expectations = new ArrayList<>();
    // 1) Agent -> Asset
    if (!isAgentless) {
      expectations.addAll(propagateToAsset(attackChainNodeExpectation, addResult));
    }

    // 2) Asset -> Asset Group
    expectations.addAll(propagateToAssetGroup(attackChainNodeExpectation, addResult));

    this.attackChainNodeExpectationRepository.saveAll(expectations);

    // Security coverage job creation
    List<AttackChainRun> attackChainRuns = new ArrayList<>();
    attackChainRuns.add(attackChainNodeExpectation.getAttackChainNode().getAttackChainRun());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(attackChainRuns);
  }

  private List<AttackChainNodeExpectation> propagateToAsset(
      @NotNull final AttackChainNodeExpectation attackChainNodeExpectation,
      @Nullable final Function<Double, NodeExpectationResult> addResult) {
    List<AttackChainNodeExpectation> expectationsForAgents =
        getExpectationsAgentsForAsset(attackChainNodeExpectation);
    List<AttackChainNodeExpectation> expectationsForAssets = getExpectationsAssets(attackChainNodeExpectation);
    computeScores(expectationsForAgents, expectationsForAssets, attackChainNodeExpectation, addResult);
    return expectationsForAssets;
  }

  private List<AttackChainNodeExpectation> propagateToAssetGroup(
      @NotNull final AttackChainNodeExpectation attackChainNodeExpectation,
      @Nullable final Function<Double, NodeExpectationResult> addResult) {
    if (attackChainNodeExpectation.getAssetGroup() != null) {
      List<AttackChainNodeExpectation> expectationsForAssets =
          getExpectationsAssetsForAssetGroup(attackChainNodeExpectation);
      List<AttackChainNodeExpectation> expectationForAssetGroups =
          getExpectationAssetGroups(attackChainNodeExpectation);
      computeScores(expectationsForAssets, expectationForAssetGroups, attackChainNodeExpectation, addResult);
      return expectationForAssetGroups;
    }
    return new ArrayList<>();
  }

  // -- UPDATE FROM EXTERNAL SOURCE : COLLECTORS --

  public AttackChainNodeExpectation updateAttackChainNodeExpectation(
      @NotBlank String expectationId, @Valid @NotNull AttackChainNodeExpectationUpdateInput input) {
    AttackChainNodeExpectation attackChainNodeExpectation = this.findAttackChainNodeExpectation(expectationId);
    Collector collector = this.collectorService.collector(input.getCollectorId());

    computeTechnicalExpectation(attackChainNodeExpectation, collector, input, false);

    return attackChainNodeExpectation;
  }

  public void bulkUpdateAttackChainNodeExpectation(
      @Valid @NotNull Map<String, AttackChainNodeExpectationUpdateInput> inputs) {
    if (inputs.isEmpty()) {
      return;
    }

    List<AttackChainNodeExpectation> attackChainNodeExpectations =
        fromIterable(this.attackChainNodeExpectationRepository.findAllById(inputs.keySet()));
    Map<String, AttackChainNodeExpectation> expectationsToUpdate =
        attackChainNodeExpectations.stream().collect(Collectors.toMap(AttackChainNodeExpectation::getId, e -> e));

    Collector collector =
        this.collectorService.collector(
            inputs.values().stream()
                .findFirst()
                .orElseThrow(ElementNotFoundException::new)
                .getCollectorId());

    // Update attackChainNode expectation at agent level
    for (Map.Entry<String, AttackChainNodeExpectationUpdateInput> entry : inputs.entrySet()) {
      String attackChainNodeExpectationId = entry.getKey();
      AttackChainNodeExpectationUpdateInput input = entry.getValue();

      AttackChainNodeExpectation attackChainNodeExpectation = expectationsToUpdate.get(attackChainNodeExpectationId);
      if (attackChainNodeExpectation == null) {
        log.error("Inject expectation not found for ID: {}", attackChainNodeExpectationId);
        continue;
      }
      computeTechnicalExpectation(attackChainNodeExpectation, collector, input, false);
    }
  }

  public void computeTechnicalExpectation(
      AttackChainNodeExpectation attackChainNodeExpectation,
      Collector collector,
      AttackChainNodeExpectationUpdateInput input,
      boolean shouldPropagateLastNodeExpectationResult) {
    // Update attackChainNode expectation at agent level
    attackChainNodeExpectation =
        this.computeAttackChainNodeExpectationForAgentOrAssetAgentless(attackChainNodeExpectation, input, collector);
    AttackChainNodeExpectation updated = this.attackChainNodeExpectationRepository.save(attackChainNodeExpectation);
    propagateTechnicalExpectation(
        updated,
        false,
        shouldPropagateLastNodeExpectationResult
            ? score -> updated.getResults().getLast()
            : null);
  }

  // -- COMPUTE RESULTS FROM INJECT EXPECTATIONS --

  public AttackChainNodeExpectation computeAttackChainNodeExpectationForAgentOrAssetAgentless(
      @NotNull final AttackChainNodeExpectation expectation,
      @NotNull final AttackChainNodeExpectationUpdateInput input,
      @NotNull final Collector collector) {
    addResult(expectation, input, collector);
    final Double score = computeScore(expectation.getResults(), expectation);
    expectation.setScore(score);
    return expectation;
  }

  // -- FINAL UPDATE --

  public void updateAll(@NotNull List<AttackChainNodeExpectation> attackChainNodeExpectations) {
    this.attackChainNodeExpectationRepository.saveAll(attackChainNodeExpectations);
  }

  // -- FETCH INJECT EXPECTATIONS --

  public Page<AttackChainNodeExpectation> expectationsNotFill() {
    return this.attackChainNodeExpectationRepository.findAll(
        (root, query, criteriaBuilder) ->
            criteriaBuilder.and(
                criteriaBuilder.isNull(root.get("score")),
                criteriaBuilder.or(
                    criteriaBuilder.equal(
                        criteriaBuilder.function(
                            "json_array_length", Integer.class, root.get("results")),
                        0),
                    criteriaBuilder.isNotNull(root.get("agent")))),
        PageRequest.of(0, 10000, Sort.by(Sort.Direction.ASC, "createdAt")));
  }

  // -- EXPECTATIONS BY TYPE --

  public List<AttackChainNodeExpectation> expectationsNotFilledAndNotExpiredBySourceId(
      @NotNull AttackChainNodeExpectation.EXPECTATION_TYPE type,
      @NotNull Integer expirationTime,
      @NotBlank String sourceId) {

    Instant expirationThreshold = Instant.now().minus(expirationTime, ChronoUnit.MINUTES);

    return attackChainNodeExpectationRepository
        .findAll(
            Specification.where(
                AttackChainNodeExpectationSpecification.type(type)
                    .and(AttackChainNodeExpectationSpecification.agentNotNull())
                    .and(AttackChainNodeExpectationSpecification.assetNotNull())
                    .and(AttackChainNodeExpectationSpecification.from(expirationThreshold))))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  public List<AttackChainNodeExpectation> expectationsNotFilledAndNotExpired(
      @NotNull AttackChainNodeExpectation.EXPECTATION_TYPE type, @NotNull Integer expirationTime) {

    Instant expirationThreshold = Instant.now().minus(expirationTime, ChronoUnit.MINUTES);

    return attackChainNodeExpectationRepository
        .findAll(
            Specification.where(
                AttackChainNodeExpectationSpecification.type(type)
                    .and(AttackChainNodeExpectationSpecification.agentNotNull())
                    .and(AttackChainNodeExpectationSpecification.assetNotNull())
                    .and(AttackChainNodeExpectationSpecification.from(expirationThreshold))))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  // -- PREVENTION --

  public List<AttackChainNodeExpectation> preventionExpectationsNotExpired(final Integer expirationTime) {
    return this.attackChainNodeExpectationRepository.findAll(
        Specification.where(
            AttackChainNodeExpectationSpecification.type(PREVENTION)
                .and(AttackChainNodeExpectationSpecification.agentNotNull())
                .and(AttackChainNodeExpectationSpecification.assetNotNull())
                .and(
                    AttackChainNodeExpectationSpecification.from(
                        Instant.now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  public List<AttackChainNodeExpectation> preventionExpectationsNotFill(@NotBlank final String sourceId) {
    return this.attackChainNodeExpectationRepository
        .findAll(Specification.where(AttackChainNodeExpectationSpecification.type(PREVENTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  public List<AttackChainNodeExpectation> preventionExpectationsNotFill() {
    return this.attackChainNodeExpectationRepository
        .findAll(Specification.where(AttackChainNodeExpectationSpecification.type(PREVENTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  public List<AttackChainNodeExpectation> preventionExpectationsNotFillAndNotExpired(
      @NotNull Integer expirationTime) {
    return expectationsNotFilledAndNotExpired(PREVENTION, expirationTime);
  }

  public List<AttackChainNodeExpectation> preventionExpectationsNotFilledAndNotExpired(
      @NotNull Integer expirationTime, @NotBlank String sourceId) {
    return expectationsNotFilledAndNotExpiredBySourceId(PREVENTION, expirationTime, sourceId);
  }

  // -- DETECTION --

  public List<AttackChainNodeExpectation> detectionExpectationsNotExpired(final Integer expirationTime) {
    return this.attackChainNodeExpectationRepository.findAll(
        Specification.where(
            AttackChainNodeExpectationSpecification.type(DETECTION)
                .and(AttackChainNodeExpectationSpecification.agentNotNull())
                .and(AttackChainNodeExpectationSpecification.assetNotNull())
                .and(
                    AttackChainNodeExpectationSpecification.from(
                        Instant.now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  public List<AttackChainNodeExpectation> detectionExpectationsNotFill(@NotBlank final String sourceId) {
    return this.attackChainNodeExpectationRepository
        .findAll(Specification.where(AttackChainNodeExpectationSpecification.type(DETECTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  public List<AttackChainNodeExpectation> detectionExpectationsNotFill() {
    return this.attackChainNodeExpectationRepository
        .findAll(Specification.where(AttackChainNodeExpectationSpecification.type(DETECTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  public List<AttackChainNodeExpectation> detectionExpectationsNotFillAndNotExpired(
      @NotNull Integer expirationTime) {
    return expectationsNotFilledAndNotExpired(DETECTION, expirationTime);
  }

  public List<AttackChainNodeExpectation> detectionExpectationsNotFilledAndNotExpired(
      @NotNull Integer expirationTime, @NotBlank String sourceId) {

    return expectationsNotFilledAndNotExpiredBySourceId(DETECTION, expirationTime, sourceId);
  }

  // -- MANUAL

  public List<AttackChainNodeExpectation> manualExpectationsNotExpired(final Integer expirationTime) {
    return this.attackChainNodeExpectationRepository.findAll(
        Specification.where(
            AttackChainNodeExpectationSpecification.type(MANUAL)
                .and(AttackChainNodeExpectationSpecification.agentNotNull())
                .and(AttackChainNodeExpectationSpecification.assetNotNull())
                .and(
                    AttackChainNodeExpectationSpecification.from(
                        Instant.now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  public List<AttackChainNodeExpectation> manualExpectationsNotFill(@NotBlank final String sourceId) {
    return this.attackChainNodeExpectationRepository
        .findAll(Specification.where(AttackChainNodeExpectationSpecification.type(MANUAL)))
        .stream()
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  public List<AttackChainNodeExpectation> manualExpectationsNotFill() {
    return this.attackChainNodeExpectationRepository
        .findAll(Specification.where(AttackChainNodeExpectationSpecification.type(MANUAL)))
        .stream()
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  public List<AttackChainNodeExpectation> manualExpectationsNotFillAndNotExpired(
      @NotNull Integer expirationTime) {
    return expectationsNotFilledAndNotExpired(MANUAL, expirationTime);
  }

  // -- BY TARGET TYPE

  public List<AttackChainNodeExpectation> findMergedExpectationsByAttackChainNodeAndTargetAndTargetType(
      @NotBlank final String attackChainNodeId,
      @NotBlank final String targetId,
      @NotBlank final String targetType) {
    try {
      TargetType targetTypeEnum = TargetType.valueOf(targetType);
      return mergeExpectationResultsByExpectationType(
          switch (targetTypeEnum) {
            case TEAMS, ASSETS_GROUPS ->
                this.findMergedExpectationsByAttackChainNodeAndTargetAndTargetType(
                    attackChainNodeId, targetId, "not applicable", targetType);
            case PLAYERS ->
                attackChainNodeExpectationRepository.findAllByAttackChainNodeAndPlayer(attackChainNodeId, targetId);
            case AGENT -> attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(attackChainNodeId, targetId);
            case ASSETS -> attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAsset(attackChainNodeId, targetId);
            default ->
                throw new RuntimeException(
                    "Target type "
                        + targetType
                        + " not implemented for this method findMergedExpectationsByInjectAndTargetAndTargetType");
          });
    } catch (IllegalArgumentException e) {
      return Collections.emptyList();
    }
  }

  public List<AttackChainNodeExpectation> findMergedExpectationsByAttackChainNodeAndTargetAndTargetType(
      @NotBlank final String attackChainNodeId,
      @NotBlank final String targetId,
      @NotBlank final String parentTargetId,
      @NotBlank final String targetType) {
    try {
      TargetType targetTypeEnum = TargetType.valueOf(targetType);
      return switch (targetTypeEnum) {
        case TEAMS -> attackChainNodeExpectationRepository.findAllByAttackChainNodeAndTeam(attackChainNodeId, targetId);
        case PLAYERS ->
            attackChainNodeExpectationRepository.findAllByAttackChainNodeAndTeamAndPlayer(
                attackChainNodeId, parentTargetId, targetId);
        case AGENT -> attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(attackChainNodeId, targetId);
        case ASSETS -> attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAsset(attackChainNodeId, targetId);
        case ASSETS_GROUPS ->
            attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAssetGroup(attackChainNodeId, targetId);
        default ->
            throw new RuntimeException(
                "Target type "
                    + targetType
                    + " not implemented for this method findMergedExpectationsByInjectAndTargetAndTargetType");
      };
    } catch (IllegalArgumentException e) {
      return Collections.emptyList();
    }
  }

  private static List<AttackChainNodeExpectationAgentOutput> toAttackChainNodeExpectationAgentsOutput(
      List<AttackChainNodeExpectation> attackChainNodeExpectations, String assetId) {
    return attackChainNodeExpectations.stream()
        .map(
            ie ->
                AttackChainNodeExpectationAgentOutput.builder()
                    .type(ie.getType())
                    .id(ie.getId())
                    .name(ie.getName())
                    .results(ie.getResults())
                    .score(ie.getScore())
                    .status(ie.getResponse())
                    .expirationTime(ie.getExpirationTime())
                    .createdAt(ie.getCreatedAt())
                    .expectationGroup(ie.isExpectationGroup())
                    .agentId(ie.getAgent().getId())
                    .agentName(ie.getAgent().getExecutedByUser())
                    .assetId(assetId)
                    .build())
        .collect(Collectors.toList());
  }

  public List<AttackChainNodeExpectationAgentOutput> findMergedExpectationsWithAgentsByAttackChainNodeAndAsset(
      String attackChainNodeId, String assetId, String expectationType) {
    List<AttackChainNodeExpectationAgentOutput> attackChainNodeExpectationAgentOutputs =
        toAttackChainNodeExpectationAgentsOutput(
            attackChainNodeExpectationRepository.findAllWithAgentsByAttackChainNodeAndAsset(
                attackChainNodeId, assetId, AttackChainNodeExpectation.EXPECTATION_TYPE.valueOf(expectationType)),
            assetId);
    attackChainNodeExpectationAgentOutputs.sort(
        Comparator.comparing(AttackChainNodeExpectationAgentOutput::getAgentName));
    return attackChainNodeExpectationAgentOutputs;
  }

  /**
   * Add a date signature to all attackChainNode expectations by agent.
   *
   * @param attackChainNodeId the attackChainNodeId for which to add the end date signature
   * @param agentId the agentId for which to add the end date signature
   * @param date the date to set as the signature value
   * @param signatureType the type of signature to add (start date or end date)
   */
  private void addDateSignatureToAttackChainNodeExpectationsByAgent(
      @NotBlank final String attackChainNodeId,
      @NotBlank final String agentId,
      @NotBlank final Instant date,
      @NotBlank final String signatureType) {
    // Insert the signature for all agent and attackChainNode in one query
    attackChainNodeExpectationRepository.insertSignature(signatureType, date.toString(), attackChainNodeId, agentId);
  }

  /**
   * Create a new End Date NodeExpectationSignature by a given agent.
   *
   * @param attackChainNodeId the attackChainNodeId for which to add the end date signature
   * @param agentId the agentId for which to add the end date signature
   * @param date the date to set as the end date signature
   */
  public void addEndDateSignatureToAttackChainNodeExpectationsByAgent(
      @NotBlank final String attackChainNodeId,
      @NotBlank final String agentId,
      @NotBlank final Instant date) {
    addDateSignatureToAttackChainNodeExpectationsByAgent(
        attackChainNodeId, agentId, date, EXPECTATION_SIGNATURE_TYPE_END_DATE);
  }

  /**
   * Create a new Start Date NodeExpectationSignature by a given agent.
   *
   * @param attackChainNodeId the attackChainNodeId for which to add the start date signature
   * @param agentId the agentId for which to add the start date signature
   * @param date the date to set as the start date signature
   */
  @Transactional
  public void addStartDateSignatureToAttackChainNodeExpectationsByAgent(
      @NotBlank final String attackChainNodeId,
      @NotBlank final String agentId,
      @NotBlank final Instant date) {
    addDateSignatureToAttackChainNodeExpectationsByAgent(
        attackChainNodeId, agentId, date, EXPECTATION_SIGNATURE_TYPE_START_DATE);
  }

  private List<AttackChainNodeExpectation> mergeExpectationResultsByExpectationType(
      List<AttackChainNodeExpectation> expectations) {
    List<String> notCopiedSourceTypes = List.of(COLLECTOR);

    HashMap<AttackChainNodeExpectation.EXPECTATION_TYPE, AttackChainNodeExpectation> electedExpectations =
        new HashMap<>();
    for (AttackChainNodeExpectation expectation : expectations) {
      if (!electedExpectations.containsKey(expectation.getType())) {
        electedExpectations.put(expectation.getType(), expectation);
        continue;
      }

      for (NodeExpectationResult expectationResult : expectation.getResults()) {
        if (!notCopiedSourceTypes.contains(expectationResult.getSourceType())
            && expectationResult.getResult() != null
            && expectationResult.getScore() != null) {
          electedExpectations
              .get(expectation.getType())
              .setResults(
                  Stream.concat(
                          electedExpectations.get(expectation.getType()).getResults().stream(),
                          Stream.of(expectationResult))
                      .toList());
          electedExpectations
              .get(expectation.getType())
              .setScore(
                  Collections.max(
                      electedExpectations.get(expectation.getType()).getResults().stream()
                          .map(NodeExpectationResult::getScore)
                          .toList()));
        }
      }
    }
    return electedExpectations.values().stream().toList();
  }

  // -- BUILD AND SAVE INJECT EXPECTATION --

  @Transactional
  public void buildAndSaveAttackChainNodeExpectations(
      ExecutableNode executableAttackChainNode, List<Expectation> expectations) {
    if (expectations == null || expectations.isEmpty()) {
      return;
    }

    final boolean isAtomicTesting = executableAttackChainNode.getInjection().getAttackChainNode().isAtomicTesting();
    final boolean isScheduledAttackChainNode = !executableAttackChainNode.isDirect();

    if (!isScheduledAttackChainNode && !isAtomicTesting) {
      return;
    }

    // Create the expectations
    final List<Team> teams = executableAttackChainNode.getTeams();
    final List<Asset> assets = executableAttackChainNode.getAssets();
    final List<AssetGroup> assetGroups = executableAttackChainNode.getAssetGroups();

    List<AttackChainNodeExpectation> attackChainNodeExpectations = new ArrayList<>();
    if (!teams.isEmpty()) {
      List<AttackChainNodeExpectation> attackChainNodeExpectationsByUserAndTeam;
      // If atomicTesting, We create expectation for every player and every team
      if (isAtomicTesting) {
        attackChainNodeExpectations =
            teams.stream()
                .flatMap(
                    team ->
                        expectations.stream()
                            .map(
                                expectation ->
                                    expectationConverter(
                                        team,
                                        executableAttackChainNode,
                                        expectation,
                                        expectationPropertiesConfig)))
                .collect(Collectors.toList());

        attackChainNodeExpectationsByUserAndTeam =
            teams.stream()
                .flatMap(
                    team ->
                        team.getUsers().stream()
                            .flatMap(
                                user ->
                                    expectations.stream()
                                        .map(
                                            expectation ->
                                                expectationConverter(
                                                    team,
                                                    user,
                                                    executableAttackChainNode,
                                                    expectation,
                                                    expectationPropertiesConfig))))
                .toList();
      } else {
        final String attackChainRunId = executableAttackChainNode.getInjection().getAttackChainRun().getId();
        // Create expectations for every enabled player in every team
        attackChainNodeExpectationsByUserAndTeam =
            teams.stream()
                .flatMap(
                    team ->
                        team.getAttackChainRunTeamUsers().stream()
                            .filter(
                                attackChainRunTeamUser ->
                                    attackChainRunTeamUser.getAttackChainRun().getId().equals(attackChainRunId))
                            .flatMap(
                                attackChainRunTeamUser ->
                                    expectations.stream()
                                        .map(
                                            expectation ->
                                                expectationConverter(
                                                    team,
                                                    attackChainRunTeamUser.getUser(),
                                                    executableAttackChainNode,
                                                    expectation,
                                                    expectationPropertiesConfig))))
                .toList();

        // Create a set of teams that have at least one enabled player
        Set<Team> teamsWithEnabledPlayers =
            attackChainNodeExpectationsByUserAndTeam.stream()
                .map(AttackChainNodeExpectation::getTeam)
                .collect(Collectors.toSet());

        // Add only the expectations where the team has at least one enabled player
        attackChainNodeExpectations =
            teamsWithEnabledPlayers.stream()
                .flatMap(
                    team ->
                        expectations.stream()
                            .map(
                                expectation ->
                                    expectationConverter(
                                        team,
                                        executableAttackChainNode,
                                        expectation,
                                        expectationPropertiesConfig)))
                .collect(Collectors.toList());
      }
      attackChainNodeExpectations.addAll(attackChainNodeExpectationsByUserAndTeam);
    } else if (!assets.isEmpty() || !assetGroups.isEmpty()) {
      attackChainNodeExpectations =
          expectations.stream()
              .map(
                  expectation ->
                      expectationConverter(
                          executableAttackChainNode, expectation, expectationPropertiesConfig))
              .collect(Collectors.toList());
    }

    if (!attackChainNodeExpectations.isEmpty()) {
      setupDefaultExpectationResults(attackChainNodeExpectations);
      attackChainNodeExpectationRepository.saveAll(attackChainNodeExpectations);
    }
  }

  /**
   * Initializes the result field for each attackChainNodeExpectation in the given list.
   *
   * <p>Correct initialization is critical: a simulation is considered finished when all
   * AttackChainNodeExpectation.results.result entries have a non-null result value.
   *
   * <p>For technical expectations (PREVENTION, DETECTION, VULNERABILITY), results are only set when
   * an agent is assigned
   *
   * <p>So in this function for all expected result we will set attackChainNodeExpectation.results[*].result
   * = null
   *
   * @param attackChainNodeExpectations the list of expectations to initialize
   */
  private void setupDefaultExpectationResults(
      @NotNull final List<AttackChainNodeExpectation> attackChainNodeExpectations) {
    List<Collector> collectors = collectorService.securityPlatformCollectors();

    attackChainNodeExpectations.forEach(
        ie -> {
          switch (ie.getType()) {
            case PREVENTION, DETECTION -> {
              if (ie.getAgent() != null) {
                ie.setResults(setUpFromCollectors(collectors));
              }
            }
            case VULNERABILITY -> {
              if (ie.getAgent() != null) {
                ie.setResults(List.of(buildDefaultForVulnerabilityManagerInFailed()));
              }
            }
            case MANUAL -> {
              if (ie.getUser() != null) {
                ie.setResults(List.of(buildDefaultForPlayerManualValidation()));
              }
            }
            // TODO : The UI needs to be fixed: when the score and result are initialized to null,
            // the user can no longer validate the flag.
            // the user can not validate the flag anymore
            //                case CHALLENGE -> {
            //                  if (ie.getUser() != null) {
            //
            // ie.setResults(List.of(ChallengeExpectationUtils.buildDefaultChallengeNodeExpectationResult()));
            //                  }
            //                }
            case ARTICLE -> {
              if (ie.getUser() != null) {
                ie.setResults(List.of(buildDefaultForMediaPressure()));
              }
            }
            default -> {}
          }
        });
  }

  /**
   * Function used to check if the output contains vulnerabilities and update the related attackChainNode
   * expectations with the result.
   *
   * @param ctx the execution processing context containing the attackChainNode and agent information
   * @param jsonNode the JSON node containing the output to check for vulnerabilities
   */
  public void matchesVulnerabilityExpectations(ExecutionProcessingContext ctx, JsonNode jsonNode) {
    boolean vulnerable =
        jsonNode != null
            && !jsonNode.isMissingNode()
            && jsonNode.isContainerNode()
            && !jsonNode.isEmpty();

    AttackChainNode attackChainNode = ctx.attackChainNode();
    Agent agent = ctx.agent();

    List<AttackChainNodeExpectation> expectations = fetchVulnerabilityExpectations(attackChainNode, agent);

    if (expectations.isEmpty()) {
      return;
    }

    NodeExpectationResult result = buildForVulnerabilityManagerInFailed();

    String label = vulnerable ? VULNERABILITY.failureLabel : VULNERABILITY.successLabel;

    setResultExpectationVulnerable(expectations, result, label);

    validateResultForAsset(expectations, result);
    attackChainNodeExpectationRepository.saveAll(expectations);
  }

  /**
   * Function used to fetch attackChainNode expectations of type VULNERABILITY for a given attackChainNode and agent.
   *
   * @param attackChainNode the attackChainNode for which to fetch the expectations
   * @param agent the agent for which to fetch the expectations
   * @return the list of attackChainNode expectations of type VULNERABILITY for the given attackChainNode and agent
   */
  private static List<AttackChainNodeExpectation> fetchVulnerabilityExpectations(
      AttackChainNode attackChainNode, Agent agent) {
    String agentId = agent != null ? agent.getId() : null;
    return attackChainNode.getExpectations().stream()
        .filter(exp -> AttackChainNodeExpectation.EXPECTATION_TYPE.VULNERABILITY == exp.getType())
        .filter(
            exp -> {
              Agent expAgent = exp.getAgent();
              if (agentId == null) {
                // For nodeExecutor executions (agent == null), match expectations not bound to any
                // agent
                return expAgent == null;
              }
              return expAgent != null && agentId.equals(expAgent.getId());
            })
        .toList();
  }

  /**
   * Function used to set the result of attackChainNode expectations of type VULNERABILITY with a label and a
   * score.
   *
   * @param attackChainNodeExpectations the list of attackChainNode expectations to update
   * @param nodeExpectationResult the result to set for the attackChainNode expectations
   */
  public void validateResultForAsset(
      List<AttackChainNodeExpectation> attackChainNodeExpectations, NodeExpectationResult nodeExpectationResult) {
    attackChainNodeExpectations.forEach(
        attackChainNodeExpectation ->
            updateAttackChainNodeExpectation(
                attackChainNodeExpectation.getId(),
                AttackChainNodeExpectationUpdateInput.builder()
                    .collectorId(nodeExpectationResult.getSourceId())
                    .result(nodeExpectationResult.getResult())
                    .isSuccess(nodeExpectationResult.getScore() != 0.0)
                    .build()));
  }
}
