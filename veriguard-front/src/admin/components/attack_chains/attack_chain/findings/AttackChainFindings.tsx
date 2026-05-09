import { useParams } from 'react-router';

import { searchDistinctFindingsForAttackChains, searchFindingsForAttackChains } from '../../../../../actions/findings/finding-actions';
import { SIMULATION } from '../../../../../constants/Entities';
import type { AttackChain, RelatedFindingOutput, SearchPaginationInput } from '../../../../../utils/api-types';
import FindingContextLink from '../../../findings/FindingContextLink';
import FindingList from '../../../findings/FindingList';

const AttackChainFindings = () => {
  const { scenarioId } = useParams() as { scenarioId: AttackChain['attack_chain_id'] };

  const additionalFilterNames = [
    'finding_node_id',
    'finding_attack_chain_run',
  ];

  const search = (input: SearchPaginationInput) => {
    return searchFindingsForAttackChains(scenarioId, input);
  };
  const searchDistinct = (input: SearchPaginationInput) => {
    return searchDistinctFindingsForAttackChains(scenarioId, input);
  };

  const additionalHeaders = [
    {
      field: 'finding_attack_chain_run',
      label: 'Simulation',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={SIMULATION} />,
    },
  ];

  return (
    <FindingList
      filterLocalStorageKey="attack_chain-findings"
      searchFindings={search}
      searchDistinctFindings={searchDistinct}
      additionalHeaders={additionalHeaders}
      additionalFilterNames={additionalFilterNames}
      contextId={scenarioId}
    />
  );
};
export default AttackChainFindings;
