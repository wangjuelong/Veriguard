import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import { fetchAttackChainRunAttackChainNodes } from '../../../../../actions/AttackChainNode';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainNode, type AttackChainRun } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';

interface Props { exerciseId: AttackChainRun['attack_chain_run_id'] }

const MailDistributionByAttackChainNode: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { nodes } = useHelper((helper: AttackChainNodeHelper) => ({ nodes: helper.getAttackChainAttackChainNodes(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchAttackChainRunAttackChainNodes(exerciseId));
  });

  const sortedAttackChainNodesByCommunicationNumber = R.pipe(
    R.sortWith([R.descend(R.prop('node_communications_number'))]),
    R.take(10),
  )(nodes || []);
  const totalMailsByAttackChainNodeData = [
    {
      name: t('Total mails'),
      data: sortedAttackChainNodesByCommunicationNumber.map((i: AttackChainNode) => ({
        x: i.node_title,
        y: i.node_communications_number,
      })),
    },
  ];

  return (
    <>
      {sortedAttackChainNodesByCommunicationNumber.length > 0 ? (
        <Chart
          options={horizontalBarsChartOptions({ theme })}
          series={totalMailsByAttackChainNodeData}
          type="bar"
          width="100%"
          height={50 + sortedAttackChainNodesByCommunicationNumber.length * 50}
        />
      ) : (
        <Empty
          message={t(
            'No data to display or the attack_chain_run has not started yet',
          )}
        />
      )}
    </>
  );
};

export default MailDistributionByAttackChainNode;
