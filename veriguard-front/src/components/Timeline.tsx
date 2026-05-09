import { CastForEducationOutlined, CastOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { Fragment, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackChainNodeStore } from '../actions/attack_chain_nodes/AttackChainNode';
import AttackChainNodeIcon from '../admin/components/common/attack_chain_nodes/AttackChainNodeIcon';
import { type AttackChainNode, type Team } from '../utils/api-types';
import useSearchAndFilter from '../utils/SortingFiltering';
import { truncate } from '../utils/String';
import { splitDuration } from '../utils/Time';
import { isNotEmptyField } from '../utils/utils';
import { useFormatter } from './i18n';

const useStyles = makeStyles()(() => ({
  container: {
    marginTop: 60,
    paddingRight: 40,
  },
  names: {
    float: 'left',
    width: '10%',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  lineName: {
    width: '100%',
    height: 50,
    lineHeight: '50px',
  },
  name: {
    fontSize: 14,
    fontWeight: 400,
    display: 'flex',
    alignItems: 'center',
  },
  timeline: {
    float: 'left',
    width: '90%',
    position: 'relative',
  },
  line: {
    position: 'relative',
    width: '100%',
    height: 50,
    lineHeight: '50px',
    padding: '0 20px 0 20px',
    borderBottom: '1px solid rgba(255, 255, 255, 0.15)',
    verticalAlign: 'middle',
  },
  scale: {
    position: 'absolute',
    width: '100%',
    height: '100%',
    top: 0,
    left: 0,
  },
  tick: {
    position: 'absolute',
    width: 1,
  },
  tickLabelTop: {
    position: 'absolute',
    left: -28,
    top: -20,
    width: 100,
    fontSize: 10,
  },
  tickLabelBottom: {
    position: 'absolute',
    left: -28,
    bottom: -20,
    width: 100,
    fontSize: 10,
  },
  injectGroup: {
    position: 'absolute',
    padding: '6px 5px 0 5px',
    zIndex: 1000,
    display: 'grid',
    gridAutoFlow: 'column',
    gridTemplateRows: 'repeat(2, 20px)',
  },
}));

interface Props {
  nodes: AttackChainNodeStore[];
  teams: Team[];
  onSelectAttackChainNode: (injectId: string) => void;
}

const Timeline: FunctionComponent<Props> = ({ nodes, onSelectAttackChainNode, teams }) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();

  // Retrieve data
  const getAttackChainNodesPerTeam = (teamId: string) => {
    return nodes.filter(i => i.node_teams?.includes(teamId) || i.node_all_teams);
  };

  const injectsPerTeam = R.mergeAll(
    teams.map((a: Team) => ({ [a.team_id]: getAttackChainNodesPerTeam(a.team_id) })),
  );

  const allTeamAttackChainNodeIds = new Set(R.values(injectsPerTeam).flat().map((inj: AttackChainNode) => inj.node_id));

  // Build map of technical AttackChainNodes or without team
  /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
  const injectsWithoutTeamMap = nodes.reduce((acc: { [x: string]: any[] }, node: AttackChainNodeStore) => {
    /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
    let keys: any[] = [];

    if (!allTeamAttackChainNodeIds.has(node.node_id)) {
      if (
        node.node_injector_contract?.convertedContent
        && 'fields' in node.node_injector_contract.convertedContent
        && node.node_injector_contract.convertedContent.fields.some(
          /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
          (field: any) => field.key === 'teams',
        )
      ) {
        keys = ['No teams'];
      } else if (node.node_type !== null) {
        keys = [node.node_type];
      }
    }

    keys?.forEach((key) => {
      if (!acc[key]) {
        acc[key] = [];
      }
      acc[key].push(node);
    });

    return acc;
  }, {} as { [key: string]: AttackChainNode[] });

  const injectsMap = {
    ...injectsPerTeam,
    ...injectsWithoutTeamMap,
  };

  // Sorted teams
  const teamAttackChainNodeNames = R.map((key: string) => ({
    team_id: key,
    team_name: key,
  }), R.keys(injectsMap));

  const sortedNativeTeams = R.sortWith(
    [R.ascend(R.prop('team_name'))],
    teams,
  );

  const filteredTeamAttackChainNode = R.reject(
    (teamAttackChainNodeName: Team) => R.includes(
      teamAttackChainNodeName.team_id,
      R.pluck('team_id', sortedNativeTeams),
    ),
    teamAttackChainNodeNames,
  );

  const sortedTeams = [...filteredTeamAttackChainNode, ...sortedNativeTeams];

  // Re utilisation of filter and sort hook
  const searchColumns = ['title', 'description', 'content'];
  const filtering = useSearchAndFilter(
    'node',
    'depends_duration',
    searchColumns,
  );

  const handleSelectAttackChainNode = (id: string) => onSelectAttackChainNode(id);

  const lastAttackChainNode = R.pipe(
    R.sortWith([R.descend(R.prop('node_depends_duration'))]),
    R.head,
  )(nodes);
  const totalDuration = lastAttackChainNode
    ? lastAttackChainNode.node_depends_duration + 3600
    : 60;
  const tickDuration = Math.round(totalDuration / 20);
  const ticks = [...Array(21)].map((_, i) => tickDuration * i);
  const byTick = R.groupBy((node: AttackChainNodeStore) => {
    const duration = node.node_depends_duration;
    for (const tick of ticks) {
      if (duration < tick) {
        return tick - tickDuration;
      }
    }
    // Return the last tick if duration exceeds all ticks
    return ticks[ticks.length - 1];
  });

  const grid0 = theme.palette.mode === 'light' ? 'rgba(0,0,0,0)' : 'rgba(255,255,255,0)';
  const grid5 = theme.palette.mode === 'light'
    ? 'rgba(0,0,0,0.05)'
    : 'rgba(255,255,255,0.05)';
  const grid25 = theme.palette.mode === 'light'
    ? '1px solid rgba(0, 0, 0, 0.25)'
    : '1px solid rgba(255, 255, 255, 0.25)';
  const grid15 = theme.palette.mode === 'light'
    ? '1px dashed rgba(0, 0, 0, 0.15)'
    : '1px dashed rgba(255, 255, 255, 0.15)';

  return (
    <>
      {nodes.length > 0 && sortedTeams.length > 0 ? (
        <div className={classes.container}>
          <div className={classes.names}>
            {sortedTeams.map(team => (
              <div key={team.team_id} className={classes.lineName}>
                <div className={classes.name}>
                  {team.team_name.startsWith('veriguard_') ? (
                    <CastOutlined fontSize="small" />
                  ) : (
                    <CastForEducationOutlined fontSize="small" />
                  )}
                  &nbsp;&nbsp;
                  {team.team_name.startsWith('veriguard_')
                    ? t(team.team_name)
                    : truncate(team.team_name, 20)}
                </div>
              </div>
            ))}
          </div>
          <div className={classes.timeline}>
            {sortedTeams.map((team, index) => {
              const injectsGroupedByTick = byTick(
                filtering.filterAndSort(injectsMap[team.team_id] ?? []),
              );
              return (
                <div
                  key={team.team_id}
                  className={classes.line}
                  style={{ backgroundColor: index % 2 === 0 ? grid0 : grid5 }}
                >
                  {Object.keys(injectsGroupedByTick).map((key, i) => {
                    const injectGroupPosition = (parseFloat(key) * 100) / totalDuration;
                    return (
                      <div
                        key={i}
                        className={classes.injectGroup}
                        style={{ left: `${injectGroupPosition}%` }}
                      >
                        {injectsGroupedByTick[key].map((node: AttackChainNodeStore) => {
                          const duration = splitDuration(node.node_depends_duration || 0);
                          const tooltipContent = (
                            <Fragment>
                              {node.node_title}
                              <br />
                              <span style={{
                                display: 'block',
                                textAlign: 'center',
                                fontWeight: 'bold',
                              }}
                              >
                                {`${duration.days} ${t('d')}, ${duration.hours} ${t('h')}, ${duration.minutes} ${t('m')}`}
                              </span>
                            </Fragment>
                          );
                          return (
                            <AttackChainNodeIcon
                              key={node.node_id}
                              isPayload={isNotEmptyField(node.node_injector_contract?.injector_contract_payload)}
                              type={
                                node.node_injector_contract?.injector_contract_payload
                                  ? node.node_injector_contract.injector_contract_payload?.payload_collector_type
                                  || node.node_injector_contract.injector_contract_payload?.payload_type
                                  : node.node_type
                              }
                              onClick={() => handleSelectAttackChainNode(node.node_id)}
                              done={node.node_status !== null}
                              disabled={!node.node_enabled}
                              size="small"
                              variant="timeline"
                              tooltip={tooltipContent}
                            />
                          );
                        })}
                      </div>
                    );
                  })}
                </div>
              );
            })}
            <div className={classes.scale}>
              {ticks.map((tick, index) => {
                const duration = splitDuration(tick);
                return (
                  <div
                    key={tick}
                    className={classes.tick}
                    style={{
                      left: `${index * 5}%`,
                      height: index % 5 === 0 ? 'calc(100% + 30px)' : '100%',
                      top: index % 5 === 0 ? -15 : 0,
                      borderRight: index % 5 === 0 ? grid25 : grid15,
                    }}
                  >
                    <div className={classes.tickLabelTop}>
                      {index % 5 === 0
                        ? `${duration.days}
                        ${t('d')}, ${duration.hours}
                        ${t('h')}, ${duration.minutes}
                        ${t('m')}`
                        : ''}
                    </div>
                    <div className={classes.tickLabelBottom}>
                      {index % 5 === 0
                        ? `${duration.days}
                        ${t('d')}, ${duration.hours}
                        ${t('h')}, ${duration.minutes}
                        ${t('m')}`
                        : ''}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
};

export default Timeline;
