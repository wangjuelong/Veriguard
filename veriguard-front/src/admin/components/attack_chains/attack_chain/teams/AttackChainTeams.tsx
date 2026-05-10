import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext } from 'react';
import { useParams } from 'react-router';

import { fetchAttackChainTeams } from '../../../../../actions/attack_chains/attack_chain-actions';
import { type AttackChainsHelper } from '../../../../../actions/attack_chains/attack_chain-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChain, type Team } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext, TeamContext } from '../../../common/Context';
import ContextualTeams from '../../../components/teams/ContextualTeams';
import UpdateTeams from '../../../components/teams/UpdateTeams';
import teamContextForAttackChain from './teamContextForAttackChain';

interface Props { scenarioTeamsUsers: AttackChain['attack_chain_teams_users'] }

const AttackChainTeams: FunctionComponent<Props> = ({ scenarioTeamsUsers }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);
  const theme = useTheme();

  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: AttackChain['attack_chain_id'] };
  const { teamsStore }: { teamsStore: Team[] } = useHelper((helper: AttackChainsHelper) => ({ teamsStore: helper.getAttackChainTeams(scenarioId) }));
  useDataLoader(() => {
    dispatch(fetchAttackChainTeams(scenarioId));
  });

  return (
    <TeamContext.Provider value={teamContextForAttackChain(scenarioId, scenarioTeamsUsers)}>
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

export default AttackChainTeams;
