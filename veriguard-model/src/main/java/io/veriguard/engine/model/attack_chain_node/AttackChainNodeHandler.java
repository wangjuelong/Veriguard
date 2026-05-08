package io.veriguard.engine.model.attack_chain_node;

import static io.veriguard.engine.EsUtils.buildRestrictions;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import io.veriguard.database.model.ExecutionStatus;
import io.veriguard.database.raw.RawAttackChainNodeIndexing;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.engine.Handler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AttackChainNodeHandler implements Handler<EsAttackChainNode> {

  private AttackChainNodeRepository attackChainNodeRepository;

  @Autowired
  public void setAttackChainNodeRepository(AttackChainNodeRepository attackChainNodeRepository) {
    this.attackChainNodeRepository = attackChainNodeRepository;
  }

  @Override
  public List<EsAttackChainNode> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawAttackChainNodeIndexing> forIndexing =
        attackChainNodeRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            attackChainNode -> {
              EsAttackChainNode esAttackChainNode = new EsAttackChainNode();
              // Base
              esAttackChainNode.setBase_id(attackChainNode.getInject_id());
              esAttackChainNode.setBase_representative(attackChainNode.getInject_title());
              esAttackChainNode.setBase_created_at(attackChainNode.getInject_created_at());

              if (attackChainNode.getInjector_contract_updated_at() != null
                  && attackChainNode
                      .getInjector_contract_updated_at()
                      .isAfter(attackChainNode.getInject_updated_at())) {
                esAttackChainNode.setBase_updated_at(
                    attackChainNode.getInjector_contract_updated_at());
              } else {
                esAttackChainNode.setBase_updated_at(attackChainNode.getInject_updated_at());
              }
              esAttackChainNode.setBase_restrictions(
                  buildRestrictions(
                      attackChainNode.getInject_attackChain(),
                      attackChainNode.getInject_AttackChainRun()));
              // Specific
              esAttackChainNode.setInject_title(attackChainNode.getInject_title());
              esAttackChainNode.setInject_status(
                  attackChainNode.getInject_status_name() != null
                          && !attackChainNode.getInject_status_name().isBlank()
                      ? attackChainNode.getInject_status_name()
                      : ExecutionStatus.DRAFT.name());
              esAttackChainNode.setBase_platforms_side_denormalized(
                  attackChainNode.getInject_platforms());
              esAttackChainNode.setExecution_date(attackChainNode.getTracking_sent_date());
              // Dependencies (see base_dependencies in EsBase)
              List<String> dependencies = new ArrayList<>();
              if (hasText(attackChainNode.getInject_attackChain())) {
                dependencies.add(attackChainNode.getInject_attackChain());
                esAttackChainNode.setBase_scenario_side(attackChainNode.getInject_attackChain());
              } else {
                esAttackChainNode.setBase_scenario_side(null);
              }
              if (hasText(attackChainNode.getInject_AttackChainRun())) {
                dependencies.add(attackChainNode.getInject_AttackChainRun());
                esAttackChainNode.setBase_simulation_side(
                    attackChainNode.getInject_AttackChainRun());
              } else {
                esAttackChainNode.setBase_simulation_side(null);
              }
              if (!isEmpty(attackChainNode.getInject_attack_patterns())) {
                esAttackChainNode.setBase_attack_patterns_side(
                    attackChainNode.getInject_attack_patterns());
              } else {
                esAttackChainNode.setBase_attack_patterns_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getInject_children())) {
                esAttackChainNode.setBase_inject_children_side(
                    attackChainNode.getInject_children());
              } else {
                esAttackChainNode.setBase_inject_children_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getAttack_patterns_children())) {
                esAttackChainNode.setBase_attack_patterns_children_side(
                    attackChainNode.getAttack_patterns_children());
              } else {
                esAttackChainNode.setBase_attack_patterns_children_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getInject_kill_chain_phases())) {
                esAttackChainNode.setBase_kill_chain_phases_side(
                    attackChainNode.getInject_kill_chain_phases());
              } else {
                esAttackChainNode.setBase_kill_chain_phases_side(Set.of());
              }
              if (hasText(attackChainNode.getInject_injector_contract())) {
                esAttackChainNode.setBase_inject_contract_side(
                    attackChainNode.getInject_injector_contract());
              } else {
                esAttackChainNode.setBase_inject_contract_side(null);
              }
              if (!isEmpty(attackChainNode.getInject_tags())) {
                esAttackChainNode.setBase_tags_side(attackChainNode.getInject_tags());
              } else {
                esAttackChainNode.setBase_tags_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getInject_assets())) {
                esAttackChainNode.setBase_assets_side(attackChainNode.getInject_assets());
              } else {
                esAttackChainNode.setBase_assets_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getInject_asset_groups())) {
                esAttackChainNode.setBase_asset_groups_side(
                    attackChainNode.getInject_asset_groups());
              } else {
                esAttackChainNode.setBase_asset_groups_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getInject_teams())) {
                esAttackChainNode.setBase_teams_side(attackChainNode.getInject_teams());
              } else {
                esAttackChainNode.setBase_teams_side(Set.of());
              }
              esAttackChainNode.setBase_dependencies(dependencies);
              return esAttackChainNode;
            })
        .toList();
  }
}
