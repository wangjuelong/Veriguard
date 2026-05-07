package io.veriguard.engine.model.injectexpectation;

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
              EsAttackChainNodeExpectation esAttackChainNodeExpectation = new EsAttackChainNodeExpectation();
              // Base
              esAttackChainNodeExpectation.setBase_id(attackChainNodeExpectation.getInject_expectation_id());
              esAttackChainNodeExpectation.setBase_representative(
                  attackChainNodeExpectation.getInject_expectation_name());
              esAttackChainNodeExpectation.setBase_created_at(
                  attackChainNodeExpectation.getInject_expectation_created_at());
              esAttackChainNodeExpectation.setBase_updated_at(
                  attackChainNodeExpectation.getInject_expectation_updated_at());
              esAttackChainNodeExpectation.setBase_restrictions(
                  buildRestrictions(
                      attackChainNodeExpectation.getExercise_id(), attackChainNodeExpectation.getInject_id()));
              // Specific
              esAttackChainNodeExpectation.setInject_expectation_name(
                  attackChainNodeExpectation.getInject_expectation_name());
              esAttackChainNodeExpectation.setInject_expectation_description(
                  attackChainNodeExpectation.getInject_expectation_description());
              esAttackChainNodeExpectation.setInject_expectation_type(
                  attackChainNodeExpectation.getInject_expectation_type());
              esAttackChainNodeExpectation.setInject_expectation_results(
                  attackChainNodeExpectation.getInject_expectation_results());
              esAttackChainNodeExpectation.setInject_title(attackChainNodeExpectation.getInject_title());
              esAttackChainNodeExpectation.setExecution_date(attackChainNodeExpectation.getTracking_sent_date());

              esAttackChainNodeExpectation.setInject_expectation_score(
                  attackChainNodeExpectation.getInject_expectation_score());
              esAttackChainNodeExpectation.setInject_expectation_expected_score(
                  attackChainNodeExpectation.getInject_expectation_expected_score());
              esAttackChainNodeExpectation.setInject_expectation_expiration_time(
                  attackChainNodeExpectation.getInject_expiration_time());
              esAttackChainNodeExpectation.setInject_expectation_group(
                  attackChainNodeExpectation.getInject_expectation_group());
              // Dependencies (see base_dependencies in EsBase)
              List<String> dependencies = new ArrayList<>();
              if (hasText(attackChainNodeExpectation.getExercise_id())) {
                dependencies.add(attackChainNodeExpectation.getExercise_id());
                esAttackChainNodeExpectation.setBase_simulation_side(attackChainNodeExpectation.getExercise_id());
              } else {
                esAttackChainNodeExpectation.setBase_simulation_side(null);
              }
              if (hasText(attackChainNodeExpectation.getScenario_id())) {
                dependencies.add(attackChainNodeExpectation.getScenario_id());
                esAttackChainNodeExpectation.setBase_scenario_side(attackChainNodeExpectation.getScenario_id());
              } else {
                esAttackChainNodeExpectation.setBase_scenario_side(null);
              }
              if (hasText(attackChainNodeExpectation.getInject_id())) {
                dependencies.add(attackChainNodeExpectation.getInject_id());
                esAttackChainNodeExpectation.setBase_inject_side(attackChainNodeExpectation.getInject_id());
              } else {
                esAttackChainNodeExpectation.setBase_inject_side(null);
              }
              if (hasText(attackChainNodeExpectation.getUser_id())) {
                dependencies.add(attackChainNodeExpectation.getUser_id());
                esAttackChainNodeExpectation.setBase_user_side(attackChainNodeExpectation.getUser_id());
              } else {
                esAttackChainNodeExpectation.setBase_user_side(null);
              }
              if (hasText(attackChainNodeExpectation.getTeam_id())) {
                dependencies.add(attackChainNodeExpectation.getTeam_id());
                esAttackChainNodeExpectation.setBase_team_side(attackChainNodeExpectation.getTeam_id());
              } else {
                esAttackChainNodeExpectation.setBase_team_side(null);
              }
              if (hasText(attackChainNodeExpectation.getAsset_id())) {
                dependencies.add(attackChainNodeExpectation.getAsset_id());
                esAttackChainNodeExpectation.setBase_asset_side(attackChainNodeExpectation.getAsset_id());
              } else {
                esAttackChainNodeExpectation.setBase_asset_side(null);
              }
              if (hasText(attackChainNodeExpectation.getAsset_group_id())) {
                dependencies.add(attackChainNodeExpectation.getAsset_group_id());
                esAttackChainNodeExpectation.setBase_asset_group_side(attackChainNodeExpectation.getAsset_group_id());
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
              esAttackChainNodeExpectation.setInject_expectation_status(
                  valueOf(
                      computeStatus(
                          attackChainNodeExpectation.getInject_expectation_score(),
                          attackChainNodeExpectation.getInject_expectation_expected_score())));
              esAttackChainNodeExpectation.setBase_dependencies(dependencies);
              return esAttackChainNodeExpectation;
            })
        .toList();
  }
}
