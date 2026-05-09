import { UpdateOutlined } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle, IconButton, Tooltip } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { updateAttackChainRunStartDate } from '../../../../actions/AttackChainRun';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type AttackChainRun } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import AttackChainRunDateForm from './AttackChainRunDateForm';

interface Props { attack_chain_run: AttackChainRun }

const AttackChainRunDatePopover: FunctionComponent<Props> = ({ attack_chain_run }) => {
  const [openEdit, setOpenEdit] = useState(false);
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const onSubmitEdit = async (data: Pick<AttackChainRun, 'attack_chain_run_start_date'>) => {
    await dispatch(updateAttackChainRunStartDate(attack_chain_run.attack_chain_run_id, data));
    setOpenEdit(false);
  };
  const initialValues = { attack_chain_run_start_date: attack_chain_run.attack_chain_run_start_date };
  return (
    <>
      <Tooltip title={(t('Modify the scheduling'))}>
        <span>
          <IconButton size="small" color="primary" onClick={() => setOpenEdit(true)} style={{ marginRight: 5 }} disabled={attack_chain_run.attack_chain_run_status !== 'SCHEDULED'}>
            <UpdateOutlined />
          </IconButton>
        </span>
      </Tooltip>
      <Dialog
        TransitionComponent={Transition}
        open={openEdit}
        onClose={() => setOpenEdit(false)}
        PaperProps={{ elevation: 1 }}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>{t('Update attack_chain_run start date and time')}</DialogTitle>
        <DialogContent>
          <AttackChainRunDateForm
            initialValues={initialValues}
            onSubmit={onSubmitEdit}
            handleClose={() => setOpenEdit(false)}
          />
        </DialogContent>
      </Dialog>
    </>
  );
};

export default AttackChainRunDatePopover;
