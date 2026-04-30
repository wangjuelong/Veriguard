import { useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../store';
import { type Scenario } from '../../../../utils/api-types';
import ScenarioTeams from './teams/ScenarioTeams';
import ScenarioVariables from './variables/ScenarioVariables';

const ScenarioDefinition = () => {
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  return (
    <div style={{
      display: 'grid',
      gap: `${theme.spacing(3)} ${theme.spacing(3)}`,
      gridTemplateColumns: '1fr 1fr',
    }}
    >
      <ScenarioTeams scenarioTeamsUsers={scenario.scenario_teams_users} />
      <ScenarioVariables />
    </div>
  );
};

export default ScenarioDefinition;
