import { createContext } from 'react';

import { type NodeExpectationResult } from '../../../../../utils/api-types';
import type { AttackChainNodeExpectationsStore } from '../../../common/attack_chain_nodes/expectations/Expectation';

type AttackChainNodeExpectationContextType = {
  onOpenDeleteAttackChainNodeExpectationResult: (result: NodeExpectationResult | null, injectExpectationStore: AttackChainNodeExpectationsStore | null) => void;
  onOpenEditAttackChainNodeExpectationResultResult: (result: NodeExpectationResult | null, injectExpectationStore: AttackChainNodeExpectationsStore | null) => void;
  onOpenSecurityPlatform: (result: NodeExpectationResult | null, injectExpectationStore: AttackChainNodeExpectationsStore | null) => void;
};

const AttackChainNodeExpectationContext = createContext<AttackChainNodeExpectationContextType>({
  onOpenDeleteAttackChainNodeExpectationResult: () => {},
  onOpenEditAttackChainNodeExpectationResultResult: () => {},
  onOpenSecurityPlatform: () => {},
});

export default AttackChainNodeExpectationContext;
