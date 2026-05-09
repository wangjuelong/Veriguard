import { Add } from '@mui/icons-material';
import { Drawer, Fab } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type InjectorContractHelper } from '../../../../../actions/injector_contracts/injector-contract-helper';
import { fetchInjectorContract } from '../../../../../actions/NodeContracts';
import { useHelper } from '../../../../../store';
import { type AttackChainRun, type InjectorContract } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { PermissionsContext } from '../../../common/Context';
import QuickAttackChainNode, { EMAIL_CONTRACT } from './QuickAttackChainNode';

const useStyles = makeStyles()(theme => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 230,
  },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

interface Props { attack_chain_run: AttackChainRun }

const CreateQuickAttackChainNode: FunctionComponent<Props> = ({ attack_chain_run }) => {
  const dispatch = useAppDispatch();
  const { classes } = useStyles();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);

  const [open, setOpen] = useState(false);
  const { injectorContract }: { injectorContract: InjectorContract }
    = useHelper((helper: InjectorContractHelper) => ({ injectorContract: helper.getInjectorContract(EMAIL_CONTRACT) }));
  useEffect(() => {
    dispatch(fetchInjectorContract(EMAIL_CONTRACT));
  }, []);

  return (
    <>
      <Fab
        onClick={() => setOpen(true)}
        color="primary"
        aria-label="Add"
        className={classes.createButton}
        disabled={attack_chain_run.attack_chain_run_status !== 'RUNNING'}
      >
        <Add />
      </Fab>
      {injectorContract
        && (
          <Drawer
            open={open}
            keepMounted={false}
            anchor="right"
            sx={{ zIndex: 1202 }}
            onClose={() => setOpen(false)}
            elevation={1}
            disableEnforceFocus={true}
          >
            <QuickAttackChainNode
              exerciseId={attack_chain_run.attack_chain_run_id}
              attack_chain_run={attack_chain_run}
              injectorContract={injectorContract}
              handleClose={() => setOpen(false)}
              theme={theme}
              isDisabled={permissions.readOnly}
            />
          </Drawer>
        )}
    </>
  );
};

export default CreateQuickAttackChainNode;
