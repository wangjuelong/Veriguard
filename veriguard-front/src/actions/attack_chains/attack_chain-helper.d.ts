import { type AttackChain, type LessonsCategory, type LessonsQuestion, type Objective, type Team } from '../../utils/api-types';

export interface AttackChainsHelper {
  getAttackChain: (scenarioId: string) => AttackChain;
  getAttackChains: () => AttackChain[];
  getAttackChainTeams: (scenarioId: string) => Team[];
  getAttackChainObjectives: (scenarioId: string) => Objective[];
  getAttackChainLessonsCategories: (scenarioId: string) => LessonsCategory[];
  getAttackChainLessonsQuestions: (scenarioId: string) => LessonsQuestion[];
}
