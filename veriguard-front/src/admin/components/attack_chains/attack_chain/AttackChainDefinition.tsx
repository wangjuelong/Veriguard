import { useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import { type AttackChainsHelper } from '../../../../actions/attack_chains/attack_chain-helper';
import { useHelper } from '../../../../store';
import { type AttackChain } from '../../../../utils/api-types';
import AttackChainTeams from './teams/AttackChainTeams';
import AttackChainVariables from './variables/AttackChainVariables';

const AttackChainDefinition = () => {
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: AttackChain['attack_chain_id'] };
  const { attack_chain } = useHelper((helper: AttackChainsHelper) => ({ attack_chain: helper.getAttackChain(scenarioId) }));
  return (
    <div style={{
      display: 'grid',
      gap: `${theme.spacing(3)} ${theme.spacing(3)}`,
      gridTemplateColumns: '1fr 1fr',
    }}
    >
      <AttackChainTeams scenarioTeamsUsers={attack_chain.attack_chain_teams_users} />
      <AttackChainVariables />
    </div>
  );
};

export default AttackChainDefinition;
