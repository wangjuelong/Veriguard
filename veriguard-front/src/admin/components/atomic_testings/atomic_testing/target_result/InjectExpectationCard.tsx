import { AddModeratorOutlined, InventoryOutlined } from '@mui/icons-material';
import { Chip, IconButton, Tooltip, Typography } from '@mui/material';
import { useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import ButtonPopover from '../../../../../components/common/ButtonPopover';
import Paper from '../../../../../components/common/Paper';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import type { AttackChainNodeResultOverviewOutput, AttackChainNodeTarget } from '../../../../../utils/api-types';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, INHERITED_CONTEXT, SUBJECTS } from '../../../../../utils/permissions/types';
import { computeAttackChainNodeExpectationLabel } from '../../../../../utils/statusUtils';
import { emptyFilled } from '../../../../../utils/String';
import { isAssets } from '../../../../../utils/target/TargetUtils';
import { isAgentExpectation, isAssetExpectation, isAssetGroupExpectation, isPlayerExpectation, useIsManuallyUpdatable } from '../../../attack_chain_runs/attack_chain_run/validation/expectations/ExpectationUtils';
import type { AttackChainNodeExpectationsStore } from '../../../common/attack_chain_nodes/expectations/Expectation';
import { isManualExpectation, isTechnicalExpectation } from '../../../common/attack_chain_nodes/expectations/ExpectationUtils';
import { PermissionsContext } from '../../../common/Context';
import AttackChainNodeExpectationContext from '../context/AttackChainNodeExpectationContext';
import ExpirationChip from '../ExpirationChip';
import AttackChainNodeExpectationAggregatedAgentsView from './AttackChainNodeExpectationAggregatedAgentsView';
import AttackChainNodeExpectationResultList from './AttackChainNodeExpectationResultList';

interface Props {
  node: AttackChainNodeResultOverviewOutput;
  injectExpectation: AttackChainNodeExpectationsStore;
  isAgentless: boolean;
  target: AttackChainNodeTarget;
}

const useStyles = makeStyles()(theme => ({
  score: {
    fontSize: '0.75rem',
    height: '20px',
    padding: '0 4px',
  },
  lineContainer: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
  },
}));

const AttackChainNodeExpectationCard = ({ node, injectExpectation, isAgentless, target }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const ability = useContext(AbilityContext);
  const { permissions, inherited_context } = useContext(PermissionsContext);

  const { onOpenDeleteAttackChainNodeExpectationResult, onOpenEditAttackChainNodeExpectationResultResult } = useContext(AttackChainNodeExpectationContext);

  // Hooks must be called at top level - not in JSX or conditionally
  const isManuallyUpdatable = useIsManuallyUpdatable(injectExpectation);

  const statusResult = computeAttackChainNodeExpectationLabel(injectExpectation.node_expectation_status, injectExpectation.node_expectation_type);

  const getLabelOfValidationType = (): string => {
    if (isTechnicalExpectation(injectExpectation.node_expectation_type)) {
      let entityName;
      if (isAgentExpectation(injectExpectation)) {
        entityName = 'agent';
      } else if (isAssetExpectation(injectExpectation)) {
        entityName = 'agent';
      } else if (isAssetGroupExpectation(injectExpectation)) {
        entityName = 'asset';
      }
      return injectExpectation.node_expectation_group
        ? t(`At least one ${entityName} (per group) must validate the expectation`)
        : t(`All ${entityName}s (per group) must validate the expectation`);
    } else {
      return injectExpectation.node_expectation_group
        ? t('At least one player (per team) must validate the expectation')
        : t('All players (per team) must validate the expectation');
    }
  };

  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT)
    || (inherited_context === INHERITED_CONTEXT.NONE && ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, node.node_id))
    || permissions.canManage;

  const entries = [{
    label: t('Update'),
    action: () => onOpenEditAttackChainNodeExpectationResultResult((injectExpectation?.node_expectation_results || [])[0], injectExpectation),
    disabled: false,
    userRight: canManage,
  },
  {
    label: t('Delete'),
    action: () => onOpenDeleteAttackChainNodeExpectationResult((injectExpectation?.node_expectation_results || [])[0], injectExpectation),
    disabled: false,
    userRight: canManage,
  }];

  return (
    <Paper>
      <div className={classes.lineContainer}>
        <Typography style={{ marginRight: 'auto' }} variant="h5">{injectExpectation.node_expectation_name}</Typography>
        {injectExpectation.node_expectation_score !== null && (
          <>
            <ItemStatus label={t(`${statusResult}`)} status={injectExpectation.node_expectation_status} />
            <Tooltip title={t('Score')}>
              <Chip
                classes={{ root: classes.score }}
                label={injectExpectation.node_expectation_score}
              />
            </Tooltip>
          </>
        )}
        {injectExpectation.node_expectation_score === null && injectExpectation.node_expectation_created_at && (
          <ExpirationChip
            expirationTime={injectExpectation.node_expiration_time}
            startDate={injectExpectation.node_expectation_created_at}
          />
        )}

        {/* Create expectation result */}
        {isManuallyUpdatable && canManage && (
          <Tooltip title={t('Add a result')}>
            <IconButton
              aria-label="Add"
              onClick={() => onOpenEditAttackChainNodeExpectationResultResult(null, injectExpectation)}
            >
              {['DETECTION', 'PREVENTION'].includes(injectExpectation.node_expectation_type)
                ? <AddModeratorOutlined color="primary" fontSize="medium" />
                : <InventoryOutlined color="primary" fontSize="medium" />}
            </IconButton>
          </Tooltip>
        )}

        {/* Update expectation result */}
        {isManualExpectation(injectExpectation.node_expectation_type)
          && (injectExpectation.node_expectation_results?.length ?? 0) > 0 && (
          <ButtonPopover entries={entries} variant="icon" />
        )}
      </div>
      {(!isAgentExpectation(injectExpectation) && !isAssetExpectation(injectExpectation) && !isPlayerExpectation(injectExpectation))
        && (
          <div className={classes.lineContainer}>
            <Typography gutterBottom variant="h4">{t('Validation rule:')}</Typography>
            <Typography gutterBottom>{emptyFilled(getLabelOfValidationType())}</Typography>
          </div>
        )}

      {
        // If endpoint with agents, show the nodes expectations aggregated for each agent of the endpoint
        // Else show the nodes expectations for the selected target (agents, endpoints agentless,...)
        (isAssets(target) && !isAgentless) ? (
          <AttackChainNodeExpectationAggregatedAgentsView
            node={node}
            expectationType={injectExpectation.node_expectation_type}
            target={target}
          />
        ) : (
          (!isAssetGroupExpectation(injectExpectation) && ['DETECTION', 'PREVENTION'].includes(injectExpectation.node_expectation_type) && (injectExpectation.node_expectation_results?.length ?? 0) > 0)
          && (
            <AttackChainNodeExpectationResultList
              injectExpectation={injectExpectation}
              injectExpectationResults={injectExpectation.node_expectation_results ?? []}
              injectExpectationAgent={injectExpectation.node_expectation_agent}
              injectorContractPayload={node.node_injector_contract?.injector_contract_payload}
              injectType={node.node_type}
            />
          )
        )
      }
    </Paper>
  );
};

export default AttackChainNodeExpectationCard;
