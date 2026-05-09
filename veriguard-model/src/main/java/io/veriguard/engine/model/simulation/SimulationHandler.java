package io.veriguard.engine.model.simulation;

import static io.veriguard.engine.EsUtils.buildRestrictions;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import io.veriguard.database.raw.RawSimulation;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.engine.Handler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class SimulationHandler implements Handler<EsSimulation> {

  private final AttackChainRunRepository simulationRepository;

  @Override
  public List<EsSimulation> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawSimulation> forIndexing = simulationRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            simulation -> {
              EsSimulation esSimulation = new EsSimulation();
              // Base
              esSimulation.setBase_id(simulation.getAttack_chain_run_id());
              esSimulation.setStatus(simulation.getAttack_chain_run_status());
              esSimulation.setBase_created_at(simulation.getAttack_chain_run_created_at());
              esSimulation.setBase_updated_at(simulation.getAttack_chain_run_injects_updated_at());
              esSimulation.setName(simulation.getAttack_chain_run_name());
              esSimulation.setExecution_date(simulation.getAttack_chain_run_start_date());

              esSimulation.setBase_representative(simulation.getAttack_chain_run_name());
              esSimulation.setBase_restrictions(
                  buildRestrictions(simulation.getAttack_chain_run_id(), simulation.getAttack_chain_id()));
              // Specific
              esSimulation.setBase_platforms_side_denormalized(simulation.getAttack_chain_run_platforms());
              // Dependencies (see base_dependencies in EsBase)
              if (!isEmpty(simulation.getAttack_chain_run_tags())) {
                esSimulation.setBase_tags_side(simulation.getAttack_chain_run_tags());
              } else {
                esSimulation.setBase_tags_side(Set.of());
              }
              if (!isEmpty(simulation.getAttack_chain_run_assets())) {
                esSimulation.setBase_assets_side(simulation.getAttack_chain_run_assets());
              } else {
                esSimulation.setBase_assets_side(Set.of());
              }
              if (!isEmpty(simulation.getAttack_chain_run_asset_groups())) {
                esSimulation.setBase_asset_groups_side(simulation.getAttack_chain_run_asset_groups());
              } else {
                esSimulation.setBase_asset_groups_side(Set.of());
              }
              if (!isEmpty(simulation.getAttack_chain_run_teams())) {
                esSimulation.setBase_teams_side(simulation.getAttack_chain_run_teams());
              } else {
                esSimulation.setBase_teams_side(Set.of());
              }
              if (hasText(simulation.getAttack_chain_id())) {
                esSimulation.setBase_attack_chain_side(simulation.getAttack_chain_id());
              } else {
                esSimulation.setBase_attack_chain_side(null);
              }
              return esSimulation;
            })
        .toList();
  }
}
