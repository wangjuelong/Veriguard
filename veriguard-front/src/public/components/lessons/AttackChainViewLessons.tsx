import { useEffect } from 'react';
import { useParams } from 'react-router';

import { fetchMe } from '../../../actions/Application';
import { fetchAttackChain, fetchLessonsCategories, fetchLessonsQuestions } from '../../../actions/attack_chains/attack_chain-actions';
import { type AttackChainsHelper } from '../../../actions/attack_chains/attack_chain-helper';
import { type UserHelper } from '../../../actions/helper';
import { ViewLessonContext, type ViewLessonContextType } from '../../../admin/components/common/Context';
import { useHelper } from '../../../store';
import { type AttackChain } from '../../../utils/api-types';
import { useQueryParameter } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useAttackChainPermissions from '../../../utils/permissions/useAttackChainPermissions';
import LessonsPreview from './LessonsPreview';

const AttackChainViewLessons = () => {
  const dispatch = useAppDispatch();
  const [preview] = useQueryParameter(['preview']);
  const [userId] = useQueryParameter(['user']);
  const { scenarioId } = useParams() as { scenarioId: AttackChain['attack_chain_id'] };
  const isPreview = preview === 'true';

  const processToGenericSource = (attack_chain: AttackChain | undefined) => {
    if (!attack_chain) return undefined;
    return {
      id: scenarioId,
      type: 'attack_chain',
      name: attack_chain.attack_chain_name,
      subtitle: attack_chain.attack_chain_subtitle,
      userId,
      isPlayerViewAvailable: false,
    };
  };

  const {
    me,
    source,
    lessonsCategories,
    lessonsQuestions,
  } = useHelper((helper: AttackChainsHelper & UserHelper) => {
    const currentUser = helper.getMe();
    const scenarioData = helper.getAttackChain(scenarioId);
    return {
      me: currentUser,
      attack_chain: scenarioData,
      source: processToGenericSource(scenarioData),
      lessonsCategories: helper.getAttackChainLessonsCategories(scenarioId),
      lessonsQuestions: helper.getAttackChainLessonsQuestions(scenarioId),
    };
  });

  const finalUserId = userId && userId !== 'null' ? userId : me?.user_id;

  useEffect(() => {
    dispatch(fetchMe());
    if (isPreview) {
      dispatch(fetchAttackChain(scenarioId));
      dispatch(fetchLessonsCategories(scenarioId));
      dispatch(fetchLessonsQuestions(scenarioId));
    }
  }, [dispatch, scenarioId, userId, finalUserId]);

  // Pass the full attack_chain because the attack_chain is never loaded in the store at this point
  const permissions = useAttackChainPermissions(scenarioId);

  const context: ViewLessonContextType = {};

  return (
    <ViewLessonContext.Provider value={context}>
      {isPreview && (
        <LessonsPreview
          source={{
            ...source,
            finalUserId,
          }}
          lessonsCategories={lessonsCategories}
          lessonsQuestions={lessonsQuestions}
          permissions={permissions}
        />
      )}
    </ViewLessonContext.Provider>
  );
};

export default AttackChainViewLessons;
