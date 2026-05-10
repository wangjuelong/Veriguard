import { type FunctionComponent } from 'react';

import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type Group } from '../../../../../utils/api-types';
import GroupManageAtomicTestingGrants from './atomic_testings/GroupManageAtomicTestingGrants';
import GroupManageSimulationGrants from './attack_chain_runs/GroupManageAttackChainRunGrants';
import GroupManageAttackChainGrants from './attack_chains/GroupManageAttackChainGrants';
import GroupManagePayloadGrants from './payloads/GroupManagePayloadGrants';
import TabbedView from './ui/TabbedView';

interface GroupManageGrantsProps {
  group: Group;
  openGrants: boolean;
  handleCloseGrants: () => void;
  fetchAndUpdateGroup: () => void;
}

const GroupManageGrants: FunctionComponent<GroupManageGrantsProps> = ({
  group,
  openGrants,
  handleCloseGrants,
  fetchAndUpdateGroup,
}) => {
  const { t } = useFormatter();

  return (
    <Drawer
      open={openGrants}
      handleClose={handleCloseGrants}
      title={t('Manage grants')}
    >
      <TabbedView
        tabs={[
          {
            key: 'AttackChains',
            label: t('AttackChains'),
            component: (
              <GroupManageAttackChainGrants groupId={group.group_id} onGrantChange={fetchAndUpdateGroup} />
            ),
          },
          {
            key: 'Simulations',
            label: t('Simulations'),
            component: (
              <GroupManageSimulationGrants groupId={group.group_id} onGrantChange={fetchAndUpdateGroup} />
            ),
          },
          {
            key: 'Atomic testings',
            label: t('Atomic testings'),
            component: <GroupManageAtomicTestingGrants groupId={group.group_id} onGrantChange={fetchAndUpdateGroup} />,
          },
          {
            key: 'Payloads',
            label: t('Payloads'),
            component: <GroupManagePayloadGrants groupId={group.group_id} onGrantChange={fetchAndUpdateGroup} />,
          },
        ]}
      />
    </Drawer>
  );
};

export default GroupManageGrants;
