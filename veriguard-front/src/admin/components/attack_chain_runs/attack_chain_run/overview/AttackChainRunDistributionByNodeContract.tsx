import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type AttackChainNodeStore } from '../../../../../actions/attack_chain_nodes/AttackChainNode';
import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainNodeExpectation, type AttackChainRun } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';

interface Props { exerciseId: AttackChainRun['attack_chain_run_id'] }

const AttackChainRunDistributionByInjectorContract: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, tPick } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectsMap, injectExpectations }: {
    injectsMap: Record<string, AttackChainNodeStore>;
    injectExpectations: AttackChainNodeExpectation[];
  } = useHelper((helper: AttackChainNodeHelper) => ({
    injectsMap: helper.getAttackChainNodesMap(),
    injectExpectations: helper.getAttackChainRunAttackChainNodeExpectations(exerciseId),
  }));

  const sortedInjectorContractsByTotalScore = R.pipe(
    R.filter((n: AttackChainNodeExpectation) => !R.isEmpty(n.node_expectation_results)),
    R.map((n: AttackChainNodeExpectation) => R.assoc(
      'node_expectation_node',
      injectsMap[n.node_expectation_node ?? ''] || {},
      n,
    )),
    R.groupBy(R.path(['node_expectation_node', 'node_type'])),
    R.toPairs,
    R.map((n: [string, AttackChainNodeExpectation[]]) => ({
      node_type: n[0],
      node_total_score: R.sum(R.map((o: AttackChainNodeExpectation) => o.node_expectation_score ?? 0, n[1])),
    })),
    R.sortWith([R.descend(R.prop('node_total_score'))]),
    R.take(10),
  )(injectExpectations);

  const totalScoreByInjectorContractData = [
    {
      name: t('Total score'),
      data: sortedInjectorContractsByTotalScore.map((i: AttackChainNodeStore & { node_total_score: number }) => ({
        x: tPick(i.node_injector_contract?.injector_contract_labels),
        y: i.node_total_score,
        fillColor: i.node_injector_contract?.convertedContent?.config?.[`color_${theme.palette.mode}`],
      })),
    },
  ];

  return (
    <>
      {sortedInjectorContractsByTotalScore.length > 0 ? (
        <Chart
          id="attack_chain_run_distribution_total_score_by_node_type"
          options={horizontalBarsChartOptions({
            theme,
            distributed: true,
          })}
          series={totalScoreByInjectorContractData}
          type="bar"
          width="100%"
          height={50 + sortedInjectorContractsByTotalScore.length * 50}
        />
      ) : (
        <Empty
          id="attack_chain_run_distribution_total_score_by_node_type"
          message={t(
            'No data to display or the attack_chain_run has not started yet',
          )}
        />
      )}
    </>
  );
};

export default AttackChainRunDistributionByInjectorContract;
