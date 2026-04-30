import { ListItemIcon, ListItemText, MenuItem } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Link, useLocation } from 'react-router';

import { useFormatter } from '../../../i18n';
import { type LeftMenuItem } from './leftmenu-model';
import StyledTooltip from './StyledTooltip';
import useLeftMenuStyle from './useLeftMenuStyle';

interface Props {
  navOpen: boolean;
  item: LeftMenuItem;
}

const MenuItemSingle: FunctionComponent<Props> = ({ navOpen, item }) => {
  // Standard hooks
  const { t } = useFormatter();
  const location = useLocation();
  const leftMenuStyle = useLeftMenuStyle();

  const isCurrentTab = location.pathname === item.path;
  return (
    <StyledTooltip title={!navOpen && t(item.label)} placement="right">
      <MenuItem
        aria-label={t(item.label)}
        component={Link}
        to={item.path}
        selected={isCurrentTab}
        dense
        sx={{
          'paddingLeft': '10px',
          'paddingRight': '10px',
          'height': 32,
          'borderRadius': '7px',
          'marginBottom': '1px',
          'transition': 'background-color 220ms cubic-bezier(.2,.7,.2,1)',
          '& .MuiListItemIcon-root .MuiSvgIcon-root, & .MuiListItemIcon-root svg': {
            fontSize: 18,
            width: 18,
            height: 18,
          },
          '&.Mui-selected': {
            backgroundColor: 'action.hover',
            boxShadow: 'none',
          },
          '&.Mui-selected:hover': {
            backgroundColor: 'action.hover',
            boxShadow: 'none',
          },
        }}
      >
        <ListItemIcon style={{ ...leftMenuStyle.listItemIcon }}>
          {item.icon()}
        </ListItemIcon>
        {navOpen && (
          <ListItemText
            primary={t(item.label)}
            slotProps={{
              primary: {
                sx: {
                  ...leftMenuStyle.listItemText,
                  fontSize: 13,
                  fontWeight: isCurrentTab ? 500 : 400,
                  color: isCurrentTab ? 'text.primary' : 'text.secondary',
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                },
              },
            }}
          />
        )}
      </MenuItem>
    </StyledTooltip>
  );
};

export default MenuItemSingle;
