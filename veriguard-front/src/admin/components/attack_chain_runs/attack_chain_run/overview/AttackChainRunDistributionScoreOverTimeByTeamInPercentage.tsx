import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainNodeExpectation, type AttackChainRun } from '../../../../../utils/api-types';
import { lineChartOptions } from '../../../../../utils/Charts';
import { computeTeamsColors } from './DistributionUtils';

interface Props { exerciseId: AttackChainRun['attack_chain_run_id'] }

const AttackChainRunDistributionScoreOverTimeByTeamInPercentage: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectExpectations, teams, teamsMap } = useHelper((helper: AttackChainNodeHelper & TeamsHelper) => ({
    injectExpectations: helper.getAttackChainRunAttackChainNodeExpectations(exerciseId),
    teams: helper.getAttackChainRunTeams(exerciseId),
    teamsMap: helper.getTeamsMap(),
  }));
  const teamsTotalScores = R.pipe(
    R.filter((n: AttackChainNodeExpectation) => !R.isEmpty(n.node_expectation_results) && n?.node_expectation_team),
    R.groupBy(R.prop('node_expectation_team')),
    R.toPairs,
    R.map((n: [string, AttackChainNodeExpectation[]]) => ({
      ...teamsMap[n[0]],
      team_total_score: R.sum(
        R.map((o: AttackChainNodeExpectation) => o.node_expectation_score, n[1]),
      ),
    })),
  )(injectExpectations);
  const teamsColors = computeTeamsColors(teams, theme);
  let cumulation = 0;
  const teamsPercentScoresData = R.pipe(
    R.filter((n: AttackChainNodeExpectation) => !R.isEmpty(n.node_expectation_results) && n?.node_expectation_team && n?.node_expectation_user === null),
    R.groupBy(R.prop('node_expectation_team')),
    R.toPairs,
    R.map((n: [string, AttackChainNodeExpectation[]]) => {
      cumulation = 0;
      return [
        n[0],
        R.pipe(
          R.sortWith([R.ascend(R.prop('node_expectation_updated_at'))]),
          R.map((i: AttackChainNodeExpectation) => {
            cumulation += i.node_expectation_score ?? 0;
            return R.assoc(
              'node_expectation_percent_score',
              Math.round(
                (cumulation * 100)
                / (teamsMap[n[0]] && teamsMap[n[0]].team_nodes_expectations_total_expected_score_by_attack_chain_run
                  ? teamsMap[n[0]]
                    .team_nodes_expectations_total_expected_score_by_attack_chain_run[exerciseId]
                  : 1),
              ),
              i,
            );
          }),
        )(n[1]),
      ];
    }),
    R.map((n: [string, Array<AttackChainNodeExpectation & { node_expectation_percent_score: number }>]) => ({
      name: teamsMap[n[0]]?.team_name,
      color: teamsColors[n[0]],
      data: n[1].map(i => ({
        x: i.node_expectation_updated_at,
        y: i.node_expectation_percent_score,
      })),
    })),
  )(injectExpectations);

  return (
    <>
      {teamsTotalScores.length > 0 ? (
        <Chart
          id="attack_chain_run_distribution_score_over_time"
          options={lineChartOptions({
            theme,
            isTimeSeries: true,
            xFormatter: nsdt,
          })}
          series={teamsPercentScoresData}
          type="line"
          width="100%"
          height={350}
        />
      ) : (
        <Empty
          id="attack_chain_run_distribution_score_over_time"
          message={t(
            'No data to display or the attack_chain_run has not started yet',
          )}
        />
      )}
    </>
  );
};

export default AttackChainRunDistributionScoreOverTimeByTeamInPercentage;
