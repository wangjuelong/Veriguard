import { type FunctionComponent, useState } from 'react';
import { useParams } from 'react-router';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import { fetchAttackChainTeams } from '../../../../../actions/attack_chains/attack_chain-actions';
import { type AttackChainsHelper } from '../../../../../actions/attack_chains/attack_chain-helper';
import { fetchAttackChainAttackChainNodesSimple } from '../../../../../actions/attack_chains/attack_chain-node-actions';
import { fetchAttackChainDocuments } from '../../../../../actions/documents/documents-actions';
import { testAttackChainNode } from '../../../../../actions/node_test/attack_chain-node-test-actions';
import type { TeamsHelper } from '../../../../../actions/teams/team-helper';
import { fetchVariablesForAttackChain } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { useHelper } from '../../../../../store';
import { type AttackChain } from '../../../../../utils/api-types';
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
import teamContextForAttackChain from '../teams/teamContextForAttackChain';

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

  return (
    <ViewModeContext.Provider value={viewMode}>
      <TeamContext.Provider value={teamContext}>
        <EndpointContext.Provider value={endpointContext}>
          <AttackChainNodeTestContext.Provider value={injectTestContext}>
            <AttackChainNodes
              teams={teams}
              variables={variables}
              uriVariable={`/admin/attack_chains/${scenarioId}/definition`}
              setViewMode={handleViewMode}
              availableButtons={availableButtons}
            />
          </AttackChainNodeTestContext.Provider>
        </EndpointContext.Provider>
      </TeamContext.Provider>
    </ViewModeContext.Provider>
  );
};

export default AttackChainAttackChainNodes;
