import { useEffect } from 'react';
import { useParams } from 'react-router';

import { fetchMe } from '../../../actions/Application';
import { addLessonsAnswers, fetchLessonsAnswers, fetchLessonsCategories, fetchLessonsQuestions, fetchPlayerLessonsAnswers, fetchPlayerLessonsCategories, fetchPlayerLessonsQuestions } from '../../../actions/attack_chain_runs/attack_chain_run-action';
import { type AttackChainRunsHelper } from '../../../actions/attack_chain_runs/attack_chain_run-helper';
import { fetchAttackChainRun, fetchPlayerAttackChainRun } from '../../../actions/AttackChainRun';
import { type UserHelper } from '../../../actions/helper';
import { ViewLessonContext, type ViewLessonContextType } from '../../../admin/components/common/Context';
import { useHelper } from '../../../store';
import { type AttackChainRun } from '../../../utils/api-types';
import { useQueryParameter } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useSimulationPermissions from '../../../utils/permissions/useAttackChainRunPermissions';
import LessonsPlayer from './LessonsPlayer';
import LessonsPreview from './LessonsPreview';

const AttackChainRunViewLessons = () => {
  const dispatch = useAppDispatch();
  const [preview] = useQueryParameter(['preview']);
  const [userId] = useQueryParameter(['user']);
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };
  const isPreview = preview === 'true';

  const processToGenericSource = (attack_chain_run: AttackChainRun | undefined) => {
    if (!attack_chain_run) return undefined;
    return {
      id: exerciseId,
      type: 'attack_chain_run',
      name: attack_chain_run.attack_chain_run_name,
      subtitle: attack_chain_run.attack_chain_run_subtitle,
      userId,
      isUserAbsent: userId === 'null',
      isPlayerViewAvailable: true,
    };
  };

  const {
    me,
    attack_chain_run,
    source,
    lessonsCategories,
    lessonsQuestions,
    lessonsAnswers,
  } = useHelper((helper: AttackChainRunsHelper & UserHelper) => {
    const currentUser = helper.getMe();
    const exerciseData = helper.getAttackChainRun(exerciseId);
    return {
      me: currentUser,
      attack_chain_run: exerciseData,
      source: processToGenericSource(exerciseData),
      lessonsCategories: helper.getAttackChainRunLessonsCategories(exerciseId),
      lessonsQuestions: helper.getAttackChainRunLessonsQuestions(exerciseId),
      lessonsAnswers: helper.getAttackChainRunUserLessonsAnswers(
        exerciseId,
        userId && userId !== 'null' ? userId : currentUser?.user_id,
      ),
    };
  });

  const finalUserId = userId && userId !== 'null' ? userId : me?.user_id;

  useEffect(() => {
    dispatch(fetchMe());
    if (isPreview) {
      dispatch(fetchAttackChainRun(exerciseId));
      dispatch(fetchLessonsCategories(exerciseId));
      dispatch(fetchLessonsQuestions(exerciseId));
      dispatch(fetchLessonsAnswers(exerciseId));
    } else {
      dispatch(fetchPlayerAttackChainRun(exerciseId, userId));
      dispatch(fetchPlayerLessonsCategories(exerciseId, finalUserId));
      dispatch(fetchPlayerLessonsQuestions(exerciseId, finalUserId));
      dispatch(fetchPlayerLessonsAnswers(exerciseId, finalUserId));
    }
  }, [dispatch, exerciseId, userId, finalUserId]);

  // Pass the full attack_chain_run because the attack_chain_run is never loaded in the store at this point
  const permissions = useSimulationPermissions(exerciseId, attack_chain_run);

  const context: ViewLessonContextType = {
    onAddLessonsAnswers: (questionCategory, lessonsQuestionId, answerData) => dispatch(
      addLessonsAnswers(
        exerciseId,
        questionCategory,
        lessonsQuestionId,
        answerData,
        finalUserId,
      ),
    ),
    onFetchPlayerLessonsAnswers: () => dispatch(fetchPlayerLessonsAnswers(exerciseId, finalUserId)),
  };

  return (
    <ViewLessonContext.Provider value={context}>
      {isPreview ? (
        <LessonsPreview
          source={{
            ...source,
            finalUserId,
          }}
          lessonsCategories={lessonsCategories}
          lessonsQuestions={lessonsQuestions}
          permissions={permissions}
        />
      ) : (
        <LessonsPlayer
          source={{
            ...source,
            finalUserId,
          }}
          lessonsCategories={lessonsCategories}
          lessonsQuestions={lessonsQuestions}
          lessonsAnswers={lessonsAnswers}
          permissions={permissions}
        />
      )}
    </ViewLessonContext.Provider>
  );
};

export default AttackChainRunViewLessons;
