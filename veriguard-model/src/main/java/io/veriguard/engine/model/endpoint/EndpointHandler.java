package io.veriguard.engine.model.endpoint;

import static io.veriguard.engine.EsUtils.buildRestrictions;

import io.veriguard.database.raw.RawEndpoint;
import io.veriguard.database.repository.EndpointRepository;
import io.veriguard.engine.Handler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EndpointHandler implements Handler<EsEndpoint> {

  private EndpointRepository endpointRepository;

  @Autowired
  public void setEndpointRepository(EndpointRepository endpointRepository) {
    this.endpointRepository = endpointRepository;
  }

  @Override
  public List<EsEndpoint> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawEndpoint> forIndexing = endpointRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            endpoint -> {
              EsEndpoint esEndpoint = new EsEndpoint();
              // Base
              esEndpoint.setBase_id(endpoint.getAsset_id());
              esEndpoint.setBase_representative(endpoint.getAsset_name());
              esEndpoint.setBase_created_at(endpoint.getAsset_created_at());
              esEndpoint.setBase_updated_at(endpoint.getEndpoint_updated_at());
              // not sure what to put here, if anything
              esEndpoint.setBase_restrictions(buildRestrictions(endpoint.getAsset_id()));

              esEndpoint.setEndpoint_name(endpoint.getAsset_name());
              esEndpoint.setEndpoint_description(endpoint.getAsset_description());
              esEndpoint.setEndpoint_external_reference(endpoint.getAsset_external_reference());
              esEndpoint.setEndpoint_ips(endpoint.getEndpoint_ips());
              esEndpoint.setEndpoint_hostname(endpoint.getEndpoint_hostname());
              esEndpoint.setEndpoint_platform(endpoint.getEndpoint_platform());
              esEndpoint.setEndpoint_arch(endpoint.getEndpoint_arch());
              esEndpoint.setEndpoint_mac_addresses(endpoint.getEndpoint_mac_addresses());
              esEndpoint.setEndpoint_seen_ip(endpoint.getEndpoint_seen_ip());
              esEndpoint.setEndpoint_is_eol(endpoint.getEndpoint_is_eol());
              // Dependencies (see base_dependencies in EsBase)
              if (endpoint.getAsset_findings() != null && !endpoint.getAsset_findings().isEmpty()) {
                esEndpoint.setBase_findings_side(endpoint.getAsset_findings());
              } else {
                esEndpoint.setBase_findings_side(Set.of());
              }
              if (endpoint.getAsset_tags() != null && !endpoint.getAsset_tags().isEmpty()) {
                esEndpoint.setBase_tags_side(endpoint.getAsset_tags());
              } else {
                esEndpoint.setBase_tags_side(Set.of());
              }
              if (endpoint.getEndpoint_attackChainRuns() != null
                  && !endpoint.getEndpoint_attackChainRuns().isEmpty()) {
                esEndpoint.setBase_attack_chain_run_side(endpoint.getEndpoint_attackChainRuns());
              } else {
                esEndpoint.setBase_attack_chain_run_side(Set.of());
              }
              if (endpoint.getEndpoint_attackChains() != null
                  && !endpoint.getEndpoint_attackChains().isEmpty()) {
                esEndpoint.setBase_attack_chain_side(endpoint.getEndpoint_attackChains());
              } else {
                esEndpoint.setBase_attack_chain_side(Set.of());
              }
              return esEndpoint;
            })
        .toList();
  }
}
