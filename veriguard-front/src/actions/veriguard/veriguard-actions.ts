import {
  simpleCall,
  simpleDelCall,
  simplePostCall,
  simplePutCall,
} from '../../utils/Action';

const CAPABILITIES_URI = '/api/capabilities';
const ATTACK_USE_CASES_URI = '/api/attack-use-cases';
const ATTACK_ORCHESTRATION_URI = '/api/attack-orchestration';
const SANDBOXES_URI = '/api/sandboxes';

export type CapabilityModuleOutput = {
  module_key: string;
  module_name: string;
  implementation_state: string;
  acceptance_ready: boolean;
  controls: string[];
  external_integrations_required: string[];
};

export type CapabilityMatrixOutput = {
  modules: CapabilityModuleOutput[];
  summary: {
    prd_module_count: number;
    acceptance_ready_count: number;
    external_integration_count: number;
    total_use_case_templates: number;
  };
};

export type AttackTypeOutput = {
  surface: string;
  attack_type: string;
  template_count: number;
  prd_required: boolean;
};

export type UseCaseTemplateOutput = {
  template_id: string;
  surface: string;
  attack_type: string;
  executor_kind: string;
  supports_multiple_tuples: boolean;
  mapped_custom_case_type: string;
};

export type AttackCatalogOutput = {
  traffic_attack_types: AttackTypeOutput[];
  host_attack_types: AttackTypeOutput[];
  custom_case_types: string[];
  total_use_case_templates: number;
  minimum_attack_type_requirement_met: boolean;
  multiple_tuple_per_case_supported: boolean;
  generated_templates: UseCaseTemplateOutput[];
};

export type OrchestrationSchemaOutput = {
  node_policy_fields: string[];
  execution_modes: string[];
  dependency_logic: string[];
  soc_rule_match_fields: string[];
  chain_result_states: string[];
};

export type SandboxProviderType = 'VMWARE' | 'OPENSTACK' | 'KVM' | 'KUBERNETES' | 'CUSTOM';
export type SandboxNetworkPolicy = 'DENY_ALL' | 'ALLOWLIST' | 'ISOLATED_LAB' | 'CUSTOM';
export type SandboxSampleType =
  | 'RANSOMWARE'
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
  sandbox_provider_type: SandboxProviderType;
  sandbox_endpoint: string;
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

export const fetchVeriguardCapabilityMatrix = async () => {
  const response = await simpleCall(`${CAPABILITIES_URI}/matrix`);
  return response.data as CapabilityMatrixOutput;
};

export const fetchVeriguardAttackCatalog = async () => {
  const response = await simpleCall(`${ATTACK_USE_CASES_URI}/catalog`);
  return response.data as AttackCatalogOutput;
};

export const fetchVeriguardOrchestrationSchema = async () => {
  const response = await simpleCall(`${ATTACK_ORCHESTRATION_URI}/schema`);
  return response.data as OrchestrationSchemaOutput;
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
