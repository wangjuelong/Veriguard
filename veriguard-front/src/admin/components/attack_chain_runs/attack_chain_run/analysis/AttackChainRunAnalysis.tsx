import { useContext } from 'react';
import { useParams } from 'react-router';

import {
  attackPathsBySimulation, averageBySimulation,
  countBySimulation,
  entitiesBySimulation, fetchCustomDashboardFromSimulation, seriesBySimulation, widgetToEntitiesBySimulation,
} from '../../../../../actions/attack_chain_runs/attack_chain_run-action';
import type { AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { updateAttackChainRun } from '../../../../../actions/AttackChainRun';
import { useHelper } from '../../../../../store';
import {
  type AttackChainRun,
  type CustomDashboard,
  type WidgetToEntitiesInput,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { AbilityContext, Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import type { ParameterOption } from '../../../workspaces/custom_dashboards/CustomDashboardContext';
import CustomDashboardWrapper from '../../../workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from '../../../workspaces/custom_dashboards/NoDashboardComponent';
import SelectDashboardButton from '../../../workspaces/custom_dashboards/SelectDashboardButton';
import { ALL_TIME_TIME_RANGE } from '../../../workspaces/custom_dashboards/widgets/configuration/common/TimeRangeUtils';

const SimulationAnalysis = () => {
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };

  const attack_chain_run = useHelper((helper: AttackChainRunsHelper) => {
    return helper.getAttackChainRun(exerciseId);
  });

  const handleSelectNewDashboard = (dashboardId: string) => {
    dispatch(updateAttackChainRun(attack_chain_run.attack_chain_run_id, {
      ...attack_chain_run,
      attack_chain_run_custom_dashboard: dashboardId,
    }));
  };

  const paramsBuilder = (dashboardParameters: CustomDashboard['custom_dashboard_parameters']) => {
    const params: Record<string, ParameterOption> = {};
    dashboardParameters?.forEach((p) => {
      let value = '';
      let hidden = false;
      if ('simulation' === p.custom_dashboards_parameter_type) {
        value = exerciseId;
        hidden = true;
      } else if ('attackChain' === p.custom_dashboards_parameter_type) {
        value = attack_chain_run.attack_chain_run_attack_chain ?? '';
        hidden = true;
      } else if ('timeRange' === p.custom_dashboards_parameter_type) {
        value = ALL_TIME_TIME_RANGE;
        hidden = true;
      } else if (['startDate', 'endDate'].includes(p.custom_dashboards_parameter_type)) {
        hidden = true;
      } else {
        value = p.custom_dashboards_parameter_id;
      }
      params[p.custom_dashboards_parameter_id] = {
        value,
        hidden,
      };
    });
    return params;
  };

  const configuration = {
    customDashboardId: attack_chain_run?.attack_chain_run_custom_dashboard,
    paramLocalStorageKey: 'custom-dashboard-attack_chain_run-' + exerciseId,
    paramsBuilder,
    parentContextId: exerciseId,
    canChooseDashboard: ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, exerciseId),
    handleSelectNewDashboard,
    fetchCustomDashboard: () => fetchCustomDashboardFromSimulation(exerciseId),
    fetchCount: (widgetId: string, params: Record<string, string | undefined>) => countBySimulation(exerciseId, widgetId, params),
    fetchAverage: (widgetId: string, params: Record<string, string | undefined>) => averageBySimulation(exerciseId, widgetId, params),
    fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => seriesBySimulation(exerciseId, widgetId, params),
    fetchEntitiesRuntime: (widgetId: string, input: WidgetToEntitiesInput) => widgetToEntitiesBySimulation(exerciseId, widgetId, input),
    fetchEntities: (widgetId: string, params: Record<string, string | undefined>) => entitiesBySimulation(exerciseId, widgetId, params),
    fetchAttackPaths: (widgetId: string, params: Record<string, string | undefined>) => attackPathsBySimulation(exerciseId, widgetId, params),
  };

  return (
    <CustomDashboardWrapper
      configuration={configuration}
      noDashboardSlot={(
        <NoDashboardComponent
          actionComponent={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.RESOURCE} field={exerciseId}>
              <SelectDashboardButton variant="text" scenarioOrSimulationId={exerciseId} handleApplyChange={handleSelectNewDashboard} />
            </Can>
          )}
        />
      )}
    />
  );
};

export default SimulationAnalysis;
