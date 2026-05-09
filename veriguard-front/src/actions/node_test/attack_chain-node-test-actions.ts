import { simpleCall, simpleDelCall, simplePostCall } from '../../utils/Action';
import { type AttackChainNodeBulkProcessingInput, type SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';

const SCENARIO_URI = `/api/attack_chains`;

export const searchAttackChainNodeTests = (scenarioId: string, searchPaginationInput: SearchPaginationInput) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/nodes/test/search`;
  return simplePostCall(uri, searchPaginationInput);
};

export const fetchAttackChainNodeTestStatus = (testId: string) => {
  const uri = `${SCENARIO_URI}/nodes/test/${testId}`;
  return simpleCall(uri);
};

export const testAttackChainNode = (scenarioId: string, injectId: string) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/nodes/${injectId}/test`;
  return simpleCall(uri).catch((error) => {
    MESSAGING$.notifyError('Can\'t be tested');
    throw error;
  });
};

export const bulkTestAttackChainNodes = (scenarioId: string, data: AttackChainNodeBulkProcessingInput) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/nodes/test`;
  return simplePostCall(uri, data, undefined, false).catch((error) => {
    MESSAGING$.notifyError('Can\'t be tested');
    throw error;
  });
};

export const deleteAttackChainNodeTest = (scenarioId: string, testId: string) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/nodes/test/${testId}`;
  return simpleDelCall(uri);
};
