import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { fetchTargetResultAssetWithAgents } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import ExpandableSection from '../../../../../components/common/ExpandableSection';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import type {
  AttackChainNodeExpectationAgentOutput,
  AttackChainNodeResultOverviewOutput,
  AttackChainNodeTarget,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { computeAttackChainNodeExpectationLabel } from '../../../../../utils/statusUtils';
import type { AttackChainNodeExpectationsStore } from '../../../common/attack_chain_nodes/expectations/Expectation';
import AttackChainNodeExpectationResultList from './AttackChainNodeExpectationResultList';

interface Props {
  node: AttackChainNodeResultOverviewOutput;
  expectationType: string;
  target: AttackChainNodeTarget;
}

const AttackChainNodeExpectationAggregatedAgentsView = ({ node, expectationType, target }: Props) => {
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const theme = useTheme();
  const [loading, setLoading] = useState(false);

  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchTargetResultAssetWithAgents(node.node_id, target.target_id, expectationType)).finally(() => setLoading(false));
  });

  const { injectExpectationsWithAgents } = useHelper((helper: AttackChainNodeHelper) =>
    ({ injectExpectationsWithAgents: helper.getAttackChainNodeExpectationsByAsset(target.target_id, expectationType) }));

  if (loading) {
    return <Loader />;
  }

  return (
    <>
      {!loading && injectExpectationsWithAgents && injectExpectationsWithAgents.length > 0 && (
        <>
          {injectExpectationsWithAgents.map((injectExpectationAgent: AttackChainNodeExpectationAgentOutput) => {
            const statusResult = computeAttackChainNodeExpectationLabel(injectExpectationAgent.node_expectation_status, injectExpectationAgent.node_expectation_type);
            const header = (
              <>
                <Typography gutterBottom sx={{ mr: theme.spacing(1.5) }}>
                  {injectExpectationAgent.node_expectation_agent_name}
                </Typography>
                <ItemStatus label={t(`${statusResult}`)} status={injectExpectationAgent.node_expectation_status} />
              </>
            );
            return injectExpectationAgent?.node_expectation_status !== 'PENDING' && injectExpectationAgent?.node_expectation_agent
              && (
                <Paper
                  variant="outlined"
                  style={{
                    padding: theme.spacing(2, 0),
                    margin: theme.spacing(2, 0),
                  }}
                >
                  <ExpandableSection
                    forceExpanded={false}
                    header={header}
                    key={injectExpectationAgent.node_expectation_id}
                  >
                    <div style={{ margin: theme.spacing(0, 2) }}>
                      <AttackChainNodeExpectationResultList
                        injectExpectation={injectExpectationAgent as AttackChainNodeExpectationsStore}
                        injectExpectationResults={injectExpectationAgent.node_expectation_results ?? []}
                        injectExpectationAgent={injectExpectationAgent.node_expectation_agent}
                        injectorContractPayload={node.node_injector_contract?.injector_contract_payload}
                        injectType={node.node_type}
                      />
                    </div>
                  </ExpandableSection>
                </Paper>
              );
          })}
        </>
      )}
    </>
  );
};

export default AttackChainNodeExpectationAggregatedAgentsView;
