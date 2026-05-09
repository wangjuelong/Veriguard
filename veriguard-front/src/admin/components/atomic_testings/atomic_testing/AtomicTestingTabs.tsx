import { Tab, Tabs } from '@mui/material';
import { Link, useLocation } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import type { AttackChainNodeResultOverviewOutput } from '../../../../utils/api-types';
import { externalContractTypesWithFindings } from '../../../../utils/node_contract/NodeContractUtils';

const useStyles = makeStyles()(theme => ({
  item: {
    height: 30,
    fontSize: 13,
    float: 'left',
    paddingRight: theme.spacing(1),
  },
}));

interface Props { injectResultOverview: AttackChainNodeResultOverviewOutput }

const AtomicTestingTabs = ({ injectResultOverview }: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const location = useLocation();

  let tabValue = location.pathname;
  if (location.pathname.includes(`/admin/atomic_testings/${injectResultOverview.node_id}/detail`)) {
    tabValue = `/admin/atomic_testings/${injectResultOverview.node_id}/detail`;
  }

  return (
    <Tabs value={tabValue}>
      <Tab
        component={Link}
        to={`/admin/atomic_testings/${injectResultOverview.node_id}`}
        value={`/admin/atomic_testings/${injectResultOverview.node_id}`}
        label={t('Overview')}
        className={classes.item}
      />
      <Tab
        component={Link}
        to={`/admin/atomic_testings/${injectResultOverview.node_id}/detail`}
        value={`/admin/atomic_testings/${injectResultOverview.node_id}/detail`}
        label={t('AttackChainNode Execution details')}
        className={classes.item}
      />
      {injectResultOverview.node_injector_contract?.injector_contract_payload && (
        <Tab
          component={Link}
          to={`/admin/atomic_testings/${injectResultOverview.node_id}/payload_info`}
          value={`/admin/atomic_testings/${injectResultOverview.node_id}/payload_info`}
          label={t('Payload info')}
          className={classes.item}
        />
      )}
      {(injectResultOverview.node_injector_contract?.injector_contract_payload
        || externalContractTypesWithFindings.includes(injectResultOverview.node_type ?? '')) && (
        <Tab
          component={Link}
          to={`/admin/atomic_testings/${injectResultOverview.node_id}/findings`}
          value={`/admin/atomic_testings/${injectResultOverview.node_id}/findings`}
          label={t('Findings')}
          className={classes.item}
        />
      )}
      {injectResultOverview.node_injector_contract?.injector_contract_payload && (
        <Tab
          component={Link}
          to={`/admin/atomic_testings/${injectResultOverview.node_id}/remediations`}
          value={`/admin/atomic_testings/${injectResultOverview.node_id}/remediations`}
          label={t('Remediations')}
          className={classes.item}
        />
      )}
    </Tabs>
  );
};
export default AtomicTestingTabs;
