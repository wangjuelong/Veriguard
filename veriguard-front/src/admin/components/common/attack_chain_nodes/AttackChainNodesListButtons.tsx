import { BarChartOutlined, GridOnOutlined, ReorderOutlined, ViewTimelineOutlined } from '@mui/icons-material';
import { ToggleButton, ToggleButtonGroup, Tooltip } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { AttackChainNodeContext, PermissionsContext, ViewModeContext } from '../Context';
import AttackChainNodeImportMenu from './AttackChainNodeImportMenu';

const useStyles = makeStyles()(() => ({
  container: {
    display: 'flex',
    justifyContent: 'flex-end',
    alignItems: 'center',
    gap: 10,
  },
}));

interface Props {
  setViewMode?: (mode: string) => void;
  availableButtons: string[];
  onImportedAttackChainNodes?: () => void;
}

const AttackChainNodesListButtons: FunctionComponent<Props> = ({
  setViewMode,
  availableButtons,
  onImportedAttackChainNodes,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const injectContext = useContext(AttackChainNodeContext);
  const viewModeContext = useContext(ViewModeContext);
  const { permissions } = useContext(PermissionsContext);

  const hasImportModesEnabled = () => !!injectContext.onImportAttackChainNodeFromXls || !!injectContext.onImportAttackChainNodeFromJson;

  return (
    <div className={classes.container}>
      {hasImportModesEnabled()
        && permissions.canManage && <AttackChainNodeImportMenu onImportedAttackChainNodes={onImportedAttackChainNodes} />}
      <ToggleButtonGroup
        size="small"
        exclusive
        style={{ float: 'right' }}
        aria-label="Change view mode"
      >
        {(!!setViewMode && availableButtons.includes('list'))
          && (
            <Tooltip title={t('List view')}>
              <ToggleButton
                value="list"
                onClick={() => setViewMode('list')}
                selected={viewModeContext === 'list'}
                aria-label="List view mode"
              >
                <ReorderOutlined fontSize="small" color={viewModeContext === 'list' ? 'inherit' : 'primary'} />
              </ToggleButton>
            </Tooltip>
          )}
        {(!!setViewMode && availableButtons.includes('chain'))
          && (
            <Tooltip title={t('Interactive view')}>
              <ToggleButton
                value="chain"
                onClick={() => setViewMode('chain')}
                selected={viewModeContext === 'chain'}
                aria-label="Interactive view mode"
              >
                <ViewTimelineOutlined fontSize="small" color={viewModeContext === 'chain' ? 'inherit' : 'primary'} />
              </ToggleButton>
            </Tooltip>
          )}
        {(!!setViewMode && availableButtons.includes('distribution'))
          && (
            <Tooltip title={t('Distribution view')}>
              <ToggleButton
                value="distribution"
                onClick={() => setViewMode('distribution')}
                aria-label="Distribution view mode"
              >
                <BarChartOutlined fontSize="small" color="primary" />
              </ToggleButton>
            </Tooltip>
          )}
        {(!!setViewMode && availableButtons.includes('matrix'))
          && (
            <Tooltip title={t('Attack pattern matrix')}>
              <ToggleButton
                value="matrix"
                onClick={() => setViewMode('matrix')}
                selected={viewModeContext === 'matrix'}
                aria-label="Attack pattern matrix"
              >
                <GridOnOutlined fontSize="small" color={viewModeContext === 'matrix' ? 'inherit' : 'primary'} />
              </ToggleButton>
            </Tooltip>
          )}
      </ToggleButtonGroup>
    </div>
  );
};

export default AttackChainNodesListButtons;
