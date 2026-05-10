import { describe, expect, test } from 'vitest';

import { type AttackChainNodeOutputType } from '../../../../../../actions/attack_chain_nodes/AttackChainNode';
import { type AttackChainNodeEditValue } from '../attackChainEditorTypes';
import {
  computeNodeBadgeState,
  toNodeEditValue,
  toNodeSettingsInput,
} from '../attackChainNodeEditAdapters';

const baseNode = {
  node_id: 'node-1',
  node_title: '侦察',
  node_depends_duration: 0,
} as unknown as AttackChainNodeOutputType;

describe('attackChainNodeEditAdapters', () => {
  describe('toNodeEditValue', () => {
    test('reads V3 fields and description via the unsafe extras cast', () => {
      const node = {
        ...baseNode,
        node_description: '内网扫描',
        node_validation_parameter_set_id: 'ps-strict',
        node_repeat_count: 3,
        node_repeat_interval_seconds: 600,
      } as unknown as AttackChainNodeOutputType;

      expect(toNodeEditValue(node)).toEqual({
        node_title: '侦察',
        node_description: '内网扫描',
        validation_parameter_set_id: 'ps-strict',
        repeat_count: 3,
        repeat_interval_seconds: 600,
      });
    });

    test('falls back to safe defaults when V3 fields are missing', () => {
      expect(toNodeEditValue(baseNode)).toEqual({
        node_title: '侦察',
        node_description: null,
        validation_parameter_set_id: null,
        repeat_count: 1,
        repeat_interval_seconds: 0,
      });
    });
  });

  describe('toNodeSettingsInput', () => {
    test('maps drawer value to wire-format input DTO', () => {
      const value: AttackChainNodeEditValue = {
        node_title: '横向移动',
        node_description: 'pass-the-hash',
        validation_parameter_set_id: 'ps-1',
        repeat_count: 2,
        repeat_interval_seconds: 300,
      };
      expect(toNodeSettingsInput(value)).toEqual({
        node_title: '横向移动',
        node_description: 'pass-the-hash',
        node_validation_parameter_set_id: 'ps-1',
        node_repeat_count: 2,
        node_repeat_interval_seconds: 300,
      });
    });

    test('preserves null parameter_set_id and null description', () => {
      const value: AttackChainNodeEditValue = {
        node_title: '侦察',
        node_description: null,
        validation_parameter_set_id: null,
        repeat_count: 1,
        repeat_interval_seconds: 0,
      };
      const input = toNodeSettingsInput(value);
      expect(input.node_validation_parameter_set_id).toBeNull();
      expect(input.node_description).toBeNull();
    });
  });

  describe('computeNodeBadgeState', () => {
    test('OK when neither parameter set override nor repeat is set', () => {
      expect(computeNodeBadgeState(baseNode)).toEqual({ kind: 'OK' });
    });

    test('OVERRIDE when node has its own validation_parameter_set_id', () => {
      const node = {
        ...baseNode,
        node_validation_parameter_set_id: 'ps-1',
      } as unknown as AttackChainNodeOutputType;
      expect(computeNodeBadgeState(node)).toEqual({ kind: 'OVERRIDE' });
    });

    test('REPEAT when repeat_count > 1, includes the count', () => {
      const node = {
        ...baseNode,
        node_repeat_count: 4,
      } as unknown as AttackChainNodeOutputType;
      expect(computeNodeBadgeState(node)).toEqual({
        kind: 'REPEAT',
        count: 4,
      });
    });

    test('REPEAT takes precedence over OVERRIDE when both are present', () => {
      const node = {
        ...baseNode,
        node_validation_parameter_set_id: 'ps-1',
        node_repeat_count: 2,
      } as unknown as AttackChainNodeOutputType;
      expect(computeNodeBadgeState(node)).toEqual({
        kind: 'REPEAT',
        count: 2,
      });
    });

    test('repeat_count = 1 explicitly is treated as no repeat', () => {
      const node = {
        ...baseNode,
        node_repeat_count: 1,
      } as unknown as AttackChainNodeOutputType;
      expect(computeNodeBadgeState(node)).toEqual({ kind: 'OK' });
    });
  });
});
