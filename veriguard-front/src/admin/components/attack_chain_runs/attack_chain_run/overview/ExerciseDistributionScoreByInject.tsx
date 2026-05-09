import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainRun, type AttackChainNode, type AttackChainNodeExpectation } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';

interface Props { exerciseId: AttackChainRun['attack_chain_run_id'] }

const AttackChainRunDistributionScoreByAttackChainNode: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectsMap, injectExpectations } = useHelper((helper: AttackChainNodeHelper) => ({
    injectsMap: helper.getAttackChainNodesMap(),
    injectExpectations: helper.getAttackChainRunAttackChainNodeExpectations(exerciseId),
  }));

  const injectsTotalScores = R.pipe(
    R.filter((n: AttackChainNodeExpectation) => !R.isEmpty(n.node_expectation_results)),
    R.groupBy(R.prop('node_expectation_node')),
    R.toPairs,
    R.map((n: [string, AttackChainNodeExpectation[]]) => ({
      ...injectsMap[n[0]],
      node_total_score: R.sum(R.map((o: AttackChainNodeExpectation) => o.node_expectation_score, n[1])),
    })),
  )(injectExpectations);

  const sortedAttackChainNodesByTotalScore = R.pipe(
    R.sortWith([R.descend(R.prop('node_total_score'))]),
    R.filter((n: AttackChainNodeExpectation & { node_total_score: number }) => n.node_total_score > 0),
    R.take(10),
  )(injectsTotalScores);

  const totalScoreByAttackChainNodeData = [
    {
      name: t('Total score'),
      data: sortedAttackChainNodesByTotalScore.map((i: AttackChainNode & { node_total_score: number }) => ({
        x: i.node_title,
        y: i.node_total_score,
      })),
    },
  ];

  return (
    <>
      {sortedAttackChainNodesByTotalScore.length > 0 ? (
        <Chart
          id="attack_chain_run_distribution_total_score_by_node"
          options={horizontalBarsChartOptions({
            theme,
            distributed: true,
          })}
          series={totalScoreByAttackChainNodeData}
          type="bar"
          width="100%"
          height={50 + sortedAttackChainNodesByTotalScore.length * 50}
        />
      ) : (
        <Empty
          id="attack_chain_run_distribution_total_score_by_node"
          message={t(
            'No data to display or the attack_chain_run has not started yet',
          )}
        />
      )}
    </>
  );
};
export default AttackChainRunDistributionScoreByAttackChainNode;
