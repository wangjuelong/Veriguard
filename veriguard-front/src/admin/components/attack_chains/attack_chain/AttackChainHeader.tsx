import { PlayArrowOutlined, Stop } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type Dispatch, type SetStateAction, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { createRunningAttackChainRunFromAttackChain, updateAttackChainRecurrence } from '../../../../actions/attack_chains/attack_chain-actions';
import { type AttackChainsHelper } from '../../../../actions/attack_chains/attack_chain-helper';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { ATTACK_CHAIN_RUN_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import {
  type AttackChain,
  type AttackChainRun,
} from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Cron } from '../../../../utils/period/Cron';
import handle from '../../../../utils/period/Period';
import { type PeriodExpressionHandler } from '../../../../utils/period/PeriodExpressionHandler';
import useAttackChainPermissions from '../../../../utils/permissions/useAttackChainPermissions';
import { truncate } from '../../../../utils/String';
import AttackChainPopover from './AttackChainPopover';
import AttackChainRecurringFormDialog from './AttackChainRecurringFormDialog';

const useStyles = makeStyles()(() => ({
  title: {
    float: 'left',
    marginRight: 10,
  },
  statusScheduled: {
    float: 'left',
    margin: '4px 0 0 5px',
    width: 20,
    height: 20,
    borderRadius: '50%',
    boxShadow: '0px 0px 5px 2px #4caf50',
    animation: 'pulse-green 1s linear infinite alternate',
  },
  statusNotScheduled: {
    float: 'left',
    margin: '4px 0 0 5px',
    width: 20,
    height: 20,
    borderRadius: '50%',
    boxShadow: '0px 0px 5px 2px #f44336',
  },
  actions: {
    margin: '-6px 0 0 0',
    float: 'right',
    display: 'flex',
  },
}));

interface AttackChainHeaderProps {
  cronObject: PeriodExpressionHandler | null;
  setCronObject: Dispatch<SetStateAction<PeriodExpressionHandler | null>>;
  setSelectRecurring: Dispatch<SetStateAction<string>>;
  selectRecurring: string;
  setOpenAttackChainRecurringFormDialog: Dispatch<SetStateAction<boolean>>;
  setOpenInstantiateSimulationAndStart: Dispatch<SetStateAction<boolean>>;
  openAttackChainRecurringFormDialog: boolean;
  openInstantiateSimulationAndStart: boolean;
  noRepeat: boolean;
}

const AttackChainHeader = ({
  cronObject,
  setCronObject,
  setSelectRecurring,
  selectRecurring,
  noRepeat,
  openAttackChainRecurringFormDialog,
  setOpenAttackChainRecurringFormDialog,
  openInstantiateSimulationAndStart,
  setOpenInstantiateSimulationAndStart,
}: AttackChainHeaderProps) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { classes } = useStyles();
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: AttackChain['attack_chain_id'] };
  const { canLaunch } = useAttackChainPermissions(scenarioId);

  // Fetching data
  const { attack_chain }: { attack_chain: AttackChain } = useHelper((helper: AttackChainsHelper) => ({ attack_chain: helper.getAttackChain(scenarioId) }));

  // Local
  const ended = attack_chain.attack_chain_recurrence_end && new Date(attack_chain.attack_chain_recurrence_end).getTime() < new Date().getTime();
  const onSubmit = (cron: Cron, start: string, end?: string) => {
    dispatch(updateAttackChainRecurrence(scenarioId, {
      attack_chain_recurrence: cron.toCronExpression(),
      attack_chain_recurrence_start: start,
      attack_chain_recurrence_end: end,
    })).then((result: { [x: string]: string }) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        setCronObject(cron);
      }
    });
    setOpenAttackChainRecurringFormDialog(false);
  };

  useEffect(() => {
    if (attack_chain.attack_chain_recurrence != null) {
      const newCron = handle(attack_chain.attack_chain_recurrence);
      setCronObject(newCron);
      if (noRepeat) {
        setSelectRecurring('noRepeat');
      } else {
        setSelectRecurring(newCron?.getRecurrenceMagnitude() || 'daily');
      }
    } else {
      setCronObject(null);
    }
  }, [attack_chain.attack_chain_recurrence]);
  const stop = () => {
    setCronObject(null);
    dispatch(updateAttackChainRecurrence(scenarioId, {
      attack_chain_recurrence: undefined,
      attack_chain_recurrence_start: undefined,
      attack_chain_recurrence_end: undefined,
    }));
  };

  return (
    <>
      <Tooltip title={attack_chain.attack_chain_name}>
        <Typography variant="h1" gutterBottom={true} classes={{ root: classes.title }}>
          {truncate(attack_chain.attack_chain_name, 80)}
        </Typography>
      </Tooltip>
      <div style={{
        float: 'left',
        margin: '4px 10px 0 8px',
        color: theme.palette.text?.disabled,
        borderLeft: `1px solid ${theme.palette.text?.disabled}`,
        height: 20,
      }}
      />
      <Tooltip title={t(attack_chain.attack_chain_recurrence ? 'Scheduled' : 'Not scheduled')}>
        <div className={attack_chain.attack_chain_recurrence ? classes.statusScheduled : classes.statusNotScheduled} />
      </Tooltip>
      <div className={classes.actions}>
        { canLaunch
          && attack_chain.attack_chain_recurrence && !ended ? (
              <Button
                style={{ marginRight: theme.spacing(1) }}
                startIcon={<Stop />}
                variant="outlined"
                color="inherit"
                size="small"
                onClick={stop}
              >
                {t('Stop')}
              </Button>
            )
          : (
              <>
                {canLaunch
                  && (
                    <Button
                      style={{
                        marginRight: theme.spacing(1),
                        lineHeight: 'initial',
                      }}
                      startIcon={<PlayArrowOutlined />}
                      variant="contained"
                      color="primary"
                      size="small"
                      onClick={() => setOpenInstantiateSimulationAndStart(true)}
                    >
                      {t('Launch now')}
                    </Button>
                  )}
              </>
            )}
        <AttackChainPopover
          attack_chain={attack_chain}
          actions={['Duplicate', 'Update', 'Delete', 'Export']}
          onDelete={() => navigate('/admin/attack_chains')}
        />
      </div>
      <AttackChainRecurringFormDialog
        cronObject={cronObject}
        setCronObject={setCronObject}
        selectRecurring={selectRecurring}
        onSelectRecurring={setSelectRecurring}
        open={openAttackChainRecurringFormDialog}
        setOpen={setOpenAttackChainRecurringFormDialog}
        onSubmit={onSubmit}
        initialValues={attack_chain}
      />
      <Dialog
        open={openInstantiateSimulationAndStart}
        TransitionComponent={Transition}
        onClose={() => setOpenInstantiateSimulationAndStart(false)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('A attack_chain_run will be launched based on this attack_chain and will start immediately. Are you sure you want to proceed?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenInstantiateSimulationAndStart(false)}>
            {t('Cancel')}
          </Button>
          <Button
            color="secondary"
            onClick={async () => {
              setOpenInstantiateSimulationAndStart(false);
              const attack_chain_run: AttackChainRun = (await createRunningAttackChainRunFromAttackChain(scenarioId)).data;
              navigate(`${ATTACK_CHAIN_RUN_BASE_URL}/${attack_chain_run.attack_chain_run_id}`);
              MESSAGING$.notifySuccess(t('New attack_chain_run successfully created and started'));
            }}
          >
            {t('Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
      <div className="clearfix" />
    </>
  );
};

export default AttackChainHeader;
