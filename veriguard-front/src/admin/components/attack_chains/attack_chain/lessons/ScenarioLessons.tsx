import { useMemo } from 'react';
import { useParams } from 'react-router';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import { type AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import {
  addLessonsCategory,
  addLessonsQuestion,
  applyLessonsTemplate,
  deleteLessonsCategory,
  deleteLessonsQuestion,
  emptyLessonsCategories,
  fetchLessonsCategories,
  fetchLessonsQuestions, fetchPlayersByAttackChain,
  fetchAttackChainTeams,
  updateLessonsCategory,
  updateLessonsCategoryTeams,
  updateLessonsQuestion,
  updateAttackChainLessons,
} from '../../../../../actions/attack_chains/attack_chain-actions';
import { type AttackChainsHelper } from '../../../../../actions/attack_chains/attack_chain-helper';
import { addAttackChainEvaluation, fetchAttackChainEvaluations, updateAttackChainEvaluation } from '../../../../../actions/Evaluation';
import { type UserHelper } from '../../../../../actions/helper';
import { type LessonsTemplatesHelper } from '../../../../../actions/lessons/lesson-helper';
import { addAttackChainObjective, deleteAttackChainObjective, fetchAttackChainObjectives, updateAttackChainObjective } from '../../../../../actions/Objective';
import { fetchTeams } from '../../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import { useHelper } from '../../../../../store';
import {
  type EvaluationInput,
  type LessonsCategoryCreateInput,
  type LessonsCategoryTeamsInput,
  type LessonsCategoryUpdateInput,
  type LessonsQuestionCreateInput,
  type LessonsQuestionUpdateInput,
  type ObjectiveInput, type AttackChain,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useAttackChainPermissions from '../../../../../utils/permissions/useAttackChainPermissions';
import { LessonContext, type LessonContextType } from '../../../common/Context';
import Lessons from '../../../lessons/attack_chains/Lessons';

const AttackChainLessons = () => {
  const dispatch = useAppDispatch();

  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: AttackChain['attack_chain_id'] };

  const processToGenericSource = (attack_chain: AttackChain) => {
    return {
      id: attack_chain.attack_chain_id,
      type: 'attack_chain',
      name: attack_chain.attack_chain_name,
      lessons_anonymized: attack_chain.attack_chain_lessons_anonymized ?? false,
    };
  };

  const {
    attack_chain,
    objectives,
    teams,
    teamsMap,
    lessonsCategories,
    lessonsQuestions,
    lessonsTemplates,
  } = useHelper((helper: AttackChainRunsHelper & AttackChainNodeHelper & LessonsTemplatesHelper & AttackChainsHelper & TeamsHelper & UserHelper) => {
    const scenarioData = helper.getAttackChain(scenarioId);
    return {
      attack_chain: scenarioData,
      objectives: helper.getAttackChainObjectives(scenarioId),
      lessonsCategories: helper.getAttackChainLessonsCategories(scenarioId),
      lessonsQuestions: helper.getAttackChainLessonsQuestions(scenarioId),
      lessonsTemplates: helper.getLessonsTemplates(),
      teamsMap: helper.getTeamsMap(),
      teams: helper.getAttackChainTeams(scenarioId),
    };
  });
  useDataLoader(() => {
    dispatch(fetchTeams());
    dispatch(fetchPlayersByAttackChain(scenarioId));
    dispatch(fetchLessonsCategories(scenarioId));
    dispatch(fetchLessonsQuestions(scenarioId));
    dispatch(fetchAttackChainObjectives(scenarioId));
    dispatch(fetchAttackChainTeams(scenarioId));
  });

  const source = useMemo(
    () => processToGenericSource(attack_chain),
    [attack_chain],
  );

  const permissions = useAttackChainPermissions(scenarioId);

  const context: LessonContextType = {
    onApplyLessonsTemplate: (data: string) => dispatch(applyLessonsTemplate(scenarioId, data)),
    onEmptyLessonsCategories: () => dispatch(emptyLessonsCategories(scenarioId)),
    onUpdateSourceLessons: (data: boolean) => dispatch(updateAttackChainLessons(scenarioId, { lessons_anonymized: data })),
    // Categories
    onAddLessonsCategory: (data: LessonsCategoryCreateInput) => dispatch(addLessonsCategory(scenarioId, data)),
    onDeleteLessonsCategory: (data: string) => dispatch(deleteLessonsCategory(scenarioId, data)),
    onUpdateLessonsCategory: (lessonCategoryId: string, data: LessonsCategoryUpdateInput) => dispatch(updateLessonsCategory(scenarioId, lessonCategoryId, data)),
    onUpdateLessonsCategoryTeams: (lessonCategoryId: string, data: LessonsCategoryTeamsInput) => dispatch(updateLessonsCategoryTeams(scenarioId, lessonCategoryId, data)),
    // Questions
    onDeleteLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string) => dispatch(
      deleteLessonsQuestion(
        scenarioId,
        lessonsCategoryId,
        lessonsQuestionId,
      ),
    ),
    onUpdateLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => dispatch(
      updateLessonsQuestion(
        scenarioId,
        lessonsCategoryId,
        lessonsQuestionId,
        data,
      ),
    ),
    onAddLessonsQuestion: (lessonsCategoryId: string, data: LessonsQuestionCreateInput) => dispatch(addLessonsQuestion(scenarioId, lessonsCategoryId, data)),
    // Objectives
    onAddObjective: (data: ObjectiveInput) => dispatch(addAttackChainObjective(scenarioId, data)),
    onUpdateObjective: (objectiveId: string, data: ObjectiveInput) => dispatch(updateAttackChainObjective(scenarioId, objectiveId, data)),
    onDeleteObjective: (objectiveId: string) => dispatch(deleteAttackChainObjective(scenarioId, objectiveId)),
    // Evaluation
    onAddEvaluation: (objectiveId: string, data: EvaluationInput) => dispatch(addAttackChainEvaluation(scenarioId, objectiveId, data)),
    onUpdateEvaluation: (objectiveId: string, evaluationId: string, data: EvaluationInput) => dispatch(updateAttackChainEvaluation(scenarioId, objectiveId, evaluationId, data)),
    onFetchEvaluation: (objectiveId: string) => dispatch(fetchAttackChainEvaluations(scenarioId, objectiveId)),
  };

  return (
    <LessonContext.Provider value={context}>
      <Lessons
        source={{
          ...source,
          isReadOnly: permissions.readOnly,
          isUpdatable: permissions.canManage,
        }}
        objectives={objectives}
        teamsMap={teamsMap}
        teams={teams}
        lessonsCategories={lessonsCategories}
        lessonsQuestions={lessonsQuestions}
        lessonsTemplates={lessonsTemplates}
      >
      </Lessons>
    </LessonContext.Provider>
  );
};

export default AttackChainLessons;
