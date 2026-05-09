import { Kayaking } from '@mui/icons-material';
import { Box } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { fetchAttackChains } from '../actions/attack_chains/attack_chain-actions';
import { useHelper } from '../store';
import { useAppDispatch } from '../utils/hooks';
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

const AttackChainField = (props) => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  // Fetching data
  const attack_chains = useHelper(helper => helper.getAttackChains());
  useDataLoader(() => {
    dispatch(fetchAttackChains());
  });

  const { name, onKeyDown, style, label, placeholder } = props;
  const scenarioOptions = (attack_chains || []).map(n => ({
    id: n.attack_chain_id,
    label: n.attack_chain_name,
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
      options={scenarioOptions}
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

export default AttackChainField;
