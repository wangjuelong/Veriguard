import { type AttackChainSettingsInputDto } from '../../../../../actions/attack_chains/attack_chain-actions';
import {
  type AttackChain,
  type ValidationParameterSetOutput,
} from '../../../../../utils/api-types';
import {
  type AttackChainSettingsValue,
  type ParameterSetOption,
} from './attackChainEditorTypes';

/**
 * Adapters between AttackChain wire format (api-types) and the Phase 8
 * AttackChainSettingsDrawer's local AttackChainSettingsValue / ParameterSetOption shapes.
 *
 * Kept as a separate module so the mapping logic stays unit-testable — the
 * Drawer host component (AttackChainAttackChainNodes.tsx) drags in Redux +
 * router providers and is harder to render in isolation.
 */

export const toParameterSetOption = (api: ValidationParameterSetOutput): ParameterSetOption => ({
  id: api.parameter_set_id ?? '',
  name: api.parameter_set_name ?? '',
  is_template: api.parameter_set_is_template ?? false,
});

export const toSettingsValue = (chain: AttackChain): AttackChainSettingsValue => ({
  execution_mode: chain.attack_chain_execution_mode,
  validation_parameter_set_id: chain.attack_chain_validation_parameter_set_id ?? null,
  soc_correlation_rules: (chain.attack_chain_soc_correlation_rules ?? []).map(rule => ({
    connector_id: rule.connector_id ?? '',
    rule_id: rule.rule_id ?? '',
    display_name: rule.display_name ?? '',
    match_window_seconds: rule.match_window_seconds ?? 0,
  })),
});

export const toApiInput = (value: AttackChainSettingsValue): AttackChainSettingsInputDto => ({
  attack_chain_execution_mode: value.execution_mode,
  attack_chain_validation_parameter_set_id: value.validation_parameter_set_id,
  attack_chain_soc_correlation_rules: value.soc_correlation_rules.map(rule => ({
    connector_id: rule.connector_id,
    rule_id: rule.rule_id,
    display_name: rule.display_name,
    match_window_seconds: rule.match_window_seconds,
  })),
});
