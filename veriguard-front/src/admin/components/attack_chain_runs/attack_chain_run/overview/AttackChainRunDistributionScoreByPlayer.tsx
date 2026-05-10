import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import { type UserHelper } from '../../../../../actions/helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainNodeExpectation, type AttackChainRun, type User } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { resolveUserName } from '../../../../../utils/String';

interface Props { exerciseId: AttackChainRun['attack_chain_run_id'] }

const AttackChainRunDistributionScoreByPlayer: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectExpectations, usersMap } = useHelper((helper: AttackChainNodeHelper & UserHelper) => ({
    injectExpectations: helper.getAttackChainRunAttackChainNodeExpectations(exerciseId),
    usersMap: helper.getUsersMap(),
  }));

  const usersTotalScores = R.pipe(
    R.filter(
      (n: AttackChainNodeExpectation) => !R.isEmpty(n.node_expectation_results)
        && n.node_expectation_user !== null,
    ),
    R.groupBy(R.prop('node_expectation_user')),
    R.toPairs,
    R.map((n: [string, AttackChainNodeExpectation[]]) => ({
      ...usersMap[n[0]],
      user_total_score: R.sum(R.map((o: AttackChainNodeExpectation) => o.node_expectation_score, n[1])),
    })),
  )(injectExpectations);

  const sortedUsersByTotalScore = R.pipe(
    R.sortWith([R.descend(R.prop('user_total_score'))]),
    R.take(10),
  )(usersTotalScores);

  const totalScoreByUserData = [
    {
      name: t('Total score'),
      data: sortedUsersByTotalScore.map((u: User & { user_total_score: number }) => ({
        x: resolveUserName(u) ?? '',
        y: u.user_total_score || null,
      })),
    },
  ];

  return (
    <>
      {usersTotalScores.length > 0 ? (
        <Chart
          id="attack_chain_run_distribution_total_score_by_player"
          options={horizontalBarsChartOptions({ theme })}
          series={totalScoreByUserData}
          type="bar"
          width="100%"
          height={50 + usersTotalScores.length * 50}
        />
      ) : (
        <Empty
          id="attack_chain_run_distribution_total_score_by_player"
          message={t(
            'No data to display or the attack_chain_run has not started yet',
          )}
        />
      )}
    </>
  );
};
export default AttackChainRunDistributionScoreByPlayer;
