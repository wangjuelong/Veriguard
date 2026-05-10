import { describe, expect, test } from 'vitest';

import { type EdgeConditionDto } from '../../../../../../actions/attack_chain_edges/edge-action';
import { type EdgeConditionTree } from '../attackChainEditorTypes';
import { fromEdgeConditionDto, toEdgeConditionDto } from '../attackChainEdgeConditionAdapters';

describe('attackChainEdgeConditionAdapters', () => {
  describe('toEdgeConditionDto', () => {
    test('flat leaf maps to wire-format eq with discriminator', () => {
      const tree: EdgeConditionTree = {
        kind: 'leaf',
        dimension: 'PREVENTION',
        status: 'ALL_SUCCESS',
      };
      expect(toEdgeConditionDto(tree)).toEqual({
        type: 'eq',
        dimension: 'PREVENTION',
        status: 'ALL_SUCCESS',
      });
    });

    test('AND group maps to wire-format and lowercase op', () => {
      const tree: EdgeConditionTree = {
        kind: 'group',
        op: 'AND',
        children: [
          { kind: 'leaf', dimension: 'PREVENTION', status: 'ANY_SUCCESS' },
          { kind: 'leaf', dimension: 'DETECTION', status: 'SETTLED' },
        ],
      };
      expect(toEdgeConditionDto(tree)).toEqual({
        type: 'and',
        children: [
          { type: 'eq', dimension: 'PREVENTION', status: 'ANY_SUCCESS' },
          { type: 'eq', dimension: 'DETECTION', status: 'SETTLED' },
        ],
      });
    });

    test('nested OR-AND tree preserves structure depth-first', () => {
      const tree: EdgeConditionTree = {
        kind: 'group',
        op: 'OR',
        children: [
          { kind: 'leaf', dimension: 'PREVENTION', status: 'ALL_SUCCESS' },
          {
            kind: 'group',
            op: 'AND',
            children: [
              { kind: 'leaf', dimension: 'DETECTION', status: 'ANY_SUCCESS' },
              { kind: 'leaf', dimension: 'MANUAL', status: 'SETTLED' },
            ],
          },
        ],
      };
      expect(toEdgeConditionDto(tree)).toEqual({
        type: 'or',
        children: [
          { type: 'eq', dimension: 'PREVENTION', status: 'ALL_SUCCESS' },
          {
            type: 'and',
            children: [
              { type: 'eq', dimension: 'DETECTION', status: 'ANY_SUCCESS' },
              { type: 'eq', dimension: 'MANUAL', status: 'SETTLED' },
            ],
          },
        ],
      });
    });

    test('empty AND group maps to empty children list (evaluator: vacuous true)', () => {
      const tree: EdgeConditionTree = {
        kind: 'group',
        op: 'AND',
        children: [],
      };
      expect(toEdgeConditionDto(tree)).toEqual({
        type: 'and',
        children: [],
      });
    });
  });

  describe('fromEdgeConditionDto', () => {
    test('round-trips a leaf', () => {
      const dto: EdgeConditionDto = {
        type: 'eq',
        dimension: 'PREVENTION',
        status: 'ALL_SUCCESS',
      };
      expect(fromEdgeConditionDto(dto)).toEqual({
        kind: 'leaf',
        dimension: 'PREVENTION',
        status: 'ALL_SUCCESS',
      });
    });

    test('round-trips a nested group', () => {
      const dto: EdgeConditionDto = {
        type: 'and',
        children: [
          { type: 'eq', dimension: 'PREVENTION', status: 'ALL_SUCCESS' },
          {
            type: 'or',
            children: [
              { type: 'eq', dimension: 'DETECTION', status: 'SETTLED' },
            ],
          },
        ],
      };
      expect(fromEdgeConditionDto(dto)).toEqual({
        kind: 'group',
        op: 'AND',
        children: [
          { kind: 'leaf', dimension: 'PREVENTION', status: 'ALL_SUCCESS' },
          {
            kind: 'group',
            op: 'OR',
            children: [
              { kind: 'leaf', dimension: 'DETECTION', status: 'SETTLED' },
            ],
          },
        ],
      });
    });
  });

  describe('round-trip', () => {
    test('toEdgeConditionDto then fromEdgeConditionDto returns original tree', () => {
      const tree: EdgeConditionTree = {
        kind: 'group',
        op: 'AND',
        children: [
          { kind: 'leaf', dimension: 'PREVENTION', status: 'ALL_SUCCESS' },
          {
            kind: 'group',
            op: 'OR',
            children: [
              { kind: 'leaf', dimension: 'DETECTION', status: 'ANY_FAILED' },
              { kind: 'leaf', dimension: 'MANUAL', status: 'ALL_FAILED' },
            ],
          },
        ],
      };
      expect(fromEdgeConditionDto(toEdgeConditionDto(tree))).toEqual(tree);
    });
  });
});
