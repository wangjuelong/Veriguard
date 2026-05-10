import { CssBaseline } from '@mui/material';
import { StyledEngineProvider } from '@mui/material/styles';
import { lazy, Suspense, useEffect } from 'react';
import { Navigate, Route, Routes } from 'react-router';

import { fetchMe, fetchPlatformParameters } from './actions/Application';
import { type LoggedHelper } from './actions/helper';
import fetchPublicPlatformParameters from './actions/settings/platform-settings-action';
import ConnectedIntlProvider from './components/AppIntlProvider';
import ConnectedThemeProvider from './components/AppThemeProvider';
import { errorWrapper } from './components/Error';
import Loader from './components/Loader';
import Message from './components/Message';
import NotFound from './components/NotFound';
import SystemBanners from './public/components/systembanners/SystemBanners';
import { useHelper } from './store';
import ErrorHandler from './utils/error/ErrorHandler';
import { useAppDispatch } from './utils/hooks';
import { UserContext } from './utils/hooks/useAuth';
import PermissionsProvider from './utils/permissions/PermissionsProvider';

const RootPublic = lazy(() => import('./public/Root'));
const IndexPrivate = lazy(() => import('./private/Index'));
const IndexAdmin = lazy(() => import('./admin/Index'));
const Comcheck = lazy(() => import('./public/components/comcheck/Comcheck'));
const SimulationReport = lazy(() => import('./admin/components/attack_chain_runs/attack_chain_run/reports/AttackChainRunReportPage'));
const AttackChainRunViewLessons = lazy(() => import('./public/components/lessons/AttackChainRunViewLessons'));
const AttackChainViewLessons = lazy(() => import('./public/components/lessons/AttackChainViewLessons'));

const Root = () => {
  const { logged, me, settings } = useHelper((helper: LoggedHelper) => {
    return {
      logged: helper.logged(),
      me: helper.getMe(),
      settings: helper.getPlatformSettings(),
    };
  });
  const dispatch = useAppDispatch();
  useEffect(() => {
    dispatch(fetchPublicPlatformParameters());
    dispatch(fetchMe());
  }, []);

  // Fetch full settings once authenticated, re-fetch public settings on logout
  useEffect(() => {
    if (logged && me) {
      dispatch(fetchPlatformParameters());
    } else if (logged === null) {
      dispatch(fetchPublicPlatformParameters());
    }
  }, [logged, me]);

  if (logged && typeof logged === 'object' && Object.keys(logged).length === 0) {
    return <div />;
  }

  if (!logged || !me || !settings) {
    return (
      <Suspense fallback={<Loader />}>
        <RootPublic />
      </Suspense>
    );
  }

  return (
    <PermissionsProvider capabilities={me.user_capabilities} grants={me.user_grants} isAdmin={me.user_admin}>
      <UserContext.Provider
        value={{
          me,
          settings,
        }}
      >
        <StyledEngineProvider injectFirst>
          <ConnectedIntlProvider>
            <ConnectedThemeProvider>
              <CssBaseline />
              <Message />
              <ErrorHandler />
              <SystemBanners settings={settings} />
              <Suspense fallback={<Loader />}>
                <Routes>
                  <Route
                    path=""
                    element={logged.isOnlyPlayer ? <Navigate to="private" replace={true} />
                      : <Navigate to="admin" replace={true} />}
                  />
                  <Route path="private/*" element={errorWrapper(IndexPrivate)()} />
                  <Route path="admin/*" element={errorWrapper(IndexAdmin)()} />
                  {/* Routes from /public/Index that need to be accessible for logged user are duplicated here */}
                  <Route path="comcheck/:statusId" element={errorWrapper(Comcheck)()} />
                  <Route path="lessons/attack_chain_run/:exerciseId" element={errorWrapper(AttackChainRunViewLessons)()} />
                  <Route path="lessons/attack_chain/:scenarioId" element={errorWrapper(AttackChainViewLessons)()} />
                  <Route path="reports/:reportId/attack_chain_run/:exerciseId" element={errorWrapper(SimulationReport)()} />

                  {/* Not found */}
                  <Route path="*" element={<NotFound />} />
                </Routes>
              </Suspense>
            </ConnectedThemeProvider>
          </ConnectedIntlProvider>
        </StyledEngineProvider>
      </UserContext.Provider>
    </PermissionsProvider>

  );
};

export default Root;
