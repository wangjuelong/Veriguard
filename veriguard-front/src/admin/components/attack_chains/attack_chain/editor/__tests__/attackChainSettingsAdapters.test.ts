import { describe, expect, test } from 'vitest';

import type {
  AttackChain,
  ValidationParameterSetOutput,
} from '../../../../../../utils/api-types';
import { type AttackChainSettingsValue } from '../attackChainEditorTypes';
import { toApiInput, toParameterSetOption, toSettingsValue } from '../attackChainSettingsAdapters';

const baseChain: AttackChain = {
  attack_chain_created_at: '2026-05-01T00:00:00Z',
  attack_chain_execution_mode: 'STOP_ON_BLOCK',
  attack_chain_id: 'chain-1',
  attack_chain_name: '链路 A',
  attack_chain_updated_at: '2026-05-09T00:00:00Z',
};

describe('attackChainSettingsAdapters', () => {
  describe('toParameterSetOption', () => {
    test('maps wire-format ValidationParameterSetOutput to drawer ParameterSetOption', () => {
      const api: ValidationParameterSetOutput = {
        parameter_set_id: 'ps-1',
        parameter_set_name: '严格',
        parameter_set_is_template: true,
      };
      expect(toParameterSetOption(api)).toEqual({
        id: 'ps-1',
        name: '严格',
        is_template: true,
      });
    });

    test('falls back to safe defaults when wire-format omits optional fields', () => {
      expect(toParameterSetOption({})).toEqual({
        id: '',
        name: '',
        is_template: false,
      });
    });
  });

  describe('toSettingsValue', () => {
    test('reads execution_mode + parameter_set_id + soc_correlation_rules from AttackChain', () => {
      const chain: AttackChain = {
        ...baseChain,
        attack_chain_execution_mode: 'CONTINUE',
        attack_chain_validation_parameter_set_id: 'ps-default',
        attack_chain_soc_correlation_rules: [
          {
            connector_id: 'elastic',
            rule_id: 'rule-1',
            display_name: 'Suspicious Login',
            match_window_seconds: 3600,
          },
        ],
      };
      expect(toSettingsValue(chain)).toEqual({
        execution_mode: 'CONTINUE',
        validation_parameter_set_id: 'ps-default',
        soc_correlation_rules: [
          {
            connector_id: 'elastic',
            rule_id: 'rule-1',
            display_name: 'Suspicious Login',
            match_window_seconds: 3600,
          },
        ],
      });
    });

    test('coerces null parameter_set_id and empty soc_correlation_rules', () => {
      expect(toSettingsValue(baseChain)).toEqual({
        execution_mode: 'STOP_ON_BLOCK',
        validation_parameter_set_id: null,
        soc_correlation_rules: [],
      });
    });

    test('fills missing soc rule fields with empty strings to satisfy strict editor types', () => {
      const chain: AttackChain = {
        ...baseChain,
        attack_chain_soc_correlation_rules: [{}],
      };
      const result = toSettingsValue(chain);
      expect(result.soc_correlation_rules[0]).toEqual({
        connector_id: '',
        rule_id: '',
        display_name: '',
        match_window_seconds: 0,
      });
    });
  });

  describe('toApiInput', () => {
    test('maps drawer AttackChainSettingsValue to wire-format input DTO', () => {
      const value: AttackChainSettingsValue = {
        execution_mode: 'CONTINUE',
        validation_parameter_set_id: 'ps-1',
        soc_correlation_rules: [
          {
            connector_id: 'elastic',
            rule_id: 'rule-2',
            display_name: 'Failed Auth Spike',
            match_window_seconds: 7200,
          },
        ],
      };
      expect(toApiInput(value)).toEqual({
        attack_chain_execution_mode: 'CONTINUE',
        attack_chain_validation_parameter_set_id: 'ps-1',
        attack_chain_soc_correlation_rules: [
          {
            connector_id: 'elastic',
            rule_id: 'rule-2',
            display_name: 'Failed Auth Spike',
            match_window_seconds: 7200,
          },
        ],
      });
    });

    test('preserves null parameter_set_id (drawer "no override" maps to backend null)', () => {
      const value: AttackChainSettingsValue = {
        execution_mode: 'STOP_ON_BLOCK',
        validation_parameter_set_id: null,
        soc_correlation_rules: [],
      };
      expect(toApiInput(value).attack_chain_validation_parameter_set_id).toBeNull();
    });
  });
});
