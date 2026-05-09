import { createContext } from 'react';

import { type AttackChainNodeExpectationResult } from '../../../../../utils/api-types';
import type { AttackChainNodeExpectationsStore } from '../../../common/attack_chain_nodes/expectations/Expectation';

type AttackChainNodeExpectationContextType = {
  onOpenDeleteAttackChainNodeExpectationResult: (result: AttackChainNodeExpectationResult | null, injectExpectationStore: AttackChainNodeExpectationsStore | null) => void;
  onOpenEditAttackChainNodeExpectationResultResult: (result: AttackChainNodeExpectationResult | null, injectExpectationStore: AttackChainNodeExpectationsStore | null) => void;
  onOpenSecurityPlatform: (result: AttackChainNodeExpectationResult | null, injectExpectationStore: AttackChainNodeExpectationsStore | null) => void;
};

const AttackChainNodeExpectationContext = createContext<AttackChainNodeExpectationContextType>({
  onOpenDeleteAttackChainNodeExpectationResult: () => {},
  onOpenEditAttackChainNodeExpectationResultResult: () => {},
  onOpenSecurityPlatform: () => {},
});

export default AttackChainNodeExpectationContext;
