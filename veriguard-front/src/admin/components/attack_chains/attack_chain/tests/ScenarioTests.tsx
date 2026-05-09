import { type FunctionComponent } from 'react';
import { useParams } from 'react-router';

import { bulkTestAttackChainNodes, deleteAttackChainNodeTest, fetchAttackChainNodeTestStatus, searchAttackChainNodeTests, testAttackChainNode } from '../../../../../actions/node_test/attack_chain-node-test-actions';
import { type AttackChainNodeTestStatusOutput, type AttackChain } from '../../../../../utils/api-types';
import AttackChainNodeTestList from '../../../attack_chain_nodes/AttackChainNodeTestList';
import { AttackChainNodeTestContext, type AttackChainNodeTestContextType } from '../../../common/Context';

const AttackChainTests: FunctionComponent = () => {
  const { scenarioId, statusId } = useParams() as {
    scenarioId: AttackChain['attack_chain_id'];
    statusId: AttackChainNodeTestStatusOutput['status_id'];
  };
  const injectTestContext: AttackChainNodeTestContextType = {
    contextId: scenarioId,
    bulkTestAttackChainNodes: bulkTestAttackChainNodes,
    deleteAttackChainNodeTest: deleteAttackChainNodeTest,
    searchAttackChainNodeTests: searchAttackChainNodeTests,
    fetchAttackChainNodeTestStatus: fetchAttackChainNodeTestStatus,
    testAttackChainNode: testAttackChainNode,
  };

  return (
    <AttackChainNodeTestContext.Provider value={injectTestContext}>
      <AttackChainNodeTestList statusId={statusId} />
    </AttackChainNodeTestContext.Provider>
  );
};

export default AttackChainTests;
