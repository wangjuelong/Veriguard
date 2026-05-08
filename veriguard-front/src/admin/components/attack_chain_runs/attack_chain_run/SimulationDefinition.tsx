import { useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import type { ExercisesHelper } from '../../../../actions/attack_chain_runs/exercise-helper';
import { useHelper } from '../../../../store';
import { type Exercise } from '../../../../utils/api-types';
import SimulationTeams from './teams/SimulationTeams';
import SimulationVariables from './variables/SimulationVariables';

const SimulationDefinition = () => {
  const theme = useTheme();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  return (
    <div style={{
      display: 'grid',
      gap: `${theme.spacing(3)} ${theme.spacing(3)}`,
      gridTemplateColumns: '1fr 1fr',
    }}
    >
      <SimulationTeams exerciseTeamsUsers={exercise.exercise_teams_users ?? []} />
      <SimulationVariables />
    </div>
  );
};

export default SimulationDefinition;
