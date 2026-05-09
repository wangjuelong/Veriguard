import { type FunctionComponent, useContext } from 'react';

import ContextLink from '../../../components/ContextLink';
import { ATOMIC_BASE_URL, ATTACK_CHAIN_BASE_URL, ATTACK_CHAIN_RUN_BASE_URL } from '../../../constants/BaseUrls';
import { INJECT, SCENARIO, SIMULATION } from '../../../constants/Entities';
import { type RelatedFindingOutput } from '../../../utils/api-types';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

interface Props {
  finding: RelatedFindingOutput;
  type: string;
}

const FindingContextLink: FunctionComponent<Props> = ({ finding, type }) => {
  const ability = useContext(AbilityContext);

  switch (type) {
    case INJECT: {
      const title = finding.finding_node?.node_title;
      const injectId = finding.finding_node?.node_id;
      const simulationId = finding.finding_attack_chain_run?.attack_chain_run_id;

      if (!title || !injectId) return '-';

      const isAtomic = !simulationId;
      const url = isAtomic
        ? `${ATOMIC_BASE_URL}/${injectId}`
        : `${ATTACK_CHAIN_RUN_BASE_URL}/${simulationId}/nodes/${injectId}`;

      const userRight = isAtomic
        ? (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT) || ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, injectId))
        : ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, finding.finding_attack_chain_run?.attack_chain_run_id);

      return userRight ? <ContextLink title={title} url={url} /> : title;
    }

    case SIMULATION: {
      const title = finding.finding_attack_chain_run?.attack_chain_run_name;
      const id = finding.finding_attack_chain_run?.attack_chain_run_id;

      if (!title || !id) return '-';

      return ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, finding.finding_attack_chain_run?.attack_chain_run_id) ? <ContextLink title={title} url={`${ATTACK_CHAIN_RUN_BASE_URL}/${id}`} /> : title;
    }

    case SCENARIO: {
      const title = finding.finding_attack_chain?.attack_chain_name;
      const id = finding.finding_attack_chain?.attack_chain_id;

      if (!title || !id) return '-';

      return ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, finding.finding_attack_chain?.attack_chain_id) ? <ContextLink title={title} url={`${ATTACK_CHAIN_BASE_URL}/${id}`} /> : title;
    }

    default:
      return '-';
  }
};

export default FindingContextLink;
