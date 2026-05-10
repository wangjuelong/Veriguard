import { NotificationsOutlined, UpdateOutlined } from '@mui/icons-material';
import { Alert, AlertTitle, Box, IconButton, Tab, Tabs, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, lazy, Suspense, useEffect, useState } from 'react';
import { Link, Route, Routes, useLocation, useParams } from 'react-router';

import { fetchAttackChain } from '../../../../actions/attack_chains/attack_chain-actions';
import { type AttackChainsHelper } from '../../../../actions/attack_chains/attack_chain-helper';
import { findNotificationRuleByResource } from '../../../../actions/attack_chains/attack_chain-notification-rules';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { useHelper } from '../../../../store';
import { type AttackChain, type NotificationRuleOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import handle from '../../../../utils/period/Period';
import { type PeriodExpressionHandler } from '../../../../utils/period/PeriodExpressionHandler';
import { INHERITED_CONTEXT } from '../../../../utils/permissions/types';
import useAttackChainPermissions from '../../../../utils/permissions/useAttackChainPermissions';
import { AttackChainNodeContext, DocumentContext, type DocumentContextType, PermissionsContext, type PermissionsContextType } from '../../common/Context';
import injectContextForAttackChain from './AttackChainContext';
import AttackChainHeader from './AttackChainHeader';
import AttackChainNotificationRulesDrawer from './notification_rule/AttackChainNotificationRulesDrawer';

const AttackChainComponent = lazy(() => import('./AttackChain'));
const AttackChainDefinition = lazy(() => import('./AttackChainDefinition'));
const AttackChainNodes = lazy(() => import('./attack_chain_nodes/AttackChainAttackChainNodes'));
const Tests = lazy(() => import('./tests/AttackChainTests'));
const Lessons = lazy(() => import('./lessons/AttackChainLessons'));
const AttackChainFindings = lazy(() => import('./findings/AttackChainFindings'));
const AttackChainAnalysis = lazy(() => import('./analysis/AttackChainAnalysis'));

const MS_PER_DAY = 1000 * 60 * 60 * 24;

const IndexAttackChainComponent: FunctionComponent<{ attack_chain: AttackChain }> = ({ attack_chain }) => {
  const { t, locale, fld } = useFormatter();
  const location = useLocation();
  const theme = useTheme();
  const permissionsContext: PermissionsContextType = {
    permissions: useAttackChainPermissions(attack_chain.attack_chain_id),
    inherited_context: INHERITED_CONTEXT.SCENARIO,
  };
  const documentContext: DocumentContextType = {
    onInitDocument: () => ({
      document_tags: [],
      document_attack_chains: attack_chain
        ? [{
            id: attack_chain.attack_chain_id,
            label: attack_chain.attack_chain_name,
          }]
        : [],
      document_attack_chain_runs: [],
    }),
  };
  let tabValue = location.pathname;
  if (location.pathname.includes(`/admin/attack_chains/${attack_chain.attack_chain_id}/definition`)) {
    tabValue = `/admin/attack_chains/${attack_chain.attack_chain_id}/definition`;
  } else if (location.pathname.includes(`/admin/attack_chains/${attack_chain.attack_chain_id}/tests`)) {
    tabValue = `/admin/attack_chains/${attack_chain.attack_chain_id}/tests`;
  }
  const [openAttackChainRecurringFormDialog, setOpenAttackChainRecurringFormDialog] = useState<boolean>(false);
  const [openInstantiateSimulationAndStart, setOpenInstantiateSimulationAndStart] = useState<boolean>(false);
  const [selectRecurring, setSelectRecurring] = useState('noRepeat');
  const [cronObject, setCronObject] = useState<PeriodExpressionHandler | null>(handle(attack_chain.attack_chain_recurrence));
  const noRepeat = !!attack_chain.attack_chain_recurrence_end && !!attack_chain.attack_chain_recurrence_start
    && new Date(attack_chain.attack_chain_recurrence_end).getTime() - new Date(attack_chain.attack_chain_recurrence_start).getTime() <= MS_PER_DAY
    && ['noRepeat', 'daily'].includes(selectRecurring);
  const getHumanReadableScheduling = () => {
    if (!cronObject?.isValid()) {
      return null;
    }
    // process time

    let sentence: string;
    sentence = `${cronObject.toTranslatableStringArray(locale).map(element => t(element)).join(' ')}`;
    if (attack_chain.attack_chain_recurrence_end) {
      sentence += ` ${t('recurrence_from')} ${fld(attack_chain.attack_chain_recurrence_start)}`;
      sentence += ` ${t('recurrence_to')} ${fld(attack_chain.attack_chain_recurrence_end)}`;
    } else {
      sentence += ` ${t('recurrence_starting_from')} ${fld(attack_chain.attack_chain_recurrence_start)}`;
    }
    return sentence;
  };
  const [openAttackChainNotificationRuleDrawer, setOpenAttackChainNotificationRuleDrawer] = useState(false);
  const [editNotification, setEditNotification] = useState<boolean>(false);
  const [notificationRule, setNotificationRule] = useState<NotificationRuleOutput>({
    notification_rule_id: '',
    notification_rule_resource_id: '',
    notification_rule_resource_type: '',
    notification_rule_subject: '',
    notification_rule_trigger: '',
  });

  useEffect(() => {
    findNotificationRuleByResource(attack_chain.attack_chain_id).then((result: { data: NotificationRuleOutput[] }) => {
      if (result.data.length > 0) {
        setEditNotification(true);
        setNotificationRule(result.data[0]);
      }
    });
  }, []);

  const onCreateNotification = (result: NotificationRuleOutput) => {
    setEditNotification(true);
    setNotificationRule(result);
  };

  const onDeleteNotification = () => {
    setEditNotification(false);
    setNotificationRule({
      notification_rule_id: '',
      notification_rule_resource_id: '',
      notification_rule_resource_type: '',
      notification_rule_subject: '',
      notification_rule_trigger: '',
    });
  };

  return (
    <PermissionsContext.Provider value={permissionsContext}>
      <DocumentContext.Provider value={documentContext}>
        <>
          <Breadcrumbs
            variant="list"
            elements={[
              {
                label: t('AttackChains'),
                link: '/admin/attack_chains',
              },
              {
                label: attack_chain.attack_chain_name,
                current: true,
              },
            ]}
          />
          <AttackChainHeader
            cronObject={cronObject}
            setCronObject={setCronObject}
            setSelectRecurring={setSelectRecurring}
            selectRecurring={selectRecurring}
            setOpenAttackChainRecurringFormDialog={setOpenAttackChainRecurringFormDialog}
            openAttackChainRecurringFormDialog={openAttackChainRecurringFormDialog}
            setOpenInstantiateSimulationAndStart={setOpenInstantiateSimulationAndStart}
            openInstantiateSimulationAndStart={openInstantiateSimulationAndStart}
            noRepeat={noRepeat}
          />
          <Box
            sx={{
              borderBottom: 1,
              borderColor: 'divider',
              marginBottom: 2,
            }}
            display="flex"
            flexDirection="row"
            justifyContent="space-between"
          >
            <Tabs
              style={{ flex: 1 }}
              value={tabValue}
              variant="scrollable"
              scrollButtons="auto"
            >
              <Tab
                component={Link}
                to={`/admin/attack_chains/${attack_chain.attack_chain_id}`}
                value={`/admin/attack_chains/${attack_chain.attack_chain_id}`}
                label={t('Overview')}
              />
              <Tab
                component={Link}
                to={`/admin/attack_chains/${attack_chain.attack_chain_id}/definition`}
                value={`/admin/attack_chains/${attack_chain.attack_chain_id}/definition`}
                label={t('Definition')}
              />
              <Tab
                component={Link}
                to={`/admin/attack_chains/${attack_chain.attack_chain_id}/nodes`}
                value={`/admin/attack_chains/${attack_chain.attack_chain_id}/nodes`}
                label={t('AttackChainNodes')}
              />
              <Tab
                component={Link}
                to={`/admin/attack_chains/${attack_chain.attack_chain_id}/tests`}
                value={`/admin/attack_chains/${attack_chain.attack_chain_id}/tests`}
                label={t('Tests')}
              />
              <Tab
                component={Link}
                to={`/admin/attack_chains/${attack_chain.attack_chain_id}/lessons`}
                value={`/admin/attack_chains/${attack_chain.attack_chain_id}/lessons`}
                label={t('Lessons learned')}
              />
              <Tab
                component={Link}
                to={`/admin/attack_chains/${attack_chain.attack_chain_id}/findings`}
                value={`/admin/attack_chains/${attack_chain.attack_chain_id}/findings`}
                label={t('Findings')}
              />
              <Tab
                component={Link}
                to={`/admin/attack_chains/${attack_chain.attack_chain_id}/analysis`}
                value={`/admin/attack_chains/${attack_chain.attack_chain_id}/analysis`}
                label={t('Analysis')}
              />
            </Tabs>
            <div style={{
              display: 'flex',
              flexDirection: 'row',
            }}
            >
              {
                permissionsContext.permissions.canManage && (
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                  }}
                  >
                    <IconButton
                      size="small"
                      style={{ marginRight: theme.spacing(1) }}
                      onClick={() => setOpenAttackChainNotificationRuleDrawer(true)}
                    >
                      <NotificationsOutlined color={editNotification ? 'success' : 'primary'} />
                    </IconButton>
                    <Typography
                      variant="body1"
                      style={{ marginRight: theme.spacing(1) }}
                    >
                      {t('Notification rules')}
                    </Typography>
                    <AttackChainNotificationRulesDrawer
                      open={openAttackChainNotificationRuleDrawer}
                      setOpen={setOpenAttackChainNotificationRuleDrawer}
                      editing={editNotification}
                      onCreate={onCreateNotification}
                      onUpdate={result => setNotificationRule(result)}
                      onDelete={onDeleteNotification}
                      notificationRule={notificationRule}
                      scenarioId={attack_chain.attack_chain_id}
                      scenarioName={attack_chain.attack_chain_name}
                    />
                  </div>
                )
              }
              { permissionsContext.permissions.canManage && (
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                }}
                >
                  {!cronObject?.isValid() && (
                    <IconButton size="small" onClick={() => setOpenAttackChainRecurringFormDialog(true)} style={{ marginRight: theme.spacing(1) }}>
                      <UpdateOutlined color="primary" />
                    </IconButton>
                  )}
                  {cronObject?.isValid() && !attack_chain.attack_chain_recurrence && (
                    <IconButton
                      size="small"
                      style={{
                        cursor: 'default',
                        marginRight: theme.spacing(1),
                      }}
                    >
                      <UpdateOutlined />
                    </IconButton>
                  )}
                  {cronObject?.isValid() && attack_chain.attack_chain_recurrence && (
                    <Tooltip title={(t('Modify the scheduling'))}>
                      <IconButton size="small" onClick={() => setOpenAttackChainRecurringFormDialog(true)} style={{ marginRight: theme.spacing(1) }}>
                        <UpdateOutlined color="primary" />
                      </IconButton>
                    </Tooltip>
                  )}
                  <span style={{ color: theme.palette.text?.disabled }}>{!cronObject?.isValid() && t('Not scheduled')}</span>
                  {cronObject?.isValid() && <span>{getHumanReadableScheduling()}</span>}
                </div>
              )}

            </div>

          </Box>
          <Suspense fallback={<Loader />}>
            <Routes>
              <Route path="" element={errorWrapper(AttackChainComponent)({ setOpenInstantiateSimulationAndStart })} />
              <Route path="definition" element={errorWrapper(AttackChainDefinition)()} />
              <Route path="nodes" element={errorWrapper(AttackChainNodes)()} />
              <Route path="tests/:statusId?" element={errorWrapper(Tests)()} />
              <Route path="lessons" element={errorWrapper(Lessons)()} />
              <Route path="findings" element={errorWrapper(AttackChainFindings)()} />
              <Route path="analysis" element={errorWrapper(AttackChainAnalysis)()} />
              {/* Not found */}
              <Route path="*" element={<NotFound />} />
            </Routes>
          </Suspense>
        </>
      </DocumentContext.Provider>
    </PermissionsContext.Provider>
  );
};

const Index = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const [pristine, setPristine] = useState(true);
  const [loading, setLoading] = useState(true);
  const { t } = useFormatter();
  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: AttackChain['attack_chain_id'] };
  const { attack_chain } = useHelper((helper: AttackChainsHelper) => ({ attack_chain: helper.getAttackChain(scenarioId) }));
  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchAttackChain(scenarioId)).finally(() => {
      setPristine(false);
      setLoading(false);
    });
  });

  const scenarioAttackChainNodeContext = injectContextForAttackChain(attack_chain);

  // avoid to show loader if something trigger useDataLoader
  if (pristine && loading) {
    return <Loader />;
  }
  if (!loading && !attack_chain) {
    return (
      <Alert severity="warning">
        <AlertTitle>{t('Warning')}</AlertTitle>
        {t('AttackChain is currently unavailable or you do not have sufficient permissions to access it.')}
      </Alert>
    );
  }
  return (
    <AttackChainNodeContext.Provider value={scenarioAttackChainNodeContext}>
      <IndexAttackChainComponent attack_chain={attack_chain} />
    </AttackChainNodeContext.Provider>
  );
};

export default Index;
