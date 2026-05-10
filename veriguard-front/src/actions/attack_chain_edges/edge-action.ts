import { simplePutCall } from '../../utils/Action';

const EDGE_URI = '/api/attack_chain_edges';

/**
 * Wire-format snapshot for sealed recursive {@code EdgeCondition} (Phase 12b-B3.5b).
 *
 * Mirrors backend io.veriguard.database.model.EdgeCondition with Jackson's
 * {@code @JsonTypeInfo(use=NAME, property="type")} discriminator. Not yet in
 * api-types.d.ts since the entity field changed in this PR.
 */
export type EdgeConditionDto =
  | EdgeConditionEqDto
  | EdgeConditionAndDto
  | EdgeConditionOrDto;

export interface EdgeConditionEqDto {
  type: 'eq';
  dimension: 'PREVENTION' | 'DETECTION' | 'MANUAL';
  status: 'ANY_SUCCESS' | 'ANY_FAILED' | 'ALL_SUCCESS' | 'ALL_FAILED' | 'SETTLED';
}

export interface EdgeConditionAndDto {
  type: 'and';
  children: EdgeConditionDto[];
}

export interface EdgeConditionOrDto {
  type: 'or';
  children: EdgeConditionDto[];
}

export interface EdgeConditionUpdateInputDto {
  dependency_condition: EdgeConditionDto | null;
}

export const updateAttackChainEdgeCondition = (
  edgeId: string,
  data: EdgeConditionUpdateInputDto,
) => {
  const uri = `${EDGE_URI}/${edgeId}/condition`;
  return simplePutCall(uri, data);
};
