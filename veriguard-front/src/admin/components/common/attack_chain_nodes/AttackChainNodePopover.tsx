import { Button, Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';
import { Link } from 'react-router';

import { type AttackChainNodeStore } from '../../../../actions/attack_chain_nodes/AttackChainNode';
import { exportAttackChainNode } from '../../../../actions/attack_chain_nodes/node-action';
import { duplicateAttackChainNodeForAttackChain, duplicateAttackChainNodeForAttackChainRun } from '../../../../actions/AttackChainNode';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import DialogDuplicate from '../../../../components/common/DialogDuplicate';
import DialogTest from '../../../../components/common/DialogTest';
import ExportOptionsDialog from '../../../../components/common/export/ExportOptionsDialog';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import type {
  AttackChainNode,
  AttackChainNodeIndividualExportRequestInput,
  AttackChainNodeStatus,
  AttackChainNodeTestStatusOutput,
} from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAppDispatch } from '../../../../utils/hooks';
import { download } from '../../../../utils/utils';
import { AttackChainNodeContext, AttackChainNodeTestContext, PermissionsContext } from '../Context';

type AttackChainNodePopoverType = {
  node_id: string;
  node_attack_chain_run?: string;
  node_attack_chain?: string;
  node_status?: AttackChainNodeStatus;
  node_testable?: boolean;
  node_teams?: string[];
  node_type?: string;
  node_enabled?: boolean;
  node_title?: string;
};

interface Props {
  node: AttackChainNodePopoverType;
  setSelectedAttackChainNodeId: (injectId: AttackChainNode['node_id']) => void;
  isDisabled?: boolean;
  canBeTested?: boolean;
  canDone?: boolean;
  canTriggerNow?: boolean;
  onCreate?: (result: {
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }) => void;
  onUpdate?: (result: {
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }) => void;
  onDelete?: (result: string) => void;
}

const AttackChainNodePopover: FunctionComponent<Props> = ({
  node,
  setSelectedAttackChainNodeId,
  isDisabled = false,
  canBeTested = false,
  canDone = false,
  canTriggerNow = false,
  onCreate,
  onUpdate,
  onDelete,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);
  const {
    onUpdateAttackChainNodeTrigger,
    onUpdateAttackChainNodeActivation,
    onAttackChainNodeDone,
    onDeleteAttackChainNode,
  } = useContext(AttackChainNodeContext);

  const {
    contextId,
    testAttackChainNode,
    url,
  } = useContext(AttackChainNodeTestContext);

  const [openDelete, setOpenDelete] = useState(false);
  const [duplicate, setDuplicate] = useState(false);
  const [openTest, setOpenTest] = useState(false);
  const [openEnable, setOpenEnable] = useState(false);
  const [openDisable, setOpenDisable] = useState(false);
  const [openDone, setOpenDone] = useState(false);
  const [openTrigger, setOpenTrigger] = useState(false);
  const [openExportDialog, setOpenExportDialog] = useState(false);

  const handleOpenDuplicate = () => {
    setDuplicate(true);
  };
  const handleCloseDuplicate = () => setDuplicate(false);

  const submitDuplicate = () => {
    if (node.node_attack_chain_run) {
      dispatch(duplicateAttackChainNodeForAttackChainRun(node.node_attack_chain_run, node.node_id)).then((result: {
        result: string;
        entities: { nodes: Record<string, AttackChainNodeStore> };
      }) => {
        onCreate?.(result);
      });
    }
    if (node.node_attack_chain) {
      dispatch(duplicateAttackChainNodeForAttackChain(node.node_attack_chain, node.node_id)).then((result: {
        result: string;
        entities: { nodes: Record<string, AttackChainNodeStore> };
      }) => {
        onCreate?.(result);
      });
    }
    handleCloseDuplicate();
  };

  const handleOpenDelete = () => {
    setOpenDelete(true);
  };
  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    onDeleteAttackChainNode(node.node_id).then(() => {
      onDelete?.(node.node_id);
      handleCloseDelete();
    });
  };

  const handleOpenTest = () => {
    setOpenTest(true);
  };
  const handleCloseTest = () => setOpenTest(false);

  const handleExportOpen = () => setOpenExportDialog(true);
  const handleExportClose = () => setOpenExportDialog(false);

  const handleExportJsonSingle = (withPlayers: boolean, withTeams: boolean, withVariableValues: boolean) => {
    const exportData: AttackChainNodeIndividualExportRequestInput = {
      options: {
        with_players: withPlayers,
        with_teams: withTeams,
        with_variable_values: withVariableValues,
      },
    };
    exportAttackChainNode(node.node_id, exportData).then((result) => {
      const contentDisposition = result.headers['content-disposition'];
      const match = contentDisposition.match(/filename\s*=\s*(.*)/i);
      const filename = match[1];
      download(result.data, filename, result.headers['content-type']);
    });
    handleExportClose();
  };

  const submitTest = () => {
    if (testAttackChainNode) {
      testAttackChainNode(contextId, node.node_id).then((result: { data: AttackChainNodeTestStatusOutput }) => {
        MESSAGING$.notifySuccess(t('AttackChainNode test has been sent, you can view test logs details on {itsDedicatedPage}.', { itsDedicatedPage: <Link to={`${url}${result.data.status_id}`}>{t('its dedicated page')}</Link> }));
      });
    }
    handleCloseTest();
  };

  const handleOpenEnable = () => {
    setOpenEnable(true);
  };
  const handleCloseEnable = () => setOpenEnable(false);

  const submitEnable = () => {
    onUpdateAttackChainNodeActivation(node.node_id, { node_enabled: true }).then((result) => {
      onUpdate?.(result);
      handleCloseEnable();
    });
  };

  const handleOpenDisable = () => {
    setOpenDisable(true);
  };
  const handleCloseDisable = () => setOpenDisable(false);

  const submitDisable = () => {
    onUpdateAttackChainNodeActivation(node.node_id, { node_enabled: false }).then((result) => {
      onUpdate?.(result);
      handleCloseDisable();
    });
  };

  const handleOpenDone = () => {
    setOpenDone(true);
  };
  const handleCloseDone = () => setOpenDone(false);

  const submitDone = () => {
    onAttackChainNodeDone?.(node.node_id).then((result) => {
      onUpdate?.(result);
      handleCloseDone();
    });
  };

  const handleOpenEditContent = () => {
    setSelectedAttackChainNodeId(node.node_id);
  };

  const handleOpenTrigger = () => {
    setOpenTrigger(true);
  };
  const handleCloseTrigger = () => setOpenTrigger(false);

  const submitTrigger = () => {
    onUpdateAttackChainNodeTrigger?.(node.node_id).then((result) => {
      onUpdate?.(result);
      handleCloseTrigger();
    });
  };

  // Button Popover
  const entries = [];
  entries.push({
    label: 'Update',
    action: () => handleOpenEditContent(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  entries.push({
    label: 'Duplicate',
    action: () => handleOpenDuplicate(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  if (node.node_testable && canBeTested) entries.push({
    label: 'Test',
    action: () => handleOpenTest(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  entries.push({
    label: 'node_export_json_single',
    action: () => handleExportOpen(),
    disabled: isDisabled,
    userRight: true,
  });
  if (!node.node_status && onAttackChainNodeDone && canDone) entries.push({
    label: 'Mark as done',
    action: () => handleOpenDone(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  if (node.node_type !== 'veriguard_manual' && canTriggerNow && onUpdateAttackChainNodeTrigger) entries.push({
    label: 'Trigger now',
    action: () => handleOpenTrigger(),
    disabled: isDisabled || !permissions.isRunning,
    userRight: permissions.canLaunch,
  });
  if (node.node_enabled) entries.push({
    label: 'Disable',
    action: () => handleOpenDisable(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  else entries.push({
    label: 'Enable',
    action: () => handleOpenEnable(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  entries.push({
    label: 'Delete',
    action: () => handleOpenDelete(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />

      <DialogDuplicate
        open={duplicate}
        handleClose={handleCloseDuplicate}
        handleSubmit={submitDuplicate}
        text={`${t('Do you want to duplicate this node:')} ${node.node_title} ?`}
      />
      <Dialog
        slots={{ transition: Transition }}
        open={openDone}
        onClose={handleCloseDone}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t(`Do you want to mark this node as done: ${node.node_title}?`)}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDone}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={submitDone}>
            {t('Mark')}
          </Button>
        </DialogActions>
      </Dialog>
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete this node:')} ${node.node_title} ?`}
      />
      <DialogTest
        open={openTest}
        handleClose={handleCloseTest}
        handleSubmit={submitTest}
        text={`${t('Do you want to test this node:')} ${node.node_title} ?`}
      />
      <Dialog
        slots={{ transition: Transition }}
        open={openEnable}
        onClose={handleCloseEnable}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t(`Do you want to enable this node: ${node.node_title}?`)}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseEnable}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={submitEnable}>
            {t('Enable')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        slots={{ transition: Transition }}
        open={openDisable}
        onClose={handleCloseDisable}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {`${t('Do you want to disable this node:')} ${node.node_title} ?`}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDisable}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={submitDisable}>
            {t('Disable')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        slots={{ transition: Transition }}
        open={openTrigger}
        onClose={handleCloseTrigger}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t(`Do you want to trigger this node now: ${node.node_title}?`)}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseTrigger}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={submitTrigger}>
            {t('Trigger')}
          </Button>
        </DialogActions>
      </Dialog>
      <ExportOptionsDialog
        title={t('node_export_prompt')}
        open={openExportDialog}
        onCancel={handleExportClose}
        onClose={handleExportClose}
        onSubmit={handleExportJsonSingle}
      />
    </>
  );
};

export default AttackChainNodePopover;
