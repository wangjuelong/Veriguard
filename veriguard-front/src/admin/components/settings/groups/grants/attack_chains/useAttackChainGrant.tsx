import { Checkbox } from '@mui/material';
import { useEffect } from 'react';

import { addGrant, deleteGrant } from '../../../../../../actions/Grant';
import { fetchGroup } from '../../../../../../actions/Group';
import type { GroupHelper } from '../../../../../../actions/group/group-helper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type AttackChain, type Grant, type GroupGrantInput } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import { type TableConfig } from '../ui/TableData';

interface AttackChainGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const useAttackChainGrant = ({ groupId, onGrantChange }: AttackChainGrantsProps) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const group = useHelper((helper: GroupHelper) => helper.getGroup(groupId));

  useEffect(() => {
    dispatch(fetchGroup(groupId));
  }, [dispatch]);

  useEffect(() => {
    if (!group) return;
    onGrantChange();
  }, [group]);

  if (!group) {
    return { configs: [] };
  }

  const handleGrant = (scenarioId: string, grantId: string | null, grantName: GroupGrantInput['grant_name'], checked: boolean) => {
    if (checked) {
      const data: GroupGrantInput = {
        grant_name: grantName,
        grant_resource: scenarioId,
        grant_resource_type: 'SCENARIO',
      };
      dispatch(addGrant(group.group_id, data));
    } else {
      dispatch(deleteGrant(group.group_id, grantId));
    }
  };

  const getGrantIds = (attack_chain: AttackChain) => {
    const grants = group.group_grants ?? [];
    const findGrantId = (name: string) => grants
      .find((g: Grant) => g.grant_resource === attack_chain.attack_chain_id && g.grant_name === name)?.grant_id ?? null;
    return {
      observerId: findGrantId('OBSERVER'),
      plannerId: findGrantId('PLANNER'),
      launcherId: findGrantId('LAUNCHER'),
    };
  };

  const configs: TableConfig<AttackChain>[] = [
    {
      label: t('AttackChain'),
      value: attack_chain => attack_chain.attack_chain_name,
      width: '40%',
      align: 'left',
    },
    {
      label: t('Access'),
      value: (attack_chain) => {
        const { observerId, plannerId, launcherId } = getGrantIds(attack_chain);
        return (
          <Checkbox
            checked={!!(observerId || plannerId || launcherId)}
            disabled={!!(plannerId || launcherId)}
            onChange={(_, checked) => handleGrant(attack_chain.attack_chain_id, observerId, 'OBSERVER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Manage+Delete'),
      value: (attack_chain) => {
        const { plannerId, launcherId } = getGrantIds(attack_chain);
        return (
          <Checkbox
            checked={!!(plannerId || launcherId)}
            disabled={!!launcherId}
            onChange={(_, checked) => handleGrant(attack_chain.attack_chain_id, plannerId, 'PLANNER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Launch'),
      value: (attack_chain) => {
        const { launcherId } = getGrantIds(attack_chain);
        return (
          <Checkbox
            checked={!!launcherId}
            onChange={(_, checked) => handleGrant(attack_chain.attack_chain_id, launcherId, 'LAUNCHER', checked)}
          />
        );
      },
      width: '20%',
    },
  ];

  return { configs };
};

export default useAttackChainGrant;
