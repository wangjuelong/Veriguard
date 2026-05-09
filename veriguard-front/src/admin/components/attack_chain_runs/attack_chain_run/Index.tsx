import { Alert, AlertTitle, Box, Tab, Tabs } from '@mui/material';
import { type FunctionComponent, lazy, Suspense, useEffect, useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchAttackChainFromSimulation } from '../../../../actions/attack_chain_runs/attack_chain_run-action';
import { type AttackChainRunsHelper } from '../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { fetchAttackChainRun } from '../../../../actions/AttackChainRun';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { useHelper } from '../../../../store';
import { type AttackChainRun as AttackChainRunType } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { INHERITED_CONTEXT } from '../../../../utils/permissions/types';
import useSimulationPermissions from '../../../../utils/permissions/useSimulationPermissions';
import { DocumentContext, type DocumentContextType, AttackChainNodeContext, PermissionsContext, type PermissionsContextType } from '../../common/Context';
import injectContextForAttackChainRun from './AttackChainRunContext';
import AttackChainRunDatePopover from './AttackChainRunDatePopover';
import AttackChainRunHeader from './AttackChainRunHeader';

const Simulation = lazy(() => import('./overview/SimulationComponent'));
const Lessons = lazy(() => import('./lessons/SimulationLessons'));
const SimulationFindings = lazy(() => import('./findings/SimulationFindings'));
const SimulationAnalysis = lazy(() => import('./analysis/SimulationAnalysis'));
const SimulationDefinition = lazy(() => import('./SimulationDefinition'));
const AttackChainNodes = lazy(() => import('./attack_chain_nodes/AttackChainRunAttackChainNodes'));
const Tests = lazy(() => import('./tests/AttackChainRunTests'));
const TimelineOverview = lazy(() => import('./timeline/TimelineOverview'));
const Mails = lazy(() => import('./mails/Mails'));
const MailsAttackChainNode = lazy(() => import('./mails/AttackChainNode'));
const Logs = lazy(() => import('./logs/Logs'));
const Chat = lazy(() => import('./chat/Chat'));
const Validations = lazy(() => import('./validation/Validations'));

const useStyles = makeStyles()(() => ({
  scheduling: {
    display: 'flex',
    margin: '-35px 8px 0 0',
    float: 'right',
    alignItems: 'center',
  },
}));

const IndexComponent: FunctionComponent<{ attack_chain_run: AttackChainRunType }> = ({ attack_chain_run }) => {
  const [isLoading, setIsLoading] = useState(false);
  const { t, fldt } = useFormatter();
  const location = useLocation();
  const { classes } = useStyles();
  const permissionsContext: PermissionsContextType = {
    permissions: useSimulationPermissions(attack_chain_run.attack_chain_run_id, attack_chain_run),
    inherited_context: INHERITED_CONTEXT.SIMULATION,
  };
  const documentContext: DocumentContextType = {
    onInitDocument: () => ({
      document_tags: [],
      document_attack_chains: [],
      document_attack_chain_runs: attack_chain_run
        ? [{
            id: attack_chain_run.attack_chain_run_id,
            label: attack_chain_run.attack_chain_run_name,
          }]
        : [],
    }),
  };

  let tabValue = location.pathname;
  if (location.pathname.includes(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/definition`)) {
    tabValue = `/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/definition`;
  } else if (location.pathname.includes(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/animation`)) {
    tabValue = `/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/animation`;
  } else if (location.pathname.includes(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/results`)) {
    tabValue = `/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/results`;
  } else if (location.pathname.includes(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/tests`)) {
    tabValue = `/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/tests`;
  }

  return (
    <PermissionsContext.Provider value={permissionsContext}>
      <DocumentContext.Provider value={documentContext}>

        <div style={{ paddingRight: ['/results', '/animation'].some(el => location.pathname.includes(el)) ? 200 : 0 }}>
          <Breadcrumbs
            variant="object"
            elements={[
              {
                label: t('Simulations'),
                link: '/admin/attack_chain_runs',
              },
              {
                label: attack_chain_run.attack_chain_run_name,
                current: true,
              },
            ]}
          />
          <AttackChainRunHeader onLoading={setIsLoading} isLoading={isLoading} />
          {isLoading
            ? <Loader />
            : (
                <>
                  <Box
                    sx={{
                      borderBottom: 1,
                      borderColor: 'divider',
                      marginBottom: 2,
                    }}
                  >
                    <Tabs value={tabValue}>
                      <Tab
                        component={Link}
                        to={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}`}
                        value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}`}
                        label={t('Overview')}
                      />
                      <Tab
                        component={Link}
                        to={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/definition`}
                        value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/definition`}
                        label={t('Definition')}
                      />
                      <Tab
                        component={Link}
                        to={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes`}
                        value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/nodes`}
                        label={t('AttackChainNodes')}
                      />
                      <Tab
                        component={Link}
                        to={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/tests`}
                        value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/tests`}
                        label={t('Tests')}
                      />
                      <Tab
                        component={Link}
                        to={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/animation`}
                        value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/animation`}
                        label={t('Animation')}
                      />
                      <Tab
                        component={Link}
                        to={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/lessons`}
                        value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/lessons`}
                        label={t('Lessons learned')}
                      />
                      <Tab
                        component={Link}
                        to={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/findings`}
                        value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/findings`}
                        label={t('Findings')}
                      />
                      <Tab
                        component={Link}
                        to={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/analysis`}
                        value={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/analysis`}
                        label={t('Analysis')}
                      />
                    </Tabs>
                    {permissionsContext.permissions.canManage && (
                      <div className={classes.scheduling}>
                        <AttackChainRunDatePopover attack_chain_run={attack_chain_run} />
                        {attack_chain_run.attack_chain_run_start_date ? fldt(attack_chain_run.attack_chain_run_start_date) : t('Manual')}
                      </div>
                    )}
                  </Box>
                  <Suspense fallback={<Loader />}>
                    <Routes>
                      <Route path="" element={errorWrapper(Simulation)()} />
                      <Route path="definition" element={errorWrapper(SimulationDefinition)()} />
                      <Route path="nodes" element={errorWrapper(AttackChainNodes)()} />
                      <Route path="tests/:statusId?" element={errorWrapper(Tests)()} />
                      <Route path="animation" element={<Navigate to="timeline" replace={true} />} />
                      <Route path="animation/timeline" element={errorWrapper(TimelineOverview)()} />
                      <Route path="animation/mails" element={errorWrapper(Mails)()} />
                      <Route path="animation/mails/:injectId" element={errorWrapper(MailsAttackChainNode)()} />
                      <Route path="animation/logs" element={errorWrapper(Logs)()} />
                      <Route path="animation/chat" element={errorWrapper(Chat)()} />
                      <Route path="animation/validations" element={errorWrapper(Validations)()} />
                      <Route path="lessons" element={errorWrapper(Lessons)()} />
                      <Route path="findings" element={errorWrapper(SimulationFindings)()} />
                      <Route path="analysis" element={errorWrapper(SimulationAnalysis)()} />
                      {/* Not found */}
                      <Route path="*" element={<NotFound />} />
                    </Routes>
                  </Suspense>
                </>
              )}
        </div>
      </DocumentContext.Provider>
    </PermissionsContext.Provider>
  );
};

const Index = () => {
  // Standard hooks
  const [pristine, setPristine] = useState(true);
  const [loading, setLoading] = useState(true);
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: AttackChainRunType['attack_chain_run_id'] };
  const { attack_chain_run } = useHelper((helper: AttackChainRunsHelper) => ({ attack_chain_run: helper.getAttackChainRun(exerciseId) }));
  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchAttackChainRun(exerciseId)).finally(() => {
      setLoading(false);
    });
  }, [exerciseId]);

  useEffect(() => {
    if (!attack_chain_run) return;
    setLoading(true);
    if (!attack_chain_run.attack_chain_run_attack_chain) {
      setPristine(false);
      setLoading(false);
    } else {
      dispatch(fetchAttackChainFromSimulation(attack_chain_run.attack_chain_run_id))
        .finally(() => {
          setPristine(false);
          setLoading(false);
        });
    }
  }, [attack_chain_run]);

  const exerciseAttackChainNodeContext = injectContextForAttackChainRun(attack_chain_run);

  // avoid to show loader if something trigger useDataLoader
  if (pristine && loading) {
    return <Loader />;
  }
  if (!loading && !attack_chain_run) {
    return (
      <Alert severity="warning">
        <AlertTitle>{t('Warning')}</AlertTitle>
        {t('Simulation is currently unavailable or you do not have sufficient permissions to access it.')}
      </Alert>
    );
  }
  return (
    <AttackChainNodeContext.Provider value={exerciseAttackChainNodeContext}>
      <IndexComponent attack_chain_run={attack_chain_run} />
    </AttackChainNodeContext.Provider>
  );
};

export default Index;
