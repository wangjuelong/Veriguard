package io.veriguard.database.repository;

import io.veriguard.database.model.AssetType;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.raw.RawVulnerableEndpoint;
import io.veriguard.utils.Constants;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VulnerableEndpointRepository extends JpaRepository<Endpoint, String> {

  @Query(
      value =
          "WITH agents_per_asset AS ("
              + "SELECT a.asset_id, "
              + "array_agg(ag.agent_id) FILTER ( WHERE ag.agent_id IS NOT NULL ) as agent_ids, "
              + "array_agg(ag.agent_privilege) FILTER ( WHERE ag.agent_id IS NOT NULL ) as agent_privs, "
              + "array_agg(ag.agent_last_seen) FILTER ( WHERE ag.agent_id IS NOT NULL ) as agent_last_seen "
              + "FROM assets a LEFT JOIN agents ag ON a.asset_id = ag.agent_asset "
              + "WHERE a.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "'"
              + "GROUP BY a.asset_id"
              + ")"
              + "SELECT CONCAT(a.asset_id, '_', i.node_attack_chain_run_id) as base_id, "
              + "a.asset_id as vulnerable_endpoint_id, "
              + "i.node_attack_chain_run_id as vulnerable_endpoint_simulation, "
              + "MAX(se.attack_chain_id) as vulnerable_endpoint_scenario, " // MAX here is used to get 1
              // element and not a list
              // because we know that 1
              // attackChainRun is linked to
              // only 1 attackChain
              + "a.endpoint_hostname as vulnerable_endpoint_hostname, "
              + "a.endpoint_platform as vulnerable_endpoint_platform, "
              + "a.endpoint_is_eol as vulnerable_endpoint_eol, "
              + "a.endpoint_arch as vulnerable_endpoint_architecture, "
              + "e.run_created_at as vulnerable_endpoint_created_at, "
              + "CASE WHEN e.run_updated_at > a.asset_updated_at "
              + "  THEN e.run_updated_at ELSE a.asset_updated_at END as vulnerable_endpoint_updated_at, "
              + "array_agg(fa.finding_id) FILTER ( WHERE fa.finding_id IS NOT NULL ) as vulnerable_endpoint_findings, "
              + "array_agg(distinct at.tag_id) FILTER ( WHERE at.tag_id IS NOT NULL ) as vulnerable_endpoint_tags, "
              + "ag.agent_ids as vulnerable_endpoint_agents, "

              // denormalised
              + "array_agg(f.finding_id) FILTER ( WHERE f.finding_id IS NOT NULL AND f.finding_type = 'CVE' ) as vulnerable_endpoint_cves, "
              + "ag.agent_last_seen as vulnerable_endpoint_agents_last_seen, "
              + "ag.agent_privs as vulnerable_endpoint_agents_privileges "
              + "FROM findings f "
              + "JOIN findings_assets fa ON f.finding_id = fa.finding_id "
              + "JOIN assets a ON a.asset_id = fa.asset_id "
              + "LEFT JOIN agents_per_asset ag ON a.asset_id = ag.asset_id "
              + "LEFT JOIN assets_tags at ON a.asset_id = at.asset_id "
              + "JOIN attack_chain_nodes i ON i.node_id = f.finding_inject_id "
              + "JOIN attack_chain_runs e ON i.node_attack_chain_run_id = e.run_id "
              + "LEFT JOIN attack_chains_runs se ON se.run_id = e.run_id "
              + "WHERE (e.run_updated_at > :from OR a.asset_updated_at > :from) "
              + "AND f.finding_type = 'CVE' "
              + "AND a.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' "
              + "GROUP BY a.asset_id, i.node_attack_chain_run_id, e.run_updated_at, e.run_created_at, ag.agent_ids, ag.agent_last_seen, ag.agent_privs "
              + "ORDER BY e.run_updated_at LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawVulnerableEndpoint> findForIndexing(@Param("from") Instant from);
}
