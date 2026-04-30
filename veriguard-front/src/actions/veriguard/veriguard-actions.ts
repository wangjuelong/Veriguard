import {
  simpleCall,
  simpleDelCall,
  simplePostCall,
  simplePutCall,
} from '../../utils/Action';

const SANDBOXES_URI = '/api/sandboxes';

export type SandboxNetworkPolicy = 'DENY_ALL' | 'ALLOWLIST' | 'ISOLATED_LAB' | 'CUSTOM';
export type SandboxSampleType
  = | 'RANSOMWARE'
    | 'MINER'
    | 'WORM'
    | 'MALICIOUS_DRIVER'
    | 'PRIVILEGE_ESCALATION'
    | 'ACCOUNT_THEFT'
    | 'PROXY_EXECUTION'
    | 'SECURITY_COMPONENT_BYPASS';
export type SandboxStatus = 'ACTIVE' | 'INACTIVE';
export type SandboxRuleDirection = 'INGRESS' | 'EGRESS';
export type SandboxRuleAction = 'ALLOW' | 'DENY';

export type SandboxNetworkRule = {
  rule_direction: SandboxRuleDirection;
  rule_action: SandboxRuleAction;
  rule_protocol: string;
  rule_cidr: string;
  rule_ports: string;
};

export type SandboxInput = {
  sandbox_name: string;
  sandbox_description?: string;
  sandbox_network_policy: SandboxNetworkPolicy;
  sandbox_network_rules: SandboxNetworkRule[];
  sandbox_auto_restore_enabled: boolean;
  sandbox_supported_sample_types: SandboxSampleType[];
  sandbox_status: SandboxStatus;
};

export type SandboxOutput = SandboxInput & {
  sandbox_id: string;
  sandbox_created_at: string;
  sandbox_updated_at: string;
};

export const fetchVeriguardSandboxes = async () => {
  const response = await simpleCall(SANDBOXES_URI);
  return response.data as SandboxOutput[];
};

export const createVeriguardSandbox = async (input: SandboxInput) => {
  const response = await simplePostCall(SANDBOXES_URI, input);
  return response.data as SandboxOutput;
};

export const updateVeriguardSandbox = async (sandboxId: string, input: SandboxInput) => {
  const response = await simplePutCall(`${SANDBOXES_URI}/${sandboxId}`, input);
  return response.data as SandboxOutput;
};

export const deleteVeriguardSandbox = async (sandboxId: string) => {
  await simpleDelCall(`${SANDBOXES_URI}/${sandboxId}`);
};

export const exportSandboxIptables = async (sandboxId: string): Promise<{
  filename: string;
  content: string;
}> => {
  const response = await simpleCall(
    `${SANDBOXES_URI}/${sandboxId}/network-rules/exports/iptables`,
    {
      responseType: 'text' as const,
      transformResponse: [(data: string) => data],
    },
  );
  const cd: string = response.headers['content-disposition'] ?? '';
  const match = /filename="([^"]+)"/.exec(cd);
  return {
    filename: match?.[1] ?? `sandbox-${sandboxId}.iptables.sh`,
    content: response.data as string,
  };
};

export const exportSandboxRoutingConf = async (sandboxId: string): Promise<{
  filename: string;
  content: string;
}> => {
  const response = await simpleCall(
    `${SANDBOXES_URI}/${sandboxId}/network-rules/exports/routing-conf`,
    {
      responseType: 'text' as const,
      transformResponse: [(data: string) => data],
    },
  );
  const cd: string = response.headers['content-disposition'] ?? '';
  const match = /filename="([^"]+)"/.exec(cd);
  return {
    filename: match?.[1] ?? `sandbox-${sandboxId}.routing.conf`,
    content: response.data as string,
  };
};
