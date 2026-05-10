import { searchDistinctFindings, searchFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import { INJECT, SCENARIO, SIMULATION } from '../../../constants/Entities';
import type { RelatedFindingOutput } from '../../../utils/api-types';
import FindingContextLink from './FindingContextLink';
import FindingList from './FindingList';

const Findings = () => {
  const { t } = useFormatter();

  const additionalFilterNames = [
    'finding_node_id',
    'finding_attack_chain',
    'finding_attack_chain_run',
  ];

  const additionalHeaders = [
    {
      field: 'finding_attack_chain',
      label: 'AttackChain',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={SCENARIO} />,
    },
    {
      field: 'finding_attack_chain_run',
      label: 'Simulation',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={SIMULATION} />,
    },
    {
      field: 'finding_node',
      label: 'AttackChainNode',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={INJECT} />,
    },
  ];
  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Findings'),
          current: true,
        }]}
      />
      <FindingList
        searchDistinctFindings={searchDistinctFindings}
        searchFindings={searchFindings}
        additionalHeaders={additionalHeaders}
        additionalFilterNames={additionalFilterNames}
        filterLocalStorageKey="findings"
      />
    </>
  );
};

export default Findings;
