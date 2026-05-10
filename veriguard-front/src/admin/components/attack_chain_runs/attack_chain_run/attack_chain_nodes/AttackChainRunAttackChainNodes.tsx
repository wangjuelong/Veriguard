/* eslint-disable i18next/no-literal-string -- Phase 12b-B4 二开 UI 中文文案，未来 i18n 清洗。 */
import { BarChartOutlined, GridOnOutlined, ReorderOutlined, ViewTimelineOutlined } from '@mui/icons-material';
import { Drawer, GridLegacy, Paper, Stack, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import {
  type AttackChainLinkExpectationWire,
  fetchAttackChainRunAttackChainNodeExpectationResults,
  fetchLinkExpectationsForAttackChainRun,
} from '../../../../../actions/attack_chain_runs/attack_chain_run-action';
import { type AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { fetchAttackChainRunAttackChainNodes } from '../../../../../actions/AttackChainNode';
import { fetchAttackChainRunAttackChainNodeExpectations, fetchAttackChainRunTeams } from '../../../../../actions/AttackChainRun';
import { fetchAttackChainRunDocuments } from '../../../../../actions/documents/documents-actions';
import { testAttackChainNode } from '../../../../../actions/node_test/attack_chain_run-node-test-actions';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import { fetchVariablesForAttackChainRun } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainNode, type AttackChainNodeExpectation, type AttackChainRun, type NodeExpectationResultsByAttackPattern } from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';
import endpointContextForAttackChainRun from '../../../../../utils/context/endpoint/EndpointContextForAttackChainRun';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import AttackChainNodeDistributionByTeam from '../../../common/attack_chain_nodes/AttackChainNodeDistributionByTeam';
import AttackChainNodeDistributionByType from '../../../common/attack_chain_nodes/AttackChainNodeDistributionByType';
import AttackChainNodes from '../../../common/attack_chain_nodes/AttackChainNodes';
import {
  AttackChainNodeTestContext,
  type AttackChainNodeTestContextType,
  TeamContext,
  ViewModeContext,
} from '../../../common/Context';
import MitreMatrix from '../../../common/matrix/MitreMatrix';
import AttackChainRunDistributionScoreByTeamInPercentage from '../overview/AttackChainRunDistributionScoreByTeamInPercentage';
import AttackChainRunDistributionScoreOverTimeByInjectorContract from '../overview/AttackChainRunDistributionScoreOverTimeByNodeContract';
import AttackChainRunDistributionScoreOverTimeByTeam from '../overview/AttackChainRunDistributionScoreOverTimeByTeam';
import AttackChainRunDistributionScoreOverTimeByTeamInPercentage from '../overview/AttackChainRunDistributionScoreOverTimeByTeamInPercentage';
import LinkExpectationPanel from '../runtime/LinkExpectationPanel';
import { toLinkExpectationItems } from '../runtime/linkExpectationsAdapter';
import { buildRuntimeNodeMap, type RuntimeNodeInput } from '../runtime/runtimeNodeAdapter';
import { RuntimeNodeContext } from '../runtime/RuntimeNodeContext';
import VerdictBanner from '../runtime/VerdictBanner';
import toVerdictBannerData from '../runtime/verdictBannerAdapter';
import teamContextForAttackChainRun from '../teams/teamContextForAttackChainRun';

const useStyles = makeStyles()(() => ({
  paperChart: {
    position: 'relative',
    padding: '0 20px 0 0',
    overflow: 'hidden',
    height: '100%',
  },
}));

const AttackChainRunAttackChainNodes: FunctionComponent = () => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const availableButtons = ['chain', 'list', 'distribution', 'matrix'];
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };

  const [viewMode, setViewMode] = useState(() => {
    const storedValue = localStorage.getItem('attack_chain_or_attack_chain_run_view_mode');
    return storedValue === null || !availableButtons.includes(storedValue) ? 'list' : storedValue;
  });

  const handleViewMode = (mode: string) => {
    localStorage.setItem('attack_chain_or_attack_chain_run_view_mode', mode);
    setViewMode(mode);
  };

  const { attack_chain_run, teams, variables, attack_chain_nodes, node_expectations } = useHelper(
    (helper: AttackChainRunsHelper & VariablesHelper & TeamsHelper & AttackChainNodeHelper) => {
      return {
        attack_chain_run: helper.getAttackChainRun(exerciseId),
        teams: helper.getAttackChainRunTeams(exerciseId),
        variables: helper.getAttackChainRunVariables(exerciseId),
        attack_chain_nodes: helper.getAttackChainRunAttackChainNodes(exerciseId),
        node_expectations: helper.getAttackChainRunAttackChainNodeExpectations(exerciseId),
      };
    },
  );
  useDataLoader(() => {
    dispatch(fetchAttackChainRunTeams(exerciseId));
    dispatch(fetchVariablesForAttackChainRun(exerciseId));
    dispatch(fetchAttackChainRunDocuments(exerciseId));
    dispatch(fetchAttackChainRunAttackChainNodes(exerciseId));
    dispatch(fetchAttackChainRunAttackChainNodeExpectations(exerciseId));
  });

  // matrix view state
  const [matrixMode, setMatrixMode] = useState<'compact' | 'full'>('compact');
  const [verdictDimension, setVerdictDimension] = useState<'prevention' | 'detection'>('prevention');
  const [attackPatternResults, setAttackPatternResults] = useState<NodeExpectationResultsByAttackPattern[]>([]);

  // 链路级 SOC expectation —— simpleCall 不进 redux helper，本地 state 维护.
  const [linkExpectations, setLinkExpectations] = useState<AttackChainLinkExpectationWire[]>([]);
  const [panelOpen, setPanelOpen] = useState(false);
  useEffect(() => {
    let cancelled = false;
    fetchLinkExpectationsForAttackChainRun(exerciseId)
      .then((response) => {
        if (cancelled || !response) return;
        const data = (response as { data?: AttackChainLinkExpectationWire[] }).data;
        setLinkExpectations(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        // simpleCall 已 notifyErrorHandler；不二次显示
      });
    return () => {
      cancelled = true;
    };
  }, [exerciseId]);

  useEffect(() => {
    if (viewMode !== 'matrix') return () => {};
    let cancelled = false;
    fetchAttackChainRunAttackChainNodeExpectationResults(exerciseId)
      .then((result: { data: NodeExpectationResultsByAttackPattern[] }) => {
        if (cancelled || !result) return;
        setAttackPatternResults(Array.isArray(result.data) ? result.data : []);
      })
      .catch(() => {
        // simpleCall 已 notifyErrorHandler；不二次显示
      });
    return () => {
      cancelled = true;
    };
  }, [exerciseId, viewMode]);

  const runtimeNodeInputs = useMemo<RuntimeNodeInput[]>(() => {
    const list: AttackChainNode[] = attack_chain_nodes ?? [];
    return list.map((node): RuntimeNodeInput => ({
      nodeId: node.node_id,
      title: node.node_title,
      subtitle: node.node_injector_contract?.injector_contract_id,
      nodeState: node.node_node_state,
      repeatCount: node.node_repeat_count,
      currentIteration: node.node_current_iteration,
    }));
  }, [attack_chain_nodes]);

  const runtimeMap = useMemo(() => {
    const expectations: AttackChainNodeExpectation[] = node_expectations ?? [];
    return buildRuntimeNodeMap(runtimeNodeInputs, expectations);
  }, [runtimeNodeInputs, node_expectations]);

  const verdictData = useMemo(
    () => toVerdictBannerData(attack_chain_run, runtimeMap, linkExpectations),
    [attack_chain_run, runtimeMap, linkExpectations],
  );

  const linkExpectationItems = useMemo(
    () => toLinkExpectationItems(linkExpectations),
    [linkExpectations],
  );

  const teamContext = teamContextForAttackChainRun(
    exerciseId,
    attack_chain_run.attack_chain_run_teams_users,
    attack_chain_run.attack_chain_run_all_users_number,
    attack_chain_run.attack_chain_run_users_number,
  );
  const endpointContext = endpointContextForAttackChainRun(exerciseId);

  const injectTestContext: AttackChainNodeTestContextType = {
    contextId: exerciseId,
    url: `/admin/attack_chain_runs/${exerciseId}/tests/`,
    testAttackChainNode: testAttackChainNode,
  };

  return (
    <ViewModeContext.Provider value={viewMode}>
      {(viewMode === 'list' || viewMode === 'chain') && (
        <RuntimeNodeContext.Provider value={{ runtimeByNodeId: runtimeMap }}>
          <VerdictBanner data={verdictData} onClickDetection={() => setPanelOpen(true)} />
          <TeamContext.Provider value={teamContext}>
            <EndpointContext.Provider value={endpointContext}>
              <AttackChainNodeTestContext.Provider value={injectTestContext}>
                <AttackChainNodes
                  setViewMode={handleViewMode}
                  availableButtons={availableButtons}
                  teams={teams}
                  variables={variables}
                  uriVariable={`/admin/attack_chain_runs/${exerciseId}/definition`}
                />
              </AttackChainNodeTestContext.Provider>
            </EndpointContext.Provider>
          </TeamContext.Provider>
          <Drawer
            anchor="right"
            open={panelOpen}
            onClose={() => setPanelOpen(false)}
            PaperProps={{
              sx: {
                width: {
                  xs: '100%',
                  sm: 420,
                },
              },
            }}
          >
            <LinkExpectationPanel items={linkExpectationItems} />
          </Drawer>
        </RuntimeNodeContext.Provider>
      )}
      {viewMode === 'distribution' && (
        <div style={{ marginTop: -12 }}>
          <ToggleButtonGroup
            size="small"
            exclusive={true}
            style={{ float: 'right' }}
            aria-label="Change view mode"
          >
            <Tooltip title={t('List view')}>
              <ToggleButton
                value="list"
                onClick={() => handleViewMode('list')}
                selected={false}
                aria-label="List view mode"
              >
                <ReorderOutlined fontSize="small" color="primary" />
              </ToggleButton>
            </Tooltip>
            <Tooltip title={t('Interactive view')}>
              <ToggleButton
                value="chain"
                onClick={() => handleViewMode('chain')}
                selected={false}
                aria-label="Interactive view mode"
              >
                <ViewTimelineOutlined fontSize="small" color="primary" />
              </ToggleButton>
            </Tooltip>
            <Tooltip title={t('Distribution view')}>
              <ToggleButton
                value="distribution"
                onClick={() => handleViewMode('distribution')}
                selected={true}
                aria-label="Distribution view mode"
              >
                <BarChartOutlined fontSize="small" color="inherit" />
              </ToggleButton>
            </Tooltip>
            <Tooltip title={t('Attack pattern matrix')}>
              <ToggleButton
                value="matrix"
                onClick={() => handleViewMode('matrix')}
                selected={false}
                aria-label="Attack pattern matrix"
              >
                <GridOnOutlined fontSize="small" color="primary" />
              </ToggleButton>
            </Tooltip>
          </ToggleButtonGroup>
          <GridLegacy container spacing={3}>
            <GridLegacy container item spacing={3}>
              <GridLegacy
                item
                xs={6}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of nodes by type')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainNodeDistributionByType exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={6}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of nodes by team')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainNodeDistributionByTeam exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expectations by node type')}
                  {' '}
                  (%)
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainRunDistributionScoreByTeamInPercentage exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expected total score by node type')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainRunDistributionScoreOverTimeByInjectorContract exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expectations by team')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainRunDistributionScoreOverTimeByTeam exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expected total score by team')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainRunDistributionScoreOverTimeByTeamInPercentage exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
            </GridLegacy>
          </GridLegacy>
        </div>
      )}
      {viewMode === 'matrix' && (
        <div style={{ marginTop: -12 }}>
          <Stack
            direction="row"
            spacing={1}
            sx={{
              float: 'right',
              marginBottom: 1,
            }}
          >
            <ToggleButtonGroup
              size="small"
              exclusive={true}
              aria-label="Change view mode"
            >
              <Tooltip title={t('List view')}>
                <ToggleButton
                  value="list"
                  onClick={() => handleViewMode('list')}
                  selected={false}
                  aria-label="List view mode"
                >
                  <ReorderOutlined fontSize="small" color="primary" />
                </ToggleButton>
              </Tooltip>
              <Tooltip title={t('Interactive view')}>
                <ToggleButton
                  value="chain"
                  onClick={() => handleViewMode('chain')}
                  selected={false}
                  aria-label="Interactive view mode"
                >
                  <ViewTimelineOutlined fontSize="small" color="primary" />
                </ToggleButton>
              </Tooltip>
              <Tooltip title={t('Distribution view')}>
                <ToggleButton
                  value="distribution"
                  onClick={() => handleViewMode('distribution')}
                  selected={false}
                  aria-label="Distribution view mode"
                >
                  <BarChartOutlined fontSize="small" color="primary" />
                </ToggleButton>
              </Tooltip>
              <Tooltip title={t('Attack pattern matrix')}>
                <ToggleButton
                  value="matrix"
                  onClick={() => handleViewMode('matrix')}
                  selected={true}
                  aria-label="Attack pattern matrix"
                >
                  <GridOnOutlined fontSize="small" color="inherit" />
                </ToggleButton>
              </Tooltip>
            </ToggleButtonGroup>
            <ToggleButtonGroup
              size="small"
              exclusive
              value={matrixMode}
              onChange={(_e, v) => v && setMatrixMode(v)}
            >
              <ToggleButton value="compact" aria-label="Compact view">{t('Compact view')}</ToggleButton>
              <ToggleButton value="full" aria-label="Full ATT&CK matrix">{t('Full ATT&CK matrix')}</ToggleButton>
            </ToggleButtonGroup>
            <ToggleButtonGroup
              size="small"
              exclusive
              value={verdictDimension}
              onChange={(_e, v) => v && setVerdictDimension(v)}
            >
              <ToggleButton value="prevention" aria-label="By prevention">{t('By prevention')}</ToggleButton>
              <ToggleButton value="detection" aria-label="By detection">{t('By detection')}</ToggleButton>
            </ToggleButtonGroup>
          </Stack>
          <Typography variant="h4">{t('Attack pattern matrix')}</Typography>
          <Paper variant="outlined" sx={{ padding: 2 }}>
            <MitreMatrix
              injectResults={attackPatternResults}
              mode={matrixMode}
              coloringScheme="verdict"
              verdictDimension={verdictDimension}
              goToLink={`/admin/attack_chain_runs/${exerciseId}/nodes`}
            />
          </Paper>
        </div>
      )}
    </ViewModeContext.Provider>
  );
};

export default AttackChainRunAttackChainNodes;
