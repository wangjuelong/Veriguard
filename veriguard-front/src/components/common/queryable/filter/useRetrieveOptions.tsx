import type { AxiosResponse } from 'axios';
import { useContext, useState } from 'react';

import { searchAssetGroupByIdAsOption } from '../../../../actions/asset_groups/assetgroup-action';
import { searchEndpointByIdAsOption } from '../../../../actions/assets/endpoint-actions';
import { searchSecurityPlatformByIdAsOption } from '../../../../actions/assets/securityPlatform-actions';
import { searchAttackChainNodeByIdAsOption, searchTargetOptionsById } from '../../../../actions/attack_chain_nodes/node-action';
import { searchAttackChainRunByIdAsOption } from '../../../../actions/attack_chain_runs/attack_chain_run-action';
import { searchSimulationByIdAsOptions } from '../../../../actions/attack_chain_runs/attack_chain_run-action';
import { searchAttackChainByIdAsOption } from '../../../../actions/attack_chains/attack_chain-actions';
import { searchAttackPatternsByIdAsOption } from '../../../../actions/AttackPattern';
import { searchCustomDashboardByIdAsOptions } from '../../../../actions/custom_dashboards/customdashboard-action';
import { searchDomainsByIdsAsOption } from '../../../../actions/domains/domain-actions';
import { searchInjectorByIdAsOptions } from '../../../../actions/injectors/injector-action';
import { searchKillChainPhasesByIdAsOption } from '../../../../actions/kill_chain_phases/killChainPhase-action';
import { searchOrganizationByIdAsOptions } from '../../../../actions/organizations/organization-actions';
import { searchTagByIdAsOption } from '../../../../actions/tags/tag-action';
import { searchTeamByIdAsOption } from '../../../../actions/teams/team-actions';
import ContractOutputElementType from '../../../../admin/components/findings/ContractOutputElementType';
import { type GroupOption, type Option } from '../../../../utils/Option';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { CUSTOM_DASHBOARD, SCENARIOS, SIMULATIONS } from './constants';

interface RetrieveOptionsConfig {
  defaultValues?: GroupOption[] | undefined;
  contextId?: string;
  filterKey: string;
}

const useRetrieveOptions = () => {
  const [options, setOptions] = useState<Option[]>([]);
  const ability = useContext(AbilityContext);

  const handleOptions = (response: AxiosResponse<GroupOption[] | Option[]>, filterDefaultValues: GroupOption[]) => {
    if (filterDefaultValues && filterDefaultValues.length > 0) {
      setOptions([...filterDefaultValues, ...response.data.map((d: Option) => ({
        ...d,
        group: 'Values',
      }))]);
    } else {
      setOptions(response.data);
    }
  };

  const searchOptions = (ids: string[], config: RetrieveOptionsConfig) => {
    const { filterKey, contextId = '' } = config;
    const filterDefaultValues = (config.defaultValues ?? []).filter(v => ids.includes(v.id));
    switch (filterKey) {
      case SIMULATIONS:
      case 'base_attack_chain_run_side':
        searchSimulationByIdAsOptions(ids).then((response) => {
          handleOptions(response, filterDefaultValues);
        });
        break;
      case 'injector_contract_injector':
      case 'node_injector_contract':
        searchInjectorByIdAsOptions(ids, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'injector_contract_kill_chain_phases':
      case 'attack_chain_kill_chain_phases':
      case 'attack_chain_run_kill_chain_phases':
      case 'node_kill_chain_phases':
        searchKillChainPhasesByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'payload_attack_patterns':
      case 'base_attack_patterns_side':
      case 'node_attack_patterns':
        searchAttackPatternsByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'payload_domains':
      case 'injector_contract_domains':
      case 'node_contract_domains':
        searchDomainsByIdsAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'target_asset_groups':
        // TODO allow to fetch for a specific resource if no capa issue/3864
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
          searchTargetOptionsById('ASSETS_GROUPS', ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
      case 'target_assets':
      case 'target_endpoint':
      case 'base_endpoint_side':
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
          searchTargetOptionsById('ASSETS', ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
      case 'target_teams':
        searchTargetOptionsById('TEAMS', ids).then((response) => {
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
        searchTagByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_asset_groups':
      case 'node_asset_groups':
      case 'base_asset_groups_side':
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
          searchAssetGroupByIdAsOption(ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
      case 'finding_assets':
      case 'node_assets':
      case 'base_assets_side':
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
          searchEndpointByIdAsOption(ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
      case 'node_teams':
      case 'base_teams_side':
        searchTeamByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_node_id':
        searchAttackChainNodeByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_type':
        setOptions(ids.map(id => ({
          id,
          label: ContractOutputElementType[id as keyof typeof ContractOutputElementType] ?? id,
        })));
        break;
      case 'finding_attack_chain_run':
        searchAttackChainRunByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_attack_chain' :
      case 'attack_chain_run_attack_chain':
      case 'base_attack_chain_side':
      case SCENARIOS:
        searchAttackChainByIdAsOption(ids).then((response) => {
          handleOptions(response, filterDefaultValues);
        });
        break;
      case 'user_organization':
        searchOrganizationByIdAsOptions(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case CUSTOM_DASHBOARD:
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.DASHBOARDS)) {
          searchCustomDashboardByIdAsOptions(ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
      case 'base_security_platforms_side':
        searchSecurityPlatformByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      default:
        setOptions(ids.map(id => ({
          id,
          label: id,
        })));
        break;
    }
  };

  return {
    options,
    searchOptions,
  };
};

export default useRetrieveOptions;
