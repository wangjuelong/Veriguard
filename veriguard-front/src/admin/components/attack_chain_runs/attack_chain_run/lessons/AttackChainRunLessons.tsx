import { useMemo } from 'react';
import { useParams } from 'react-router';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import { addLessonsCategory, addLessonsQuestion, applyLessonsTemplate, deleteLessonsCategory, deleteLessonsQuestion, emptyLessonsCategories, fetchLessonsAnswers, fetchLessonsCategories, fetchLessonsQuestions, fetchPlayersByAttackChainRun, resetLessonsAnswers, sendLessons, updateLessonsCategory, updateLessonsCategoryTeams, updateLessonsQuestion } from '../../../../../actions/attack_chain_runs/attack_chain_run-action';
import { type AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { type AttackChainsHelper } from '../../../../../actions/attack_chains/attack_chain-helper';
import { fetchAttackChainRunAttackChainNodes } from '../../../../../actions/AttackChainNode';
import { fetchAttackChainRunTeams, updateAttackChainRunLessons } from '../../../../../actions/AttackChainRun';
import { addAttackChainRunEvaluation, fetchAttackChainRunEvaluations, updateAttackChainRunEvaluation } from '../../../../../actions/Evaluation';
import { type UserHelper } from '../../../../../actions/helper';
import { type LessonsTemplatesHelper } from '../../../../../actions/lessons/lesson-helper';
import { addAttackChainRunObjective, deleteAttackChainRunObjective, fetchAttackChainRunObjectives, updateAttackChainRunObjective } from '../../../../../actions/Objective';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainRun, type EvaluationInput, type LessonsCategoryCreateInput, type LessonsCategoryTeamsInput, type LessonsCategoryUpdateInput, type LessonsQuestionCreateInput, type LessonsQuestionUpdateInput, type LessonsSendInput, type ObjectiveInput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useSimulationPermissions from '../../../../../utils/permissions/useAttackChainRunPermissions';
import { LessonContext, type LessonContextType } from '../../../common/Context';
import Lessons from '../../../lessons/attack_chain_runs/Lessons';

const SimulationLessons = () => {
  const dispatch = useAppDispatch();
  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };
  const { t } = useFormatter();

  const processToGenericSource = (attack_chain_run: AttackChainRun) => {
    return {
      id: attack_chain_run.attack_chain_run_id,
      type: 'attack_chain_run',
      name: attack_chain_run.attack_chain_run_name,
      score: attack_chain_run.attack_chain_run_score ?? 0,
      lessons_answers_number: attack_chain_run.attack_chain_run_lessons_answers_number ?? 0,
      communications_number: attack_chain_run.attack_chain_run_communications_number ?? 0,
      start_date: attack_chain_run.attack_chain_run_start_date ?? t('Unknown'),
      end_date: attack_chain_run.attack_chain_run_end_date ?? t('Unknown'),
      users_number: attack_chain_run.attack_chain_run_users_number ?? 0,
      logs_number: attack_chain_run.attack_chain_run_logs_number ?? 0,
      lessons_anonymized: attack_chain_run.attack_chain_run_lessons_anonymized ?? false,
    };
  };

  const {
    attack_chain_run,
    objectives,
    nodes,
    teams,
    teamsMap,
    lessonsCategories,
    lessonsQuestions,
    lessonsAnswers,
    lessonsTemplates,
    usersMap,
  } = useHelper((helper: AttackChainRunsHelper & AttackChainNodeHelper & LessonsTemplatesHelper & AttackChainsHelper & TeamsHelper & UserHelper) => {
    const exerciseData = helper.getAttackChainRun(exerciseId);
    return {
      attack_chain_run: exerciseData,
      objectives: helper.getAttackChainRunObjectives(exerciseId),
      nodes: helper.getAttackChainRunAttackChainNodes(exerciseId),
      lessonsCategories: helper.getAttackChainRunLessonsCategories(exerciseId),
      lessonsQuestions: helper.getAttackChainRunLessonsQuestions(exerciseId),
      lessonsAnswers: helper.getAttackChainRunLessonsAnswers(exerciseId),
      lessonsTemplates: helper.getLessonsTemplates(),
      teamsMap: helper.getTeamsMap(),
      teams: helper.getAttackChainRunTeams(exerciseId),
      usersMap: helper.getUsersMap(),
    };
  });

  const source = useMemo(
    () => processToGenericSource(attack_chain_run),
    [attack_chain_run],
  );

  useDataLoader(() => {
    dispatch(fetchPlayersByAttackChainRun(exerciseId));
    dispatch(fetchLessonsCategories(exerciseId));
    dispatch(fetchLessonsQuestions(exerciseId));
    dispatch(fetchLessonsAnswers(exerciseId));
    dispatch(fetchAttackChainRunObjectives(exerciseId));
    dispatch(fetchAttackChainRunAttackChainNodes(exerciseId));
    dispatch(fetchAttackChainRunTeams(exerciseId));
  });
  const permissions = useSimulationPermissions(exerciseId, attack_chain_run);

  const context: LessonContextType = {
    onApplyLessonsTemplate: (data: string) => dispatch(applyLessonsTemplate(exerciseId, data)),
    onResetLessonsAnswers: () => dispatch(resetLessonsAnswers(exerciseId)),
    onEmptyLessonsCategories: () => dispatch(emptyLessonsCategories(exerciseId)),
    onUpdateSourceLessons: (data: boolean) => dispatch(updateAttackChainRunLessons(exerciseId, { lessons_anonymized: data })),
    onSendLessons: (data: LessonsSendInput) => dispatch(sendLessons(exerciseId, data)),
    // Categories
    onAddLessonsCategory: (data: LessonsCategoryCreateInput) => dispatch(addLessonsCategory(exerciseId, data)),
    onDeleteLessonsCategory: (data: string) => dispatch(deleteLessonsCategory(exerciseId, data)),
    onUpdateLessonsCategory: (lessonCategoryId: string, data: LessonsCategoryUpdateInput) => dispatch(updateLessonsCategory(exerciseId, lessonCategoryId, data)),
    onUpdateLessonsCategoryTeams: (lessonCategoryId: string, data: LessonsCategoryTeamsInput) => dispatch(updateLessonsCategoryTeams(exerciseId, lessonCategoryId, data)),
    // Questions
    onDeleteLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string) => dispatch(
      deleteLessonsQuestion(
        exerciseId,
        lessonsCategoryId,
        lessonsQuestionId,
      ),
    ),
    onUpdateLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => dispatch(
      updateLessonsQuestion(
        exerciseId,
        lessonsCategoryId,
        lessonsQuestionId,
        data,
      ),
    ),
    onAddLessonsQuestion: (lessonsCategoryId: string, data: LessonsQuestionCreateInput) => dispatch(
      addLessonsQuestion(exerciseId, lessonsCategoryId, data),
    ),
    // Objectives
    onAddObjective: (data: ObjectiveInput) => dispatch(addAttackChainRunObjective(exerciseId, data)),
    onUpdateObjective: (objectiveId: string, data: ObjectiveInput) => dispatch(updateAttackChainRunObjective(exerciseId, objectiveId, data)),
    onDeleteObjective: (objectiveId: string) => dispatch(deleteAttackChainRunObjective(exerciseId, objectiveId)),
    // Evaluation
    onAddEvaluation: (objectiveId: string, data: EvaluationInput) => dispatch(addAttackChainRunEvaluation(exerciseId, objectiveId, data)),
    onUpdateEvaluation: (objectiveId: string, evaluationId: string, data: EvaluationInput) => dispatch(updateAttackChainRunEvaluation(exerciseId, objectiveId, evaluationId, data)),
    onFetchEvaluation: (objectiveId: string) => dispatch(fetchAttackChainRunEvaluations(exerciseId, objectiveId)),
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
        nodes={nodes}
        teamsMap={teamsMap}
        teams={teams}
        lessonsCategories={lessonsCategories}
        lessonsQuestions={lessonsQuestions}
        lessonsAnswers={lessonsAnswers}
        lessonsTemplates={lessonsTemplates}
        usersMap={usersMap}
      >
      </Lessons>
    </LessonContext.Provider>
  );
};

export default SimulationLessons;
