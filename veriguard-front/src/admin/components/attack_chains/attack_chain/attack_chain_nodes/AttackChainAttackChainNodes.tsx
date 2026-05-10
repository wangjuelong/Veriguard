/* eslint-disable i18next/no-literal-string -- Phase 12b-B 二开 UI 硬编码中文，未来统一 i18n 清洗。 */
import { Settings as SettingsIcon } from '@mui/icons-material';
import { Box, Button, Stack } from '@mui/material';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';

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
import { type AttackChainSettingsValue, type ParameterSetOption } from '../editor/attackChainEditorTypes';
import { toApiInput, toParameterSetOption, toSettingsValue } from '../editor/attackChainSettingsAdapters';
import AttackChainSettingsDrawer from '../editor/AttackChainSettingsDrawer';
import teamContextForAttackChain from '../teams/teamContextForAttackChain';

const PARAMETER_SET_FETCH_SIZE = 100;

const VALIDATION_PARAMETER_SET_SEARCH_URI = '/api/validation_parameter_sets/search';

const AttackChainAttackChainNodes: FunctionComponent = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const { scenarioId } = useParams() as { scenarioId: AttackChain['attack_chain_id'] };

  const availableButtons = ['chain', 'list'];

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
  const [settingsOpen, setSettingsOpen] = useState(false);
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
    if (settingsOpen && parameterSetOptions.length === 0) {
      loadParameterSetOptions();
    }
    if (settingsOpen && healthyConnectorIds.length === 0) {
      loadHealthyConnectorIds();
    }
  }, [
    settingsOpen,
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

  return (
    <ViewModeContext.Provider value={viewMode}>
      <TeamContext.Provider value={teamContext}>
        <EndpointContext.Provider value={endpointContext}>
          <AttackChainNodeTestContext.Provider value={injectTestContext}>
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
            <AttackChainNodes
              teams={teams}
              variables={variables}
              uriVariable={`/admin/attack_chains/${scenarioId}/definition`}
              setViewMode={handleViewMode}
              availableButtons={availableButtons}
            />
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
          </AttackChainNodeTestContext.Provider>
        </EndpointContext.Provider>
      </TeamContext.Provider>
    </ViewModeContext.Provider>
  );
};

export default AttackChainAttackChainNodes;
