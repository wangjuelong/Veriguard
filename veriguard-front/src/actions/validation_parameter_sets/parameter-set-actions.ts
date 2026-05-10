import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import {
  type SearchPaginationInput,
  type ValidationParameterSetInput,
  type ValidationParameterSetOutput,
} from '../../utils/api-types';

const VALIDATION_PARAMETER_SET_URI = '/api/validation_parameter_sets';

export const searchValidationParameterSets = (searchPaginationInput: SearchPaginationInput) => {
  const uri = `${VALIDATION_PARAMETER_SET_URI}/search`;
  return simplePostCall(uri, searchPaginationInput);
};

export const fetchValidationParameterSet = (parameterSetId: string) => {
  const uri = `${VALIDATION_PARAMETER_SET_URI}/${parameterSetId}`;
  return simpleCall(uri);
};

export const createValidationParameterSet = (data: ValidationParameterSetInput) => {
  return simplePostCall(VALIDATION_PARAMETER_SET_URI, data);
};

export const updateValidationParameterSet = (
  parameterSetId: string,
  data: ValidationParameterSetInput,
) => {
  const uri = `${VALIDATION_PARAMETER_SET_URI}/${parameterSetId}`;
  return simplePutCall(uri, data);
};

export const deleteValidationParameterSet = (parameterSetId: string) => {
  const uri = `${VALIDATION_PARAMETER_SET_URI}/${parameterSetId}`;
  return simpleDelCall(uri);
};

export const duplicateValidationParameterSet = (parameterSetId: string, newName: string) => {
  const uri = `${VALIDATION_PARAMETER_SET_URI}/${parameterSetId}/duplicate`;
  return simplePostCall(uri, { newName });
};

export type { ValidationParameterSetInput, ValidationParameterSetOutput };
