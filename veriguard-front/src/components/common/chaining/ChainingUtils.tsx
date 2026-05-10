import { type AttackChainEdge, type AttackChainEdgeCondition } from '../../../utils/api-types';

/**
 * Render a human-readable label for an edge's condition tree (Phase 12b-B3.5b
 * uses the sealed recursive {@link AttackChainEdgeCondition}, so we walk the
 * tree depth-first and join leaf descriptions with the parent group operator).
 */
const stringifyCondition = (condition: AttackChainEdgeCondition): string => {
  if (condition.type === 'eq') {
    return `${condition.dimension} is ${condition.status}`;
  }
  const childLabels = condition.children.map(stringifyCondition);
  if (childLabels.length === 0) return '';
  if (childLabels.length === 1) return childLabels[0];
  const sep = condition.type === 'and' ? ' AND ' : ' OR ';
  return `(${childLabels.join(sep)})`;
};

const fromAttackChainNodeDependencyToLabel = (dependency: AttackChainEdge) => {
  if (!dependency.dependency_condition) return '';
  return stringifyCondition(dependency.dependency_condition);
};

export default { fromAttackChainNodeDependencyToLabel };
