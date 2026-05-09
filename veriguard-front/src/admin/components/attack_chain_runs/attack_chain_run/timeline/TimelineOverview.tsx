import { List, ListItem, ListItemButton, ListItemIcon, ListItemText, Paper, Typography, useTheme } from '@mui/material';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AttackChainNodeStore } from '../../../../../actions/attack_chain_nodes/AttackChainNode';
import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import { type AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { fetchAttackChainRunAttackChainNodes, updateAttackChainNodeForAttackChainRun } from '../../../../../actions/AttackChainNode';
import { fetchAttackChainRunTeams } from '../../../../../actions/AttackChainRun';
import { fetchAttackChainRunChallenges } from '../../../../../actions/challenge-action';
import type { ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchAttackChainRunDocuments } from '../../../../../actions/documents/documents-actions';
import { fetchVariablesForAttackChainRun } from '../../../../../actions/variables/variable-actions';
import type { VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { BACK_LABEL, BACK_URI } from '../../../../../components/Breadcrumbs';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import ProgressBarCountdown from '../../../../../components/ProgressBarCountdown';
import SearchFilter from '../../../../../components/SearchFilter';
import Timeline from '../../../../../components/Timeline';
import { useHelper } from '../../../../../store';
import { type AttackChainRun, type AttackChainNode } from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';
import endpointContextForAttackChainRun from '../../../../../utils/context/endpoint/EndpointContextForAttackChainRun';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useSearchAndFilter from '../../../../../utils/SortingFiltering';
import { isNotEmptyField } from '../../../../../utils/utils';
import AttackChainNodeIcon from '../../../common/attack_chain_nodes/AttackChainNodeIcon';
import AttackChainNodePopover from '../../../common/attack_chain_nodes/AttackChainNodePopover';
import UpdateAttackChainNode from '../../../common/attack_chain_nodes/UpdateAttackChainNode';
import { ArticleContext, ChallengeContext, TeamContext } from '../../../common/Context';
import TagsFilter from '../../../common/filters/TagsFilter';
import AnimationMenu from '../AnimationMenu';
import articleContextForAttackChainRun from '../articles/articleContextForAttackChainRun';
import teamContextForAttackChainRun from '../teams/teamContextForAttackChainRun';
import AttackChainNodeOverTimeArea from './AttackChainNodeOverTimeArea';
import AttackChainNodeOverTimeLine from './AttackChainNodeOverTimeLine';

const useStyles = makeStyles()(theme => ({
  item: { height: 50 },
  bodyItems: {
    display: 'grid',
    gap: `0px ${theme.spacing(3)}`,
    gridTemplateColumns: '1fr 1fr 1fr',
    alignItems: 'center',
  },
  bodyItem: {
    fontSize: 14,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
}));

const TimelineOverview = () => {
  const { classes } = useStyles();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };
  const { t, fndt } = useFormatter();
  const [selectedAttackChainNodeId, setSelectedAttackChainNodeId] = useState<string | null>(null);

  const {
    attack_chain_run,
    nodes,
    teams,
    articles,
    variables,
  } = useHelper((helper: AttackChainNodeHelper & AttackChainRunsHelper & ArticlesHelper & VariablesHelper) => {
    return {
      attack_chain_run: helper.getAttackChainRun(exerciseId),
      nodes: helper.getAttackChainRunAttackChainNodes(exerciseId),
      teams: helper.getAttackChainRunTeams(exerciseId),
      articles: helper.getAttackChainRunArticles(exerciseId),
      variables: helper.getAttackChainRunVariables(exerciseId),
    };
  });

  // Fetching Data
  useDataLoader(() => {
    dispatch(fetchAttackChainRunAttackChainNodes(exerciseId));
    dispatch(fetchAttackChainRunTeams(exerciseId));
    dispatch(fetchVariablesForAttackChainRun(exerciseId));
    dispatch(fetchAttackChainRunDocuments(exerciseId));
  });

  // Sort
  const searchColumns = ['title', 'description', 'content'];
  const filtering = useSearchAndFilter(
    'node',
    'depends_duration',
    searchColumns,
  );

  const isEnable = (node: AttackChainNodeStore): boolean => !!node.node_enabled;
  const filteredAttackChainNodes: AttackChainNodeStore[] = filtering.filterAndSort(nodes.filter((node: AttackChainNodeStore) => isEnable(node)));
  const pendingAttackChainNodes: AttackChainNodeStore[] = filtering.filterAndSort(filteredAttackChainNodes.filter((node: AttackChainNodeStore) => node.node_status === null));
  const processedAttackChainNodes: AttackChainNodeStore[] = filtering.filterAndSort(filteredAttackChainNodes.filter((i: AttackChainNodeStore) => i.node_status !== null));

  const onUpdateAttackChainNode = async (node: AttackChainNode) => {
    if (selectedAttackChainNodeId) {
      await dispatch(updateAttackChainNodeForAttackChainRun(exerciseId, selectedAttackChainNodeId, node));
    }
  };

  const teamContext = teamContextForAttackChainRun(exerciseId, []);
  const articleContext = articleContextForAttackChainRun(exerciseId);
  const endpointContext = endpointContextForAttackChainRun(exerciseId);
  const challengeContext = { fetchChallenges: () => dispatch(fetchAttackChainRunChallenges(exerciseId)) };

  return (
    <div>
      <AnimationMenu exerciseId={exerciseId} />
      <div>
        <SearchFilter
          variant="small"
          onChange={filtering.handleSearch}
          keyword={filtering.keyword}
        />
        <TagsFilter
          onAddTag={filtering.handleAddTag}
          onRemoveTag={filtering.handleRemoveTag}
          currentTags={filtering.tags}
        />
      </div>
      <Timeline
        nodes={filteredAttackChainNodes}
        teams={teams}
        onSelectAttackChainNode={(id: string) => setSelectedAttackChainNodeId(id)}
      />
      <div className="clearfix" />
      <div style={{
        display: 'grid',
        marginTop: 50,
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: '1fr 1fr',
      }}
      >
        <Typography variant="h4">{t('Pending nodes')}</Typography>
        <Typography variant="h4">{t('Processed nodes')}</Typography>
        <Paper variant="outlined">
          {pendingAttackChainNodes.length > 0 ? (
            <List style={{ paddingTop: theme.spacing(0) }}>
              {pendingAttackChainNodes.map((node: AttackChainNodeStore) => {
                return (
                  <ListItem
                    key={node.node_id}
                    secondaryAction={(
                      <AttackChainNodePopover
                        node={node}
                        setSelectedAttackChainNodeId={setSelectedAttackChainNodeId}
                        canDone
                        canTriggerNow
                      />
                    )}
                  >
                    <ListItemButton
                      dense
                      classes={{ root: classes.item }}
                      divider
                      onClick={() => setSelectedAttackChainNodeId(node.node_id)}
                    >
                      <ListItemIcon>
                        <AttackChainNodeIcon
                          isPayload={isNotEmptyField(node.node_injector_contract?.injector_contract_payload)}
                          type={
                            node.node_injector_contract?.injector_contract_payload
                              ? node.node_injector_contract.injector_contract_payload?.payload_collector_type
                              || node.node_injector_contract.injector_contract_payload?.payload_type
                              : node.node_type
                          }
                          variant="inline"
                        />
                      </ListItemIcon>
                      <ListItemText
                        primary={(
                          <div className={classes.bodyItems}>
                            <div
                              className={classes.bodyItem}
                            >
                              {node.node_title}
                            </div>
                            <div
                              className={classes.bodyItem}
                            >
                              <ProgressBarCountdown
                                date={node.node_date}
                                paused={
                                  attack_chain_run?.attack_chain_run_status === 'PAUSED'
                                  || attack_chain_run?.attack_chain_run_status === 'CANCELED'
                                }
                              />
                            </div>
                            <div
                              className={classes.bodyItem}
                              style={{
                                fontFamily: 'Consolas, monaco, monospace',
                                fontSize: 12,
                              }}
                            >
                              {fndt(node.node_date)}
                            </div>
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                );
              })}
            </List>
          ) : (
            <Empty message={t('No pending nodes in this attack_chain_run.')} />
          )}
        </Paper>
        <Paper variant="outlined">
          {processedAttackChainNodes.length > 0 ? (
            <List style={{ paddingTop: 0 }}>
              {processedAttackChainNodes.map((node: AttackChainNodeStore) => (
                <ListItem key={node.node_id}>
                  <ListItemButton
                    dense
                    classes={{ root: classes.item }}
                    divider
                    component={Link}
                    to={`/admin/attack_chain_runs/${exerciseId}/nodes/${node.node_id}?${BACK_LABEL}=Animation&${BACK_URI}=/admin/attack_chain_runs/${exerciseId}/animation/timeline`}
                  >
                    <ListItemIcon>
                      <AttackChainNodeIcon
                        isPayload={isNotEmptyField(node.node_injector_contract?.injector_contract_payload)}
                        type={
                          node.node_injector_contract?.injector_contract_payload
                            ? node.node_injector_contract.injector_contract_payload?.payload_collector_type
                            || node.node_injector_contract.injector_contract_payload?.payload_type
                            : node.node_type
                        }
                        variant="inline"
                      />
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div className={classes.bodyItems}>
                          <div
                            className={classes.bodyItem}
                          >
                            {node.node_title}
                          </div>
                          <div
                            className={classes.bodyItem}
                          >
                            <ItemStatus
                              key={node.node_id}
                              variant="inList"
                              label={node.node_status?.status_name ? t(node.node_status.status_name) : 'No Status'}
                              status={node.node_status?.status_name}
                            />
                          </div>
                          <div
                            className={classes.bodyItem}
                            style={{
                              fontFamily: 'Consolas, monaco, monospace',
                              fontSize: 12,
                            }}
                          >
                            {fndt(node.node_status?.tracking_sent_date)}
                            {' '}
                            {
                              node.node_status?.tracking_sent_date && node.node_status.tracking_end_date
                              && ((new Date(node.node_status.tracking_end_date).getTime() - new Date(node.node_status.tracking_sent_date).getTime()) / 1000).toFixed(2)
                            }
                            {t('s')}
                          </div>
                        </div>
                      )}
                    />
                  </ListItemButton>
                </ListItem>
              ))}
            </List>
          ) : (
            <Empty message={t('No processed nodes in this attack_chain_run.')} />
          )}
        </Paper>
      </div>
      <div style={{
        display: 'grid',
        marginTop: theme.spacing(3),
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: '1fr 1fr',
      }}
      >
        <Typography variant="h4">{t('Sent nodes over time')}</Typography>
        <Typography variant="h4">{t('Sent nodes over time')}</Typography>
        <Paper variant="outlined">
          <AttackChainNodeOverTimeArea nodes={filteredAttackChainNodes} />
        </Paper>
        <Paper variant="outlined">
          <AttackChainNodeOverTimeLine nodes={filteredAttackChainNodes} />
        </Paper>
      </div>
      {selectedAttackChainNodeId && (
        <ArticleContext.Provider value={articleContext}>
          <TeamContext.Provider value={teamContext}>
            <EndpointContext.Provider value={endpointContext}>
              <ChallengeContext.Provider value={challengeContext}>
                <UpdateAttackChainNode
                  open
                  handleClose={() => setSelectedAttackChainNodeId(null)}
                  onUpdateAttackChainNode={onUpdateAttackChainNode}
                  injectId={selectedAttackChainNodeId}
                  isAtomic={false}
                  nodes={nodes}
                  articlesFromAttackChainRunOrAttackChain={articles}
                  uriVariable={`/admin/attack_chain_runs/${exerciseId}/definition`}
                  variablesFromAttackChainRunOrAttackChain={variables}
                />
              </ChallengeContext.Provider>
            </EndpointContext.Provider>
          </TeamContext.Provider>
        </ArticleContext.Provider>
      )}
    </div>
  );
};

export default TimelineOverview;
