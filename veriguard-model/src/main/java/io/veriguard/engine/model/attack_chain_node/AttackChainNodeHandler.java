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
              esAttackChainNode.setBase_id(attackChainNode.getNode_id());
              esAttackChainNode.setBase_representative(attackChainNode.getNode_title());
              esAttackChainNode.setBase_created_at(attackChainNode.getNode_created_at());

              if (attackChainNode.getInjector_contract_updated_at() != null
                  && attackChainNode
                      .getInjector_contract_updated_at()
                      .isAfter(attackChainNode.getNode_updated_at())) {
                esAttackChainNode.setBase_updated_at(
                    attackChainNode.getInjector_contract_updated_at());
              } else {
                esAttackChainNode.setBase_updated_at(attackChainNode.getNode_updated_at());
              }
              esAttackChainNode.setBase_restrictions(
                  buildRestrictions(
                      attackChainNode.getNode_attackChain(),
                      attackChainNode.getNode_AttackChainRun()));
              // Specific
              esAttackChainNode.setNode_title(attackChainNode.getNode_title());
              esAttackChainNode.setNode_status(
                  attackChainNode.getNode_status_name() != null
                          && !attackChainNode.getNode_status_name().isBlank()
                      ? attackChainNode.getNode_status_name()
                      : ExecutionStatus.DRAFT.name());
              esAttackChainNode.setBase_platforms_side_denormalized(
                  attackChainNode.getNode_platforms());
              esAttackChainNode.setExecution_date(attackChainNode.getTracking_sent_date());
              // Dependencies (see base_dependencies in EsBase)
              List<String> dependencies = new ArrayList<>();
              if (hasText(attackChainNode.getNode_attackChain())) {
                dependencies.add(attackChainNode.getNode_attackChain());
                esAttackChainNode.setBase_attack_chain_side(attackChainNode.getNode_attackChain());
              } else {
                esAttackChainNode.setBase_attack_chain_side(null);
              }
              if (hasText(attackChainNode.getNode_AttackChainRun())) {
                dependencies.add(attackChainNode.getNode_AttackChainRun());
                esAttackChainNode.setBase_attack_chain_run_side(
                    attackChainNode.getNode_AttackChainRun());
              } else {
                esAttackChainNode.setBase_attack_chain_run_side(null);
              }
              if (!isEmpty(attackChainNode.getNode_attack_patterns())) {
                esAttackChainNode.setBase_attack_patterns_side(
                    attackChainNode.getNode_attack_patterns());
              } else {
                esAttackChainNode.setBase_attack_patterns_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getNode_children())) {
                esAttackChainNode.setBase_node_children_side(
                    attackChainNode.getNode_children());
              } else {
                esAttackChainNode.setBase_node_children_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getAttack_patterns_children())) {
                esAttackChainNode.setBase_attack_patterns_children_side(
                    attackChainNode.getAttack_patterns_children());
              } else {
                esAttackChainNode.setBase_attack_patterns_children_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getNode_kill_chain_phases())) {
                esAttackChainNode.setBase_kill_chain_phases_side(
                    attackChainNode.getNode_kill_chain_phases());
              } else {
                esAttackChainNode.setBase_kill_chain_phases_side(Set.of());
              }
              if (hasText(attackChainNode.getNode_injector_contract())) {
                esAttackChainNode.setBase_node_contract_side(
                    attackChainNode.getNode_injector_contract());
              } else {
                esAttackChainNode.setBase_node_contract_side(null);
              }
              if (!isEmpty(attackChainNode.getNode_tags())) {
                esAttackChainNode.setBase_tags_side(attackChainNode.getNode_tags());
              } else {
                esAttackChainNode.setBase_tags_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getNode_assets())) {
                esAttackChainNode.setBase_assets_side(attackChainNode.getNode_assets());
              } else {
                esAttackChainNode.setBase_assets_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getNode_asset_groups())) {
                esAttackChainNode.setBase_asset_groups_side(
                    attackChainNode.getNode_asset_groups());
              } else {
                esAttackChainNode.setBase_asset_groups_side(Set.of());
              }
              if (!isEmpty(attackChainNode.getNode_teams())) {
                esAttackChainNode.setBase_teams_side(attackChainNode.getNode_teams());
              } else {
                esAttackChainNode.setBase_teams_side(Set.of());
              }
              esAttackChainNode.setBase_dependencies(dependencies);
              return esAttackChainNode;
            })
        .toList();
  }
}
