import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useSearchParams } from 'react-router';

import Breadcrumbs, { type BreadcrumbsElement } from '../../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../../components/i18n';
import type { AttackChainNodeResultOverviewOutput, AttackChainRun as AttackChainRunType } from '../../../../../utils/api-types';
import AtomicTestingTitle from '../../../atomic_testings/atomic_testing/AtomicTestingTitle';
import ResponsePie from '../../../common/attack_chain_nodes/ResponsePie';
import AttackChainNodeIndexTabs from './AttackChainNodeIndexTabs';

interface Props {
  injectResultOverview: AttackChainNodeResultOverviewOutput;
  attack_chain_run: AttackChainRunType;
}

const AttackChainNodeIndexHeader = ({ injectResultOverview, attack_chain_run }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [searchParams] = useSearchParams();
  const backlabel = searchParams.get('backlabel');
  const backuri = searchParams.get('backuri');

  const breadcrumbs: BreadcrumbsElement[] = [
    {
      label: t('Simulations'),
      link: '/admin/attack_chain_runs',
    },
    {
      label: t(attack_chain_run.attack_chain_run_name),
      link: `/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}`,
    },
  ];

  if (backlabel && backuri) {
    breadcrumbs.push({
      label: backlabel,
      link: backuri,
    });
  }
  breadcrumbs.push({ label: t('AttackChainNodes') });
  breadcrumbs.push({
    label: injectResultOverview.node_title,
    current: true,
  });

  return (
    <Box
      sx={{
        borderBottom: 1,
        borderColor: 'divider',
        marginBottom: 2,
      }}
    >
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr auto',
        gap: theme.spacing(2),
        alignItems: 'start',
      }}
      >
        <Box display="flex" flexDirection="column" justifyContent="left" alignItems="flex-start">
          <Breadcrumbs variant="object" elements={breadcrumbs} />
          <AtomicTestingTitle injectResultOverview={injectResultOverview} />
          <AttackChainNodeIndexTabs injectResultOverview={injectResultOverview} attack_chain_run={attack_chain_run} backlabel={backlabel} backuri={backuri} />
        </Box>
        <ResponsePie hasTitles={false} forceSize={112} expectationResultsByTypes={injectResultOverview.node_expectation_results} />
      </div>
    </Box>
  );
};

export default AttackChainNodeIndexHeader;
