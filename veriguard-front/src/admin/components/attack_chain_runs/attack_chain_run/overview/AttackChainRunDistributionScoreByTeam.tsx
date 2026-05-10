import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainNodeExpectation, type AttackChainRun, type Team } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { computeTeamsColors } from './DistributionUtils';

interface Props { exerciseId: AttackChainRun['attack_chain_run_id'] }

const AttackChainRunDistributionScoreByTeam: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
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

  const sortedTeamsByTotalScore = R.pipe(
    R.sortWith([R.descend(R.prop('team_total_score'))]),
    R.take(10),
  )(teamsTotalScores);

  const teamsColors = computeTeamsColors(teams, theme);
  const totalScoreByTeamData = [
    {
      name: t('Total score'),
      data: sortedTeamsByTotalScore.map((a: Team & { team_total_score: number }) => ({
        x: a.team_name,
        y: a.team_total_score,
        fillColor: teamsColors[a.team_id],
      })),
    },
  ];

  return (
    <>
      {teamsTotalScores.length > 0 ? (
        <Chart
          id="attack_chain_run_distribution_total_score_by_team"
          options={horizontalBarsChartOptions({ theme })}
          series={totalScoreByTeamData}
          type="bar"
          width="100%"
          height={50 + teamsTotalScores.length * 50}
        />
      ) : (
        <Empty
          id="attack_chain_run_distribution_total_score_by_team"
          message={t(
            'No data to display or the attack_chain_run has not started yet',
          )}
        />
      )}
    </>
  );
};

export default AttackChainRunDistributionScoreByTeam;
