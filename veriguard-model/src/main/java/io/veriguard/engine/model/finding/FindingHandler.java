package io.veriguard.engine.model.finding;

import static io.veriguard.engine.EsUtils.buildRestrictions;
import static org.springframework.util.StringUtils.hasText;

import io.veriguard.database.raw.RawFinding;
import io.veriguard.database.repository.FindingRepository;
import io.veriguard.engine.Handler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FindingHandler implements Handler<EsFinding> {

  private FindingRepository findingRepository;

  @Autowired
  public void setFindingRepository(FindingRepository findingRepository) {
    this.findingRepository = findingRepository;
  }

  @Override
  public List<EsFinding> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawFinding> forIndexing = findingRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            finding -> {
              EsFinding esFinding = new EsFinding();
              // Base
              esFinding.setBase_id(finding.getFinding_id());
              esFinding.setBase_representative(finding.getFinding_value());
              esFinding.setBase_created_at(finding.getFinding_created_at());
              esFinding.setBase_updated_at(finding.getFinding_updated_at());
              esFinding.setBase_restrictions(buildRestrictions(finding.getAttack_chain_id()));
              // Specific
              esFinding.setFinding_type(finding.getFinding_type());
              esFinding.setFinding_field(finding.getFinding_field());
              esFinding.setFinding_value(finding.getFinding_value());
              // Dependencies (see base_dependencies in EsBase)
              List<String> dependencies = new ArrayList<>();
              if (hasText(finding.getFinding_inject_id())) {
                dependencies.add(finding.getFinding_inject_id());
                esFinding.setBase_node_side(finding.getFinding_inject_id());
              } else {
                esFinding.setBase_node_side(null);
              }
              if (hasText(finding.getNode_attackChainRun())) {
                dependencies.add(finding.getNode_attackChainRun());
                esFinding.setBase_attack_chain_run_side(finding.getNode_attackChainRun());
              } else {
                esFinding.setBase_attack_chain_run_side(null);
              }
              if (hasText(finding.getAttack_chain_id())) {
                dependencies.add(finding.getAttack_chain_id());
                esFinding.setBase_attack_chain_side(finding.getAttack_chain_id());
              } else {
                esFinding.setBase_attack_chain_side(null);
              }
              if (hasText(finding.getAsset_id())) {
                dependencies.add(finding.getAsset_id());
                esFinding.setBase_endpoint_side(finding.getAsset_id());
              } else {
                esFinding.setBase_endpoint_side(null);
              }
              esFinding.setBase_dependencies(dependencies);
              return esFinding;
            })
        .toList();
  }
}
