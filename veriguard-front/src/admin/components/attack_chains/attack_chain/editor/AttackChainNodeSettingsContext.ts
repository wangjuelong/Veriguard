import { createContext } from 'react';

import { type AttackChainNodeOutputType } from '../../../../../actions/attack_chain_nodes/AttackChainNode';

/**
 * Context exposing the editor's "open node settings drawer" hook to descendant
 * canvas nodes (Phase 12b-B3.5a).
 *
 * Why a context rather than threading the callback through ChainedTimeline's
 * node `data` prop: ChainedTimeline.tsx is shared between the editor and the
 * run view. The run view should never render the settings badge, so we keep
 * ChainedTimeline ignorant of editor-only concerns and let the editor host
 * inject the callback only when it mounts the canvas inside its own page.
 *
 * `null` (default) means the surrounding tree is non-editor — the node
 * component will skip rendering NodeBadge entirely.
 */
export interface AttackChainNodeSettingsContextType { openNodeSettings: (node: AttackChainNodeOutputType) => void }

export const AttackChainNodeSettingsContext = createContext<AttackChainNodeSettingsContextType | null>(null);
