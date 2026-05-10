import {
  type AttackChain,
  type AttackChainChallengesReader,
  type AttackChainRun,
  type Challenge,
  type Document,
  type Domain,
  type Organization,
  type PlatformSettings,
  type SimulationChallengesReader,
  type Tag,
  type Token,
  type User,
} from '../utils/api-types';

export interface UserHelper {
  getMe: () => User;
  getMeAdmin: () => boolean;
  getUsersMap: () => Record<string, User>;
}

export interface OrganizationHelper {
  getOrganizations: () => Organization[];
  getOrganizationsMap: () => Record<string, Organization>;
}

export interface TagHelper {
  getTag: (tagId: Tag['tag_id']) => Tag;
  getTags: () => Tag[];
  getTagsMap: () => Record<string, Tag>;
}

export interface DomainHelper { getDomains: () => Domain[] }

export interface LoggedHelper {
  // TODO type logged object
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  logged: () => any;
  getMe: () => User;
  getPlatformSettings: () => PlatformSettings;
  getPlatformName: () => string;
  getUserLang: () => string;
}

export interface ChallengeHelper {
  getChallengesMap: () => Record<string, Challenge>;
  getChallenges: () => Challenge[];
  getAttackChainRunChallenges: (exerciseId: AttackChainRun['attack_chain_run_id']) => Challenge[];
  getAttackChainChallenges: (scenarioId: AttackChain['attack_chain_id']) => Challenge[];
}

export interface DocumentHelper {
  getDocuments: () => Document[];
  getDocumentsMap: () => Record<string, Document>;
}

export interface MeTokensHelper { getMeTokens: () => Token[] }

export interface SimulationChallengesReaderHelper { getSimulationChallengesReader: (exerciseId: SimulationChallengesReader['attack_chain_run_id']) => SimulationChallengesReader }

export interface AttackChainChallengesReaderHelper { getAttackChainChallengesReader: (scenarioId: SimulationChallengesReader['attack_chain_id']) => AttackChainChallengesReader }
