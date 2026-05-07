package io.veriguard.service.targets.search;

import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.veriguard.utils.pagination.SortField;
import jakarta.persistence.criteria.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class PlayerTargetSearchAdaptor extends SearchAdaptorBase {

  private final UserRepository userRepository;
  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  public PlayerTargetSearchAdaptor(
      UserRepository userRepository, HelperTargetSearchAdaptor helperTargetSearchAdaptor) {
    this.userRepository = userRepository;
    this.helperTargetSearchAdaptor = helperTargetSearchAdaptor;

    // field name translations
    this.fieldTranslations.put("target_tags", "user_tags");
    this.fieldTranslations.put("target_teams", "user_teams");
  }

  private static Specification<User> playersSpecificationFromAttackChainNode(AttackChainNode scopedAttackChainNode) {
    return (root, query, builder) -> {
      if (scopedAttackChainNode.isAtomicTesting()) {
        Path<Object> attackChainNodePath = root.join("teams").join("injects").get("id");
        return builder.equal(attackChainNodePath, scopedAttackChainNode.getId());
      } else {
        if (scopedAttackChainNode.isAllTeams()) {
          Path<Object> attackChainRunTeamUsersPath =
              root.get("exerciseTeamUsers").get("exercise").get("id");
          Path<Object> attackChainNodePath = root.join("teams").join("exercises").get("injects").get("id");
          return builder.and(
              builder.equal(attackChainNodePath, scopedAttackChainNode.getId()),
              builder.equal(attackChainRunTeamUsersPath, scopedAttackChainNode.getAttackChainRun().getId()));
        } else {
          Path<Object> attackChainRunTeamUsersPath =
              root.get("exerciseTeamUsers").get("exercise").get("id");
          Path<Object> attackChainNodePath = root.join("teams").join("injects").get("id");
          return builder.and(
              builder.equal(attackChainNodePath, scopedAttackChainNode.getId()),
              builder.equal(attackChainRunTeamUsersPath, scopedAttackChainNode.getAttackChainRun().getId()));
        }
      }
    };
  }

  @Override
  public Page<AttackChainNodeTarget> search(SearchPaginationInput input, AttackChainNode scopedAttackChainNode) {
    SearchPaginationInput translatedInput = this.translate(input, scopedAttackChainNode);

    // mind the specific sorts "email" because no name for players
    SortField defaultSort = new SortField("user_email", "ASC", null);
    translatedInput.setSorts(List.of(defaultSort));

    Page<User> filteredPlayers =
        buildPaginationJPA(
            (Specification<User> specification, Pageable pageable) ->
                this.userRepository.findAll(
                    playersSpecificationFromAttackChainNode(scopedAttackChainNode).and(specification), pageable),
            translatedInput,
            User.class);

    return new PageImpl<>(
        filteredPlayers.getContent().stream()
            .map(player -> convertFromPlayerOutput(player, scopedAttackChainNode))
            .toList(),
        filteredPlayers.getPageable(),
        filteredPlayers.getTotalElements());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForAttackChainNode(AttackChainNode scopedAttackChainNode, String textSearch) {
    throw new NotImplementedException("Implement when needed");
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    throw new NotImplementedException("Implement when needed");
  }

  private AttackChainNodeTarget convertFromPlayerOutput(User player, AttackChainNode attackChainNode) {
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        attackChainNode,
        () ->
            new PlayerTarget(
                player.getId(),
                player.getNameOrEmail(),
                player.getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                player.getTeams().stream().map(Team::getId).collect(Collectors.toSet())),
        false);
  }
}
