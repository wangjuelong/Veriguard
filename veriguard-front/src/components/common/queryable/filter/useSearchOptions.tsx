import { type AxiosResponse } from 'axios';
import { useState } from 'react';

import { searchAssetGroupAsOption, searchAssetGroupLinkedToFindingsAsOption } from '../../../../actions/asset_groups/assetgroup-action';
import { searchEndpointAsOption, searchEndpointLinkedToFindingsAsOption } from '../../../../actions/assets/endpoint-actions';
import { searchSecurityPlatformAsOption } from '../../../../actions/assets/securityPlatform-actions';
import { searchAttackChainNodeLinkedToFindingsAsOption, searchTargetOptions } from '../../../../actions/attack_chain_nodes/node-action';
import { searchAttackChainRunLinkedToFindingsAsOption } from '../../../../actions/attack_chain_runs/attack_chain_run-action';
import { searchSimulationAsOptions } from '../../../../actions/attack_chain_runs/attack_chain_run-search-action';
import { searchAttackChainAsOption, searchAttackChainCategoryAsOption } from '../../../../actions/attack_chains/attack_chain-actions';
import { searchAttackChainSimulationsAsOption } from '../../../../actions/attack_chains/attack_chain-attack_chain_run-action';
import { searchAttackPatternsByNameAsOption } from '../../../../actions/AttackPattern';
import { searchCustomDashboardAsOptions } from '../../../../actions/custom_dashboards/customdashboard-action';
import { searchDomainsByNameAsOption } from '../../../../actions/domains/domain-actions';
import { searchKillChainPhasesByNameAsOption } from '../../../../actions/kill_chain_phases/killChainPhase-action';
import { searchInjectorsByNameAsOption } from '../../../../actions/node_executors/node_executor-action';
import { searchOrganizationsByNameAsOption } from '../../../../actions/organizations/organization-actions';
import { searchTagAsOption } from '../../../../actions/tags/tag-action';
import { searchTeamsAsOption } from '../../../../actions/teams/team-actions';
import ContractOutputElementType, { CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS } from '../../../../admin/components/findings/ContractOutputElementType';
import { type GroupOption, type Option } from '../../../../utils/Option';
import { useFormatter } from '../../../i18n';
import { CUSTOM_DASHBOARD, SCENARIO_SIMULATIONS, SCENARIOS, SIMULATIONS } from './constants';

export interface SearchOptionsConfig {
  filterKey: string;
  contextId?: string;
  defaultValues?: GroupOption[] | undefined;
}

const useSearchOptions = () => {
  // Standard hooks
  const { t } = useFormatter();

  const [options, setOptions] = useState<GroupOption[] | Option[]>([]);

  const handleOptions = (response: AxiosResponse<GroupOption[] | Option[]>, defaultValues: GroupOption[] | undefined) => {
    if (defaultValues && defaultValues.length > 0) {
      setOptions([...defaultValues, ...response.data.map((d: Option) => ({
        ...d,
        group: 'Values',
      }))]);
    } else {
      setOptions(response.data);
    }
  };

  const searchOptions = (config: SearchOptionsConfig, search: string = '') => {
    const { filterKey, contextId = '' } = config;
    switch (filterKey) {
      case SIMULATIONS:
      case 'base_attack_chain_run_side':
        searchSimulationAsOptions(search).then((response) => {
          handleOptions(response, config.defaultValues);
        });
        break;
      case 'injector_contract_injector':
      case 'node_injector_contract':
        searchInjectorsByNameAsOption(search, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'injector_contract_kill_chain_phases':
      case 'attack_chain_kill_chain_phases':
      case 'attack_chain_run_kill_chain_phases':
      case 'node_kill_chain_phases':
        searchKillChainPhasesByNameAsOption(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'payload_attack_patterns':
      case 'base_attack_patterns_side':
      case 'node_attack_patterns':
        searchAttackPatternsByNameAsOption(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'payload_domains':
      case 'injector_contract_domains':
      case 'node_contract_domains':
        searchDomainsByNameAsOption(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'target_asset_groups':
        searchTargetOptions(contextId, 'ASSETS_GROUPS', search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'target_assets':
      case 'target_endpoint':
        searchTargetOptions(contextId, 'ASSETS', search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'target_teams':
        searchTargetOptions(contextId, 'TEAMS').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'asset_tags':
      case 'asset_group_tags':
      case 'attack_chain_run_tags':
      case 'node_tags':
      case 'payload_tags':
      case 'attack_chain_tags':
      case 'target_tags':
      case 'team_tags':
      case 'finding_tags':
      case 'user_tags':
      case 'base_tags_side':
        searchTagAsOption(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_asset_groups':
        searchAssetGroupLinkedToFindingsAsOption(search, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'node_asset_groups':
        searchAssetGroupAsOption(search, contextId, contextId ? 'SIMULATION_OR_SCENARIO' : 'ATOMIC_TESTING').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'base_asset_groups_side':
        searchAssetGroupAsOption(search, contextId, 'ALL_INJECTS').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_assets':
        searchEndpointLinkedToFindingsAsOption(search, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'node_assets':
      case 'base_endpoint_side':
        searchEndpointAsOption(search, contextId, contextId ? 'SIMULATION_OR_SCENARIO' : 'ATOMIC_TESTING').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'base_assets_side':
        searchEndpointAsOption(search, contextId, 'ALL_INJECTS').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'node_teams':
        searchTeamsAsOption(search, contextId, contextId ? 'SIMULATION_OR_SCENARIO' : 'ATOMIC_TESTING').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'base_teams_side':
        searchTeamsAsOption(search, contextId, 'ALL_INJECTS').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_node_id':
        searchAttackChainNodeLinkedToFindingsAsOption(search, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_type': {
        const typeOptions = CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS
          .filter(type => !search || t(ContractOutputElementType[type]).toLowerCase().includes(search.toLowerCase()))
          .map(type => ({
            id: type,
            label: ContractOutputElementType[type],
          }))
          .sort((a, b) => t(a.label).localeCompare(t(b.label)));
        setOptions(typeOptions);
        break;
      }
      case 'finding_attack_chain_run':
        searchAttackChainRunLinkedToFindingsAsOption(search, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_attack_chain':
      case 'attack_chain_run_attack_chain':
      case 'base_attack_chain_side':
      case SCENARIOS:
        searchAttackChainAsOption(search).then((response) => {
          handleOptions(response, config.defaultValues);
        });
        break;
      case 'attack_chain_category':
        searchAttackChainCategoryAsOption(search).then((response: { data: Option[] }) => {
          setOptions(response.data.map(d => ({
            id: d.id,
            label: t(d.label),
          })));
        });
        break;
      case 'user_organization':
        searchOrganizationsByNameAsOption(search).then((response: { data: Option[] }) => {
          setOptions(response.data.map(d => ({
            id: d.id,
            label: t(d.label),
          })));
        });
        break;
      case CUSTOM_DASHBOARD:
        searchCustomDashboardAsOptions(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'base_security_platforms_side':
        searchSecurityPlatformAsOption(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case SCENARIO_SIMULATIONS:
        searchAttackChainSimulationsAsOption(contextId, search).then((response) => {
          setOptions(response.data);
        });
        break;
      default:
    }
  };

  return {
    options,
    setOptions,
    searchOptions,
  };
};

export default useSearchOptions;
