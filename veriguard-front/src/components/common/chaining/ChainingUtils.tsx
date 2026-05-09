import { type AttackChainEdge } from '../../../utils/api-types';

const fromAttackChainNodeDependencyToLabel = (dependency: AttackChainEdge) => {
  let label = '';
  if (dependency.dependency_condition?.conditions !== undefined) {
    label = dependency.dependency_condition.conditions
      .map(value => `${value.key} is ${value.value ? 'Success' : 'Failure'}`)
      .join(dependency.dependency_condition.mode === 'and' ? ' AND ' : ' OR ');
  }

  return label;
};

export default { fromAttackChainNodeDependencyToLabel };
