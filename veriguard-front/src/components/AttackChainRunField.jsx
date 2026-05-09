import { Kayaking } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useDispatch } from 'react-redux';
import { makeStyles } from 'tss-react/mui';

import { fetchAttackChainRuns } from '../actions/AttackChainRun';
import { useHelper } from '../store';
import useDataLoader from '../utils/hooks/useDataLoader';
import Autocomplete from './Autocomplete';

const useStyles = makeStyles()(() => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
  autoCompleteIndicator: { display: 'none' },
}));

/**
 * @deprecated The component use old form library react-final-form
 */
const AttackChainRunField = (props) => {
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const attack_chain_runs = useHelper(helper => helper.getAttackChainRuns());
  useDataLoader(() => {
    dispatch(fetchAttackChainRuns());
  });
  const { name, onKeyDown, style, label, placeholder } = props;
  const exerciseOptions = (attack_chain_runs || []).map(n => ({
    id: n.attack_chain_run_id,
    label: n.attack_chain_run_name,
  }));
  return (
    <Autocomplete
      variant="standard"
      size="small"
      name={name}
      fullWidth
      multiple
      label={label}
      placeholder={placeholder}
      options={exerciseOptions}
      style={style}
      onKeyDown={onKeyDown}
      renderOption={(renderProps, option) => (
        <Box component="li" {...renderProps} key={option.id}>
          <div className={classes.icon}>
            <Kayaking />
          </div>
          <div className={classes.text}>{option.label}</div>
        </Box>
      )}
      classes={{ clearIndicator: classes.autoCompleteIndicator }}
    />
  );
};

export default AttackChainRunField;
