import { type AttackChainNodeExpectation, type AttackChainNodeExpectationResult } from '../../../../../../utils/api-types';
import { type AttackChainNodeExpectationsStore } from '../../../../common/attack_chain_nodes/expectations/Expectation';
import { isManualExpectation } from '../../../../common/attack_chain_nodes/expectations/ExpectationUtils';

export const groupedByAsset = (es: AttackChainNodeExpectationsStore[]) => {
  return es.reduce((group, expectation) => {
    const { node_expectation_asset } = expectation;
    if (node_expectation_asset) {
      const values = group.get(node_expectation_asset) ?? [];
      values.push(expectation);
      group.set(node_expectation_asset, values);
    }
    return group;
  }, new Map());
};

export const isAssetGroupExpectation = (injectExpectation: AttackChainNodeExpectation) => {
  return injectExpectation.node_expectation_asset_group != null
    && injectExpectation.node_expectation_asset == null
    && injectExpectation.node_expectation_agent == null;
};

export const isAssetExpectation = (injectExpectation: AttackChainNodeExpectation) => {
  return injectExpectation.node_expectation_asset != null
    && injectExpectation.node_expectation_agent == null;
};

export const isAgentExpectation = (injectExpectation: AttackChainNodeExpectation) => {
  return injectExpectation.node_expectation_agent != null;
};

export const isPlayerExpectation = (injectExpectation: AttackChainNodeExpectation) => {
  return injectExpectation.node_expectation_user != null;
};

export const useIsManuallyUpdatable = (injectExpectation: AttackChainNodeExpectation) => {
  // Technical
  if (['DETECTION', 'PREVENTION'].includes(injectExpectation.node_expectation_type)) {
    if (isAssetGroupExpectation(injectExpectation) || isAgentExpectation(injectExpectation)) return false;

    return true;
  }
  // Human
  if (isManualExpectation(injectExpectation.node_expectation_type)) {
    if ((injectExpectation.node_expectation_results?.length ?? 0) > 0) return false;

    return true;
  }
  return false;
};

/**
 * Returns a formatted label for the source of an expectation result.
 *
 * @param {AttackChainNodeExpectationResult | null | undefined} expectationResult - The result object containing source information.
 * @returns {string} The formatted source label, e.g. "sourceName (sourcePlatform)" or "-" if not available.
 */
export const getSourceLabel = (
  expectationResult?: AttackChainNodeExpectationResult | null,
): string => {
  const sourceName = expectationResult?.sourceName?.trim();
  const sourcePlatform = expectationResult?.sourcePlatform?.trim();

  if (!sourceName) {
    return '-';
  }

  return sourcePlatform ? `${sourceName} (${sourcePlatform})` : sourceName;
};
