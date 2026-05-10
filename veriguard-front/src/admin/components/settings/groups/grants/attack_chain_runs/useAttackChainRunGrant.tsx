import { Checkbox } from '@mui/material';
import { useEffect } from 'react';

import { addGrant, deleteGrant } from '../../../../../../actions/Grant';
import { fetchGroup } from '../../../../../../actions/Group';
import type { GroupHelper } from '../../../../../../actions/group/group-helper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type AttackChainRun, type Grant, type GroupGrantInput } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import type { TableConfig } from '../ui/TableData';

interface SimulationGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const useSimulationGrant = ({ groupId, onGrantChange }: SimulationGrantsProps) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const group = useHelper((helper: GroupHelper) => helper.getGroup(groupId));

  useEffect(() => {
    dispatch(fetchGroup(groupId));
  }, [dispatch]);

  if (!group) {
    return { configs: [] };
  }

  useEffect(() => {
    onGrantChange();
  }, [group]);

  const handleGrant = (exerciseId: string, grantId: string | null, grantName: GroupGrantInput['grant_name'], checked: boolean) => {
    if (checked) {
      const data: GroupGrantInput = {
        grant_name: grantName,
        grant_resource: exerciseId,
        grant_resource_type: 'SIMULATION',
      };
      dispatch(addGrant(group.group_id, data));
    } else {
      dispatch(deleteGrant(group.group_id, grantId));
    }
  };

  const getGrantIds = (attack_chain_run: AttackChainRun) => {
    const grants = group.group_grants ?? [];
    const findGrantId = (name: string) => grants
      .find((g: Grant) => g.grant_resource === attack_chain_run.attack_chain_run_id && g.grant_name === name)?.grant_id ?? null;
    return {
      observerId: findGrantId('OBSERVER'),
      plannerId: findGrantId('PLANNER'),
      launcherId: findGrantId('LAUNCHER'),
    };
  };

  const configs: TableConfig<AttackChainRun>[] = [
    {
      label: t('Simulation'),
      value: attack_chain_run => attack_chain_run.attack_chain_run_name,
      width: '40%',
      align: 'left',
    },
    {
      label: t('Access'),
      value: (attack_chain_run) => {
        const { observerId, plannerId, launcherId } = getGrantIds(attack_chain_run);
        return (
          <Checkbox
            checked={!!(observerId || plannerId || launcherId)}
            disabled={!!(plannerId || launcherId)}
            onChange={(_, checked) => handleGrant(attack_chain_run.attack_chain_run_id, observerId, 'OBSERVER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Manage+Delete'),
      value: (attack_chain_run) => {
        const { plannerId, launcherId } = getGrantIds(attack_chain_run);
        return (
          <Checkbox
            checked={!!(plannerId || launcherId)}
            disabled={!!launcherId}
            onChange={(_, checked) => handleGrant(attack_chain_run.attack_chain_run_id, plannerId, 'PLANNER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Launch'),
      value: (attack_chain_run) => {
        const { launcherId } = getGrantIds(attack_chain_run);
        return (
          <Checkbox
            checked={!!launcherId}
            onChange={(_, checked) => handleGrant(attack_chain_run.attack_chain_run_id, launcherId, 'LAUNCHER', checked)}
          />
        );
      },
      width: '20%',
    },
  ];

  return { configs };
};

export default useSimulationGrant;
