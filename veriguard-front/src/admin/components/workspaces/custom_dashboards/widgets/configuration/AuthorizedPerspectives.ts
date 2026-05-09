const getAuthorizedPerspectives = (): Map<string, string[]> => new Map([
  ['expectation-node', ['base_created_at', 'node_expectation_status', 'node_expectation_type', 'base_updated_at', 'base_attack_chain_run_side', 'base_attack_chain_side', 'base_asset_side', 'base_asset_group_side', 'base_security_platforms_side']],
  ['finding', ['base_created_at', 'finding_type', 'base_updated_at', 'base_endpoint_side', 'base_attack_chain_run_side', 'base_attack_chain_side']],
  ['endpoint', ['endpoint_arch', 'endpoint_platform', 'endpoint_ips', 'endpoint_hostname', 'base_attack_chain_run_side', 'base_attack_chain_side']],
  ['vulnerable-endpoint', ['vulnerable_endpoint_architecture', 'vulnerable_endpoint_agents_active_status', 'vulnerable_endpoint_agents_privileges', 'vulnerable_endpoint_platform', 'base_attack_chain_run_side', 'base_attack_chain_side']],
  ['node', ['node_status', 'base_tags_side', 'base_assets_side', 'base_asset_groups_side', 'base_teams_side', 'base_platforms_side_denormalized', 'base_attack_chain_run_side', 'base_attack_chain_side']],
  ['attack_chain_run', ['status', 'base_tags_side', 'base_assets_side', 'base_asset_groups_side', 'base_teams_side', 'base_platforms_side_denormalized', 'base_attack_chain_side']],
  ['attack_chain', ['status', 'base_tags_side', 'base_assets_side', 'base_asset_groups_side', 'base_teams_side', 'base_platforms_side_denormalized']],
]);

export default getAuthorizedPerspectives;
