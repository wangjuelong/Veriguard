import { useParams } from 'react-router';

import { searchDistinctFindingsForSimulations, searchFindingsForSimulations } from '../../../../../actions/findings/finding-actions';
import { INJECT } from '../../../../../constants/Entities';
import type { AttackChainRun, RelatedFindingOutput, SearchPaginationInput } from '../../../../../utils/api-types';
import FindingContextLink from '../../../findings/FindingContextLink';
import FindingList from '../../../findings/FindingList';

const SimulationFindings = () => {
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };

  const additionalFilterNames = [
    'finding_node_id',
  ];

  const search = (input: SearchPaginationInput) => {
    return searchFindingsForSimulations(exerciseId, input);
  };
  const searchDistinct = (input: SearchPaginationInput) => {
    return searchDistinctFindingsForSimulations(exerciseId, input);
  };
  const additionalHeaders = [
    {
      field: 'finding_node',
      label: 'AttackChainNode',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={INJECT} />,
    },
  ];

  return (
    <FindingList
      filterLocalStorageKey={`attack_chain_run-findings_${exerciseId}`}
      searchFindings={search}
      searchDistinctFindings={searchDistinct}
      additionalHeaders={additionalHeaders}
      additionalFilterNames={additionalFilterNames}
      contextId={exerciseId}
    />
  );
};
export default SimulationFindings;
