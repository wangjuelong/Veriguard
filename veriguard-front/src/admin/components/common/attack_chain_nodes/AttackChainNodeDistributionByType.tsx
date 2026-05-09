import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type AttackChainNodeStore } from '../../../../actions/attack_chain_nodes/AttackChainNode';
import { type AttackChainNodeHelper } from '../../../../actions/attack_chain_nodes/node-helper';
import { fetchAttackChainRunAttackChainNodes } from '../../../../actions/AttackChainNode';
import { type NodeContractHelper } from '../../../../actions/node_contracts/node-contract-helper';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type AttackChainNodeExpectation, type AttackChainRun } from '../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../utils/Charts';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';

interface Props { exerciseId: AttackChainRun['attack_chain_run_id'] }

const AttackChainNodeDistributionByType: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, tPick } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { nodes } = useHelper((helper: AttackChainNodeHelper & NodeContractHelper) => ({ nodes: helper.getAttackChainRunAttackChainNodes(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchAttackChainRunAttackChainNodes(exerciseId));
  });

  const injectsByType = R.pipe(
    R.filter((n: AttackChainNodeStore) => n.node_sent_at !== null),
    R.groupBy(R.prop('node_type')),
    R.toPairs,
    R.map((n: [string, AttackChainNodeExpectation[]]) => ({
      node_type: n[0],
      number: n[1].length,
    })),
    R.sortWith([R.descend(R.prop('number'))]),
  )(nodes);
  const injectsByInjectorContractData = [
    {
      name: t('Number of nodes'),
      data: injectsByType.map((a: AttackChainNodeStore & { number: number }) => ({
        x: tPick(a.node_injector_contract?.injector_contract_labels),
        y: a.number,
        fillColor: a.node_injector_contract?.convertedContent?.config?.[`color_${theme.palette.mode}`],
      })),
    },
  ];

  return (
    <>
      {injectsByType.length > 0 ? (
        <Chart
          options={horizontalBarsChartOptions({ theme })}
          series={injectsByInjectorContractData}
          type="bar"
          width="100%"
          height={50 + injectsByType.length * 50}
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

export default AttackChainNodeDistributionByType;
