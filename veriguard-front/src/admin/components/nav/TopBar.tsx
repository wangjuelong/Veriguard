import { AccountCircleOutlined, ImportantDevicesOutlined } from '@mui/icons-material';
import { AppBar, Button, IconButton, Menu, MenuItem, Toolbar, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { logout } from '../../../actions/Application';
import { useFormatter } from '../../../components/i18n';
import SearchInput from '../../../components/SearchFilter';
import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';

const useStyles = makeStyles()(theme => ({
  appBar: {
    width: '100%',
    zIndex: theme.zIndex.drawer + 1,
    background: 0,
    backgroundColor: theme.palette.background.nav,
    paddingTop: theme.spacing(0.2),
    borderLeft: 0,
    borderRight: 0,
    borderTop: 0,
    color: theme.palette.text?.primary,
  },
  logoContainer: { margin: '2px 0 0 10px' },
  logo: {
    cursor: 'pointer',
    height: 35,
    marginRight: 3,
  },
  logoCollapsed: {
    cursor: 'pointer',
    height: 35,
    marginRight: 4,
  },
  menuContainer: {
    width: '50%',
    float: 'left',
  },
  barRight: {
    position: 'absolute',
    top: 0,
    right: 13,
    height: '100%',
  },
  barRightContainer: {
    float: 'left',
    height: '100%',
    paddingTop: 12,
  },
}));

const TopBar: FunctionComponent = () => {
  // Standard hooks
  const theme = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);

  const [menuOpen, setMenuOpen] = useState<{
    open: boolean;
    anchorEl: HTMLButtonElement | null;
  }>({
    open: false,
    anchorEl: null,
  });
  const handleOpenMenu = (
    event: ReactMouseEvent<HTMLButtonElement, MouseEvent>,
  ) => {
    event.preventDefault();
    setMenuOpen({
      open: true,
      anchorEl: event.currentTarget,
    });
  };
  const handleCloseMenu = () => {
    setMenuOpen({
      open: false,
      anchorEl: null,
    });
  };
  const dispatch = useAppDispatch();
  const [navOpen, setNavOpen] = useState(
    localStorage.getItem('navOpen') === 'true',
  );
  useEffect(() => {
    const sub = MESSAGING$.toggleNav.subscribe({ next: () => setNavOpen(localStorage.getItem('navOpen') === 'true') });
    return () => {
      sub.unsubscribe();
    };
  });
  const handleLogout = async () => {
    await dispatch(logout());
    navigate('/');
    handleCloseMenu();
  };

  // Full Text search
  const onFullTextSearch = (search?: string) => {
    if (search) {
      navigate(`/admin/fulltextsearch?search=${search}`);
    }
  };

  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');

  return (
    <AppBar
      position="fixed"
      className={classes.appBar}
      variant="outlined"
      elevation={0}
    >
      <Toolbar style={{
        marginTop: bannerHeightNumber,
        paddingLeft: 0,
      }}
      >
        <div className={classes.logoContainer}>
          <Link to="/admin">
            <img
              src={navOpen ? theme.logo : theme.logo_collapsed}
              alt="logo"
              className={navOpen ? classes.logo : classes.logoCollapsed}
            />
          </Link>
        </div>
        <div className={classes.menuContainer} style={{ marginLeft: navOpen ? 20 : 30 }}>
          <SearchInput
            variant="topBar"
            placeholder={`${t('Search the platform')}...`}
            fullWidth={true}
            onSubmit={onFullTextSearch}
            keyword={search}
          />
        </div>
        <div className={classes.barRight}>
          <div className={classes.barRightContainer}>
            <Tooltip title={t('Install simulation agents')}>
              <Button
                aria-haspopup="true"
                component={Link}
                to="/admin/agents"
                size="small"
                variant="outlined"
                startIcon={<ImportantDevicesOutlined fontSize="small" />}
                sx={{
                  'textTransform': 'none',
                  'fontSize': 13,
                  'fontWeight': 500,
                  'borderRadius': '8px',
                  'borderColor': 'divider',
                  'color': location.pathname === '/admin/agents' ? 'primary.main' : 'text.secondary',
                  'height': 32,
                  'paddingX': 1.5,
                  'marginRight': 1,
                  '&:hover': {
                    borderColor: 'text.secondary',
                    backgroundColor: 'action.hover',
                  },
                }}
              >
                {t('Install agent')}
              </Button>
            </Tooltip>
            <IconButton
              aria-label="account-menu"
              onClick={handleOpenMenu}
              size="medium"
              color={
                location.pathname === '/admin/profile' ? 'primary' : 'inherit'
              }
            >
              <AccountCircleOutlined fontSize="medium" />
            </IconButton>
            <Menu
              id="menu-appbar"
              anchorEl={menuOpen.anchorEl}
              open={menuOpen.open}
              onClose={handleCloseMenu}
            >
              <MenuItem
                onClick={handleCloseMenu}
                component={Link}
                to="/admin/profile"
              >
                {t('Profile')}
              </MenuItem>
              <MenuItem aria-label="logout-item" onClick={handleLogout}>{t('Logout')}</MenuItem>
            </Menu>
          </div>
        </div>
      </Toolbar>
    </AppBar>
  );
};

export default TopBar;
