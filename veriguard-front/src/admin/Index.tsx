import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { lazy, Suspense, useEffect } from 'react';
import { Route, Routes, useNavigate } from 'react-router';
import { type CSSObject } from 'tss-react';
import { makeStyles } from 'tss-react/mui';

import { fetchAttackPatterns } from '../actions/AttackPattern';
import fetchDomains from '../actions/domains/domain-actions';
import { type LoggedHelper } from '../actions/helper';
import { fetchKillChainPhases } from '../actions/KillChainPhase';
import { fetchTags } from '../actions/Tag';
import { errorWrapper } from '../components/Error';
import Loader from '../components/Loader';
import NotFound from '../components/NotFound';
import { computeBannerSettings } from '../public/components/systembanners/utils';
import { useHelper } from '../store';
import { useAppDispatch } from '../utils/hooks';
import useDataLoader from '../utils/hooks/useDataLoader';
import ProtectedRoute from '../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../utils/permissions/types';
import AttackChainNodeIndex from './components/attack_chain_runs/attack_chain_run/attack_chain_nodes/AttackChainNodeIndex';
import LeftBar from './components/nav/LeftBar';
import TopBar from './components/nav/TopBar';

const Home = lazy(() => import('./components/Home'));
const IndexProfile = lazy(() => import('./components/profile/Index'));
const FullTextSearch = lazy(() => import('./components/search/FullTextSearch'));
const Findings = lazy(() => import('./components/findings/Findings'));
const AttackChainRuns = lazy(() => import('./components/attack_chain_runs/AttackChainRuns'));
const IndexAttackChainRun = lazy(() => import('./components/attack_chain_runs/attack_chain_run/Index'));
const AtomicTestings = lazy(() => import('./components/atomic_testings/AtomicTestings'));
const IndexAtomicTesting = lazy(() => import('./components/atomic_testings/atomic_testing/Index'));
const AttackChains = lazy(() => import('./components/attack_chains/AttackChains'));
const IndexAttackChain = lazy(() => import('./components/attack_chains/attack_chain/Index'));
const ValidationParameterSets = lazy(() => import('./components/validation_parameter_sets/Index'));
const Assets = lazy(() => import('./components/assets/Index'));
const Teams = lazy(() => import('./components/teams/Index'));
const IndexComponents = lazy(() => import('./components/components/Index'));
const IndexIntegrations = lazy(() => import('./components/integrations/Index'));
const IndexAgents = lazy(() => import('./components/agents/Agents'));
const IndexCustomDashboard = lazy(() => import('./components/workspaces/custom_dashboards/Index'));
const Payloads = lazy(() => import('./components/payloads/Payloads'));
const VeriguardConsole = lazy(() => import('./components/veriguard/VeriguardConsole'));
const StabilityTrendView = lazy(() => import('./components/stability/StabilityTrendView'));
const CombinationsList = lazy(() => import('./components/combination/CombinationsList'));
const CombinationRunCanvas = lazy(() => import('./components/combination/CombinationRunCanvas'));
const CoverageBaselinesList = lazy(() => import('./components/coverage/CoverageBaselinesList'));
const CoverageRunCanvas = lazy(() => import('./components/coverage/CoverageRunCanvas'));
const MonitoringJobsList = lazy(() => import('./components/monitoring/MonitoringJobsList'));
const MonitoringTrendView = lazy(() => import('./components/monitoring/MonitoringTrendView'));
const IndexSettings = lazy(() => import('./components/settings/Index'));

const useStyles = makeStyles()(theme => ({ toolbar: theme.mixins.toolbar as CSSObject }));

const Index = () => {
  const theme = useTheme();

  const { classes } = useStyles();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { logged, settings } = useHelper((helper: LoggedHelper) => {
    return {
      logged: helper.logged(),
      settings: helper.getPlatformSettings(),
    };
  });

  useEffect(() => {
    if (logged.isOnlyPlayer) {
      navigate('/');
    }
  }, [logged]);

  const boxSx = {
    flexGrow: 1,
    padding: 3,
    transition: theme.transitions.create('width', {
      easing: theme.transitions.easing.easeInOut,
      duration: theme.transitions.duration.enteringScreen,
    }),
    overflowX: 'hidden',
    overflowY: 'hidden',
  };
  // load taxonomies one time at login
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
    dispatch(fetchKillChainPhases());
    dispatch(fetchTags());
    dispatch(fetchDomains());
  });
  const { bannerHeight } = computeBannerSettings(settings);

  return (
    <Box
      sx={{
        display: 'flex',
        minWidth: 1400,
        marginTop: bannerHeight,
        marginBottom: bannerHeight,
      }}
    >
      <TopBar />
      <LeftBar />
      <Box component="main" sx={boxSx}>
        <div className={classes.toolbar} />
        <Suspense fallback={<Loader />}>
          <Routes>
            <Route path="profile/*" element={errorWrapper(IndexProfile)()} />
            <Route path="" element={errorWrapper(Home)()} />
            <Route path="fulltextsearch" element={errorWrapper(FullTextSearch)()} />
            <Route
              path="findings"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.FINDINGS,
                  }]}
                  Component={errorWrapper(Findings)()}
                />
              )}
            />
            <Route path="attack_chain_runs" element={errorWrapper(AttackChainRuns)()} />
            <Route
              path="attack_chain_runs/:exerciseId/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.ASSESSMENT,
                  }, {
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.RESOURCE,
                    resourceURIParamName: 'exerciseId',
                  }]}
                  Component={errorWrapper(IndexAttackChainRun)()}
                />
              )}
            />
            <Route
              path="attack_chain_runs/:exerciseId/nodes/:injectId/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.ASSESSMENT,
                  }, {
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.RESOURCE,
                    resourceURIParamName: 'exerciseId',
                  }]}
                  Component={errorWrapper(AttackChainNodeIndex)()}
                />
              )}
            />
            <Route path="atomic_testings" element={errorWrapper(AtomicTestings)()} />
            <Route
              path="atomic_testings/:injectId/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.ASSESSMENT,
                  }, {
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.RESOURCE,
                    resourceURIParamName: 'injectId',
                  }]}
                  Component={errorWrapper(IndexAtomicTesting)()}
                />
              )}
            />
            <Route path="attack_chains" element={errorWrapper(AttackChains)()} />
            <Route
              path="attack_chains/:scenarioId/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.ASSESSMENT,
                  }, {
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.RESOURCE,
                    resourceURIParamName: 'scenarioId',
                  }]}
                  Component={errorWrapper(IndexAttackChain)()}
                />
              )}
            />
            <Route
              path="validation_parameter_sets"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.ASSESSMENT,
                  }]}
                  Component={errorWrapper(ValidationParameterSets)()}
                />
              )}
            />
            <Route path="assets/*" element={errorWrapper(Assets)()} />
            <Route path="teams/*" element={errorWrapper(Teams)()} />
            <Route path="components/*" element={errorWrapper(IndexComponents)()} />
            <Route
              path="workspaces/custom_dashboards/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.DASHBOARDS,
                  }]}
                  Component={errorWrapper(IndexCustomDashboard)()}
                />
              )}
            />
            <Route path="payloads" element={errorWrapper(Payloads)()} />
            <Route
              path="veriguard"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(VeriguardConsole)()}
                />
              )}
            />
            {/* PR C5: 稳定性引擎子模块（招标 §3.3 ★1 + §4.2 ★3） */}
            <Route
              path="stability"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.ASSESSMENT,
                  }]}
                  Component={errorWrapper(StabilityTrendView)()}
                />
              )}
            />
            {/* PR D5: 攻击组合引擎（招标 §3.6 ★2） */}
            <Route
              path="combinations"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(CombinationsList)()}
                />
              )}
            />
            <Route
              path="combinations/:id"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(CombinationRunCanvas)()}
                />
              )}
            />
            {/* PR C3: 边界覆盖度子模块（招标 §3.1 + §4.1） */}
            <Route
              path="coverage"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(CoverageBaselinesList)()}
                />
              )}
            />
            <Route
              path="coverage/baselines/:id"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(CoverageRunCanvas)()}
                />
              )}
            />
            {/* PR C4: 边界策略常态化监控（招标 §3.2） */}
            <Route
              path="monitoring"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(MonitoringJobsList)()}
                />
              )}
            />
            <Route
              path="monitoring/:id"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(MonitoringTrendView)()}
                />
              )}
            />
            <Route
              path="integrations/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(IndexIntegrations)()}
                />
              )}
            />
            <Route path="agents/*" element={errorWrapper(IndexAgents)()} />
            <Route
              path="settings/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(IndexSettings)()}
                />
              )}
            />

            {/* Not found */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Suspense>
      </Box>
    </Box>
  );
};

export default Index;
