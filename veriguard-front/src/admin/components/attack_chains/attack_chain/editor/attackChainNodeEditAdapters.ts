import { type AttackChainNodeOutputType } from '../../../../../actions/attack_chain_nodes/AttackChainNode';
import { type AttackChainNodeSettingsInputDto } from '../../../../../actions/attack_chain_nodes/node-action';
import {
  type AttackChainNodeEditValue,
  type NodeBadgeState,
} from './attackChainEditorTypes';

/**
 * Adapters between AttackChainNodeOutputType (wire format) and Phase 8
 * NodeEditDrawer / NodeBadge local shapes.
 *
 * Lives next to attackChainSettingsAdapters (link-level) and uses the same
 * "extract pure mapping logic so canvas wiring stays unit-testable" pattern.
 */

/**
 * Wire-format fields not yet exposed on AttackChainNodeOutput in api-types.d.ts.
 *
 * Description is on the AttackChainNode entity but lives outside the canvas
 * output type; V3 settings (parameter set / repeat) are part of the V3 schema
 * but the frontend api types haven't been regenerated since the migration.
 * We read both via a focused unsafe cast rather than re-typing every consumer.
 */
interface AttackChainNodeExtraFields {
  node_description?: string | null;
  node_validation_parameter_set_id?: string | null;
  node_repeat_count?: number;
  node_repeat_interval_seconds?: number;
}

const extras = (node: AttackChainNodeOutputType): AttackChainNodeExtraFields =>
  node as unknown as AttackChainNodeExtraFields;

export const toNodeEditValue = (node: AttackChainNodeOutputType): AttackChainNodeEditValue => {
  const fields = extras(node);
  return {
    node_title: node.node_title ?? '',
    node_description: fields.node_description ?? null,
    validation_parameter_set_id: fields.node_validation_parameter_set_id ?? null,
    repeat_count: fields.node_repeat_count ?? 1,
    repeat_interval_seconds: fields.node_repeat_interval_seconds ?? 0,
  };
};

export const toNodeSettingsInput = (
  value: AttackChainNodeEditValue,
): AttackChainNodeSettingsInputDto => ({
  node_title: value.node_title,
  node_description: value.node_description,
  node_validation_parameter_set_id: value.validation_parameter_set_id,
  node_repeat_count: value.repeat_count,
  node_repeat_interval_seconds: value.repeat_interval_seconds,
});

/**
 * Compute the badge to show on a canvas node based on its current V3 state.
 *
 * Precedence: REPEAT (if repeat_count > 1) > OVERRIDE (if node has its own
 * parameter_set_id) > OK. INCOMPLETE is reserved for future detection of
 * required-but-missing config; we don't surface it yet (none of the V3 fields
 * are mandatory at the node level).
 */
export const computeNodeBadgeState = (node: AttackChainNodeOutputType): NodeBadgeState => {
  const fields = extras(node);
  const repeatCount = fields.node_repeat_count ?? 1;
  if (repeatCount > 1) {
    return {
      kind: 'REPEAT',
      count: repeatCount,
    };
  }
  if (fields.node_validation_parameter_set_id) {
    return { kind: 'OVERRIDE' };
  }
  return { kind: 'OK' };
};
