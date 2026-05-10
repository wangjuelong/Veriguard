/* eslint-disable i18next/no-literal-string -- Phase 12b-B 二开 UI 硬编码中文，未来统一 i18n 清洗。 */
import { GridOnOutlined, ReorderOutlined, Settings as SettingsIcon, ViewTimelineOutlined } from '@mui/icons-material';
import { Box, Button, Paper, Stack, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { updateAttackChainEdgeCondition } from '../../../../../actions/attack_chain_edges/edge-action';
import { type AttackChainNodeOutputType } from '../../../../../actions/attack_chain_nodes/AttackChainNode';
import { updateAttackChainNodeSettings } from '../../../../../actions/attack_chain_nodes/node-action';
import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import {
  fetchAttackChain,
  fetchAttackChainTeams,
  updateAttackChainSettings,
} from '../../../../../actions/attack_chains/attack_chain-actions';
import { type AttackChainsHelper } from '../../../../../actions/attack_chains/attack_chain-helper';
import { fetchAttackChainAttackChainNodesSimple } from '../../../../../actions/attack_chains/attack_chain-node-actions';
import { fetchAttackChainDocuments } from '../../../../../actions/documents/documents-actions';
import { testAttackChainNode } from '../../../../../actions/node_test/attack_chain-node-test-actions';
import {
  fetchSocConnectors,
  type SocConnectorOutputDto,
} from '../../../../../actions/soc_connectors/soc-connector-actions';
import type { TeamsHelper } from '../../../../../actions/teams/team-helper';
import { fetchVariablesForAttackChain } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { initSorting } from '../../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { simplePostCall } from '../../../../../utils/Action';
import {
  type AttackChain,
  type PageValidationParameterSetOutput,
} from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';
import endpointContextForAttackChain from '../../../../../utils/context/endpoint/EndpointContextForAttackChain';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import AttackChainNodes from '../../../common/attack_chain_nodes/AttackChainNodes';
import {
  AttackChainNodeTestContext,
  type AttackChainNodeTestContextType,
  TeamContext,
  ViewModeContext,
} from '../../../common/Context';
import { toAttackPatternResultsForEditor } from '../../../common/matrix/attackPatternMatrixAdapter';
import MitreMatrix from '../../../common/matrix/MitreMatrix';
import { fromEdgeConditionDto, toEdgeConditionDto } from '../editor/attackChainEdgeConditionAdapters';
import { AttackChainEdgeConditionContext } from '../editor/AttackChainEdgeConditionContext';
import {
  type AttackChainNodeEditValue,
  type AttackChainSettingsValue,
  type EdgeConditionTree,
  type ParameterSetOption,
} from '../editor/attackChainEditorTypes';
import { toNodeEditValue, toNodeSettingsInput } from '../editor/attackChainNodeEditAdapters';
import { AttackChainNodeSettingsContext } from '../editor/AttackChainNodeSettingsContext';
import { toApiInput, toParameterSetOption, toSettingsValue } from '../editor/attackChainSettingsAdapters';
import AttackChainSettingsDrawer from '../editor/AttackChainSettingsDrawer';
import ConditionEdgePopover from '../editor/ConditionEdgePopover';
import NodeEditDrawer from '../editor/NodeEditDrawer';
import teamContextForAttackChain from '../teams/teamContextForAttackChain';

const PARAMETER_SET_FETCH_SIZE = 100;

const VALIDATION_PARAMETER_SET_SEARCH_URI = '/api/validation_parameter_sets/search';

const AttackChainAttackChainNodes: FunctionComponent = () => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { scenarioId } = useParams() as { scenarioId: AttackChain['attack_chain_id'] };

  const availableButtons = ['chain', 'list', 'matrix'];

  const { attack_chain, teams, variables } = useHelper(
    (helper: AttackChainNodeHelper & AttackChainsHelper & VariablesHelper & TeamsHelper) => {
      return {
        attack_chain: helper.getAttackChain(scenarioId),
        teams: helper.getAttackChainTeams(scenarioId),
        variables: helper.getAttackChainVariables(scenarioId),
      };
    },
  );
  useDataLoader(() => {
    dispatch(fetchAttackChainAttackChainNodesSimple(scenarioId));
    dispatch(fetchAttackChainTeams(scenarioId));
    dispatch(fetchVariablesForAttackChain(scenarioId));
    dispatch(fetchAttackChainDocuments(scenarioId));
  });

  const teamContext = teamContextForAttackChain(
    scenarioId,
    attack_chain.attack_chain_teams_users,
    attack_chain.attack_chain_all_users_number,
    attack_chain.attack_chain_users_number,
  );
  const endpointContext = endpointContextForAttackChain(scenarioId);

  const [viewMode, setViewMode] = useState(() => {
    const storedValue = localStorage.getItem('attack_chain_or_attack_chain_run_view_mode');
    return storedValue === null || !availableButtons.includes(storedValue) ? 'list' : storedValue;
  });

  const [matrixMode, setMatrixMode] = useState<'compact' | 'full'>('compact');

  const handleViewMode = (mode: string) => {
    setViewMode(mode);
    localStorage.setItem('attack_chain_or_attack_chain_run_view_mode', mode);
  };
  const injectTestContext: AttackChainNodeTestContextType
    = {
      contextId: scenarioId,
      url: `/admin/attack_chains/${scenarioId}/tests/`,
      testAttackChainNode: testAttackChainNode,
    };

  // -- 攻击编排链路级设置 drawer (Phase 12b-B3) --
  // -- 节点级 NodeEditDrawer + NodeBadge (Phase 12b-B3.5a) --
  // -- 边级 ConditionEdgePopover (Phase 12b-B3.5b) --
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [editingNode, setEditingNode] = useState<AttackChainNodeOutputType | null>(null);
  const [editingEdge, setEditingEdge] = useState<{
    edgeId: string;
    anchor: HTMLElement;
    initialValue: EdgeConditionTree;
  } | null>(null);
  const [parameterSetOptions, setParameterSetOptions] = useState<ParameterSetOption[]>([]);
  const [healthyConnectorIds, setHealthyConnectorIds] = useState<string[]>([]);
  const loadParameterSetOptions = useCallback(async () => {
    const response: { data: PageValidationParameterSetOutput } = await simplePostCall(
      VALIDATION_PARAMETER_SET_SEARCH_URI,
      buildSearchPagination({
        size: PARAMETER_SET_FETCH_SIZE,
        sorts: initSorting('parameter_set_name', 'ASC'),
      }),
    );
    setParameterSetOptions((response.data.content ?? []).map(toParameterSetOption));
  }, []);
  const loadHealthyConnectorIds = useCallback(async () => {
    const response: { data: SocConnectorOutputDto[] } = await fetchSocConnectors();
    const healthy = (response.data ?? [])
      .filter(c => c.status === 'HEALTHY')
      .map(c => c.connector_id);
    setHealthyConnectorIds(healthy);
  }, []);
  useEffect(() => {
    const drawerOpen = settingsOpen || editingNode !== null;
    if (drawerOpen && parameterSetOptions.length === 0) {
      loadParameterSetOptions();
    }
    if (drawerOpen && healthyConnectorIds.length === 0) {
      loadHealthyConnectorIds();
    }
  }, [
    settingsOpen,
    editingNode,
    parameterSetOptions.length,
    healthyConnectorIds.length,
    loadParameterSetOptions,
    loadHealthyConnectorIds,
  ]);

  const handleSettingsSubmit = async (value: AttackChainSettingsValue) => {
    await dispatch(updateAttackChainSettings(scenarioId, toApiInput(value)));
    setSettingsOpen(false);
    await dispatch(fetchAttackChain(scenarioId));
  };

  const handleNodeSettingsSubmit = async (value: AttackChainNodeEditValue) => {
    if (!editingNode) return;
    await updateAttackChainNodeSettings(editingNode.node_id, toNodeSettingsInput(value));
    setEditingNode(null);
    await dispatch(fetchAttackChainAttackChainNodesSimple(scenarioId));
  };

  const allNodes = useHelper((helper: AttackChainNodeHelper) =>
    helper.getAttackChainAttackChainNodes(scenarioId),
  );

  const editorMatrixResults = useMemo(
    () => toAttackPatternResultsForEditor(allNodes ?? []),
    [allNodes],
  );

  const findEdgeCondition = useCallback(
    (edgeId: string): EdgeConditionTree => {
      const defaultTree: EdgeConditionTree = {
        kind: 'group',
        op: 'AND',
        children: [],
      };
      for (const node of allNodes ?? []) {
        for (const edge of node.node_depends_on ?? []) {
          if (edge.edge_id === edgeId) {
            return edge.dependency_condition
              ? fromEdgeConditionDto(edge.dependency_condition)
              : defaultTree;
          }
        }
      }
      return defaultTree;
    },
    [allNodes],
  );

  const handleOpenEdgeCondition = useCallback(
    (edgeId: string, anchor: HTMLElement) => {
      setEditingEdge({
        edgeId,
        anchor,
        initialValue: findEdgeCondition(edgeId),
      });
    },
    [findEdgeCondition],
  );

  const handleEdgeConditionSubmit = async (value: EdgeConditionTree) => {
    if (!editingEdge) return;
    await updateAttackChainEdgeCondition(editingEdge.edgeId, { dependency_condition: toEdgeConditionDto(value) });
    setEditingEdge(null);
    await dispatch(fetchAttackChainAttackChainNodesSimple(scenarioId));
  };

  return (
    <ViewModeContext.Provider value={viewMode}>
      <TeamContext.Provider value={teamContext}>
        <EndpointContext.Provider value={endpointContext}>
          <AttackChainNodeTestContext.Provider value={injectTestContext}>
            <AttackChainEdgeConditionContext.Provider value={{ openEdgeCondition: handleOpenEdgeCondition }}>
              <AttackChainNodeSettingsContext.Provider value={{ openNodeSettings: setEditingNode }}>
                <Stack direction="row" justifyContent="flex-end" sx={{ mb: 1 }}>
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={<SettingsIcon fontSize="small" />}
                    onClick={() => setSettingsOpen(true)}
                  >
                    链路设置
                  </Button>
                </Stack>
                {(viewMode === 'chain' || viewMode === 'list') && (
                  <AttackChainNodes
                    teams={teams}
                    variables={variables}
                    uriVariable={`/admin/attack_chains/${scenarioId}/definition`}
                    setViewMode={handleViewMode}
                    availableButtons={availableButtons}
                  />
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
                        exclusive
                        value={matrixMode}
                        onChange={(_e, v) => v && setMatrixMode(v)}
                      >
                        <ToggleButton value="compact" aria-label="Compact view">{t('Compact view')}</ToggleButton>
                        <ToggleButton value="full" aria-label="Full ATT&CK matrix">{t('Full ATT&CK matrix')}</ToggleButton>
                      </ToggleButtonGroup>
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
                    </Stack>
                    <Typography variant="h4">{t('Attack pattern matrix')}</Typography>
                    <Paper variant="outlined" sx={{ padding: 2 }}>
                      <MitreMatrix
                        injectResults={editorMatrixResults}
                        mode={matrixMode}
                        coloringScheme="coverage"
                      />
                    </Paper>
                  </div>
                )}
                {settingsOpen && (
                  <Box>
                    <AttackChainSettingsDrawer
                      open
                      initialValue={toSettingsValue(attack_chain)}
                      parameterSetOptions={parameterSetOptions}
                      availableConnectorIds={healthyConnectorIds}
                      onCancel={() => setSettingsOpen(false)}
                      onSubmit={handleSettingsSubmit}
                    />
                  </Box>
                )}
                {editingNode && (
                  <NodeEditDrawer
                    open
                    initialValue={toNodeEditValue(editingNode)}
                    parameterSetOptions={parameterSetOptions}
                    onCancel={() => setEditingNode(null)}
                    onSubmit={handleNodeSettingsSubmit}
                  />
                )}
                {editingEdge && (
                  <ConditionEdgePopover
                    open
                    anchorEl={editingEdge.anchor}
                    initialValue={editingEdge.initialValue}
                    onCancel={() => setEditingEdge(null)}
                    onSubmit={handleEdgeConditionSubmit}
                  />
                )}
              </AttackChainNodeSettingsContext.Provider>
            </AttackChainEdgeConditionContext.Provider>
          </AttackChainNodeTestContext.Provider>
        </EndpointContext.Provider>
      </TeamContext.Provider>
    </ViewModeContext.Provider>
  );
};

export default AttackChainAttackChainNodes;
