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
import { lineChartOptions } from '../../../../../utils/Charts';

interface Props { exerciseId: AttackChainRun['attack_chain_run_id'] }

const AttackChainRunDistributionScoreOverTimeByInjectorContract: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, nsdt, tPick } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectsMap, injectExpectations }: {
    injectsMap: Record<string, AttackChainNodeStore>;
    injectExpectations: AttackChainNodeExpectation[];
  } = useHelper((helper: AttackChainNodeHelper) => ({
    injectsMap: helper.getAttackChainNodesMap(),
    injectExpectations: helper.getAttackChainRunAttackChainNodeExpectations(exerciseId),
  }));

  let cumulation = 0;
  const injectsTypesScores = R.pipe(
    R.filter((n: AttackChainNodeExpectation) => !R.isEmpty(n.node_expectation_results) && n?.node_expectation_team && n?.node_expectation_user === null),
    R.map((n: AttackChainNodeExpectation & { node_expectation_node: string }) => R.assoc(
      'node_expectation_node',
      injectsMap[n.node_expectation_node] || {},
      n,
    )),
    R.groupBy(R.path(['node_expectation_node', 'node_contract'])),
    R.toPairs,
    R.map((n: [string, AttackChainNodeExpectation[]]) => {
      cumulation = 0;
      return [
        n[0],
        R.pipe(
          R.sortWith([R.ascend(R.prop('node_expectation_updated_at'))]),
          R.map((i: AttackChainNodeExpectation) => {
            cumulation += i.node_expectation_score ?? 0;
            return R.assoc('node_expectation_cumulated_score', cumulation, i);
          }),
        )(n[1]),
      ];
    }),
    R.map((n: [string, Array<AttackChainNodeExpectation & {
      node_expectation_cumulated_score: number;
      node_expectation_node: AttackChainNodeStore;
    }>]) => ({
      name: tPick(n[1][0].node_expectation_node.node_injector_contract?.injector_contract_labels),
      color: n[1][0].node_expectation_node.node_injector_contract?.convertedContent?.config?.[`color_${theme.palette.mode}`],
      data: n[1].map((i: AttackChainNodeExpectation & { node_expectation_cumulated_score: number }) => ({
        x: i.node_expectation_updated_at,
        y: i.node_expectation_cumulated_score,
      })),
    })),
  )(injectExpectations);

  return (
    <>
      {injectsTypesScores.length > 0 ? (
        <Chart
          id="attack_chain_run_distribution_score_over_time_by_node"
          options={lineChartOptions({
            theme,
            isTimeSeries: true,
            xFormatter: nsdt,
          })}
          series={injectsTypesScores}
          type="line"
          width="100%"
          height={350}
        />
      ) : (
        <Empty
          id="attack_chain_run_distribution_score_over_time_by_node"
          message={t(
            'No data to display or the attack_chain_run has not started yet',
          )}
        />
      )}
    </>
  );
};

export default AttackChainRunDistributionScoreOverTimeByInjectorContract;
