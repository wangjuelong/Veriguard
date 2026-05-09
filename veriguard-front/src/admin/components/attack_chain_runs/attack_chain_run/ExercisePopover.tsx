import { type FunctionComponent, useContext, useState } from 'react';
import { useNavigate } from 'react-router';

import { checkAttackChainRunTagRules } from '../../../../actions/attack_chain_runs/attack_chain_run-action';
import { deleteAttackChainRun, duplicateAttackChainRun, updateAttackChainRun } from '../../../../actions/AttackChainRun';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogApplyTagRule from '../../../../components/common/DialogApplyTagRule';
import DialogDelete from '../../../../components/common/DialogDelete';
import DialogDuplicate from '../../../../components/common/DialogDuplicate';
import Drawer from '../../../../components/common/Drawer';
import ExportOptionsDialog from '../../../../components/common/export/ExportOptionsDialog';
import { useFormatter } from '../../../../components/i18n';
import {
  type CheckAttackChainRulesOutput,
  type AttackChainRun,
  type UpdateAttackChainRunInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import useSimulationPermissions from '../../../../utils/permissions/useSimulationPermissions';
import AttackChainRunForm from './AttackChainRunForm';
import AttackChainRunReports from './reports/AttackChainRunReports';

export type AttackChainRunActionPopover = 'Duplicate' | 'Update' | 'Delete' | 'Export' | 'Access reports';

interface AttackChainRunPopoverProps {
  attack_chain_run: AttackChainRun;
  actions: AttackChainRunActionPopover[];
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const AttackChainRunPopover: FunctionComponent<AttackChainRunPopoverProps> = ({
  attack_chain_run,
  actions = [],
  onDelete,
  inList = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const permissions = useSimulationPermissions(attack_chain_run.attack_chain_run_id, attack_chain_run);
  const ability = useContext(AbilityContext);

  // Form
  const initialValues: UpdateAttackChainRunInput = {
    attack_chain_run_name: attack_chain_run.attack_chain_run_name,
    attack_chain_run_subtitle: attack_chain_run.attack_chain_run_subtitle ?? '',
    attack_chain_run_description: attack_chain_run.attack_chain_run_description,
    attack_chain_run_category: attack_chain_run.attack_chain_run_category ?? 'attack-attack_chain',
    attack_chain_run_main_focus: attack_chain_run.attack_chain_run_main_focus ?? 'incident-response',
    attack_chain_run_severity: attack_chain_run.attack_chain_run_severity ?? 'high',
    attack_chain_run_tags: attack_chain_run.attack_chain_run_tags ?? [],
    attack_chain_run_mail_from: attack_chain_run.attack_chain_run_mail_from ?? '',
    attack_chain_run_mails_reply_to: attack_chain_run.attack_chain_run_mails_reply_to ?? [],
    attack_chain_run_message_header: attack_chain_run.attack_chain_run_message_header ?? '',
    attack_chain_run_message_footer: attack_chain_run.attack_chain_run_message_footer ?? '',
    attack_chain_run_custom_dashboard: attack_chain_run.attack_chain_run_custom_dashboard ?? '',
    apply_tag_rule: false,
  };

  // Edit
  const [openEdit, setOpenEdit] = useState(false);
  const handleOpenEdit = () => setOpenEdit(true);
  const handleCloseEdit = () => setOpenEdit(false);
  const [exerciseFormData, setAttackChainRunFormData] = useState<UpdateAttackChainRunInput>(initialValues);

  // Delete
  const [openDelete, setOpenDelete] = useState(false);
  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    dispatch(deleteAttackChainRun(attack_chain_run.attack_chain_run_id)).then(() => {
      handleCloseDelete();
      if (onDelete) onDelete(attack_chain_run.attack_chain_run_id);
    });
  };

  // Duplicate
  const [openDuplicate, setOpenDuplicate] = useState(false);
  const handleOpenDuplicate = () => setOpenDuplicate(true);
  const handleCloseDuplicate = () => setOpenDuplicate(false);

  const submitDuplicate = () => {
    dispatch(duplicateAttackChainRun(attack_chain_run.attack_chain_run_id)).then((result: {
      result: string;
      entities: { attack_chain_runs: AttackChainRun };
    }) => {
      handleCloseDuplicate();
      navigate(`/admin/attack_chain_runs/${result.result}`);
    });
  };

  // Export
  const [openExport, setOpenExport] = useState(false);
  const handleOpenExport = () => setOpenExport(true);
  const handleCloseExport = () => setOpenExport(false);

  // Reports
  const [openReports, setOpenReports] = useState(false);
  const handleOpenReports = () => setOpenReports(true);
  const handleCloseReports = () => setOpenReports(false);

  // apply rule dialog
  const [openApplyRule, setOpenApplyRule] = useState(false);
  const handleOpenApplyRule = () => setOpenApplyRule(true);
  const handleCloseApplyRule = () => setOpenApplyRule(false);

  const submitExport = (withPlayers: boolean, withTeams: boolean, withVariableValues: boolean) => {
    const link = document.createElement('a');
    link.href = `/api/attack_chain_runs/${attack_chain_run.attack_chain_run_id}/export?isWithTeams=${withTeams}&isWithPlayers=${withPlayers}&isWithVariableValues=${withVariableValues}`;
    link.click();
    handleCloseExport();
  };

  // Button Popover
  const entries = [];
  if (actions.includes('Update')) entries.push({
    label: 'Update',
    action: () => handleOpenEdit(),
    disabled: !permissions.canManage,
    userRight: permissions.canManage,
  });
  if (actions.includes('Duplicate')) entries.push({
    label: 'Duplicate',
    action: () => handleOpenDuplicate(),
    userRight: permissions.canManage && ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT),
  });
  if (actions.includes('Export')) entries.push({
    label: 'Export',
    action: () => handleOpenExport(),
    userRight: true,
  });
  if (actions.includes('Access reports')) entries.push({
    label: 'Access reports',
    action: () => handleOpenReports(),
    userRight: true,
  });
  if (actions.includes('Delete')) entries.push({
    label: 'Delete',
    action: () => handleOpenDelete(),
    userRight: permissions.canManage,
  });

  const submitAttackChainRunUpdate = (data: UpdateAttackChainRunInput) => {
    const input = {
      attack_chain_run_name: data.attack_chain_run_name,
      attack_chain_run_subtitle: data.attack_chain_run_subtitle,
      attack_chain_run_severity: data.attack_chain_run_severity,
      attack_chain_run_category: data.attack_chain_run_category,
      attack_chain_run_description: data.attack_chain_run_description,
      attack_chain_run_main_focus: data.attack_chain_run_main_focus,
      attack_chain_run_tags: data.attack_chain_run_tags,
      attack_chain_run_mails_reply_to: data.attack_chain_run_mails_reply_to,
      attack_chain_run_mail_from: data.attack_chain_run_mail_from,
      attack_chain_run_message_header: data.attack_chain_run_message_header,
      attack_chain_run_message_footer: data.attack_chain_run_message_footer,
      attack_chain_run_custom_dashboard: data.attack_chain_run_custom_dashboard,
      apply_tag_rule: data.apply_tag_rule,
    };
    return dispatch(updateAttackChainRun(attack_chain_run.attack_chain_run_id, input)).then(() => handleCloseEdit());
  };

  const handleTagRuleChoice = (shouldApply: boolean) => {
    exerciseFormData.apply_tag_rule = shouldApply;
    submitAttackChainRunUpdate(exerciseFormData);
    handleCloseApplyRule();
  };

  const onSubmit = (data: UpdateAttackChainRunInput) => {
    setAttackChainRunFormData(data);
    // before updating the attack_chain_run we are checking if tag rules could apply
    // -> if yes we ask the user to apply or not apply the rules at the update
    checkAttackChainRunTagRules(attack_chain_run.attack_chain_run_id, data.attack_chain_run_tags ?? []).then(
      (result: { data: CheckAttackChainRulesOutput }) => {
        if (result.data.rules_found) {
          handleOpenApplyRule();
        } else {
          submitAttackChainRunUpdate(data);
        }
      },
    );
  };
  return (
    <>
      <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update attack_chain_run')}
      >
        <AttackChainRunForm
          onSubmit={onSubmit}
          initialValues={initialValues}
          disabled={permissions.readOnly}
          handleClose={handleCloseEdit}
          edit
        />

      </Drawer>
      <DialogApplyTagRule
        open={openApplyRule}
        handleClose={handleCloseApplyRule}
        handleApplyRule={() => handleTagRuleChoice(true)}
        handleDontApplyRule={() => handleTagRuleChoice(false)}
      />
      <Drawer
        open={openReports}
        containerStyle={{ padding: '0px' }}
        handleClose={handleCloseReports}
        title={t('Reports')}
      >
        <AttackChainRunReports exerciseId={attack_chain_run.attack_chain_run_id} exerciseName={attack_chain_run.attack_chain_run_name} />
      </Drawer>
      <DialogDuplicate
        open={openDuplicate}
        handleClose={handleCloseDuplicate}
        handleSubmit={submitDuplicate}
        text={`${t('Do you want to duplicate this attack_chain_run:')} ${attack_chain_run.attack_chain_run_name} ?`}
      />
      <ExportOptionsDialog
        title={t('Export the attack_chain_run')}
        open={openExport}
        onCancel={handleCloseExport}
        onClose={handleCloseExport}
        onSubmit={submitExport}
      />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete this attack_chain_run:')} ${attack_chain_run.attack_chain_run_name} ?`}
      />
    </>
  );
};

export default AttackChainRunPopover;
