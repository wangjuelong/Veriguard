import type { AttackChainNodeTarget } from '../api-types';

export const isAssetGroups = (target: AttackChainNodeTarget) => {
  return target.target_type === 'ASSETS_GROUPS';
};

export const isAssets = (target: AttackChainNodeTarget) => {
  return target.target_type === 'ASSETS';
};

export const isAgent = (target: AttackChainNodeTarget) => {
  return target.target_type === 'AGENT';
};

export const isAgentless = (hasAgents: boolean, hasTeams: boolean) => {
  return !hasAgents && !hasTeams;
};

export const isTeam = (target: AttackChainNodeTarget) => {
  return target.target_type === 'TEAMS';
};

export const isPlayer = (target: AttackChainNodeTarget) => {
  return target.target_type === 'PLAYERS';
};
