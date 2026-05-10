import { type FunctionComponent, useState } from 'react';

import { checkAttackChainTagRules, updateAttackChain } from '../../../../actions/attack_chains/attack_chain-actions';
import DialogApplyTagRule from '../../../../components/common/DialogApplyTagRule';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import {
  type AttackChain,
  type CheckAttackChainRulesOutput,
  type UpdateAttackChainInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useAttackChainPermissions from '../../../../utils/permissions/useAttackChainPermissions';
import AttackChainForm from '../AttackChainForm';

interface Props {
  attack_chain: AttackChain;
  open: boolean;
  handleClose: () => void;
}

const AttackChainUpdate: FunctionComponent<Props> = ({
  attack_chain,
  open,
  handleClose,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const permissions = useAttackChainPermissions(attack_chain.attack_chain_id);

  // apply rule dialog
  const [openApplyRule, setOpenApplyRule] = useState(false);
  const handleOpenApplyRule = () => setOpenApplyRule(true);
  const handleCloseApplyRule = () => setOpenApplyRule(false);

  // AttackChain form
  const initialValues = (({
    attack_chain_name,
    attack_chain_subtitle,
    attack_chain_description,
    attack_chain_category,
    attack_chain_main_focus,
    attack_chain_severity,
    attack_chain_tags,
    attack_chain_external_reference,
    attack_chain_external_url,
    attack_chain_custom_dashboard,
  }) => ({
    attack_chain_name,
    attack_chain_subtitle: attack_chain_subtitle ?? '',
    attack_chain_category: attack_chain_category ?? 'attack-attack_chain',
    attack_chain_main_focus: attack_chain_main_focus ?? 'incident-response',
    attack_chain_severity: attack_chain_severity ?? 'high',
    attack_chain_description: attack_chain_description ?? '',
    attack_chain_tags: attack_chain_tags ?? [],
    attack_chain_external_reference: attack_chain_external_reference ?? '',
    attack_chain_external_url: attack_chain_external_url ?? '',
    attack_chain_custom_dashboard: attack_chain_custom_dashboard ?? '',
  }))(attack_chain);

  const [scenarioFormData, setAttackChainFormData] = useState<UpdateAttackChainInput>(initialValues);

  const submitAttackChainUpdate = (data: UpdateAttackChainInput) => {
    dispatch(updateAttackChain(attack_chain.attack_chain_id, data));
    handleClose();
  };

  const submitEdit = (data: UpdateAttackChainInput) => {
    setAttackChainFormData(data);

    // before updating the attack_chain we are checking if tag rules could apply
    // -> if yes we ask the user to apply or not apply the rules at the update
    checkAttackChainTagRules(attack_chain.attack_chain_id, data.attack_chain_tags ?? []).then(
      (result: { data: CheckAttackChainRulesOutput }) => {
        if (result.data.rules_found) {
          handleOpenApplyRule();
        } else {
          submitAttackChainUpdate(data);
        }
      },
    );
  };

  const handleTagRuleChoice = (shouldApply: boolean) => {
    scenarioFormData.apply_tag_rule = shouldApply;
    submitAttackChainUpdate(scenarioFormData);
    handleCloseApplyRule();
  };

  return (
    <>
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Update the attack_chain')}
      >
        <AttackChainForm
          initialValues={initialValues}
          editing
          disabled={permissions.readOnly}
          onSubmit={submitEdit}
          handleClose={handleClose}
        />
      </Drawer>
      <DialogApplyTagRule
        open={openApplyRule}
        handleClose={handleCloseApplyRule}
        handleApplyRule={() => handleTagRuleChoice(true)}
        handleDontApplyRule={() => handleTagRuleChoice(false)}
      />
    </>
  );
};

export default AttackChainUpdate;
