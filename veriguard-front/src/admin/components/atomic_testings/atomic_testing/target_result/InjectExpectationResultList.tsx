import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';

import ButtonPopover from '../../../../../components/common/ButtonPopover';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import {
  type AttackChainNode,
  type AttackChainNodeExpectation,
  type AttackChainNodeExpectationResult,
  type PayloadSimple,
} from '../../../../../utils/api-types';
import { isNotEmptyField } from '../../../../../utils/utils';
import { getSourceLabel } from '../../../attack_chain_runs/attack_chain_run/validation/expectations/ExpectationUtils';
import { type AttackChainNodeExpectationsStore } from '../../../common/attack_chain_nodes/expectations/Expectation';
import AttackChainNodeIcon from '../../../common/attack_chain_nodes/AttackChainNodeIcon';
import AttackChainNodeExpectationContext from '../context/AttackChainNodeExpectationContext';
import TargetResultAlertNumber from './TargetResultAlertNumber';

interface Props {
  injectExpectation: AttackChainNodeExpectationsStore;
  injectExpectationResults: AttackChainNodeExpectationResult[];
  injectExpectationAgent: AttackChainNodeExpectation['node_expectation_agent'];
  injectorContractPayload?: PayloadSimple;
  injectType: AttackChainNode['node_type'];
}

const AttackChainNodeExpectationResultList = ({
  injectExpectation,
  injectExpectationResults,
  injectExpectationAgent,
  injectorContractPayload,
  injectType,
}: Props) => {
  const { nsdt, t } = useFormatter();
  const theme = useTheme();

  const { onOpenDeleteAttackChainNodeExpectationResult, onOpenEditAttackChainNodeExpectationResultResult, onOpenSecurityPlatform } = useContext(AttackChainNodeExpectationContext);

  const getAvatar = (expectationResult: AttackChainNodeExpectationResult) => {
    if (expectationResult.sourceType === 'collector' || expectationResult.sourceType === 'security-platform') {
      return (
        <img
          src={expectationResult.sourceType === 'collector'
            ? `/api/images/collectors/id/${expectationResult.sourceId}`
            : `/api/images/security_platforms/id/${expectationResult.sourceId}/${theme.palette.mode}`}
          alt={expectationResult.sourceId}
          style={{
            width: 25,
            height: 25,
            borderRadius: 4,
          }}
        />
      );
    }

    return (
      <AttackChainNodeIcon
        isPayload={isNotEmptyField(injectorContractPayload)}
        type={injectorContractPayload
          ? injectorContractPayload.payload_collector_type
          ?? injectorContractPayload.payload_type
          : injectType}
      />
    );
  };

  return (
    <TableContainer>
      <Table>
        <TableHead>
          <TableRow sx={{ textTransform: 'uppercase' }}>
            <TableCell>{t('Security platforms')}</TableCell>
            <TableCell>{t('Status')}</TableCell>
            <TableCell>{t('Detection time')}</TableCell>
            <TableCell>{t('Alerts')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {injectExpectationResults.map((expectationResult, index) => {
            const isResultSecurityPlatform: boolean = !!(
              injectExpectationAgent
              && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected')
              && expectationResult.sourceType === 'collector'
            );

            return (
              <TableRow
                key={`${expectationResult.sourceName}-${index}`}
                hover={isResultSecurityPlatform}
                sx={{ cursor: `${isResultSecurityPlatform ? 'pointer' : 'default'}` }}
                onClick={() => {
                  if (isResultSecurityPlatform) {
                    onOpenSecurityPlatform(expectationResult, injectExpectation);
                  }
                }}
              >
                <TableCell>
                  <div style={{
                    display: 'flex',
                    gap: theme.spacing(1),
                  }}
                  >
                    {getAvatar(expectationResult)}
                    {getSourceLabel(expectationResult)}
                  </div>
                </TableCell>
                <TableCell>
                  {expectationResult.result && (
                    <ItemStatus
                      label={t(expectationResult.result)}
                      status={expectationResult.result}
                    />
                  )}
                </TableCell>
                <TableCell>
                  {(expectationResult.result === 'Prevented' || expectationResult.result === 'Detected' || expectationResult.result === 'SUCCESS')
                    ? nsdt(expectationResult.date) : '-' }
                </TableCell>
                <TableCell>
                  {
                    expectationResult.sourceId && injectExpectationAgent && expectationResult.sourceType === 'collector' && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected') && (
                      <TargetResultAlertNumber expectationResult={expectationResult} injectExpectationId={injectExpectation.node_expectation_id} />
                    )
                  }
                  {(!injectExpectationAgent
                    || (injectExpectationAgent && (expectationResult.result === 'Not Detected' || expectationResult.result === 'Not Prevented'))
                    || (injectExpectationAgent && expectationResult.sourceType !== 'collector' && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected'))
                  ) && (
                    '-'
                  )}
                </TableCell>
                <TableCell>
                  <ButtonPopover
                    disabled={['collector', 'media-pressure', 'challenge'].includes(expectationResult.sourceType ?? 'unknown')}
                    entries={[{
                      label: t('Update'),
                      action: () => onOpenEditAttackChainNodeExpectationResultResult(expectationResult, injectExpectation),
                      disabled: false,
                      userRight: true,
                    },
                    {
                      label: t('Delete'),
                      action: () => onOpenDeleteAttackChainNodeExpectationResult(expectationResult, injectExpectation),
                      disabled: false,
                      userRight: true,
                    }]}
                    variant="icon"
                  />
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

    </TableContainer>
  );
};
export default AttackChainNodeExpectationResultList;
