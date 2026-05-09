import { type AttackChainRun, type AttackChainRunSimple, type AttackChainNodeExpectation, type LessonsAnswer, type LessonsCategory, type LessonsQuestion, type Objective, type Team } from '../../utils/api-types';

export interface AttackChainRunsHelper {
  isAttackChainRun: (exerciseId: string) => boolean;
  getAttackChainRun: (exerciseId: string) => AttackChainRun;
  getAttackChainRuns: () => AttackChainRunSimple[];
  getAttackChainRunsMap: () => Record<string, AttackChainRun>;
  getAttackChainRunTeams: (exerciseId: string) => Team[];
  getAttackChainRunAttackChainNodeExpectations: (exerciseId: AttackChainRun['attack_chain_run_id']) => AttackChainNodeExpectation[];
  getAttackChainRunObjectives: (exerciseId: string) => Objective[];
  getAttackChainRunLessonsCategories: (exerciseId: string) => LessonsCategory[];
  getAttackChainRunLessonsQuestions: (exerciseId: string) => LessonsQuestion[];
  getAttackChainRunLessonsAnswers: (exerciseId: string) => LessonsAnswer[];
  getAttackChainRunUserLessonsAnswers: (exerciseId: string, userId: string) => LessonsAnswer[];
}
