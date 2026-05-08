import { useTheme } from '@mui/material/styles';
import type { CSSProperties } from 'react';

const useLeftMenuStyle: () => {
  listItemIcon: CSSProperties;
  listItemText: CSSProperties;
} = () => {
  const theme = useTheme();

  return ({
    // Icon block sized to fit a 18px SVG with ~10px gap (VeriGuard scale).
    listItemIcon: {
      minWidth: 28,
      fontSize: 18,
    },
    listItemText: {
      paddingLeft: theme.spacing(0.5),
      fontWeight: theme.typography.h2.fontWeight,
      fontSize: 13,
    },
  });
};

export default useLeftMenuStyle;
