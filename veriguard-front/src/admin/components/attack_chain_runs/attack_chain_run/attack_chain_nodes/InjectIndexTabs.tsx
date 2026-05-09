import { Tab, Tabs } from '@mui/material';
import { Link, useLocation } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { BACK_LABEL, BACK_URI } from '../../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../../components/i18n';
import type { AttackChainRun as AttackChainRunType, AttackChainNodeResultOverviewOutput } from '../../../../../utils/api-types';
import { externalContractTypesWithFindings } from '../../../../../utils/injector_contract/InjectorContractUtils';

const useStyles = makeStyles()(theme => ({
  item: {
    height: 30,
    fontSize: 13,
    float: 'left',
    paddingRight: theme.spacing(1),
  },
}));

interface Props {
  injectResultOverview: AttackChainNodeResultOverviewOutput;
  attack_chain_run: AttackChainRunType;
  backlabel?: string | null;
  backuri?: string | null;
}

const AttackChainNodeIndexTabs = ({ injectResultOverview, attack_chain_run, backlabel, backuri }: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const location = useLocation();
  const tabValue = location.pathname;

  const computePath = (path: string) => {
    if (backlabel && backuri) {
      return path + `?${BACK_LABEL}=${backlabel}&${BACK_URI}=${backuri}`;
    }
    return path;
  };

  return (
    <Tabs value={tabValue}>
      <Tab
        component={Link}
        to={computePath(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes/${injectResultOverview.node_id}`)}
        value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes/${injectResultOverview.node_id}`}
        label={t('Overview')}
        className={classes.item}
      />
      <Tab
        component={Link}
        to={computePath(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes/${injectResultOverview.node_id}/detail`)}
        value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes/${injectResultOverview.node_id}/detail`}
        label={t('AttackChainNode Execution details')}
        className={classes.item}
      />
      {injectResultOverview.node_injector_contract?.injector_contract_payload && (
        <Tab
          component={Link}
          to={computePath(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes/${injectResultOverview.node_id}/payload_info`)}
          value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes/${injectResultOverview.node_id}/payload_info`}
          label={t('Payload info')}
          className={classes.item}
        />
      )}
      {(injectResultOverview.node_injector_contract?.injector_contract_payload
        || externalContractTypesWithFindings.includes(injectResultOverview.node_type ?? '')) && (
        <Tab
          component={Link}
          to={computePath(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes/${injectResultOverview.node_id}/findings`)}
          value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes/${injectResultOverview.node_id}/findings`}
          label={t('Findings')}
          className={classes.item}
        />
      )}
      {injectResultOverview.node_injector_contract?.injector_contract_payload && (
        <Tab
          component={Link}
          to={computePath(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes/${injectResultOverview.node_id}/remediations`)}
          value={computePath(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes/${injectResultOverview.node_id}/remediations`)}
          label={t('Remediations')}
          className={classes.item}
        />
      )}
    </Tabs>
  );
};
export default AttackChainNodeIndexTabs;
