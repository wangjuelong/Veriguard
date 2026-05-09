import { useContext } from 'react';

import { type AttackChainRunsHelper } from '../../actions/attack_chain_runs/attack_chain_run-helper';
import { type LoggedHelper, type UserHelper } from '../../actions/helper';
import { useHelper } from '../../store';
import { type AttackChainRun } from '../api-types';
import { AbilityContext } from './permissionsContext';
import { ACTIONS, SUBJECTS } from './types';

const useSimulationPermissions = (exerciseId: string, fullAttackChainRun?: AttackChainRun) => {
  const ability = useContext(AbilityContext);

  const { attack_chain_run, me, logged } = useHelper((helper: AttackChainRunsHelper & UserHelper & LoggedHelper) => {
    return {
      attack_chain_run: helper.getAttackChainRun(exerciseId),
      me: helper.getMe(),
      logged: helper.logged(),
    };
  });

  if ((!fullAttackChainRun && !attack_chain_run) || !me) {
    return {
      canAccess: false,
      canManage: false,
      canLaunch: false,
      canDelete: false,
      readOnly: true,
      isLoggedIn: Boolean(logged),
      isRunning: false,
    };
  }

  const canAccess = ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, exerciseId) || ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, exerciseId) || ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const canLaunch = ability.can(ACTIONS.LAUNCH, SUBJECTS.RESOURCE, exerciseId) || ability.can(ACTIONS.LAUNCH, SUBJECTS.ASSESSMENT);
  const canDelete = ability.can(ACTIONS.DELETE, SUBJECTS.RESOURCE, exerciseId) || ability.can(ACTIONS.DELETE, SUBJECTS.ASSESSMENT);
  const isRunning = (attack_chain_run || fullAttackChainRun).attack_chain_run_status === 'RUNNING';
  const readOnly = !canManage;

  return {
    canAccess,
    canManage,
    canLaunch,
    canDelete,
    readOnly,
    isLoggedIn: Boolean(logged),
    isRunning,
  };
};

export default useSimulationPermissions;
