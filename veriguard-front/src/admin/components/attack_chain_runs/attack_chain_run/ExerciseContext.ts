import { useState } from 'react';

import { type AttackChainNodeOutputType, type AttackChainNodeStore } from '../../../../actions/attack_chain_nodes/AttackChainNode';
import { dryImportXlsForAttackChainRun, importXlsForAttackChainRun } from '../../../../actions/attack_chain_runs/attack_chain_run-action';
import { createAttackChainNodesForSimulation, importAttackChainNodesForSimulation, searchAttackChainRunAttackChainNodesSimple } from '../../../../actions/attack_chain_runs/attack_chain_run-node-actions';
import {
  addAttackChainNodeForAttackChainRun,
  bulkDeleteAttackChainNodesSimple,
  bulkUpdateAttackChainNodeSimple,
  deleteAttackChainNodeForAttackChainRun,
  fetchAttackChainRunAttackChainNodes,
  injectDone,
  updateAttackChainNodeActivationForAttackChainRun,
  updateAttackChainNodeForAttackChainRun,
  updateAttackChainNodeTriggerForAttackChainRun,
} from '../../../../actions/AttackChainNode';
import { fetchAttackChainRun, fetchAttackChainRunTeams } from '../../../../actions/AttackChainRun';
import { bulkTestAttackChainNodes } from '../../../../actions/node_test/attack_chain_run-node-test-actions';
import { type Page } from '../../../../components/common/queryable/Page';
import {
  type AttackChainRun,
  type ImportTestSummary,
  type AttackChainNode,
  type AttackChainNodeBulkProcessingInput,
  type AttackChainNodeBulkUpdateInputs, type AttackChainNodeInput,
  type AttackChainNodesImportInput,
  type AttackChainNodeTestStatusOutput,
  type SearchPaginationInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

const injectContextForAttackChainRun = (attack_chain_run: AttackChainRun) => {
  const dispatch = useAppDispatch();
  const [nodes, setAttackChainNodes] = useState<AttackChainNodeOutputType[]>([]);

  return {
    nodes,
    setAttackChainNodes,
    searchAttackChainNodes(input: SearchPaginationInput): Promise<{ data: Page<AttackChainNodeOutputType> }> {
      return searchAttackChainRunAttackChainNodesSimple(attack_chain_run.attack_chain_run_id, input);
    },
    onAddAttackChainNode(node: AttackChainNode): Promise<{
      result: string;
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }> {
      return dispatch(addAttackChainNodeForAttackChainRun(attack_chain_run.attack_chain_run_id, node));
    },
    onAddMultipleAttackChainNodes(inputs: AttackChainNodeInput[]): Promise<{
      result: string[];
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }> {
      return dispatch(createAttackChainNodesForSimulation(attack_chain_run.attack_chain_run_id, inputs));
    },
    onBulkUpdateAttackChainNode(param: AttackChainNodeBulkUpdateInputs): Promise<AttackChainNode[] | void> {
      return bulkUpdateAttackChainNodeSimple(param).then((result: { data: AttackChainNode[] }) => result?.data);
    },
    onUpdateAttackChainNode(injectId: AttackChainNode['node_id'], node: AttackChainNode): Promise<{
      result: string;
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }> {
      return dispatch(updateAttackChainNodeForAttackChainRun(attack_chain_run.attack_chain_run_id, injectId, node));
    },
    onUpdateAttackChainNodeTrigger(injectId: AttackChainNode['node_id']): Promise<{
      result: string;
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }> {
      return dispatch(updateAttackChainNodeTriggerForAttackChainRun(attack_chain_run.attack_chain_run_id, injectId));
    },
    onUpdateAttackChainNodeActivation(injectId: AttackChainNode['node_id'], injectEnabled: { node_enabled: boolean }): Promise<{
      result: string;
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }> {
      return dispatch(updateAttackChainNodeActivationForAttackChainRun(attack_chain_run.attack_chain_run_id, injectId, injectEnabled));
    },
    onAttackChainNodeDone(injectId: AttackChainNode['node_id']): Promise<{
      result: string;
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }> {
      return dispatch(injectDone(attack_chain_run.attack_chain_run_id, injectId));
    },
    onDeleteAttackChainNode(injectId: AttackChainNode['node_id']): Promise<void> {
      return dispatch(deleteAttackChainNodeForAttackChainRun(attack_chain_run.attack_chain_run_id, injectId));
    },
    onImportAttackChainNodeFromJson(file: File): Promise<void> {
      return importAttackChainNodesForSimulation(attack_chain_run.attack_chain_run_id, file).then(response => new Promise((resolve, _reject) => {
        dispatch(fetchAttackChainRunAttackChainNodes(attack_chain_run.attack_chain_run_id));
        dispatch(fetchAttackChainRun(attack_chain_run.attack_chain_run_id));
        dispatch(fetchAttackChainRunTeams(attack_chain_run.attack_chain_run_id));
        resolve(response.data);
      }));
    },
    onImportAttackChainNodeFromXls(importId: string, input: AttackChainNodesImportInput): Promise<ImportTestSummary> {
      return importXlsForAttackChainRun(attack_chain_run.attack_chain_run_id, importId, input).then(response => new Promise((resolve, _reject) => {
        dispatch(fetchAttackChainRunAttackChainNodes(attack_chain_run.attack_chain_run_id));
        dispatch(fetchAttackChainRun(attack_chain_run.attack_chain_run_id));
        dispatch(fetchAttackChainRunTeams(attack_chain_run.attack_chain_run_id));
        resolve(response.data);
      }));
    },
    async onDryImportAttackChainNodeFromXls(importId: string, input: AttackChainNodesImportInput): Promise<ImportTestSummary> {
      return dryImportXlsForAttackChainRun(attack_chain_run.attack_chain_run_id, importId, input).then(result => result.data);
    },
    onBulkDeleteAttackChainNodes(param: AttackChainNodeBulkProcessingInput): Promise<AttackChainNode[]> {
      return bulkDeleteAttackChainNodesSimple(param).then((result: { data: AttackChainNode[] }) => result?.data);
    },
    bulkTestAttackChainNodes(param: AttackChainNodeBulkProcessingInput): Promise<{
      uri: string;
      data: AttackChainNodeTestStatusOutput[];
    }> {
      return bulkTestAttackChainNodes(attack_chain_run.attack_chain_run_id, param).then(result => ({
        uri: `/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/tests`,
        data: result.data,
      }));
    },
  };
};

export default injectContextForAttackChainRun;
