import { useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import type { AttackChainRunsHelper } from '../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { useHelper } from '../../../../store';
import { type AttackChainRun } from '../../../../utils/api-types';
import SimulationTeams from './teams/AttackChainRunTeams';
import SimulationVariables from './variables/AttackChainRunVariables';

const SimulationDefinition = () => {
  const theme = useTheme();
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };
  const { attack_chain_run } = useHelper((helper: AttackChainRunsHelper) => ({ attack_chain_run: helper.getAttackChainRun(exerciseId) }));
  return (
    <div style={{
      display: 'grid',
      gap: `${theme.spacing(3)} ${theme.spacing(3)}`,
      gridTemplateColumns: '1fr 1fr',
    }}
    >
      <SimulationTeams exerciseTeamsUsers={attack_chain_run.attack_chain_run_teams_users ?? []} />
      <SimulationVariables />
    </div>
  );
};

export default SimulationDefinition;
