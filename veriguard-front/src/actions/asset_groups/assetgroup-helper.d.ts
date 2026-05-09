import { type AssetGroup, type AttackChainRun, type AttackChain } from '../../utils/api-types';

export interface AssetGroupsHelper {
  getAssetGroups: () => AssetGroup[];
  getAssetGroupMaps: () => Record<string, AssetGroup>;
  getAssetGroup: (assetGroupId: string) => AssetGroup | undefined;
  getAttackChainRunAssetGroups: (exerciseId: AttackChainRun['attack_chain_run_id']) => AssetGroup[];
  getAttackChainAssetGroups: (scenarioId: AttackChain['attack_chain_id']) => AssetGroup[];
}
