import { simpleCall, simpleDelCall, simplePostCall } from '../../utils/Action';
import { type AttackChainNodeBulkProcessingInput, type SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';

const EXERCISE_URI = `/api/attack_chain_runs`;

export const searchAttackChainNodeTests = (simulationId: string, searchPaginationInput: SearchPaginationInput) => {
  const uri = `${EXERCISE_URI}/${simulationId}/nodes/test/search`;
  return simplePostCall(uri, searchPaginationInput);
};

export const fetchAttackChainNodeTestStatus = (testId: string) => {
  const uri = `${EXERCISE_URI}/nodes/test/${testId}`;
  return simpleCall(uri);
};

export const testAttackChainNode = (simulationId: string, injectId: string) => {
  const uri = `${EXERCISE_URI}/${simulationId}/nodes/${injectId}/test`;
  return simpleCall(uri).catch((error) => {
    MESSAGING$.notifyError('Can\'t be tested');
    throw error;
  });
};

export const bulkTestAttackChainNodes = (simulationId: string, data: AttackChainNodeBulkProcessingInput) => {
  const uri = `${EXERCISE_URI}/${simulationId}/nodes/test`;
  return simplePostCall(uri, data, undefined, false).catch((error) => {
    MESSAGING$.notifyError('Can\'t be tested');
    throw error;
  });
};

export const deleteAttackChainNodeTest = (simulationId: string, testId: string) => {
  const uri = `${EXERCISE_URI}/${simulationId}/nodes/test/${testId}`;
  return simpleDelCall(uri);
};
