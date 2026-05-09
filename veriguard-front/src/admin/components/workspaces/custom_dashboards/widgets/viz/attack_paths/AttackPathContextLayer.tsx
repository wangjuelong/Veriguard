import { Typography } from '@mui/material';
import { useContext, useState } from 'react';

import { type AttackChainRunsHelper } from '../../../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { fetchAttackChainRun } from '../../../../../../../actions/AttackChainRun';
import { useFormatter } from '../../../../../../../components/i18n';
import Loader from '../../../../../../../components/Loader';
import { useHelper } from '../../../../../../../store';
import type { EsAttackPath, StructuralHistogramWidget } from '../../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../../utils/hooks';
import useDataLoader from '../../../../../../../utils/hooks/useDataLoader';
import { CustomDashboardContext } from '../../../CustomDashboardContext';
import AttackPath from './AttackPath';

interface Props {
  attackPathsData: EsAttackPath[];
  widgetId: string;
  widgetConfig: StructuralHistogramWidget;
}

const AttackPathContextLayer = ({ attackPathsData, widgetId, widgetConfig }: Props) => {
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState<boolean>(false);
  const { t } = useFormatter();

  const { customDashboard, customDashboardParameters } = useContext(CustomDashboardContext);

  const simulationParamIdFromSerie = (((widgetConfig.series[0] || []).filter?.filters || []).find(f => f.key == 'base_attack_chain_run_side')?.values ?? [])[0];
  const dashboardParameterId = customDashboard?.custom_dashboard_parameters?.find(p => p.custom_dashboards_parameter_type === 'simulation' && p.custom_dashboards_parameter_id === simulationParamIdFromSerie)?.custom_dashboards_parameter_id;
  const simulationIdContext = dashboardParameterId == simulationParamIdFromSerie ? customDashboardParameters[dashboardParameterId].value : simulationParamIdFromSerie;

  const { attack_chain_run } = useHelper((helper: AttackChainRunsHelper) => ({ attack_chain_run: helper.getAttackChainRun(simulationIdContext) }));

  useDataLoader(() => {
    if (!simulationIdContext) {
      return;
    }
    setLoading(true);
    dispatch(fetchAttackChainRun(simulationIdContext)).finally(() => {
      setLoading(false);
    });
  }, []);

  if (!simulationIdContext) {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '80%',
      }}
      >
        <Typography variant="h5" sx={{ textAlign: 'center' }}>
          {t('You must set a attack_chain_run')}
        </Typography>

      </div>
    );
  }

  if (loading || !attack_chain_run) {
    return <Loader />;
  }

  return (
    <AttackPath
      data={attackPathsData}
      widgetId={widgetId}
      simulationId={simulationIdContext}
      simulationStartDate={attack_chain_run?.attack_chain_run_start_date}
      simulationEndDate={attack_chain_run?.attack_chain_run_end_date}
    />
  );
};

export default AttackPathContextLayer;
