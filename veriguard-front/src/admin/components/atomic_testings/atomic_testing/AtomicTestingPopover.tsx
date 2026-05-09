import { type FunctionComponent, useContext, useState } from 'react';
import { useNavigate } from 'react-router';

import { deleteAtomicTesting, duplicateAtomicTesting } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { exportAttackChainNode } from '../../../../actions/attack_chain_nodes/node-action';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import DialogDuplicate from '../../../../components/common/DialogDuplicate';
import ExportOptionsDialog from '../../../../components/common/export/ExportOptionsDialog';
import { useFormatter } from '../../../../components/i18n';
import type {
  AttackChainNodeIndividualExportRequestInput,
  AttackChainNodeResultOutput,
  AttackChainNodeResultOverviewOutput,
} from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { download } from '../../../../utils/utils';
import AtomicTestingUpdate from './AtomicTestingUpdate';

type AtomicTestingActionType = 'Duplicate' | 'Update' | 'Delete' | 'Export';

interface Props {
  atomic: AttackChainNodeResultOutput | AttackChainNodeResultOverviewOutput;
  actions: AtomicTestingActionType[];
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const AtomicTestingPopover: FunctionComponent<Props> = ({
  atomic,
  actions = [],
  onDelete,
  inList = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext);

  // Duplicate
  const [duplicate, setDuplicate] = useState(false);
  const handleOpenDuplicate = () => setDuplicate(true);
  const handleCloseDuplicate = () => setDuplicate(false);
  const submitDuplicate = async () => {
    await duplicateAtomicTesting(atomic.node_id).then((result: { data: AttackChainNodeResultOverviewOutput }) => {
      handleCloseDuplicate();
      navigate(`/admin/atomic_testings/${result.data.node_id}`);
    });
  };

  // Edition
  const [edition, setEdition] = useState(false);
  const handleOpenEdit = () => setEdition(true);
  const handleCloseEdit = () => setEdition(false);

  // Deletion
  const [deletion, setDeletion] = useState(false);
  const handleOpenDelete = () => setDeletion(true);
  const handleCloseDelete = () => setDeletion(false);
  const submitDelete = () => {
    deleteAtomicTesting(atomic.node_id).then(() => {
      handleCloseDelete();
      if (onDelete) onDelete(atomic.node_id);
    });
  };

  // export
  const [exportOpen, setExportOpen] = useState(false);
  const handleOpenExport = () => setExportOpen(true);
  const handleCloseExport = () => setExportOpen(false);
  const doExport = (withPlayers: boolean, withTeams: boolean, withVariableValues: boolean) => {
    const exportData: AttackChainNodeIndividualExportRequestInput = {
      options: {
        with_players: withPlayers,
        with_teams: withTeams,
        with_variable_values: withVariableValues,
      },
    };

    exportAttackChainNode(atomic.node_id, exportData).then((result) => {
      const contentDisposition = result.headers['content-disposition'];
      const match = contentDisposition.match(/filename\s*=\s*(.*)/i);
      const filename = match[1];
      download(result.data, filename, result.headers['content-type']);
    });
    handleCloseExport ();
  };

  // Button Popover
  const entries = [];
  if (actions.includes('Update') && atomic.node_injector_contract !== null) entries.push({
    label: 'Update',
    action: () => handleOpenEdit(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, atomic.node_id) || ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT),
  });
  if (actions.includes('Duplicate') && atomic.node_injector_contract !== null) entries.push({
    label: 'Duplicate',
    action: () => handleOpenDuplicate(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT),
  });
  if (actions.includes('Export') && atomic.node_injector_contract !== null) entries.push({
    label: t('node_export_json_single'),
    action: () => handleOpenExport(),
    userRight: true,
  });
  if (actions.includes('Delete')) entries.push({
    label: 'Delete',
    action: () => handleOpenDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.RESOURCE, atomic.node_id) || ability.can(ACTIONS.DELETE, SUBJECTS.ASSESSMENT),
  });

  return (
    <>
      <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />
      {actions.includes(('Update'))
        && (
          <AtomicTestingUpdate
            open={edition}
            handleClose={handleCloseEdit}
            atomic={atomic}
          />
        )}
      {actions.includes('Duplicate')
        && (
          <DialogDuplicate
            open={duplicate}
            handleClose={handleCloseDuplicate}
            handleSubmit={submitDuplicate}
            text={`${t('Do you want to duplicate this atomic testing:')} ${atomic.node_title} ?`}
          />
        )}
      {actions.includes('Export')
        && (
          <ExportOptionsDialog
            title={t('atomic_testing_export_prompt')}
            open={exportOpen}
            onCancel={handleCloseExport}
            onClose={handleCloseExport}
            onSubmit={doExport}
          />
        )}
      {actions.includes('Delete')
        && (
          <DialogDelete
            open={deletion}
            handleClose={handleCloseDelete}
            handleSubmit={submitDelete}
            text={`${t('Do you want to delete this atomic testing:')} ${atomic.node_title} ?`}
          />
        )}
    </>
  );
};

export default AtomicTestingPopover;
