import { useContext } from 'react';
import { useParams } from 'react-router';

import {
  attackPathsByAttackChain, averageByAttackChain,
  countByAttackChain,
  entitiesByAttackChain,
  fetchCustomDashboardFromAttackChain, searchAttackChainAttackChainRuns,
  seriesByAttackChain,
  updateAttackChain, widgetToEntitiesByByAttackChain,
} from '../../../../../actions/attack_chains/attack_chain-actions';
import type { AttackChainsHelper } from '../../../../../actions/attack_chains/attack_chain-helper';
import { SCENARIO_SIMULATIONS } from '../../../../../components/common/queryable/filter/constants';
import { useHelper } from '../../../../../store';
import {
  type CustomDashboard,
  type AttackChain,
  type SortField,
  type WidgetToEntitiesInput,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { AbilityContext, Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import { type ParameterOption } from '../../../workspaces/custom_dashboards/CustomDashboardContext';
import CustomDashboardWrapper from '../../../workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from '../../../workspaces/custom_dashboards/NoDashboardComponent';
import SelectDashboardButton from '../../../workspaces/custom_dashboards/SelectDashboardButton';

const AttackChainAnalysis = () => {
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { scenarioId } = useParams() as { scenarioId: AttackChain['attack_chain_id'] };

  const attack_chain = useHelper((helper: AttackChainsHelper) => {
    return helper.getAttackChain(scenarioId);
  });
  const handleSelectNewDashboard = (dashboardId: string) => {
    dispatch(updateAttackChain(attack_chain.attack_chain_id, {
      ...attack_chain,
      attack_chain_custom_dashboard: dashboardId,
    }));
  };

  const lastSimulationEndedId = async () => {
    const { data } = await searchAttackChainAttackChainRuns(scenarioId, {
      size: 1,
      page: 0,
      sorts: [
        {
          property: 'attack_chain_run_end_date',
          direction: 'DESC',
          nullHandling: 'NULLS_LAST' as SortField['nullHandling'],
        },
        {
          property: 'attack_chain_run_updated_at',
          direction: 'DESC',
        },
      ],
    });
    return data.content?.[0]?.attack_chain_run_id;
  };

  const paramsBuilder = async (dashboardParameters: CustomDashboard['custom_dashboard_parameters'], localStorageParams: Record<string, ParameterOption>) => {
    const paramsList = await Promise.all(
      (dashboardParameters || []).map(async (p) => {
        const paramId = p.custom_dashboards_parameter_id;
        let paramOptions;
        const value = localStorageParams[paramId]?.value;
        if ('attack_chain' === p.custom_dashboards_parameter_type) {
          paramOptions = {
            value: attack_chain.attack_chain_id,
            hidden: true,
          };
        } else if ('attack_chain_run' === p.custom_dashboards_parameter_type) {
          const valueToSet = value == undefined ? await lastSimulationEndedId() : value;
          paramOptions = {
            value: valueToSet,
            hidden: false,
            searchOptionsConfig: {
              filterKey: SCENARIO_SIMULATIONS,
              contextId: scenarioId,
            },
          };
        } else {
          paramOptions = {
            value: value,
            hidden: false,
          };
        }
        return [paramId, paramOptions];
      }));

    return Object.fromEntries(paramsList);
  };

  const configuration = {
    customDashboardId: attack_chain?.attack_chain_custom_dashboard,
    paramLocalStorageKey: 'custom-dashboard-attack_chain-' + scenarioId,
    paramsBuilder,
    parentContextId: scenarioId,
    canChooseDashboard: ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, scenarioId),
    handleSelectNewDashboard,
    fetchCustomDashboard: () => fetchCustomDashboardFromAttackChain(scenarioId),
    fetchCount: (widgetId: string, params: Record<string, string | undefined>) => countByAttackChain(scenarioId, widgetId, params),
    fetchAverage: (widgetId: string, params: Record<string, string | undefined>) => averageByAttackChain(scenarioId, widgetId, params),
    fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => seriesByAttackChain(scenarioId, widgetId, params),
    fetchEntities: (widgetId: string, params: Record<string, string | undefined>) => entitiesByAttackChain(scenarioId, widgetId, params),
    fetchEntitiesRuntime: (widgetId: string, input: WidgetToEntitiesInput) => widgetToEntitiesByByAttackChain(scenarioId, widgetId, input),
    fetchAttackPaths: (widgetId: string, params: Record<string, string | undefined>) => attackPathsByAttackChain(scenarioId, widgetId, params),
  };

  return (
    <CustomDashboardWrapper
      configuration={configuration}
      noDashboardSlot={(
        <NoDashboardComponent
          actionComponent={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.RESOURCE} field={scenarioId}>
              <SelectDashboardButton
                variant="text"
                handleApplyChange={handleSelectNewDashboard}
                scenarioOrSimulationId={scenarioId}
              />
            </Can>
          )}
        />
      )}
    />
  );
};

export default AttackChainAnalysis;
