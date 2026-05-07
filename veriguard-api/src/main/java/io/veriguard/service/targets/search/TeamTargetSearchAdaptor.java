package io.veriguard.service.targets.search;

import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.TeamRepository;
import io.veriguard.service.TeamService;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.persistence.criteria.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class TeamTargetSearchAdaptor extends SearchAdaptorBase {

  private final TeamService teamService;
  private final TeamRepository teamRepository;
  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  public TeamTargetSearchAdaptor(
      TeamService teamService,
      TeamRepository teamRepository,
      HelperTargetSearchAdaptor helperTargetSearchAdaptor) {
    this.teamService = teamService;
    this.teamRepository = teamRepository;
    this.helperTargetSearchAdaptor = helperTargetSearchAdaptor;

    // field name translations
    this.fieldTranslations.put("target_name", "team_name");
    this.fieldTranslations.put("target_tags", "team_tags");
  }

  private static Specification<Team> teamsSpecificationFromAttackChainNode(
      AttackChainNode scopedAttackChainNode) {
    return (root, query, builder) -> {
      if (scopedAttackChainNode.isAtomicTesting()) {
        Path<Object> attackChainNodePath = root.join("attackChainNodes").get("id");
        return builder.equal(attackChainNodePath, scopedAttackChainNode.getId());
      } else {
        if (scopedAttackChainNode.isAllTeams()) {
          Path<Object> attackChainRunTeamUsersPath =
              root.get("attackChainRunTeamUsers").get("attackChainRun").get("id");
          Path<Object> attackChainNodePath = root.join("attackChainRuns").get("attackChainNodes").get("id");
          return builder.and(
              builder.equal(attackChainNodePath, scopedAttackChainNode.getId()),
              builder.equal(
                  attackChainRunTeamUsersPath, scopedAttackChainNode.getAttackChainRun().getId()));
        } else {
          Path<Object> attackChainRunTeamUsersPath =
              root.get("attackChainRunTeamUsers").get("attackChainRun").get("id");
          Path<Object> attackChainNodePath = root.join("attackChainNodes").get("id");
          return builder.and(
              builder.equal(attackChainNodePath, scopedAttackChainNode.getId()),
              builder.equal(
                  attackChainRunTeamUsersPath, scopedAttackChainNode.getAttackChainRun().getId()));
        }
      }
    };
  }

  @Override
  public Page<AttackChainNodeTarget> search(
      SearchPaginationInput input, AttackChainNode scopedAttackChainNode) {
    SearchPaginationInput translatedInput = this.translate(input, scopedAttackChainNode);

    Page<Team> filteredTeams =
        buildPaginationJPA(
            (Specification<Team> specification, Pageable pageable) ->
                this.teamRepository.findAll(
                    teamsSpecificationFromAttackChainNode(scopedAttackChainNode).and(specification),
                    pageable),
            translatedInput,
            Team.class);

    return new PageImpl<>(
        filteredTeams.getContent().stream()
            .map(team -> convertFromTeamOutput(team, scopedAttackChainNode))
            .toList(),
        filteredTeams.getPageable(),
        filteredTeams.getTotalElements());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForAttackChainNode(
      AttackChainNode scopedAttackChainNode, String textSearch) {
    if (scopedAttackChainNode.isAllTeams()) {
      return scopedAttackChainNode.getAttackChainRun().getTeams().stream()
          .filter(team -> team.getName().toLowerCase().contains(textSearch.toLowerCase()))
          .map(team -> new FilterUtilsJpa.Option(team.getId(), team.getName()))
          .toList();
    } else {
      return scopedAttackChainNode.getTeams().stream()
          .filter(team -> team.getName().toLowerCase().contains(textSearch.toLowerCase()))
          .map(team -> new FilterUtilsJpa.Option(team.getId(), team.getName()))
          .toList();
    }
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    return teamService.getTeams(ids).stream()
        .map(team -> new FilterUtilsJpa.Option(team.getId(), team.getName()))
        .toList();
  }

  private AttackChainNodeTarget convertFromTeamOutput(Team team, AttackChainNode attackChainNode) {
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        attackChainNode,
        () ->
            new TeamTarget(
                team.getId(),
                team.getName(),
                team.getTags().stream().map(Tag::getId).collect(Collectors.toSet())),
        false);
  }
}
