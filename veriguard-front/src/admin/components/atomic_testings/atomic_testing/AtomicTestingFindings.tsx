import { type FunctionComponent } from 'react';
import { useParams } from 'react-router';

import { searchDistinctFindingsForAttackChainNodes, searchFindingsForAttackChainNodes } from '../../../../actions/findings/finding-actions';
import { type AttackChainNodeResultOverviewOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import FindingList from '../../findings/FindingList';

const AtomicTestingFindings: FunctionComponent = () => {
  const { injectId } = useParams() as { injectId: AttackChainNodeResultOverviewOutput['node_id'] };

  const search = (input: SearchPaginationInput) => {
    return searchFindingsForAttackChainNodes(injectId, input);
  };

  const searchDistinct = (input: SearchPaginationInput) => {
    return searchDistinctFindingsForAttackChainNodes(injectId, input);
  };

  return (
    <FindingList filterLocalStorageKey="atm-findings" searchDistinctFindings={searchDistinct} searchFindings={search} contextId={injectId} />
  );
};

export default AtomicTestingFindings;
