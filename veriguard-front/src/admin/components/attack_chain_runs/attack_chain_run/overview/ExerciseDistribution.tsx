import { GridLegacy, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import { type AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { fetchAttackChainRunAttackChainNodes } from '../../../../../actions/AttackChainNode';
import { fetchAttackChainRunAttackChainNodeExpectations, fetchAttackChainRunTeams } from '../../../../../actions/AttackChainRun';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import arrowDark from '../../../../../static/images/misc/arrow_dark.png';
import arrowLight from '../../../../../static/images/misc/arrow_light.png';
import { useHelper } from '../../../../../store';
import { type AttackChainRun } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import AttackChainRunDistributionByInjectorContract from './AttackChainRunDistributionByInjectorContract';
import AttackChainRunDistributionScoreByAttackChainNode from './AttackChainRunDistributionScoreByAttackChainNode';
import AttackChainRunDistributionScoreByOrganization from './AttackChainRunDistributionScoreByOrganization';
import AttackChainRunDistributionScoreByPlayer from './AttackChainRunDistributionScoreByPlayer';
import AttackChainRunDistributionScoreByTeam from './AttackChainRunDistributionScoreByTeam';
import AttackChainRunDistributionScoreByTeamInPercentage from './AttackChainRunDistributionScoreByTeamInPercentage';
import AttackChainRunDistributionScoreOverTimeByInjectorContract from './AttackChainRunDistributionScoreOverTimeByInjectorContract';
import AttackChainRunDistributionScoreOverTimeByTeam from './AttackChainRunDistributionScoreOverTimeByTeam';
import AttackChainRunDistributionScoreOverTimeByTeamInPercentage from './AttackChainRunDistributionScoreOverTimeByTeamInPercentage';

const useStyles = makeStyles()(() => ({
  paperChart: {
    position: 'relative',
    padding: '0 20px 0 0',
    overflow: 'hidden',
    height: '100%',
  },
}));

interface Props {
  exerciseId: AttackChainRun['attack_chain_run_id'];
  isReport?: boolean;
}

const AttackChainRunDistribution: FunctionComponent<Props> = ({
  exerciseId,
  isReport = false,
}) => {
  // Standard hooks
  const theme = useTheme();
  const { t } = useFormatter();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState(true);

  useDataLoader(() => {
    setLoading(true);
    const fetchPromises = [
      dispatch(fetchAttackChainRunAttackChainNodeExpectations(exerciseId)),
      dispatch(fetchAttackChainRunAttackChainNodes(exerciseId)),
      dispatch(fetchAttackChainRunTeams(exerciseId)),
    ];
    Promise.all(fetchPromises)
      .finally(() => {
        setLoading(false);
      });
  });

  const { injectExpectations, attack_chain_run } = useHelper((helper: AttackChainNodeHelper & AttackChainRunsHelper) => ({
    attack_chain_run: helper.getAttackChainRun(exerciseId),
    injectExpectations: helper.getAttackChainRunAttackChainNodeExpectations(exerciseId),
  }));

  if (attack_chain_run.attack_chain_run_status === 'SCHEDULED' && injectExpectations?.length === 0 && !isReport) {
    return (
      <div style={{
        marginTop: 100,
        textAlign: 'center',
      }}
      >
        <div style={{ fontSize: 20 }}>
          {t('This attack_chain_run is not running yet. Start now!')}
        </div>
        <img style={{ marginTop: 20 }} width={100} src={theme.palette.mode === 'dark' ? arrowDark : arrowLight} alt="arrow" />
      </div>
    );
  }

  return (
    <GridLegacy id="attack_chain_run_distribution" container spacing={3} sx={{ marginBottom: 1 }}>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of score by team (in % of expectations)')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <AttackChainRunDistributionScoreByTeamInPercentage exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Teams scores over time (in % of expectations)')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <AttackChainRunDistributionScoreOverTimeByTeamInPercentage exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of total score by team')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <AttackChainRunDistributionScoreByTeam exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">{t('Teams scores over time')}</Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <AttackChainRunDistributionScoreOverTimeByTeam exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of total score by node type')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <AttackChainRunDistributionByInjectorContract exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('AttackChainNode types scores over time')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <AttackChainRunDistributionScoreOverTimeByInjectorContract exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of total score by organization')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <AttackChainRunDistributionScoreByOrganization exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={3} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of total score by player')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <AttackChainRunDistributionScoreByPlayer exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={3} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of total score by node')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <AttackChainRunDistributionScoreByAttackChainNode exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
    </GridLegacy>
  );
};
export default AttackChainRunDistribution;
