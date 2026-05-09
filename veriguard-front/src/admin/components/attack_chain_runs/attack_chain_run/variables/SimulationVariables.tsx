import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { useParams } from 'react-router';

import { addVariableForAttackChainRun, deleteVariableForAttackChainRun, fetchVariablesForAttackChainRun, updateVariableForAttackChainRun } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainRun, type Variable, type VariableInput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext, VariableContext, type VariableContextType } from '../../../common/Context';
import CreateVariable from '../../../components/variables/CreateVariable';
import Variables from '../../../components/variables/Variables';

const SimulationVariables = () => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };
  const { permissions } = useContext(PermissionsContext);
  const variables = useHelper((helper: VariablesHelper) => helper.getAttackChainRunVariables(exerciseId));
  useDataLoader(() => {
    dispatch(fetchVariablesForAttackChainRun(exerciseId));
  });

  const context: VariableContextType = {
    onCreateVariable: (data: VariableInput) => dispatch(addVariableForAttackChainRun(exerciseId, data)),
    onEditVariable: (variable: Variable, data: VariableInput) => dispatch(updateVariableForAttackChainRun(exerciseId, variable.variable_id, data)),
    onDeleteVariable: (variable: Variable) => dispatch(deleteVariableForAttackChainRun(exerciseId, variable.variable_id)),
  };

  return (
    <VariableContext.Provider value={context}>
      <div style={{
        display: 'grid',
        gap: `0 ${theme.spacing(3)}`,
        gridTemplateRows: 'min-content 1fr',
      }}
      >
        <Typography variant="h4">
          {t('Variables')}
          {permissions.canManage && (<CreateVariable />)}
        </Typography>
        <Paper sx={{ padding: theme.spacing(2) }} variant="outlined">
          <Variables variables={variables} />
        </Paper>
      </div>
    </VariableContext.Provider>
  );
};

export default SimulationVariables;
