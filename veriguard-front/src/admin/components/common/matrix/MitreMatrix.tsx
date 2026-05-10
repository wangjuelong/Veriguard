import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import { type KillChainPhaseHelper } from '../../../../actions/kill_chain_phases/killchainphase-helper';
import { useHelper } from '../../../../store';
import { type AttackPattern, type KillChainPhase, type NodeExpectationResultsByAttackPattern } from '../../../../utils/api-types';
import { sortKillChainPhase } from '../../../../utils/kill_chain_phases/kill_chain_phases';
import KillChainPhaseColumn from './KillChainPhaseColumn';
import MitreMatrixDummy from './MitreMatrixDummy';

export type MatrixMode = 'compact' | 'full';
export type MatrixColoringScheme = 'coverage' | 'verdict';
export type MatrixVerdictDimension = 'prevention' | 'detection';

const useStyles = makeStyles()(() => ({
  container: {
    width: '100%',
    display: 'flex',
    gap: 20,
    overflowX: 'auto',
    animation: 'detect-scroll linear',
    animationTimeline: 'scroll(self inline)',
  },
}));

interface Props {
  goToLink?: string;
  injectResults: NodeExpectationResultsByAttackPattern[];
  mode?: MatrixMode;
  coloringScheme?: MatrixColoringScheme;
  verdictDimension?: MatrixVerdictDimension;
}

const MitreMatrix: FunctionComponent<Props> = ({
  goToLink,
  injectResults,
  mode = 'compact',
  coloringScheme = 'verdict',
  verdictDimension = 'prevention',
}) => {
  // Standard hooks
  const { classes } = useStyles();
  // Fetching data
  const { attackPatternMap, killChainPhaseMap }: {
    attackPatternMap: Record<string, AttackPattern>;
    killChainPhaseMap: Record<string, KillChainPhase>;
  } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper) => ({
    attackPatternMap: helper.getAttackPatternsMap(),
    killChainPhaseMap: helper.getKillChainPhasesMap(),
  }));

  if (!injectResults) {
    return <MitreMatrixDummy />;
  }

  let phases: KillChainPhase[];
  let patternsByPhase: (phase: KillChainPhase) => AttackPattern[];

  if (mode === 'full') {
    const allPatterns: AttackPattern[] = Object.values(attackPatternMap);
    phases = Object.values(killChainPhaseMap);
    patternsByPhase = (phase: KillChainPhase) =>
      allPatterns.filter((p: AttackPattern) => p.attack_pattern_kill_chain_phases?.includes(phase.phase_id));
  } else {
    // compact: existing logic preserved exactly
    const resultAttackPatternIds = R.uniq(
      injectResults
        .filter(injectResult => !!injectResult.node_attack_pattern)
        .flatMap(injectResult => injectResult.node_attack_pattern) as unknown as string[],
    );
    const resultAttackPatterns: AttackPattern[] = resultAttackPatternIds
      .map((id: string) => attackPatternMap[id])
      .filter((p: AttackPattern | undefined): p is AttackPattern => p !== undefined);
    phases = R.uniq(
      resultAttackPatterns
        .flatMap((p: AttackPattern) => p.attack_pattern_kill_chain_phases ?? [])
        .map((phaseId: string) => killChainPhaseMap[phaseId])
        .filter((phase): phase is KillChainPhase => !!phase),
    );
    patternsByPhase = (phase: KillChainPhase) =>
      resultAttackPatterns.filter((p: AttackPattern) => p.attack_pattern_kill_chain_phases?.includes(phase.phase_id));
  }

  return (
    <div className={classes.container}>
      {[...phases].sort(sortKillChainPhase).map(phase => (
        <KillChainPhaseColumn
          key={phase.phase_id}
          goToLink={goToLink}
          killChainPhase={phase}
          attackPatterns={patternsByPhase(phase)}
          injectResults={injectResults}
          coloringScheme={coloringScheme}
          verdictDimension={verdictDimension}
        />
      ))}
    </div>
  );
};

export default MitreMatrix;
