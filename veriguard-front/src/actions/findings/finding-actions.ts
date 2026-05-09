import { simplePostCall } from '../../utils/Action';
import { type SearchPaginationInput } from '../../utils/api-types';

const FINDING_URI = '/api/findings';

export const searchFindings = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/search`;
  return simplePostCall(uri, data);
};

export const searchFindingsForAttackChainNodes = (injectId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/nodes/${injectId}/search`;
  return simplePostCall(uri, data);
};

export const searchFindingsOnEndpoint = (endpointId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/endpoints/${endpointId}/search`;
  return simplePostCall(uri, data);
};

export const searchFindingsForSimulations = (simulationId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/attack_chain_runs/${simulationId}/search`;
  return simplePostCall(uri, data);
};

export const searchFindingsForAttackChains = (scenarioId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/attack_chains/${scenarioId}/search`;
  return simplePostCall(uri, data);
};

// -- DISTINCT --

export const searchDistinctFindings = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/search?distinct=true`;
  return simplePostCall(uri, data);
};

export const searchDistinctFindingsForAttackChainNodes = (injectId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/nodes/${injectId}/search?distinct=true`;
  return simplePostCall(uri, data);
};

export const searchDistinctFindingsOnEndpoint = (endpointId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/endpoints/${endpointId}/search?distinct=true`;
  return simplePostCall(uri, data);
};

export const searchDistinctFindingsForSimulations = (simulationId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/attack_chain_runs/${simulationId}/search?distinct=true`;
  return simplePostCall(uri, data);
};

export const searchDistinctFindingsForAttackChains = (scenarioId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/attack_chains/${scenarioId}/search?distinct=true`;
  return simplePostCall(uri, data);
};
