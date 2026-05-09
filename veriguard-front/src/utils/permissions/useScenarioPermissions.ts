import * as R from 'ramda';
import { useContext } from 'react';

import { type AttackChainsHelper } from '../../actions/attack_chains/attack_chain-helper';
import { type LoggedHelper, type UserHelper } from '../../actions/helper';
import { useHelper } from '../../store';
import { AbilityContext } from './permissionsContext';
import { ACTIONS, SUBJECTS } from './types';

const useAttackChainPermissions = (scenarioId: string) => {
  const ability = useContext(AbilityContext);

  const { logged } = useHelper((helper: AttackChainsHelper & UserHelper & LoggedHelper) => {
    return { logged: helper.logged() };
  });

  const canAccess = ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, scenarioId) || ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, scenarioId) || ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const canLaunch = ability.can(ACTIONS.LAUNCH, SUBJECTS.RESOURCE, scenarioId) || ability.can(ACTIONS.LAUNCH, SUBJECTS.ASSESSMENT);
  const canDelete = ability.can(ACTIONS.DELETE, SUBJECTS.RESOURCE, scenarioId) || ability.can(ACTIONS.DELETE, SUBJECTS.ASSESSMENT);

  return {
    canAccess,
    canManage,
    canLaunch,
    canDelete,
    readOnly: !canManage,
    isLoggedIn: !R.isEmpty(logged),
    isRunning: false,
  };
};

export default useAttackChainPermissions;
