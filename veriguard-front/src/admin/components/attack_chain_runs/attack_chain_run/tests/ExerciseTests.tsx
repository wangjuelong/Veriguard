import { type FunctionComponent } from 'react';
import { useParams } from 'react-router';

import { bulkTestAttackChainNodes, deleteAttackChainNodeTest, fetchAttackChainNodeTestStatus, searchAttackChainNodeTests, testAttackChainNode } from '../../../../../actions/node_test/attack_chain_run-node-test-actions';
import { type AttackChainRun, type AttackChainNodeTestStatusOutput } from '../../../../../utils/api-types';
import AttackChainNodeTestList from '../../../attack_chain_nodes/AttackChainNodeTestList';
import { AttackChainNodeTestContext, type AttackChainNodeTestContextType } from '../../../common/Context';

const AttackChainRunTests: FunctionComponent = () => {
  const { exerciseId, statusId } = useParams() as {
    exerciseId: AttackChainRun['attack_chain_run_id'];
    statusId: AttackChainNodeTestStatusOutput['status_id'];
  };

  const injectTestContext: AttackChainNodeTestContextType = {
    contextId: exerciseId,
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

export default AttackChainRunTests;
