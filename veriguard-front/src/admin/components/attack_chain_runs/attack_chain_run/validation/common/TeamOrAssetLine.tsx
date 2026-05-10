import { CastForEducationOutlined, DnsOutlined, LanOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchSimulationAssetGroups } from '../../../../../../actions/asset_groups/assetgroup-action';
import { type AssetGroupsHelper } from '../../../../../../actions/asset_groups/assetgroup-helper';
import { type EndpointHelper } from '../../../../../../actions/assets/asset-helper';
import { fetchSimulationEndpoints } from '../../../../../../actions/assets/endpoint-actions';
import { fetchAttackChainRunTeams } from '../../../../../../actions/AttackChainRun';
import { type Contract } from '../../../../../../actions/contract/contract';
import { type TeamsHelper } from '../../../../../../actions/teams/team-helper';
import { useHelper } from '../../../../../../store';
import { type AssetGroup, type AttackChainNode, type Endpoint, type Team } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import { type AttackChainNodeExpectationsStore } from '../../../../common/attack_chain_nodes/expectations/Expectation';
import ManualExpectations from '../expectations/ManualExpectations';
import TechnicalExpectationAsset from '../expectations/TechnicalExpectationAsset';
import TechnicalExpectationAssetGroup from '../expectations/TechnicalExpectationAssetGroup';

const useStyles = makeStyles()(() => ({
  item: { height: 40 },
  bodyItem: {
    height: '100%',
    float: 'left',
    fontSize: 13,
  },
}));

interface Props {
  exerciseId: string;
  node: AttackChainNode;
  injectContract: Contract;
  expectationsByAttackChainNode: AttackChainNodeExpectationsStore[];
  id: string;
  expectations: AttackChainNodeExpectationsStore[];
}

const TeamOrAssetLine: FunctionComponent<Props> = ({
  exerciseId,
  node,
  injectContract,
  expectationsByAttackChainNode,
  id,
  expectations,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();

  // Fetching data
  const {
    teamsMap,
    assetsMap,
    assetGroupsMap,
  } = useHelper((helper: AssetGroupsHelper & EndpointHelper & TeamsHelper) => {
    return {
      assetsMap: helper.getEndpointsMap(),
      assetGroupsMap: helper.getAssetGroupMaps(),
      teamsMap: helper.getTeamsMap(),
    };
  });
  useDataLoader(() => {
    dispatch(fetchAttackChainRunTeams(exerciseId));
    dispatch(fetchSimulationEndpoints(exerciseId));
    dispatch(fetchSimulationAssetGroups(exerciseId));
  });

  const team: Team = teamsMap[id];
  const asset: Endpoint = assetsMap[id];
  const assetGroup: AssetGroup = assetGroupsMap[id];

  const groupByExpectationName = (es: AttackChainNodeExpectationsStore[]) => {
    return es.reduce((group, expectation) => {
      const { node_expectation_name } = expectation;
      if (node_expectation_name) {
        const values = group.get(node_expectation_name) ?? [];
        values.push(expectation);
        group.set(node_expectation_name, values);
      }
      return group;
    }, new Map());
  };

  return (
    <div key={id}>
      <ListItem
        divider
        sx={{ pl: 4 }}
        classes={{ root: classes.item }}
      >
        <ListItemIcon>
          {!!team && <CastForEducationOutlined fontSize="small" />}
          {!!asset && <DnsOutlined fontSize="small" />}
          {!!assetGroup && <LanOutlined fontSize="small" />}
        </ListItemIcon>
        <ListItemText
          primary={(
            <div className={classes.bodyItem} style={{ width: '20%' }}>
              {team?.team_name || asset?.asset_name || assetGroup?.asset_group_name}
            </div>
          )}
        />
      </ListItem>
      <List component="div" disablePadding>
        {Array.from(groupByExpectationName(expectations)).map(([expectationName, es]) => {
          if (es === 'PREVENTION' || es === 'DETECTION') {
            const expectation = es[0];
            if (asset) {
              return (
                <TechnicalExpectationAsset
                  key={expectationName}
                  expectation={expectation}
                  injectContract={injectContract}
                />
              );
            }
            if (assetGroup) {
              const relatedExpectations = expectationsByAttackChainNode.filter(e => assetGroup.asset_group_assets?.includes(e.node_expectation_asset ?? '')) ?? [];

              return (
                <TechnicalExpectationAssetGroup
                  key={expectationName}
                  expectation={expectation}
                  injectContract={injectContract}
                  relatedExpectations={relatedExpectations}
                  team={team}
                  assetGroup={assetGroup}
                />
              );
            }
            return (<div key={expectationName}></div>);
          }
          return (
            <ManualExpectations key={expectationName} node={node} expectations={es} />
          );
        })}
      </List>
    </div>
  );
};

export default TeamOrAssetLine;
