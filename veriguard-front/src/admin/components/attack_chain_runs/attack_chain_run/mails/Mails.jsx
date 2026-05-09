import { BarChartOutlined, KeyboardArrowRight, ReorderOutlined } from '@mui/icons-material';
import {
  Chip,
  Grid,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemSecondaryAction,
  ListItemText,
  Paper,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import { useContext, useState } from 'react';
import { useDispatch } from 'react-redux';
import { Link, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchAttackChainRunAttackChainNodes } from '../../../../../actions/AttackChainNode';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import SearchFilter from '../../../../../components/SearchFilter';
import { useHelper } from '../../../../../store';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useSearchAndFilter from '../../../../../utils/SortingFiltering';
import AttackChainNodeIcon from '../../../common/attack_chain_nodes/AttackChainNodeIcon';
import { PermissionsContext, TeamContext } from '../../../common/Context';
import TagsFilter from '../../../common/filters/TagsFilter';
import AnimationMenu from '../AnimationMenu';
import CreateQuickAttackChainNode from '../attack_chain_nodes/CreateQuickAttackChainNode';
import teamContextForAttackChainRun from '../teams/teamContextForAttackChainRun';
import MailDistributionByAttackChainNode from './MailDistributionByAttackChainNode';
import MailDistributionByPlayer from './MailDistributionByPlayer';
import MailDistributionByTeam from './MailDistributionByTeam';
import MailDistributionOverTimeChart from './MailDistributionOverTimeChart';
import MailDistributionOverTimeLine from './MailDistributionOverTimeLine';

const useStyles = makeStyles()(() => ({
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  paperChart: {
    height: '100%',
    minHeight: '100%',
    margin: '10px 0 0 0',
    padding: 15,
    borderRadius: 4,
  },
  bodyItem: {
    height: '100%',
    fontSize: 13,
  },
  goIcon: { paddingTop: 3 },
  coms: {
    fontSize: 12,
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    backgroundColor: 'rgba(0, 177, 255, 0.08)',
    color: '#00b1ff',
    border: '1px solid #00b1ff',
  },
  comsNotRead: {
    fontSize: 12,
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    backgroundColor: 'rgba(236, 64, 122, 0.08)',
    color: '#ec407a',
    border: '1px solid #ec407a',
  },
}));

const headerStyles = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  node_type: {
    float: 'left',
    width: '15%',
    fontSize: 12,
    fontWeight: '700',
  },
  node_title: {
    float: 'left',
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
  },
  node_users_number: {
    float: 'left',
    width: '15%',
    fontSize: 12,
    fontWeight: '700',
  },
  node_sent_at: {
    float: 'left',
    width: '15%',
    fontSize: 12,
    fontWeight: '700',
  },
  node_communications_not_ack_number: {
    float: 'left',
    width: '10%',
    fontSize: 12,
    fontWeight: '700',
  },
  node_communications_number: {
    float: 'left',
    width: '10%',
    fontSize: 12,
    fontWeight: '700',
  },
  node_tags: {
    float: 'left',
    fontSize: 12,
    fontWeight: '700',
  },
};

const inlineStyles = {
  node_type: {
    float: 'left',
    width: '15%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  node_title: {
    float: 'left',
    width: '30%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  node_users_number: {
    float: 'left',
    width: '15%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  node_sent_at: {
    float: 'left',
    width: '15%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  node_communications_not_ack_number: {
    float: 'left',
    width: '10%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  node_communications_number: {
    float: 'left',
    width: '10%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  node_tags: {
    float: 'left',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

const Mails = () => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const { t, fndt } = useFormatter();
  const [viewMode, setViewMode] = useState('list');
  const { permissions } = useContext(PermissionsContext);

  // Filter and sort hook
  const searchColumns = ['title', 'description', 'content'];
  const filtering = useSearchAndFilter('node', 'sent_at', searchColumns);
  // Fetching data
  const { exerciseId } = useParams();
  const { attack_chain_run, nodes } = useHelper((helper) => {
    return {
      attack_chain_run: helper.getAttackChainRun(exerciseId),
      nodes: helper.getAttackChainRunAttackChainNodes(exerciseId),
    };
  });
  useDataLoader(() => {
    dispatch(fetchAttackChainRunAttackChainNodes(exerciseId));
  });
  const sortedAttackChainNodes = filtering
    .filterAndSort(nodes)
    .filter(i => i.node_communications_number > 0);

  const teamContext = teamContextForAttackChainRun(
    exerciseId,
    attack_chain_run.attack_chain_run_teams_users,
    attack_chain_run.attack_chain_run_all_users_number,
    attack_chain_run.attack_chain_run_users_number,
  );

  // Rendering
  return (
    <div>
      <AnimationMenu exerciseId={exerciseId} />
      <ToggleButtonGroup
        size="small"
        exclusive={true}
        style={{ float: 'right' }}
        aria-label="Change view mode"
      >
        <Tooltip title={t('List view')}>
          <ToggleButton
            value="list"
            onClick={() => setViewMode('list')}
            selected={viewMode === 'list'}
            aria-label="List view mode"
          >
            <ReorderOutlined fontSize="small" color={viewMode === 'list' ? 'inherit' : 'primary'} />
          </ToggleButton>
        </Tooltip>
        <Tooltip title={t('Distribution view')}>
          <ToggleButton
            value="distribution"
            onClick={() => setViewMode('distribution')}
            selected={viewMode === 'distribution'}
            aria-label="Distribution view mode"
          >
            <BarChartOutlined fontSize="small" color={viewMode === 'distribution' ? 'inherit' : 'primary'} />
          </ToggleButton>
        </Tooltip>
      </ToggleButtonGroup>
      {viewMode === 'distribution' && (
        <>
          <Grid container spacing={3} classes={{ container: classes.gridContainer }}>
            <Grid size={{ xs: 6 }} style={{ paddingTop: 10 }}>
              <Typography variant="h4">
                {t('Sent mails over time')}
              </Typography>
              <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                <MailDistributionOverTimeChart exerciseId={exerciseId} />
              </Paper>
            </Grid>
            <Grid size={{ xs: 6 }} style={{ paddingTop: 10 }}>
              <Typography variant="h4">
                {t('Sent mails over time')}
              </Typography>
              <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                <MailDistributionOverTimeLine exerciseId={exerciseId} />
              </Paper>
            </Grid>
            <Grid size={{ xs: 4 }} style={{ paddingTop: 25 }}>
              <Typography variant="h4">
                {t('Distribution of mails by team')}
              </Typography>
              <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                <MailDistributionByTeam exerciseId={exerciseId} />
              </Paper>
            </Grid>
            <Grid size={{ xs: 4 }} style={{ paddingTop: 25 }}>
              <Typography variant="h4">
                {t('Distribution of mails by player')}
              </Typography>
              <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                <MailDistributionByPlayer exerciseId={exerciseId} />
              </Paper>
            </Grid>
            <Grid size={{ xs: 4 }} style={{ paddingTop: 25 }}>
              <Typography variant="h4">
                {t('Distribution of mails by node')}
              </Typography>
              <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                <MailDistributionByAttackChainNode exerciseId={exerciseId} />
              </Paper>
            </Grid>
          </Grid>
        </>
      )}
      {viewMode === 'list' && (
        <>
          <div style={{
            float: 'left',
            marginRight: 10,
          }}
          >
            <SearchFilter
              variant="small"
              onChange={filtering.handleSearch}
              keyword={filtering.keyword}
            />
          </div>
          <div style={{
            float: 'left',
            marginRight: 10,
          }}
          >
            <TagsFilter
              onAddTag={filtering.handleAddTag}
              onRemoveTag={filtering.handleRemoveTag}
              currentTags={filtering.tags}
            />
          </div>
          <div className="clearfix" />
          <List style={{ marginTop: 10 }}>
            <ListItem
              classes={{ root: classes.itemHead }}
              divider={false}
              style={{ paddingTop: 0 }}
            >
              <ListItemIcon>
                <span
                  style={{
                    padding: '0 8px 0 8px',
                    fontWeight: 700,
                    fontSize: 12,
                  }}
                >
                &nbsp;
                </span>
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div>
                    {filtering.buildHeader(
                      'node_title',
                      'Title',
                      false,
                      headerStyles,
                    )}
                    {filtering.buildHeader(
                      'node_users_number',
                      'Players',
                      true,
                      headerStyles,
                    )}
                    {filtering.buildHeader(
                      'node_sent_at',
                      'Sent at',
                      true,
                      headerStyles,
                    )}
                    {filtering.buildHeader(
                      'node_communications_not_ack_number',
                      'Mails not read',
                      true,
                      headerStyles,
                    )}
                    {filtering.buildHeader(
                      'node_communications_number',
                      'Total mails',
                      true,
                      headerStyles,
                    )}
                    {filtering.buildHeader(
                      'node_tags',
                      'Tags',
                      true,
                      headerStyles,
                    )}
                  </div>
                )}
              />
              <ListItemSecondaryAction> &nbsp; </ListItemSecondaryAction>
            </ListItem>
            {sortedAttackChainNodes.map((node) => {
              return (
                <ListItemButton
                  key={node.node_id}
                  component={Link}
                  to={`/admin/attack_chain_runs/${exerciseId}/animation/mails/${node.node_id}`}
                  classes={{ root: classes.item }}
                  divider={true}
                >
                  <ListItemIcon style={{ paddingTop: 5 }}>
                    <AttackChainNodeIcon type={node.node_type} disabled={!node.node_enabled} />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div>
                        <div
                          className={classes.bodyItem}
                          style={inlineStyles.node_title}
                        >
                          {node.node_title}
                        </div>
                        <div
                          className={classes.bodyItem}
                          style={inlineStyles.node_users_number}
                        >
                          {node.node_users_number}
                        </div>
                        <div
                          className={classes.bodyItem}
                          style={inlineStyles.node_sent_at}
                        >
                          {fndt(node.node_sent_at)}
                        </div>
                        <div
                          className={classes.bodyItem}
                          style={inlineStyles.node_communications_not_ack_number}
                        >
                          <Chip
                            classes={{ root: classes.comsNotRead }}
                            label={node.node_communications_not_ack_number}
                          />
                        </div>
                        <div
                          className={classes.bodyItem}
                          style={inlineStyles.node_communications_number}
                        >
                          <Chip
                            classes={{ root: classes.coms }}
                            label={node.node_communications_number}
                          />
                        </div>
                        <div
                          className={classes.bodyItem}
                          style={inlineStyles.node_tags}
                        >
                          <ItemTags variant="list" tags={node.node_tags} />
                        </div>
                      </div>
                    )}
                  />
                  <ListItemSecondaryAction classes={{ root: classes.goIcon }}>
                    <KeyboardArrowRight />
                  </ListItemSecondaryAction>
                </ListItemButton>
              );
            })}
          </List>
          {permissions.canManage && (
            <TeamContext.Provider value={teamContext}>
              <CreateQuickAttackChainNode attack_chain_run={attack_chain_run} />
            </TeamContext.Provider>
          )}
        </>
      )}
    </div>
  );
};

export default Mails;
