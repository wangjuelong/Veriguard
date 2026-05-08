import { ListItemIcon, ListItemText, MenuItem, MenuList, Popover, useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Link, useLocation } from 'react-router';

import { useFormatter } from '../../../i18n';
import { type LeftMenuSubItem } from './leftmenu-model';
import { type LeftMenuHelpers, type LeftMenuState } from './useLeftMenu';
import useLeftMenuStyle from './useLeftMenuStyle';

interface Props {
  menu: string;
  subItems: LeftMenuSubItem[] | undefined;
  state: LeftMenuState;
  helpers: LeftMenuHelpers;
}

const MenuItemSub: FunctionComponent<Props> = ({
  menu,
  subItems = [],
  state,
  helpers,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const location = useLocation();
  const theme = useTheme();
  const leftMenuStyle = useLeftMenuStyle();

  const { navOpen, selectedMenu, anchors } = state;
  const { handleSelectedMenuOpen, handleSelectedMenuClose } = helpers;

  const renderMenuItem = ({ label, link, exact, icon }: LeftMenuSubItem) => {
    const isCurrentTab = location.pathname === link;
    return (
      <MenuItem
        key={label}
        aria-label={t(label)}
        component={Link}
        to={link}
        selected={exact ? isCurrentTab : location.pathname.includes(link)}
        dense
        sx={{
          'paddingLeft': '12px',
          'paddingRight': '12px',
          'height': 30,
          'borderRadius': '6px',
          'marginBottom': '1px',
          'transition': 'background-color 220ms cubic-bezier(.2,.7,.2,1)',
          '& .MuiListItemIcon-root .MuiSvgIcon-root, & .MuiListItemIcon-root svg': {
            fontSize: 16,
            width: 16,
            height: 16,
          },
          '&.Mui-selected': {
            backgroundColor: 'action.hover',
            boxShadow: 'none',
          },
        }}
        onClick={handleSelectedMenuClose}
      >
        {icon && (
          <ListItemIcon style={{
            ...leftMenuStyle.listItemIcon,
            minWidth: 24,
          }}
          >
            {icon()}
          </ListItemIcon>
        )}
        <ListItemText
          primary={t(label)}
          slotProps={{
            primary: {
              paddingLeft: `${theme.spacing(0.5)}`,
              fontSize: 12.5,
              fontWeight: 400,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            },
          }}
        />
      </MenuItem>
    );
  };

  // Unified flyout popover regardless of sidebar collapse state — anchors to the
  // right of the parent nav item, opens on click (toggle from MenuItemGroup).
  return (
    <Popover
      sx={{ pointerEvents: 'none' }}
      open={selectedMenu === menu}
      anchorEl={anchors[menu]?.current}
      anchorOrigin={{
        vertical: navOpen ? 'top' : 'top',
        horizontal: 'right',
      }}
      transformOrigin={{
        vertical: 'top',
        horizontal: 'left',
      }}
      onClose={handleSelectedMenuClose}
      disableRestoreFocus
      disableScrollLock
      slotProps={{
        paper: {
          elevation: 2,
          onMouseEnter: () => handleSelectedMenuOpen(menu),
          onMouseLeave: handleSelectedMenuClose,
          sx: {
            pointerEvents: 'auto',
            minWidth: 200,
            padding: '6px',
            borderRadius: '10px',
            border: '0.5px solid',
            borderColor: 'divider',
          },
        },
      }}
    >
      <MenuList component="nav" disablePadding>
        {subItems.map((entry) => {
          if (!entry.userRight) return null;
          return renderMenuItem(entry);
        })}
      </MenuList>
    </Popover>
  );
};

export default MenuItemSub;
