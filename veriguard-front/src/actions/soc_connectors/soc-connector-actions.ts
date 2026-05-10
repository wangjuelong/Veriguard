import { simpleCall, simplePostCall } from '../../utils/Action';

const SOC_CONNECTOR_URI = '/api/soc_connectors';

/**
 * Wire-format snapshot returned by GET /api/soc_connectors and POST refresh.
 *
 * Mirrors backend io.veriguard.rest.soc_connectors.SocConnectorOutput; not yet
 * regenerated into api-types.d.ts. Kept here as a thin transport DTO so the
 * frontend can map it to Phase 11's local SocConnectorStatusItem shape.
 */
export interface SocConnectorOutputDto {
  connector_id: string;
  display_name: string;
  status: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'DISABLED';
  message?: string | null;
  available_rule_count?: number | null;
  last_checked_at?: string | null;
}

export interface SocConnectorRuleOutputDto {
  rule_id: string;
  display_name: string;
  description?: string | null;
  category?: string | null;
}

export const fetchSocConnectors = () => simpleCall(SOC_CONNECTOR_URI);

export const refreshSocConnector = (connectorId: string) => {
  return simplePostCall(`${SOC_CONNECTOR_URI}/${connectorId}/refresh`, null);
};

export const fetchSocConnectorRules = (connectorId: string) => {
  return simpleCall(`${SOC_CONNECTOR_URI}/${connectorId}/rules`);
};
