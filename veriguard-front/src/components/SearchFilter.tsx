import { Search } from '@mui/icons-material';
import { InputAdornment, TextField } from '@mui/material';
import { type ChangeEvent, type FunctionComponent, useCallback } from 'react';
import { makeStyles } from 'tss-react/mui';

import { debounce } from '../utils/utils';
import { useFormatter } from './i18n';

// VeriGuard search input — 32px height, 8px radius, .5px line, 13px font.
const useStyles = makeStyles()(theme => ({
  searchRoot: {
    'borderRadius': 8,
    'padding': '0 10px',
    'height': 32,
    'fontSize': 13,
    'backgroundColor': theme.palette.background.paper,
    'border': `0.5px solid ${theme.palette.divider}`,
    '&:hover': { borderColor: theme.palette.text.secondary },
    '&.Mui-focused': { borderColor: theme.palette.text.primary },
    '&.inDrawer': { height: 30 },
    '&.topBar': {
      marginRight: 5,
      minWidth: 550,
      width: '50%',
    },
    '&.thin': { height: 30 },
  },
  searchInput: {
    'transition': theme.transitions.create('width'),
    'width': 220,
    'fontSize': 13,
    'padding': '6px 0',
    '&:focus': { width: 320 },
    '&.small, &.thin': {
      'width': 180,
      '&:focus': { width: 260 },
    },
  },
}));

interface Props {
  keyword?: string;
  onChange?: (value?: string) => void;
  onSubmit?: (value?: string) => void;
  variant?: string;
  fullWidth?: boolean;
  placeholder?: string;
  debounceMs?: number;
}

const SearchInput: FunctionComponent<Props> = ({
  onChange,
  onSubmit,
  variant,
  keyword,
  fullWidth,
  placeholder,
  debounceMs,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();

  const classRoot = `${classes.searchRoot} ${variant ?? ''}`.trim();
  const inputClass = `${classes.searchInput} ${variant ?? ''}`.trim();

  const debouncedChangeHandler = useCallback(
    debounce((value?: string) => onChange?.(value), debounceMs),
    [onChange, debounceMs],
  );

  const handleChange = ({ target }: ChangeEvent<HTMLInputElement>) => {
    if (typeof onChange === 'function') {
      debouncedChangeHandler(target.value);
    }
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      const { target } = event as unknown as ChangeEvent<HTMLInputElement>;
      onSubmit?.(target.value);
    }
  };

  return (
    <TextField
      fullWidth={fullWidth}
      name="keyword"
      defaultValue={keyword}
      variant="outlined"
      size="small"
      placeholder={placeholder ?? `${t('Search these results')}...`}
      onChange={handleChange}
      onKeyDown={handleKeyDown}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <Search sx={{ fontSize: 16 }} />
          </InputAdornment>
        ),
        classes: {
          root: classRoot,
          input: inputClass,
        },
      }}
      sx={{ '& .MuiOutlinedInput-notchedOutline': { border: 'none' } }}
      autoComplete="off"
    />
  );
};

export default SearchInput;
