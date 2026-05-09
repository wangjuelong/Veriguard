import { CancelOutlined, PauseOutlined, PlayArrowOutlined, RestartAltOutlined } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AttackChainRunsHelper } from '../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { updateAttackChainRunStatus } from '../../../../actions/AttackChainRun';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type AttackChainRun, type AttackChainRun as AttackChainRunType } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useSimulationPermissions from '../../../../utils/permissions/useAttackChainRunPermissions';
import { truncate } from '../../../../utils/String';
import AttackChainRunPopover, { type AttackChainRunActionPopover } from './AttackChainRunPopover';
import AttackChainRunStatus from './AttackChainRunStatus';

const useStyles = makeStyles()(() => ({
  title: {
    float: 'left',
    marginRight: 10,
  },
  actions: {
    margin: '-6px 0 0 0',
    float: 'right',
    display: 'flex',
  },
}));

const Buttons = ({ exerciseId, exerciseStatus, exerciseName, onLoading, isLoading }: {
  exerciseId: AttackChainRun['attack_chain_run_id'];
  exerciseStatus: AttackChainRun['attack_chain_run_status'];
  exerciseName: AttackChainRun['attack_chain_run_name'];
  onLoading: (loading: boolean) => void;
  isLoading: boolean;
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const permissions = useSimulationPermissions(exerciseId);
  const [openChangeStatus, setOpenChangeStatus] = useState<AttackChainRun['attack_chain_run_status'] | null>(null);

  const submitUpdateStatus = async (status: { attack_chain_run_status: AttackChainRun['attack_chain_run_status'] | null }) => {
    setOpenChangeStatus(null);
    onLoading(true);
    try {
      await dispatch(updateAttackChainRunStatus(exerciseId, { attack_chain_run_status: status.attack_chain_run_status ?? undefined }));
    } finally {
      onLoading(false);
    }
  };
  const executionButton = () => {
    switch (exerciseStatus) {
      case 'SCHEDULED': {
        if (permissions.canLaunch) {
          return (
            <Button
              style={{
                marginRight: 10,
                lineHeight: 'initial',
              }}
              startIcon={<PlayArrowOutlined />}
              variant="contained"
              size="small"
              color="primary"
              onClick={() => setOpenChangeStatus('RUNNING')}
              disabled={isLoading}
            >
              {t('Start now')}
            </Button>
          );
        }
        return (<div />);
      }
      case 'RUNNING': {
        if (permissions.canLaunch) {
          return (
            <Button
              style={{ marginRight: 10 }}
              startIcon={<PauseOutlined />}
              variant="outlined"
              color="warning"
              size="small"
              onClick={() => setOpenChangeStatus('PAUSED')}
              disabled={isLoading}
            >
              {t('Pause')}
            </Button>
          );
        }
        return (<div />);
      }
      case 'PAUSED': {
        if (permissions.canLaunch) {
          return (
            <Button
              style={{ marginRight: 10 }}
              variant="outlined"
              startIcon={<PlayArrowOutlined />}
              color="success"
              onClick={() => setOpenChangeStatus('RUNNING')}
              disabled={isLoading}
            >
              {t('Resume')}
            </Button>
          );
        }
        return <div />;
      }
      default:
        return <div />;
    }
  };

  const dangerousButton = () => {
    switch (exerciseStatus) {
      case 'RUNNING':
      case 'PAUSED': {
        if (permissions.canLaunch) {
          return (
            <Button
              style={{ marginRight: 10 }}
              variant="outlined"
              startIcon={<CancelOutlined />}
              color="error"
              onClick={() => setOpenChangeStatus('CANCELED')}
              disabled={isLoading}
            >
              {t('Stop')}
            </Button>
          );
        }
        return <div />;
      }
      case 'FINISHED':
      case 'CANCELED': {
        if (permissions.canLaunch) {
          return (
            <Button
              style={{ marginRight: 10 }}
              variant="outlined"
              startIcon={<RestartAltOutlined />}
              color="warning"
              onClick={() => setOpenChangeStatus('SCHEDULED')}
              disabled={isLoading}
            >
              {t('Reset')}
            </Button>
          );
        }
        return <div />;
      }
      default:
        return <div />;
    }
  };

  const dialogContentText = () => {
    switch (openChangeStatus) {
      case 'RUNNING':
        return `${exerciseName} ${t('will be started, do you want to continue?')}`;
      case 'PAUSED':
        return `${t('AttackChainNodes will be paused, do you want to continue?')}`;
      case 'SCHEDULED':
        return `${exerciseName} ${t('data will be reset, do you want to restart?')}`;
      case 'CANCELED':
        return `${exerciseName} ${t('data will be reset, do you want to restart?')}`;
      default:
        return 'Do you want to change the status of this attack_chain_run?';
    }
  };
  return (
    <>
      {executionButton()}
      {dangerousButton()}
      <Dialog
        open={Boolean(openChangeStatus)}
        TransitionComponent={Transition}
        onClose={() => setOpenChangeStatus(null)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {dialogContentText()}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenChangeStatus(null)}>
            {t('Cancel')}
          </Button>
          <Button
            color="secondary"
            onClick={() => submitUpdateStatus({ attack_chain_run_status: openChangeStatus })}
          >
            {t('Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

const AttackChainRunHeader = ({ onLoading, isLoading }: {
  onLoading: (loading: boolean) => void;
  isLoading: boolean;
}) => {
  // Standard hooks
  const theme = useTheme();
  const { classes } = useStyles();
  const navigate = useNavigate();

  const { exerciseId } = useParams() as { exerciseId: AttackChainRunType['attack_chain_run_id'] };
  const { attack_chain_run } = useHelper((helper: AttackChainRunsHelper) => {
    return { attack_chain_run: helper.getAttackChainRun(exerciseId) };
  });

  const actions: AttackChainRunActionPopover[] = ['Update', 'Duplicate', 'Export', 'Delete', 'Access reports'];

  return (
    <>
      <Tooltip title={attack_chain_run.attack_chain_run_name}>
        <Typography variant="h1" gutterBottom={true} classes={{ root: classes.title }}>
          {truncate(attack_chain_run.attack_chain_run_name, 80)}
        </Typography>
      </Tooltip>
      <div style={{
        float: 'left',
        margin: '3px 10px 0 8px',
        color: theme.palette.text?.disabled,
        borderLeft: `1px solid ${theme.palette.text?.disabled}`,
        height: 20,
      }}
      />
      <AttackChainRunStatus exerciseStatus={attack_chain_run.attack_chain_run_status} exerciseStartDate={attack_chain_run.attack_chain_run_start_date} />
      <div className={classes.actions}>
        <Buttons
          exerciseId={attack_chain_run.attack_chain_run_id}
          exerciseStatus={attack_chain_run.attack_chain_run_status}
          exerciseName={attack_chain_run.attack_chain_run_name}
          onLoading={onLoading}
          isLoading={isLoading}
        />
        <AttackChainRunPopover
          attack_chain_run={attack_chain_run}
          actions={actions}
          onDelete={() => navigate('/admin/attack_chain_runs')}
        />
      </div>
      <div className="clearfix" />
    </>
  );
};

export default AttackChainRunHeader;
