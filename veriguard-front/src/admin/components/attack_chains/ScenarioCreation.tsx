import { type FunctionComponent, useState } from 'react';
import { useNavigate } from 'react-router';

import { addAttackChain } from '../../../actions/attack_chains/attack_chain-actions';
import { type LoggedHelper } from '../../../actions/helper';
import ButtonCreate from '../../../components/common/ButtonCreate';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { ATTACK_CHAIN_BASE_URL } from '../../../constants/BaseUrls';
import { useHelper } from '../../../store';
import { type PlatformSettings, type AttackChain, type AttackChainInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import AttackChainForm from './AttackChainForm';

const AttackChainCreation: FunctionComponent = () => {
  // Standard hooks
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();
  const navigate = useNavigate();

  const dispatch = useAppDispatch();

  const onSubmit = (data: AttackChainInput, isAttackChainAssistantChecked?: boolean) => {
    dispatch(addAttackChain(data)).then(
      (result: {
        result: string;
        entities: { attack_chains: Record<string, AttackChain> };
      }) => {
        if (result.entities) {
          navigate(`${ATTACK_CHAIN_BASE_URL}/${result.result}?openAttackChainAssistant=${isAttackChainAssistantChecked}`);
          setOpen(false);
        }
      },
    );
  };

  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const initialValues: AttackChainInput = {
    attack_chain_name: '',
    attack_chain_category: 'attack-attack_chain',
    attack_chain_main_focus: 'incident-response',
    attack_chain_severity: 'high',
    attack_chain_subtitle: '',
    attack_chain_description: '',
    attack_chain_external_reference: '',
    attack_chain_external_url: '',
    attack_chain_tags: [],
    attack_chain_message_header: t('SIMULATION HEADER'),
    attack_chain_message_footer: t('SIMULATION FOOTER'),
    attack_chain_mail_from: settings.default_mailer ?? '',
    attack_chain_mails_reply_to: [settings.default_reply_to ?? ''],
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new attack_chain')}
      >
        <AttackChainForm
          onSubmit={onSubmit}
          initialValues={initialValues}
          handleClose={() => setOpen(false)}
          isCreation
        />
      </Drawer>
    </>
  );
};
export default AttackChainCreation;
