import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type AttackChainNodeStore } from '../../../../../actions/attack_chain_nodes/AttackChainNode';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { type AttackChainNode } from '../../../../../utils/api-types';
import { lineChartOptions } from '../../../../../utils/Charts';

interface Props { nodes: AttackChainNode[] }

const AttackChainNodeOverTimeLine: FunctionComponent<Props> = ({ nodes }) => {
  // Standard hooks
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  let cumulation = 0;
  const injectsOverTime = R.pipe(
    R.filter((i: AttackChainNodeStore) => i && i.node_sent_at !== null),
    R.sortWith([R.ascend(R.prop('node_sent_at'))]),
    R.map((i: AttackChainNodeStore) => {
      cumulation += 1;
      return R.assoc('node_cumulated_number', cumulation, i);
    }),
  )(nodes);
  const injectsData = [
    {
      name: t('Number of nodes'),
      data: injectsOverTime.map((i: AttackChainNodeStore & { node_cumulated_number: number }) => ({
        x: i.node_sent_at,
        y: i.node_cumulated_number,
      })),
    },
  ];

  return (
    <>
      {injectsOverTime.length > 0 ? (
        <Chart
          options={lineChartOptions({
            theme,
            isTimeSeries: true,
            xFormatter: nsdt,
          })}
          series={injectsData}
          type="line"
          width="100%"
          height={350}
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

export default AttackChainNodeOverTimeLine;
