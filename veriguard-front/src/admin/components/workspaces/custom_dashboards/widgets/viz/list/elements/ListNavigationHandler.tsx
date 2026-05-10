import qs from 'qs';
import { type NavigateFunction } from 'react-router';

import { buildSearchPagination } from '../../../../../../../../components/common/queryable/QueryableUtils';
import {
  ATOMIC_BASE_URL,
  ATTACK_CHAIN_BASE_URL,
  ATTACK_CHAIN_RUN_BASE_URL,
  ENDPOINT_BASE_URL,
} from '../../../../../../../../constants/BaseUrls';
import {
  type EsAttackChainNode, type EsAttackChainNodeExpectation,
  type EsBase,
  type EsFinding,
  type EsVulnerableEndpoint,
} from '../../../../../../../../utils/api-types';
import { getTargetTypeFromAttackChainNodeExpectation } from './ListColumnConfig';

type NavigationHandler = (element: EsBase, navigate: NavigateFunction) => void;

const getAttackChainNodeDetailUrl = (injectElement: EsAttackChainNode): string => {
  let injectUrl = `${ATOMIC_BASE_URL}/${injectElement.base_id}`;
  if (injectElement.base_attack_chain_run_side != null && injectElement.execution_date != null) {
    injectUrl = `${ATTACK_CHAIN_RUN_BASE_URL}/${injectElement.base_attack_chain_run_side}/nodes/${injectElement.base_id}`;
  } else if (injectElement.base_attack_chain_run_side != null) {
    const craftedFilter = btoa(qs.stringify({
      ...buildSearchPagination({ textSearch: injectElement.node_title }),
      key: `${injectElement.base_attack_chain_run_side}-nodes`,
    }));
    injectUrl = `${ATTACK_CHAIN_RUN_BASE_URL}/${injectElement.base_attack_chain_run_side}/nodes?query=${craftedFilter}`;
  } else if (injectElement.base_attack_chain_side != null) {
    const craftedFilter = btoa(qs.stringify({
      ...buildSearchPagination({ textSearch: injectElement.node_title }),
      key: `${injectElement.base_attack_chain_side}-nodes`,
    }));
    injectUrl = `${ATTACK_CHAIN_BASE_URL}/${injectElement.base_attack_chain_side}/nodes?query=${craftedFilter}`;
  }
  return injectUrl;
};

const navigationHandlers: Record<string, NavigationHandler> = {
  'endpoint': (element, navigate) => {
    navigate(`${ENDPOINT_BASE_URL}/${element.base_id}`);
  },

  'vulnerable-endpoint': (element, navigate) => {
    const craftedFilter = btoa(qs.stringify({
      ...buildSearchPagination({
        filterGroup: {
          mode: 'and',
          filters: [
            {
              key: 'finding_type',
              operator: 'eq',
              mode: 'or',
              values: ['CVE'],
            },
          ],
        },
      }),
      key: 'endpoint-findings',
    }, { allowEmptyArrays: true }));
    navigate(`${ENDPOINT_BASE_URL}/${(element as EsVulnerableEndpoint).vulnerable_endpoint_id}?query=${craftedFilter}`);
  },

  'attack_chain': (element, navigate) => {
    navigate(`${ATTACK_CHAIN_BASE_URL}/${element.base_id}`);
  },

  'attack_chain_run': (element, navigate) => {
    navigate(`${ATTACK_CHAIN_RUN_BASE_URL}/${element.base_id}`);
  },

  'node': (element, navigate) => {
    navigate(getAttackChainNodeDetailUrl(element as EsAttackChainNode));
  },

  'expectation-node': (element, navigate) => {
    const expectation = element as EsAttackChainNodeExpectation;
    const injectUrl = expectation.base_attack_chain_run_side != null
      ? `${ATTACK_CHAIN_RUN_BASE_URL}/${expectation.base_attack_chain_run_side}/nodes/${expectation.base_node_side}`
      : `${ATOMIC_BASE_URL}/${expectation.base_node_side}`;
    const target = getTargetTypeFromAttackChainNodeExpectation(expectation);
    navigate(`${injectUrl}?expectation_id=${expectation.base_id}&target=${target.type}`);
  },

  'finding': (element, navigate) => {
    const findingElement = element as EsFinding;
    const craftedFilter = btoa(qs.stringify({
      ...buildSearchPagination({
        filterGroup: {
          mode: 'and',
          filters: [
            {
              key: 'finding_type',
              operator: 'eq',
              mode: 'or',
              values: [findingElement.finding_type ?? ''],
            },
          ],
        },
        textSearch: findingElement.finding_value ?? '',
      }),
      key: 'atm-findings',
    }, { allowEmptyArrays: true }));

    const baseUrl = (findingElement.base_attack_chain_run_side != null && findingElement.base_attack_chain_run_side != '')
      ? `${ATTACK_CHAIN_RUN_BASE_URL}/${findingElement.base_attack_chain_run_side}/nodes/${findingElement.base_node_side}/findings`
      : `${ATOMIC_BASE_URL}/${findingElement.base_node_side}/findings`;

    navigate(`${baseUrl}?query=${craftedFilter}&open=${findingElement.base_id}`);
  },
};

export default navigationHandlers;
