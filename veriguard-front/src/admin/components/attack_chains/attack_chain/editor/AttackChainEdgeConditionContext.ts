import { createContext } from 'react';

/**
 * Context exposing the editor's "open edge condition popover" hook to {@link
 * import('@xyflow/react').ReactFlow}'s edge click handler in ChainedTimeline
 * (Phase 12b-B3.5b).
 *
 * Same context-injection pattern as AttackChainNodeSettingsContext (B3.5a):
 * ChainedTimeline.tsx is shared with the run view, which should never let the
 * user edit edge conditions. The editor host wraps its tree in this Provider,
 * passing a callback that opens the popover; the run view leaves context as
 * {@code null} and ChainedTimeline silently no-ops on edge clicks.
 */
export interface AttackChainEdgeConditionContextType {
  openEdgeCondition: (edgeId: string, anchor: HTMLElement) => void;
}

export const AttackChainEdgeConditionContext = createContext<AttackChainEdgeConditionContextType | null>(null);
