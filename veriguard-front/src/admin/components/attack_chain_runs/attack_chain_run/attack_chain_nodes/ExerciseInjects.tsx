import { BarChartOutlined, ReorderOutlined, ViewTimelineOutlined } from '@mui/icons-material';
import { GridLegacy, Paper, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { fetchAttackChainRunTeams } from '../../../../../actions/AttackChainRun';
import { fetchAttackChainRunChallenges } from '../../../../../actions/challenge-action';
import { type ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchAttackChainRunDocuments } from '../../../../../actions/documents/documents-actions';
import { type ChallengeHelper } from '../../../../../actions/helper';
import { testAttackChainNode } from '../../../../../actions/node_test/attack_chain_run-node-test-actions';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import { fetchVariablesForAttackChainRun } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackChainRun } from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';
import endpointContextForAttackChainRun from '../../../../../utils/context/endpoint/EndpointContextForAttackChainRun';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import AttackChainNodeDistributionByTeam from '../../../common/attack_chain_nodes/AttackChainNodeDistributionByTeam';
import AttackChainNodeDistributionByType from '../../../common/attack_chain_nodes/AttackChainNodeDistributionByType';
import AttackChainNodes from '../../../common/attack_chain_nodes/AttackChainNodes';
import {
  ArticleContext,
  ChallengeContext,
  AttackChainNodeTestContext,
  type AttackChainNodeTestContextType,
  TeamContext,
  ViewModeContext,
} from '../../../common/Context';
import articleContextForAttackChainRun from '../articles/articleContextForAttackChainRun';
import AttackChainRunDistributionScoreByTeamInPercentage from '../overview/AttackChainRunDistributionScoreByTeamInPercentage';
import AttackChainRunDistributionScoreOverTimeByInjectorContract from '../overview/AttackChainRunDistributionScoreOverTimeByInjectorContract';
import AttackChainRunDistributionScoreOverTimeByTeam from '../overview/AttackChainRunDistributionScoreOverTimeByTeam';
import AttackChainRunDistributionScoreOverTimeByTeamInPercentage from '../overview/AttackChainRunDistributionScoreOverTimeByTeamInPercentage';
import teamContextForAttackChainRun from '../teams/teamContextForAttackChainRun';

const useStyles = makeStyles()(() => ({
  paperChart: {
    position: 'relative',
    padding: '0 20px 0 0',
    overflow: 'hidden',
    height: '100%',
  },
}));

const AttackChainRunAttackChainNodes: FunctionComponent = () => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const availableButtons = ['chain', 'list', 'distribution'];
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };

  const [viewMode, setViewMode] = useState(() => {
    const storedValue = localStorage.getItem('attack_chain_or_attack_chain_run_view_mode');
    return storedValue === null || !availableButtons.includes(storedValue) ? 'list' : storedValue;
  });

  const handleViewMode = (mode: string) => {
    localStorage.setItem('attack_chain_or_attack_chain_run_view_mode', mode);
    setViewMode(mode);
  };

  const { attack_chain_run, teams, articles, variables } = useHelper(
    (helper: AttackChainRunsHelper & ArticlesHelper & ChallengeHelper & VariablesHelper & TeamsHelper) => {
      return {
        attack_chain_run: helper.getAttackChainRun(exerciseId),
        teams: helper.getAttackChainRunTeams(exerciseId),
        articles: helper.getAttackChainRunArticles(exerciseId),
        variables: helper.getAttackChainRunVariables(exerciseId),
      };
    },
  );
  useDataLoader(() => {
    dispatch(fetchAttackChainRunTeams(exerciseId));
    dispatch(fetchVariablesForAttackChainRun(exerciseId));
    dispatch(fetchAttackChainRunDocuments(exerciseId));
  });

  const articleContext = articleContextForAttackChainRun(exerciseId);
  const teamContext = teamContextForAttackChainRun(exerciseId, attack_chain_run.attack_chain_run_teams_users, attack_chain_run.attack_chain_run_all_users_number, attack_chain_run.attack_chain_run_users_number);
  const endpointContext = endpointContextForAttackChainRun(exerciseId);
  const challengeContext = { fetchChallenges: () => dispatch(fetchAttackChainRunChallenges(exerciseId)) };

  const injectTestContext: AttackChainNodeTestContextType = {
    contextId: exerciseId,
    url: `/admin/attack_chain_runs/${exerciseId}/tests/`,
    testAttackChainNode: testAttackChainNode,
  };

  return (
    <ViewModeContext.Provider value={viewMode}>
      {(viewMode === 'list' || viewMode === 'chain') && (
        <ArticleContext.Provider value={articleContext}>
          <TeamContext.Provider value={teamContext}>
            <EndpointContext.Provider value={endpointContext}>
              <ChallengeContext.Provider value={challengeContext}>
                <AttackChainNodeTestContext.Provider value={injectTestContext}>
                  <AttackChainNodes
                    setViewMode={handleViewMode}
                    availableButtons={availableButtons}
                    teams={teams}
                    articles={articles}
                    variables={variables}
                    uriVariable={`/admin/attack_chain_runs/${exerciseId}/definition`}
                  />
                </AttackChainNodeTestContext.Provider>
              </ChallengeContext.Provider>
            </EndpointContext.Provider>
          </TeamContext.Provider>
        </ArticleContext.Provider>
      )}
      {viewMode === 'distribution' && (
        <div style={{ marginTop: -12 }}>
          <ToggleButtonGroup
            size="small"
            exclusive={true}
            style={{ float: 'right' }}
            aria-label="Change view mode"
          >
            <Tooltip title={t('List view')}>
              <ToggleButton
                value="list"
                onClick={() => handleViewMode('list')}
                selected={false}
                aria-label="List view mode"
              >
                <ReorderOutlined fontSize="small" color="primary" />
              </ToggleButton>
            </Tooltip>
            <Tooltip title={t('Interactive view')}>
              <ToggleButton
                value="chain"
                onClick={() => handleViewMode('chain')}
                selected={false}
                aria-label="Interactive view mode"
              >
                <ViewTimelineOutlined fontSize="small" color="primary" />
              </ToggleButton>
            </Tooltip>
            <Tooltip title={t('Distribution view')}>
              <ToggleButton
                value="distribution"
                onClick={() => handleViewMode('distribution')}
                selected={true}
                aria-label="Distribution view mode"
              >
                <BarChartOutlined fontSize="small" color="inherit" />
              </ToggleButton>
            </Tooltip>
          </ToggleButtonGroup>
          <GridLegacy container spacing={3}>
            <GridLegacy container item spacing={3}>
              <GridLegacy
                item
                xs={6}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of nodes by type')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainNodeDistributionByType exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={6}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of nodes by team')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainNodeDistributionByTeam exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expectations by node type')}
                  {' '}
                  (%)
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainRunDistributionScoreByTeamInPercentage exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expected total score by node type')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainRunDistributionScoreOverTimeByInjectorContract exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expectations by team')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainRunDistributionScoreOverTimeByTeam exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expected total score by team')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <AttackChainRunDistributionScoreOverTimeByTeamInPercentage exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
            </GridLegacy>
          </GridLegacy>
        </div>
      )}
    </ViewModeContext.Provider>
  );
};

export default AttackChainRunAttackChainNodes;
