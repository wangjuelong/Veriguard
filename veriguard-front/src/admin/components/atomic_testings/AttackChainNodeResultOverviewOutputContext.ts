import { createContext } from 'react';

import { type AttackChainNodeResultOverviewOutput } from '../../../utils/api-types';

export type AttackChainNodeResultOverviewOutputContextType = {
  injectResultOverviewOutput: AttackChainNodeResultOverviewOutput | null;
  updateAttackChainNodeResultOverviewOutput: (data: AttackChainNodeResultOverviewOutput) => void;
};
export const AttackChainNodeResultOverviewOutputContext = createContext<AttackChainNodeResultOverviewOutputContextType>({
  injectResultOverviewOutput: null,
  updateAttackChainNodeResultOverviewOutput: () => {
  },
});
