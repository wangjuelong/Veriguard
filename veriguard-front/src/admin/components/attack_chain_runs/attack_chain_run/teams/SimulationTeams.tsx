import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext } from 'react';
import { useParams } from 'react-router';

import { type AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { fetchAttackChainRunTeams } from '../../../../../actions/AttackChainRun';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainRun, type Team } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext, TeamContext } from '../../../common/Context';
import ContextualTeams from '../../../components/teams/ContextualTeams';
import UpdateTeams from '../../../components/teams/UpdateTeams';
import teamContextForAttackChainRun from './teamContextForAttackChainRun';

interface Props { exerciseTeamsUsers: AttackChainRun['attack_chain_run_teams_users'] }

const SimulationTeams: FunctionComponent<Props> = ({ exerciseTeamsUsers }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);
  const theme = useTheme();

  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };
  const { teamsStore }: { teamsStore: Team[] } = useHelper((helper: AttackChainRunsHelper) => ({ teamsStore: helper.getAttackChainRunTeams(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchAttackChainRunTeams(exerciseId));
  });

  return (
    <TeamContext.Provider value={teamContextForAttackChainRun(exerciseId, exerciseTeamsUsers)}>
      <div style={{
        display: 'grid',
        gap: `0 ${theme.spacing(3)}`,
        gridTemplateRows: 'min-content 1fr',
      }}
      >
        <Typography variant="h4">
          {t('Teams')}
          {permissions.canManage
            && (
              <UpdateTeams
                addedTeamIds={teamsStore.map((team: Team) => team.team_id)}
              />
            )}
        </Typography>
        <Paper sx={{ padding: theme.spacing(2) }} variant="outlined">
          <ContextualTeams teams={teamsStore} />
        </Paper>
      </div>
    </TeamContext.Provider>
  );
};

export default SimulationTeams;
