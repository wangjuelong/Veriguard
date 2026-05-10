import { type EdgeConditionDto } from '../../../../../actions/attack_chain_edges/edge-action';
import { type EdgeConditionTree } from './attackChainEditorTypes';

/**
 * Adapters between Phase 8 ConditionEdgePopover's local {@link EdgeConditionTree}
 * (kind: 'leaf' | 'group', op: 'AND' | 'OR') and the backend sealed
 * {@code EdgeCondition} wire format (type: 'eq' | 'and' | 'or' with Jackson
 * discriminator).
 *
 * Lives next to attackChainSettingsAdapters / attackChainNodeEditAdapters and
 * uses the same "extract pure mapping logic" pattern so the wiring stays
 * unit-testable.
 */

export const toEdgeConditionDto = (tree: EdgeConditionTree): EdgeConditionDto => {
  if (tree.kind === 'leaf') {
    return {
      type: 'eq',
      dimension: tree.dimension,
      status: tree.status,
    };
  }
  return {
    type: tree.op === 'AND' ? 'and' : 'or',
    children: tree.children.map(toEdgeConditionDto),
  };
};

export const fromEdgeConditionDto = (dto: EdgeConditionDto): EdgeConditionTree => {
  if (dto.type === 'eq') {
    return {
      kind: 'leaf',
      dimension: dto.dimension,
      status: dto.status,
    };
  }
  return {
    kind: 'group',
    op: dto.type === 'and' ? 'AND' : 'OR',
    children: dto.children.map(fromEdgeConditionDto),
  };
};
