import { Paper } from '@mui/material';
import { type FunctionComponent, useMemo } from 'react';

import useFetchAttackChainNodeExecutionResult from '../../../../../../actions/node_status/useFetchAttackChainNodeExecutionResult';
import Empty from '../../../../../../components/Empty';
import { useFormatter } from '../../../../../../components/i18n';
import type { AttackChainNodeTarget } from '../../../../../../utils/api-types';
import TerminalView from './TerminalView';

interface Props {
  injectId: string;
  target: AttackChainNodeTarget;
  forceExpanded: boolean;
}

const TerminalViewTab: FunctionComponent<Props> = ({ injectId, target, forceExpanded }) => {
  const { t } = useFormatter();
  const { injectExecutionResult, loading } = useFetchAttackChainNodeExecutionResult(injectId, target);

  const nonEmptyTraces = useMemo(() => {
    if (!injectExecutionResult?.execution_traces) {
      return [];
    }

    return Object.entries(injectExecutionResult.execution_traces)
      .filter(([, traces]) => traces.length > 0);
  }, [injectExecutionResult]);

  if (loading || nonEmptyTraces.length === 0) {
    return <Empty message={t('No traces on this target.')} />;
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      {nonEmptyTraces.map(([key, value]) => (
        <TerminalView
          key={key}
          payloadCommandBlocks={injectExecutionResult?.payload_command_blocks ?? []}
          traces={value}
          forceExpanded={forceExpanded}
        />
      ))}
    </Paper>
  );
};

export default TerminalViewTab;
