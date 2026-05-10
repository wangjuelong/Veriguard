package io.veriguard.engine.model.attack_chain_nodeexpectation;

import static io.veriguard.engine.EsUtils.buildRestrictions;
import static io.veriguard.helper.AttackChainNodeExpectationHelper.computeStatus;
import static java.lang.String.valueOf;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import io.veriguard.database.raw.RawAttackChainNodeExpectation;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.engine.Handler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttackChainNodeExpectationHandler implements Handler<EsAttackChainNodeExpectation> {

  private final AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;

  @Override
  public List<EsAttackChainNodeExpectation> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawAttackChainNodeExpectation> forIndexing =
        this.attackChainNodeExpectationRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            attackChainNodeExpectation -> {
              EsAttackChainNodeExpectation esAttackChainNodeExpectation =
                  new EsAttackChainNodeExpectation();
              // Base
              esAttackChainNodeExpectation.setBase_id(
                  attackChainNodeExpectation.getNode_expectation_id());
              esAttackChainNodeExpectation.setBase_representative(
                  attackChainNodeExpectation.getNode_expectation_name());
              esAttackChainNodeExpectation.setBase_created_at(
                  attackChainNodeExpectation.getNode_expectation_created_at());
              esAttackChainNodeExpectation.setBase_updated_at(
                  attackChainNodeExpectation.getNode_expectation_updated_at());
              esAttackChainNodeExpectation.setBase_restrictions(
                  buildRestrictions(
                      attackChainNodeExpectation.getAttack_chain_run_id(),
                      attackChainNodeExpectation.getNode_id()));
              // Specific
              esAttackChainNodeExpectation.setNode_expectation_name(
                  attackChainNodeExpectation.getNode_expectation_name());
              esAttackChainNodeExpectation.setNode_expectation_description(
                  attackChainNodeExpectation.getNode_expectation_description());
              esAttackChainNodeExpectation.setNode_expectation_type(
                  attackChainNodeExpectation.getNode_expectation_type());
              esAttackChainNodeExpectation.setNode_expectation_results(
                  attackChainNodeExpectation.getNode_expectation_results());
              esAttackChainNodeExpectation.setNode_title(
                  attackChainNodeExpectation.getNode_title());
              esAttackChainNodeExpectation.setExecution_date(
                  attackChainNodeExpectation.getTracking_sent_date());

              esAttackChainNodeExpectation.setNode_expectation_score(
                  attackChainNodeExpectation.getNode_expectation_score());
              esAttackChainNodeExpectation.setNode_expectation_expected_score(
                  attackChainNodeExpectation.getNode_expectation_expected_score());
              esAttackChainNodeExpectation.setNode_expectation_expiration_time(
                  attackChainNodeExpectation.getNode_expiration_time());
              esAttackChainNodeExpectation.setNode_expectation_group(
                  attackChainNodeExpectation.getNode_expectation_group());
              // Dependencies (see base_dependencies in EsBase)
              List<String> dependencies = new ArrayList<>();
              if (hasText(attackChainNodeExpectation.getAttack_chain_run_id())) {
                dependencies.add(attackChainNodeExpectation.getAttack_chain_run_id());
                esAttackChainNodeExpectation.setBase_attack_chain_run_side(
                    attackChainNodeExpectation.getAttack_chain_run_id());
              } else {
                esAttackChainNodeExpectation.setBase_attack_chain_run_side(null);
              }
              if (hasText(attackChainNodeExpectation.getAttack_chain_id())) {
                dependencies.add(attackChainNodeExpectation.getAttack_chain_id());
                esAttackChainNodeExpectation.setBase_attack_chain_side(
                    attackChainNodeExpectation.getAttack_chain_id());
              } else {
                esAttackChainNodeExpectation.setBase_attack_chain_side(null);
              }
              if (hasText(attackChainNodeExpectation.getNode_id())) {
                dependencies.add(attackChainNodeExpectation.getNode_id());
                esAttackChainNodeExpectation.setBase_node_side(
                    attackChainNodeExpectation.getNode_id());
              } else {
                esAttackChainNodeExpectation.setBase_node_side(null);
              }
              if (hasText(attackChainNodeExpectation.getUser_id())) {
                dependencies.add(attackChainNodeExpectation.getUser_id());
                esAttackChainNodeExpectation.setBase_user_side(
                    attackChainNodeExpectation.getUser_id());
              } else {
                esAttackChainNodeExpectation.setBase_user_side(null);
              }
              if (hasText(attackChainNodeExpectation.getTeam_id())) {
                dependencies.add(attackChainNodeExpectation.getTeam_id());
                esAttackChainNodeExpectation.setBase_team_side(
                    attackChainNodeExpectation.getTeam_id());
              } else {
                esAttackChainNodeExpectation.setBase_team_side(null);
              }
              if (hasText(attackChainNodeExpectation.getAsset_id())) {
                dependencies.add(attackChainNodeExpectation.getAsset_id());
                esAttackChainNodeExpectation.setBase_asset_side(
                    attackChainNodeExpectation.getAsset_id());
              } else {
                esAttackChainNodeExpectation.setBase_asset_side(null);
              }
              if (hasText(attackChainNodeExpectation.getAsset_group_id())) {
                dependencies.add(attackChainNodeExpectation.getAsset_group_id());
                esAttackChainNodeExpectation.setBase_asset_group_side(
                    attackChainNodeExpectation.getAsset_group_id());
              } else {
                esAttackChainNodeExpectation.setBase_asset_group_side(null);
              }
              if (!isEmpty(attackChainNodeExpectation.getAttack_pattern_ids())) {
                esAttackChainNodeExpectation.setBase_attack_patterns_side(
                    attackChainNodeExpectation.getAttack_pattern_ids());
              } else {
                esAttackChainNodeExpectation.setBase_attack_patterns_side(Set.of());
              }
              if (!isEmpty(attackChainNodeExpectation.getDomain_ids())) {
                esAttackChainNodeExpectation.setBase_security_domains_side(
                    attackChainNodeExpectation.getDomain_ids());
              } else {
                esAttackChainNodeExpectation.setBase_security_domains_side(Set.of());
              }
              if (!isEmpty(attackChainNodeExpectation.getSecurity_platform_ids())) {
                esAttackChainNodeExpectation.setBase_security_platforms_side(
                    attackChainNodeExpectation.getSecurity_platform_ids());
              } else {
                esAttackChainNodeExpectation.setBase_security_platforms_side(Set.of());
              }
              esAttackChainNodeExpectation.setNode_expectation_status(
                  valueOf(
                      computeStatus(
                          attackChainNodeExpectation.getNode_expectation_score(),
                          attackChainNodeExpectation.getNode_expectation_expected_score())));
              esAttackChainNodeExpectation.setBase_dependencies(dependencies);
              return esAttackChainNodeExpectation;
            })
        .toList();
  }
}
