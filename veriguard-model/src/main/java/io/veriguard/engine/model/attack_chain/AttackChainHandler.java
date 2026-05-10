package io.veriguard.engine.model.attack_chain;

import static io.veriguard.engine.EsUtils.buildRestrictions;
import static org.springframework.util.CollectionUtils.isEmpty;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.raw.RawAttackChainSimple;
import io.veriguard.database.repository.AttackChainRepository;
import io.veriguard.engine.Handler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AttackChainHandler implements Handler<EsAttackChain> {

  private AttackChainRepository attackChainRepository;

  @Autowired
  public void setAttackChainRepository(AttackChainRepository attackChainRepository) {
    this.attackChainRepository = attackChainRepository;
  }

  @Override
  public List<EsAttackChain> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawAttackChainSimple> forIndexing = attackChainRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            attackChain -> {
              EsAttackChain esAttackChain = new EsAttackChain();
              // Base
              esAttackChain.setBase_id(attackChain.getAttack_chain_id());
              esAttackChain.setName(attackChain.getAttack_chain_name());
              esAttackChain.setStatus(
                  attackChain.getAttack_chain_recurrence() != null
                      ? AttackChain.RECURRENCE_STATUS.SCHEDULED.name()
                      : AttackChain.RECURRENCE_STATUS.NOT_PLANNED.name());
              esAttackChain.setBase_created_at(attackChain.getAttack_chain_created_at());
              esAttackChain.setBase_updated_at(attackChain.getAttack_chain_injects_updated_at());

              esAttackChain.setBase_representative(attackChain.getAttack_chain_name());
              esAttackChain.setBase_restrictions(buildRestrictions(attackChain.getAttack_chain_id()));
              // Specific
              esAttackChain.setBase_platforms_side_denormalized(
                  attackChain.getAttack_chain_platforms());
              // Dependencies (see base_dependencies in EsBase)
              if (!isEmpty(attackChain.getAttack_chain_tags())) {
                esAttackChain.setBase_tags_side(attackChain.getAttack_chain_tags());
              } else {
                esAttackChain.setBase_tags_side(Set.of());
              }
              if (!isEmpty(attackChain.getAttack_chain_assets())) {
                esAttackChain.setBase_assets_side(attackChain.getAttack_chain_assets());
              } else {
                esAttackChain.setBase_assets_side(Set.of());
              }
              if (!isEmpty(attackChain.getAttack_chain_asset_groups())) {
                esAttackChain.setBase_asset_groups_side(attackChain.getAttack_chain_asset_groups());
              } else {
                esAttackChain.setBase_asset_groups_side(Set.of());
              }
              if (!isEmpty(attackChain.getAttack_chain_teams())) {
                esAttackChain.setBase_teams_side(attackChain.getAttack_chain_teams());
              } else {
                esAttackChain.setBase_teams_side(Set.of());
              }
              return esAttackChain;
            })
        .toList();
  }
}
