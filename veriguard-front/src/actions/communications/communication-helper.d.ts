import { type Communication } from '../../utils/api-types';

export interface CommunicationHelper { getAttackChainRunCommunications: (exerciseId: string) => Communication[] }
