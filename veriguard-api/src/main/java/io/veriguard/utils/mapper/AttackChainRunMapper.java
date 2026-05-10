package io.veriguard.utils.mapper;

import static java.util.Collections.emptyList;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChainRunStatus;
import io.veriguard.database.raw.RawAttackChainNodeExpectation;
import io.veriguard.database.raw.RawAttackChainRunSimple;
import io.veriguard.database.repository.AssetGroupRepository;
import io.veriguard.database.repository.AssetRepository;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.database.repository.TeamRepository;
import io.veriguard.rest.atomic_testing.form.TargetSimple;
import io.veriguard.rest.attack_chain_run.form.AttackChainRunSimple;
import io.veriguard.rest.document.form.RelatedEntityOutput;
import io.veriguard.utils.ResultUtils;
import io.veriguard.utils.TargetType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mapper component for converting AttackChainRun entities to output DTOs.
 *
 * <p>Provides methods for transforming attackChainRun domain objects and raw database results into
 * API response objects, including target resolution and expectation result aggregation.
 *
 * @see io.veriguard.database.model.AttackChainRun
 * @see io.veriguard.rest.attack_chain_run.form.AttackChainRunSimple
 */
@RequiredArgsConstructor
@Component
public class AttackChainRunMapper {

  private final AssetRepository assetRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final TeamRepository teamRepository;
  private final AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;

  private final ResultUtils resultUtils;
  private final AttackChainNodeMapper attackChainNodeMapper;
  private final AttackChainNodeExpectationMapper attackChainNodeExpectationMapper;

  // -- EXERCISE SIMPLE --

  /**
   * Converts a raw attackChainRun to a simplified attackChainRun DTO with full target and score
   * resolution.
   *
   * <p>Performs additional database queries to resolve teams, assets, and asset groups, then
   * computes global expectation results.
   *
   * @param rawAttackChainRun the raw attackChainRun data from database
   * @return the attackChainRun simple DTO with resolved targets and scores
   */
  public AttackChainRunSimple getAttackChainRunSimple(RawAttackChainRunSimple rawAttackChainRun) {

    AttackChainRunSimple simple = fromRawAttackChainRunSimple(rawAttackChainRun);

    if (rawAttackChainRun.getNode_ids() != null) {
      // -- GLOBAL SCORE ---
      simple.setExpectationResultByTypes(
          resultUtils.computeGlobalExpectationResults(rawAttackChainRun.getNode_ids()));

      // -- TARGETS --
      List<Object[]> teams =
          teamRepository.teamsByAttackChainRunIds(Set.of(rawAttackChainRun.getAttack_chain_run_id()));
      List<Object[]> assets =
          assetRepository.assetsByAttackChainRunIds(Set.of(rawAttackChainRun.getAttack_chain_run_id()));
      List<Object[]> assetGroups =
          assetGroupRepository.assetGroupsByAttackChainRunIds(
              Set.of(rawAttackChainRun.getAttack_chain_run_id()));

      List<TargetSimple> allTargets =
          Stream.concat(
                  attackChainNodeMapper.toTargetSimple(teams, TargetType.TEAMS).stream(),
                  Stream.concat(
                      attackChainNodeMapper.toTargetSimple(assets, TargetType.ASSETS).stream(),
                      attackChainNodeMapper
                          .toTargetSimple(assetGroups, TargetType.ASSETS_GROUPS)
                          .stream()))
              .toList();

      simple.getTargets().addAll(allTargets);
    }
    return simple;
  }

  // -- LIST OF EXERCISE SIMPLE --

  /**
   * Converts a list of raw attackChainRuns to simplified DTOs with batched target resolution.
   *
   * <p>Optimizes database access by batching target queries across all attackChainRuns rather than
   * querying for each attackChainRun individually.
   *
   * @param attackChainRuns the list of raw attackChainRun data
   * @return list of attackChainRun simple DTOs with resolved targets and scores
   */
  public List<AttackChainRunSimple> getAttackChainRunSimples(
      List<RawAttackChainRunSimple> attackChainRuns) {
    // -- MAP TO GENERATE TARGETSIMPLEs
    Set<String> attackChainRunIds =
        attackChainRuns.stream()
            .map(RawAttackChainRunSimple::getAttack_chain_run_id)
            .collect(Collectors.toSet());

    Map<String, List<Object[]>> teamMap =
        teamRepository.teamsByAttackChainRunIds(attackChainRunIds).stream()
            .collect(Collectors.groupingBy(row -> (String) row[0]));

    Map<String, List<Object[]>> assetMap =
        assetRepository.assetsByAttackChainRunIds(attackChainRunIds).stream()
            .collect(Collectors.groupingBy(row -> (String) row[0]));

    Map<String, List<Object[]>> assetGroupMap =
        assetGroupRepository.assetGroupsByAttackChainRunIds(attackChainRunIds).stream()
            .collect(Collectors.groupingBy(row -> (String) row[0]));

    Map<String, List<RawAttackChainNodeExpectation>> expectationMap =
        attackChainNodeExpectationRepository
            .rawForComputeGlobalByAttackChainRunIds(attackChainRunIds.toArray(new String[0]))
            .stream()
            .collect(Collectors.groupingBy(RawAttackChainNodeExpectation::getAttack_chain_run_id));

    List<AttackChainRunSimple> attackChainRunSimples = new ArrayList<>();

    for (RawAttackChainRunSimple attackChainRun : attackChainRuns) {
      AttackChainRunSimple simple =
          getAttackChainRunSimple(
              attackChainRun,
              teamMap.getOrDefault(attackChainRun.getAttack_chain_run_id(), emptyList()),
              assetMap.getOrDefault(attackChainRun.getAttack_chain_run_id(), emptyList()),
              assetGroupMap.getOrDefault(attackChainRun.getAttack_chain_run_id(), emptyList()),
              expectationMap.getOrDefault(attackChainRun.getAttack_chain_run_id(), emptyList()));
      attackChainRunSimples.add(simple);
    }

    return attackChainRunSimples;
  }

  private AttackChainRunSimple getAttackChainRunSimple(
      RawAttackChainRunSimple rawAttackChainRun,
      List<Object[]> teams,
      List<Object[]> assets,
      List<Object[]> assetGroups,
      List<RawAttackChainNodeExpectation> expectations) {

    AttackChainRunSimple simple = fromRawAttackChainRunSimple(rawAttackChainRun);

    if (rawAttackChainRun.getNode_ids() != null) {
      // -- GLOBAL SCORE ---
      simple.setExpectationResultByTypes(
          attackChainNodeExpectationMapper.extractExpectationResultByTypesFromRaw(
              rawAttackChainRun.getNode_ids(), expectations));
      // -- TARGETS --
      List<TargetSimple> allTargets =
          Stream.concat(
                  attackChainNodeMapper.toTargetSimple(teams, TargetType.TEAMS).stream(),
                  Stream.concat(
                      attackChainNodeMapper.toTargetSimple(assets, TargetType.ASSETS).stream(),
                      attackChainNodeMapper
                          .toTargetSimple(assetGroups, TargetType.ASSETS_GROUPS)
                          .stream()))
              .toList();

      simple.getTargets().addAll(allTargets);
    }

    return simple;
  }

  // -- RAWEXERCISESIMPLE to EXERCISESIMPLE --
  private AttackChainRunSimple fromRawAttackChainRunSimple(
      RawAttackChainRunSimple rawAttackChainRun) {
    AttackChainRunSimple simple = new AttackChainRunSimple();
    simple.setId(rawAttackChainRun.getAttack_chain_run_id());
    simple.setName(rawAttackChainRun.getAttack_chain_run_name());
    simple.setTagIds(rawAttackChainRun.getAttack_chain_run_tags());
    simple.setCategory(rawAttackChainRun.getAttack_chain_run_category());
    simple.setSubtitle(rawAttackChainRun.getAttack_chain_run_subtitle());
    simple.setStatus(AttackChainRunStatus.valueOf(rawAttackChainRun.getAttack_chain_run_status()));
    simple.setStart(rawAttackChainRun.getAttack_chain_run_start_date());
    simple.setUpdatedAt(rawAttackChainRun.getAttack_chain_run_updated_at());

    return simple;
  }

  /**
   * Converts an attackChainRun entity to a simplified DTO.
   *
   * <p>Maps basic attackChainRun properties without resolving targets or computing scores.
   *
   * @param attackChainRun the attackChainRun entity
   * @return the simplified attackChainRun DTO
   */
  public AttackChainRunSimple toAttackChainRunSimple(AttackChainRun attackChainRun) {
    AttackChainRunSimple simple = new AttackChainRunSimple();
    simple.setId(attackChainRun.getId());
    simple.setName(attackChainRun.getName());
    simple.setTagIds(
        attackChainRun.getTags().stream().map(tag -> tag.getId()).collect(Collectors.toSet()));
    simple.setCategory(attackChainRun.getCategory());
    simple.setSubtitle(attackChainRun.getSubtitle());
    simple.setStatus(attackChainRun.getStatus());
    simple.setUpdatedAt(attackChainRun.getUpdatedAt());

    return simple;
  }

  /**
   * Converts a set of attackChainRuns to related entity outputs.
   *
   * <p>Used for showing attackChainRun references in document or other entity contexts.
   *
   * @param attackChainRuns the attackChainRuns to convert
   * @return set of related entity output DTOs
   */
  public static Set<RelatedEntityOutput> toRelatedEntityOutputs(
      Set<AttackChainRun> attackChainRuns) {
    return attackChainRuns.stream()
        .map(attackChainRun -> toRelatedEntityOutput(attackChainRun))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutput(AttackChainRun attackChainRun) {
    return RelatedEntityOutput.builder()
        .id(attackChainRun.getId())
        .name(attackChainRun.getName())
        .build();
  }

  /**
   * Converts a set of attackChainNodes to related entity outputs with simulation context.
   *
   * @param attackChainNodes the attackChainNodes to convert
   * @return set of related entity output DTOs including attackChainRun context
   */
  public static Set<RelatedEntityOutput> toSimulationAttackChainNodes(
      Set<AttackChainNode> attackChainNodes) {
    return attackChainNodes.stream()
        .map(attackChainNode -> toSimulationAttackChainNode(attackChainNode))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toSimulationAttackChainNode(AttackChainNode attackChainNode) {
    return RelatedEntityOutput.builder()
        .id(attackChainNode.getId())
        .name(attackChainNode.getTitle())
        .context(attackChainNode.getAttackChainRun().getId())
        .build();
  }
}
