import { useState } from 'react';
import { useNavigate } from 'react-router';

import { addAttackChainRun } from '../../../../actions/AttackChainRun';
import { type LoggedHelper } from '../../../../actions/helper';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type AttackChainRun, type CreateAttackChainRunInput, type PlatformSettings } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import AttackChainRunForm from './AttackChainRunForm';

const AttackChainRunCreation = () => {
  // Standard hooks
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const onSubmit = (data: CreateAttackChainRunInput) => {
    dispatch(addAttackChainRun(data)).then((result: {
      result: string;
      entities: { attack_chains: Record<string, AttackChainRun> };
    }) => {
      setOpen(false);
      navigate(`/admin/attack_chain_runs/${result.result}`);
    });
  };

  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  // Form
  const initialValues: CreateAttackChainRunInput = {
    attack_chain_run_name: '',
    attack_chain_run_subtitle: '',
    attack_chain_run_description: '',
    attack_chain_run_category: 'attack-attack_chain',
    attack_chain_run_main_focus: 'incident-response',
    attack_chain_run_severity: 'high',
    attack_chain_run_tags: [],
    attack_chain_run_start_date: null,
    attack_chain_run_mail_from: settings.default_mailer,
    attack_chain_run_mails_reply_to: [settings.default_reply_to ? settings.default_reply_to : ''],
    attack_chain_run_message_header: t('SIMULATION HEADER'),
    attack_chain_run_message_footer: t('SIMULATION FOOTER'),
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new attack_chain_run')}
      >
        <AttackChainRunForm
          onSubmit={onSubmit}
          handleClose={() => setOpen(false)}
          initialValues={initialValues}
          edit={false}
        />
      </Drawer>
    </>
  );
};

export default AttackChainRunCreation;
