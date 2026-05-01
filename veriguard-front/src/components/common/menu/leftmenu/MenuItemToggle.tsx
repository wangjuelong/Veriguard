import { ChevronLeft, ChevronRight } from '@mui/icons-material';
import { IconButton, Tooltip } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../i18n';

interface Props {
  navOpen: boolean;
  onClick: () => void;
}

const MenuItemToggle: FunctionComponent<Props> = ({ navOpen, onClick }) => {
  const { t } = useFormatter();

  return (
    <Tooltip
      title={navOpen ? t('Collapse menu') : t('Expand menu')}
      placement="right"
    >
      <IconButton
        aria-label={navOpen ? 'Collapse menu' : 'Expand menu'}
        onClick={onClick}
        size="small"
        sx={{
          'alignSelf': navOpen ? 'flex-end' : 'center',
          'width': 28,
          'height': 28,
          'margin': '6px',
          'borderRadius': '7px',
          'color': 'text.secondary',
          'transition': 'background-color 220ms cubic-bezier(.2,.7,.2,1), transform 220ms cubic-bezier(.2,.7,.2,1)',
          '&:hover': {
            backgroundColor: 'action.hover',
            color: 'text.primary',
          },
        }}
      >
        {navOpen
          ? <ChevronLeft sx={{ fontSize: 18 }} />
          : <ChevronRight sx={{ fontSize: 18 }} />}
      </IconButton>
    </Tooltip>
  );
};

export default MenuItemToggle;
