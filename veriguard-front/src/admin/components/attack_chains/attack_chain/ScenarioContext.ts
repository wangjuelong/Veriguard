import { useState } from 'react';

import { type AttackChainNodeOutputType, type AttackChainNodeStore } from '../../../../actions/attack_chain_nodes/AttackChainNode';
import { dryImportXlsForAttackChain, fetchAttackChain, fetchAttackChainTeams, importXlsForAttackChain } from '../../../../actions/attack_chains/attack_chain-actions';
import { createAttackChainNodesForAttackChain, importAttackChainNodesForAttackChain, searchAttackChainAttackChainNodesSimple } from '../../../../actions/attack_chains/attack_chain-node-actions';
import { addAttackChainNodeForAttackChain, bulkDeleteAttackChainNodesSimple, bulkUpdateAttackChainNodeSimple, deleteAttackChainNodeAttackChain, fetchAttackChainAttackChainNodes, updateAttackChainNodeActivationForAttackChain, updateAttackChainNodeForAttackChain } from '../../../../actions/AttackChainNode';
import { bulkTestAttackChainNodes } from '../../../../actions/node_test/attack_chain-node-test-actions';
import { type Page } from '../../../../components/common/queryable/Page';
import { type ImportTestSummary, type AttackChainNode, type AttackChainNodeBulkProcessingInput, type AttackChainNodeBulkUpdateInputs, type AttackChainNodeInput, type AttackChainNodesImportInput, type AttackChainNodeTestStatusOutput, type AttackChain, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

const injectContextForAttackChain = (attack_chain: AttackChain) => {
  const dispatch = useAppDispatch();
  const [nodes, setAttackChainNodes] = useState<AttackChainNodeOutputType[]>([]);

  return {
    nodes,
    setAttackChainNodes,
    searchAttackChainNodes(input: SearchPaginationInput): Promise<{ data: Page<AttackChainNodeOutputType> }> {
      return searchAttackChainAttackChainNodesSimple(attack_chain.attack_chain_id, input);
    },
    onAddAttackChainNode(node: AttackChainNode): Promise<{
      result: string;
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }> {
      return dispatch(addAttackChainNodeForAttackChain(attack_chain.attack_chain_id, node));
    },

    onAddMultipleAttackChainNodes(inputs: AttackChainNodeInput[]): Promise<{
      result: string[];
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }> {
      return dispatch(createAttackChainNodesForAttackChain(attack_chain.attack_chain_id, inputs));
    },
    onBulkUpdateAttackChainNode(param: AttackChainNodeBulkUpdateInputs): Promise<AttackChainNode[] | void> {
      return bulkUpdateAttackChainNodeSimple(param).then((result: { data: AttackChainNode[] }) => result?.data);
    },
    onUpdateAttackChainNode(injectId: AttackChainNode['node_id'], node: AttackChainNode): Promise<{
      result: string;
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }> {
      return dispatch(updateAttackChainNodeForAttackChain(attack_chain.attack_chain_id, injectId, node));
    },
    onUpdateAttackChainNodeActivation(injectId: AttackChainNode['node_id'], injectEnabled: { node_enabled: boolean }): Promise<{
      result: string;
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }> {
      return dispatch(updateAttackChainNodeActivationForAttackChain(attack_chain.attack_chain_id, injectId, injectEnabled));
    },
    onDeleteAttackChainNode(injectId: AttackChainNode['node_id']): Promise<void> {
      return dispatch(deleteAttackChainNodeAttackChain(attack_chain.attack_chain_id, injectId));
    },
    onImportAttackChainNodeFromJson(file: File): Promise<void> {
      return importAttackChainNodesForAttackChain(attack_chain.attack_chain_id, file).then(response => new Promise((resolve, _reject) => {
        dispatch(fetchAttackChainAttackChainNodes(attack_chain.attack_chain_id));
        dispatch(fetchAttackChain(attack_chain.attack_chain_id));
        dispatch(fetchAttackChainTeams(attack_chain.attack_chain_id));
        resolve(response.data);
      }));
    },
    onImportAttackChainNodeFromXls(importId: string, input: AttackChainNodesImportInput): Promise<ImportTestSummary> {
      return importXlsForAttackChain(attack_chain.attack_chain_id, importId, input).then(response => new Promise((resolve, _reject) => {
        dispatch(fetchAttackChainAttackChainNodes(attack_chain.attack_chain_id));
        dispatch(fetchAttackChain(attack_chain.attack_chain_id));
        dispatch(fetchAttackChainTeams(attack_chain.attack_chain_id));
        resolve(response.data);
      }));
    },
    async onDryImportAttackChainNodeFromXls(importId: string, input: AttackChainNodesImportInput): Promise<ImportTestSummary> {
      return dryImportXlsForAttackChain(attack_chain.attack_chain_id, importId, input).then(result => result.data);
    },
    onBulkDeleteAttackChainNodes(param: AttackChainNodeBulkProcessingInput): Promise<AttackChainNode[]> {
      return bulkDeleteAttackChainNodesSimple(param).then((result: { data: AttackChainNode[] }) => result?.data);
    },
    bulkTestAttackChainNodes(param: AttackChainNodeBulkProcessingInput): Promise<{
      uri: string;
      data: AttackChainNodeTestStatusOutput[];
    }> {
      return bulkTestAttackChainNodes(attack_chain.attack_chain_id, param).then(result => ({
        uri: `/admin/attack_chains/${attack_chain.attack_chain_id}/tests`,
        data: result.data,
      }));
    },
  };
};

export default injectContextForAttackChain;
