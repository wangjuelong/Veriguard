import { findAttackChainAssetGroupsByIds } from '../../../actions/asset_groups/assetgroup-action';
import { findAttackChainEndpointsByIds } from '../../../actions/assets/endpoint-actions';
import type { AttackChain } from '../../api-types';
import type { EndpointContextType } from './EndpointContext';

const endpointContextForAttackChain = (scenarioId: AttackChain['attack_chain_id']): EndpointContextType => {
  return {
    async fetchEndpointsByIds(endpointIds: string[]) {
      return findAttackChainEndpointsByIds(scenarioId, endpointIds);
    },
    async fetchAssetGroupsByIds(assetGroupIds: string[]) {
      return findAttackChainAssetGroupsByIds(scenarioId, assetGroupIds);
    },
  };
};

export default endpointContextForAttackChain;
