import { type FunctionComponent, useContext, useState } from 'react';
import { useNavigate } from 'react-router';

import { deleteAttackChain, duplicateAttackChain, exportAttackChainUri } from '../../../../actions/attack_chains/attack_chain-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import DialogDuplicate from '../../../../components/common/DialogDuplicate';
import ExportOptionsDialog from '../../../../components/common/export/ExportOptionsDialog';
import { useFormatter } from '../../../../components/i18n';
import { type AttackChain } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import useAttackChainPermissions from '../../../../utils/permissions/useAttackChainPermissions';
import AttackChainUpdate from './AttackChainUpdate';

type AttackChainActionType = 'Duplicate' | 'Update' | 'Delete' | 'Export';

interface Props {
  attack_chain: AttackChain;
  actions: AttackChainActionType[];
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const AttackChainPopover: FunctionComponent<Props> = ({
  attack_chain,
  actions = [],
  onDelete,
  inList = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { canManage, canDelete } = useAttackChainPermissions(attack_chain.attack_chain_id);
  const ability = useContext(AbilityContext);

  // Duplicate
  const [duplicate, setDuplicate] = useState(false);
  const handleOpenDuplicate = () => setDuplicate(true);
  const handleCloseDuplicate = () => setDuplicate(false);
  const submitDuplicate = () => {
    dispatch(duplicateAttackChain(attack_chain.attack_chain_id)).then((result: {
      result: string;
      entities: { attack_chains: Record<string, AttackChain> };
    }) => {
      handleCloseDuplicate();
      navigate(`/admin/attack_chains/${result.result}`);
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
    dispatch(deleteAttackChain(attack_chain.attack_chain_id)).then(() => {
      handleCloseDelete();
      if (onDelete) onDelete(attack_chain.attack_chain_id);
    });
  };

  // Export
  const [exportation, setExportation] = useState(false);
  const handleOpenExport = () => setExportation(true);
  const handleCloseExport = () => setExportation(false);
  const submitExport = (exportPlayers: boolean, exportTeams: boolean, exportVariableValues: boolean) => {
    const link = document.createElement('a');
    link.href = exportAttackChainUri(attack_chain.attack_chain_id, exportTeams, exportPlayers, exportVariableValues);
    link.click();
  };

  // Button Popover
  const entries = [];
  if (actions.includes('Update')) entries.push({
    label: 'Update',
    action: () => handleOpenEdit(),
    userRight: canManage,
  });
  if (actions.includes('Duplicate')) entries.push({
    label: 'Duplicate',
    action: () => handleOpenDuplicate(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT),
  });
  if (actions.includes('Export')) entries.push({
    label: 'Export',
    action: () => handleOpenExport(),
    userRight: true,
  });
  if (actions.includes('Delete')) entries.push({
    label: 'Delete',
    action: () => handleOpenDelete(),
    userRight: canDelete,
  });

  return (
    <>
      {actions.length > 0 && <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />}
      {actions.includes(('Update'))
        && (
          <AttackChainUpdate
            attack_chain={attack_chain}
            open={edition}
            handleClose={handleCloseEdit}
          />
        )}
      {actions.includes('Duplicate')
        && (
          <DialogDuplicate
            open={duplicate}
            handleClose={handleCloseDuplicate}
            handleSubmit={submitDuplicate}
            text={`${t('Do you want to duplicate this attack_chain:')} ${attack_chain.attack_chain_name} ?`}
          />
        )}
      {actions.includes('Export')
        && (
          <ExportOptionsDialog
            title={t('Export the attack_chain')}
            open={exportation}
            onCancel={handleCloseExport}
            onClose={handleCloseExport}
            onSubmit={submitExport}
          />
        )}
      {actions.includes('Delete')
        && (
          <DialogDelete
            open={deletion}
            handleClose={handleCloseDelete}
            handleSubmit={submitDelete}
            text={`${t('Do you want to delete this attack_chain:')} ${attack_chain.attack_chain_name} ?`}
          />
        )}
    </>
  );
};

export default AttackChainPopover;
