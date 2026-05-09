import { useContext, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchTargetResult } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import Paper from '../../../../../components/common/Paper';
import { useFormatter } from '../../../../../components/i18n';
import type { AttackChainNodeResultOverviewOutput, AttackChainNodeTarget } from '../../../../../utils/api-types';
import { isAgent, isAssetGroups, isPlayer, isTeam } from '../../../../../utils/target/TargetUtils';
import { type AttackChainNodeExpectationsStore, type ExpectationResultType, ExpectationType } from '../../../common/attack_chain_nodes/expectations/Expectation';
import ExecutionStatusDetail from '../../../common/attack_chain_nodes/status/ExecutionStatusDetail';
import TerminalViewTab from '../../../common/attack_chain_nodes/status/traces/TerminalViewTab';
import TabbedView, { type TabConfig } from '../../../settings/groups/grants/ui/TabbedView';
import { AttackChainNodeResultOverviewOutputContext, type AttackChainNodeResultOverviewOutputContextType } from '../../AttackChainNodeResultOverviewOutputContext';
import AttackChainNodeExpectationProvider from '../context/AttackChainNodeExpectationProvider';
import AttackChainNodeExpectationCard from './AttackChainNodeExpectationCard';
import TargetResultsReactFlow from './TargetResultsReactFlow';

interface Props {
  node: AttackChainNodeResultOverviewOutput;
  target: AttackChainNodeTarget;
  isAgentless: boolean;
}

const useStyles = makeStyles()(theme => ({
  paddingTop: { paddingTop: theme.spacing(2) },
  gap: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(1),
  },
}));

const TargetResultsDetail = ({ node, target, isAgentless }: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  const [sortedGroupedTargetResults, setSortedGroupedTargetResults] = useState<Record<string, AttackChainNodeExpectationsStore[]>>({});

  const [searchParams, setSearchParams] = useSearchParams();
  const openIdParams = searchParams.get('expectation_id');

  const [activeTab, setActiveTab] = useState<string | null>(null);

  const { injectResultOverviewOutput } = useContext<AttackChainNodeResultOverviewOutputContextType>(AttackChainNodeResultOverviewOutputContext);

  const transformToSortedGroupedResults = (results: AttackChainNodeExpectationsStore[]) => {
    const groupedByType: Record<string, AttackChainNodeExpectationsStore[]> = {};
    results.forEach((result) => {
      const type = result.node_expectation_type;
      if (!groupedByType[type]) {
        groupedByType[type] = [];
      }
      groupedByType[type].push(result);
    });

    const sortedGroupedResults: Record<string, AttackChainNodeExpectationsStore[]> = {};
    Object.keys(groupedByType)
      .toSorted((a, b) => Object.keys(ExpectationType).indexOf(a as ExpectationResultType) - Object.keys(ExpectationType).indexOf(b as ExpectationResultType))
      .forEach((key) => {
        sortedGroupedResults[key] = groupedByType[key].toSorted((a, b) => {
          if (a.node_expectation_name && b.node_expectation_name) {
            return a.node_expectation_name.localeCompare(b.node_expectation_name);
          }
          if (a.node_expectation_name && !b.node_expectation_name) {
            return -1; // a comes before b
          }
          if (!a.node_expectation_name && b.node_expectation_name) {
            return 1; // b comes before a
          }
          return a.node_expectation_id.localeCompare(b.node_expectation_id);
        });
      });
    return sortedGroupedResults;
  };

  useEffect(() => {
    fetchTargetResult(node.node_id, target.target_id!, target.target_type!)
      .then((result: { data: AttackChainNodeExpectationsStore[] }) => {
        setSortedGroupedTargetResults(transformToSortedGroupedResults(result.data ?? []));
      });
  }, [injectResultOverviewOutput, target]);

  useEffect(() => {
    if (!openIdParams || !sortedGroupedTargetResults) return;

    const activeTabIndex: string = Object.values(sortedGroupedTargetResults)
      .map(results => results.find(r => r.node_expectation_id === openIdParams))
      .filter(res => !!res)[0]?.node_expectation_type.toString();

    if (!activeTabIndex) return;

    setActiveTab(activeTabIndex);
    searchParams.delete('open');
    setSearchParams(searchParams, { replace: true });
  }, [openIdParams, sortedGroupedTargetResults]);

  const tabs: TabConfig[] = [];
  if (!isAssetGroups(target)) {
    tabs.push({
      key: 'execution',
      label: 'Execution',
      component: (
        <ExecutionStatusDetail
          target={{
            id: target.target_id,
            name: target.target_name,
            targetType: target.target_type,
            platformType: target.target_subtype,
          }}
          injectId={node.node_id}
        />
      ),
    });
    if (!isTeam(target) && !isPlayer(target)) {
      tabs.push({
        key: 'terminal-view',
        label: t('Terminal view'),
        component: (
          <TerminalViewTab injectId={node.node_id} target={target} forceExpanded={isAgent(target)} />
        ),
      });
    }
  }

  Object.entries(sortedGroupedTargetResults).forEach(([type, expectationResults]) => (
    tabs.push({
      key: type,
      label: t(`TYPE_${type}`),
      component: (
        expectationResults.map(expectationResult => (
          <AttackChainNodeExpectationProvider key={expectationResult.node_expectation_id} node={node}>
            <AttackChainNodeExpectationCard
              injectExpectation={expectationResult}
              node={node}
              isAgentless={isAgentless}
              target={target}
            />
          </AttackChainNodeExpectationProvider>
        ))
      ),
    })
  ));

  return (
    <Paper>
      <TargetResultsReactFlow
        className={`${classes.paddingTop} ${classes.gap}`}
        injectStatusName={injectResultOverviewOutput?.node_status?.status_name}
        targetResultsByType={sortedGroupedTargetResults}
        lastExecutionStartDate={injectResultOverviewOutput?.node_status?.tracking_sent_date || ''}
        lastExecutionEndDate={injectResultOverviewOutput?.node_status?.tracking_end_date || ''}
      />

      <TabbedView tabs={tabs} externalCurrentTab={activeTab} notifyTabChange={setActiveTab} />
    </Paper>
  );
};

export default TargetResultsDetail;
