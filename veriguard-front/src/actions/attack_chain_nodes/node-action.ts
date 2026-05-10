import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import {
  type AttackChainNodeExportFromSearchRequestInput,
  type AttackChainNodeExportRequestInput,
  type AttackChainNodeIndividualExportRequestInput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';

export const INJECT_URI = '/api/attack_chain_nodes';

/**
 * Wire-format snapshot for PUT /api/attack_chain_nodes/{id}/settings (Phase 12b-B3.5a).
 *
 * Mirrors backend io.veriguard.rest.attack_chain_node.form.AttackChainNodeSettingsInput;
 * not yet regenerated into api-types.d.ts. Kept as a thin transport DTO so the
 * editor NodeEditDrawer can submit V3 orchestration fields without touching the
 * existing AttackChainNodeInput surface (which carries inject_content / target /
 * contract — the execution-time identity of an inject).
 */
export interface AttackChainNodeSettingsInputDto {
  node_title: string;
  node_description: string | null;
  node_validation_parameter_set_id: string | null;
  node_repeat_count: number;
  node_repeat_interval_seconds: number;
}

export const updateAttackChainNodeSettings = (
  attackChainNodeId: string,
  data: AttackChainNodeSettingsInputDto,
) => {
  const uri = `${INJECT_URI}/${attackChainNodeId}/settings`;
  return simplePutCall(uri, data);
};

export const exportAttackChainNodeSearch = (data: AttackChainNodeExportFromSearchRequestInput) => {
  const uri = '/api/attack_chain_nodes/search/export';
  return simplePostCall(uri, data, { responseType: 'arraybuffer' }).catch((error) => {
    MESSAGING$.notifyError('Could not request export of nodes');
    throw error;
  });
};

export const exportAttackChainNodes = (data: AttackChainNodeExportRequestInput) => {
  const uri = '/api/attack_chain_nodes/export';
  return simplePostCall(uri, data, { responseType: 'arraybuffer' }).catch((error) => {
    MESSAGING$.notifyError('Could not request export of nodes');
    throw error;
  });
};

export const exportAttackChainNode = (injectId: string, data: AttackChainNodeIndividualExportRequestInput) => {
  const uri = `/api/attack_chain_nodes/${injectId}/node_export`;
  return simplePostCall(uri, data, { responseType: 'arraybuffer' }).catch((error) => {
    MESSAGING$.notifyError('Could not request export of node');
    throw error;
  });
};

// -- TARGETS --

export const searchTargets = (injectId: string, targetType: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `/api/attack_chain_nodes/${injectId}/targets/${targetType}/search`;
  return simplePostCall(uri, data);
};

export const searchTargetOptions = (injectId: string, targetType: string, searchText = '') => {
  const params = { searchText };
  const uri = `/api/attack_chain_nodes/${injectId}/targets/${targetType}/options`;
  return simpleCall(uri, { params });
};

export const searchTargetOptionsById = (targetType: string, ids: string[]) => {
  const data = ids;
  const uri = `/api/attack_chain_nodes/targets/${targetType}/options`;
  return simplePostCall(uri, data);
};

// -- OPTION --

export const searchAttackChainNodeLinkedToFindingsAsOption = (searchText: string = '', sourceId: string = '') => {
  const params = {
    searchText,
    sourceId,
  };
  return simpleCall(`${INJECT_URI}/findings/options`, { params });
};

export const searchAttackChainNodeByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${INJECT_URI}/options`, ids);
};

// -- EXECUTION TRACES --

export const getAttackChainNodeTracesFromAttackChainNodeAndTarget = (injectId: string = '', targetId: string = '', targetType: string = '') => {
  const params = {
    injectId,
    targetId,
    targetType,
  };
  return simpleCall(`${INJECT_URI}/execution-traces`, { params });
};
export const getAttackChainNodeStatusWithGlobalExecutionTraces = (injectId: string = '') => {
  const params = { injectId };
  return simpleCall(`${INJECT_URI}/status`, { params });
};

// Detection Remediation
export const fetchPayloadDetectionRemediationsByAttackChainNode = (injectId: string) => {
  const uri = `${INJECT_URI}/detection-remediations/${injectId}`;
  return simpleCall(uri);
};

// Documents
export const fetchDocumentsPayloadByAttackChainNode = async (injectId: string, payloadId: string | undefined) => {
  const uri = `${INJECT_URI}/${injectId}/payload/${payloadId}/documents`;
  const result = await simpleCall(uri);
  return result.data;
};
