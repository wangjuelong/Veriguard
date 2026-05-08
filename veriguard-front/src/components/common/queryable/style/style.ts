import { useTheme } from '@mui/material/styles';
import { type CSSProperties } from 'react';

// VeriGuard list-row body cells — 13px font, tighter alignment.
const useBodyItemsStyles: () => {
  bodyItems: CSSProperties;
  bodyItem: CSSProperties;
} = () => {
  const theme = useTheme();

  return ({
    bodyItems: {
      display: 'flex',
      flexWrap: 'nowrap',
      alignItems: 'center',
      maxWidth: '100%',
      gap: theme.spacing(1),
    },
    bodyItem: {
      height: 22,
      fontSize: 13,
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      paddingRight: theme.spacing(1),
      display: 'flex',
      alignItems: 'center',
    },
  });
};

export default useBodyItemsStyles;
