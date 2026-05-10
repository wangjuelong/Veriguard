import { type AttackChainNode, type NodeExpectationResultsByAttackPattern, type NodeExpectationResultsByType } from '../../../../utils/api-types';

/**
 * 编辑器场景 adapter 输入类型 alias —— 让调用方 import 时语义清晰.
 */
export type EditorMatrixInput = readonly AttackChainNode[];

/**
 * 编辑器场景 adapter：节点 → ATT&CK matrix 输入 view-model.
 *
 * 运行画布场景**不**用此 adapter ——直接用现有 `/api/attack_chain_runs/{id}/nodes/results-by-attack-patterns`
 * 端点（{@code fetchAttackChainRunAttackChainNodeExpectationResults}）返回的同结构数据.
 *
 * 编辑器场景没有运行结果，把节点 contract 上挂的 attack_patterns 展开成
 * {@link NodeExpectationResultsByAttackPattern}[]，每个节点的 results 字段为 [] 占位.
 * AttackPatternBox 在 coloringScheme='coverage' 模式下只看是否有 expectation_results
 * 条目（≥1 节点 → 已覆盖），不看 results 内容.
 */
export const toAttackPatternResultsForEditor = (
  nodes: EditorMatrixInput,
): NodeExpectationResultsByAttackPattern[] => {
  const byPattern = new Map<string, NodeExpectationResultsByType[]>();

  for (const node of nodes) {
    const patterns = node.node_injector_contract?.injector_contract_attack_patterns;
    if (!patterns || patterns.length === 0) {
      continue;
    }
    for (const patternId of patterns) {
      const list = byPattern.get(patternId);
      const entry: NodeExpectationResultsByType = {
        node_id: node.node_id,
        node_title: node.node_title,
        results: [],
      };
      if (list) {
        list.push(entry);
      } else {
        byPattern.set(patternId, [entry]);
      }
    }
  }

  return Array.from(byPattern.entries()).map(([patternId, nodeEntries]) => ({
    node_attack_pattern: patternId,
    node_expectation_results: nodeEntries,
  }));
};
