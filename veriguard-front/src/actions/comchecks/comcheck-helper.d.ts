import { type Comcheck } from '../../utils/api-types';

export interface ComCheckHelper { getAttackChainRunComchecks: (exerciseId: string) => Comcheck[] }
