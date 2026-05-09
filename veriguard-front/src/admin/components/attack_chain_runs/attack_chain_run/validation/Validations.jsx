import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import * as R from 'ramda';
import { useState } from 'react';
import { useDispatch } from 'react-redux';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchAttackChainRunAttackChainNodes } from '../../../../../actions/AttackChainNode';
import { fetchAttackChainRunAttackChainNodeExpectations } from '../../../../../actions/AttackChainRun';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import Loader from '../../../../../components/Loader';
import SearchFilter from '../../../../../components/SearchFilter';
import { useHelper } from '../../../../../store';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { isNotEmptyField } from '../../../../../utils/utils';
import AttackChainNodeIcon from '../../../common/attack_chain_nodes/AttackChainNodeIcon';
import TagsFilter from '../../../common/filters/TagsFilter';
import AnimationMenu from '../AnimationMenu';
import TeamOrAssetLine from './common/TeamOrAssetLine';

const useStyles = makeStyles()(() => ({
  item: { height: 40 },
  bodyItem: {
    height: '100%',
    float: 'left',
    fontSize: 13,
  },
}));

const Validations = () => {
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const { exerciseId } = useParams();
  const [tags, setTags] = useState([]);
  const { fndt } = useFormatter();
  const [keyword, setKeyword] = useState('');
  const handleSearch = value => setKeyword(value);
  const handleAddTag = (value) => {
    if (value) {
      setTags(R.uniq(R.append(value, tags)));
    }
  };
  const handleRemoveTag = value => setTags(R.filter(n => n.id !== value, tags));
  // Fetching data
  const {
    attack_chain_run,
    injectExpectations,
    injectsMap,
  } = useHelper((helper) => {
    return {
      attack_chain_run: helper.getAttackChainRun(exerciseId),
      injectsMap: helper.getAttackChainNodesMap(),
      injectExpectations: helper.getAttackChainRunAttackChainNodeExpectations(exerciseId),
    };
  });
  useDataLoader(() => {
    dispatch(fetchAttackChainRunAttackChainNodeExpectations(exerciseId));
    dispatch(fetchAttackChainRunAttackChainNodes(exerciseId));
  });
  const filterByKeyword = n => keyword === ''
    || (n.node_expectation_node?.node_title || '')
      .toLowerCase()
      .indexOf(keyword.toLowerCase()) !== -1
      || (n.node_expectation_node?.node_description || '')
        .toLowerCase()
        .indexOf(keyword.toLowerCase()) !== -1;
  const sort = R.sortWith([R.descend(R.prop('node_expectation_created_at'))]);
  const sortedAttackChainNodeExpectations = R.pipe(
    R.uniqBy(R.prop('node_expectation_id')),
    R.map(n => R.assoc(
      'node_expectation_node',
      injectsMap[n.node_expectation_node] || {},
      n,
    )),
    R.filter(n => n.node_expectation_type === 'MANUAL'),
    R.filter(
      n => tags.length === 0
        || R.any(
          filter => R.includes(filter, n.node_expectation_node?.node_tags),
          R.pluck('id', tags),
        ),
    ),
    R.filter(filterByKeyword),
    sort,
  )(injectExpectations);

  const groupedByAttackChainNode = sortedAttackChainNodeExpectations.reduce((group, expectation) => {
    const { node_expectation_node } = expectation;
    const { node_id } = node_expectation_node;
    if (node_id) {
      const values = group[node_id] ?? [];
      values.push(expectation);
      group[node_id] = values;
    }
    return group;
  }, {});

  const groupedByTeamOrAsset = (expectations) => {
    return expectations.reduce((group, expectation) => {
      const { node_expectation_team } = expectation;
      const { node_expectation_asset } = expectation;
      const { node_expectation_asset_group } = expectation;
      if (node_expectation_team) {
        const values = group[node_expectation_team] ?? [];
        values.push(expectation);
        group[node_expectation_team] = values;
      }
      if (node_expectation_asset && !expectation.node_expectation_group) {
        const values = group[node_expectation_asset] ?? [];
        values.push(expectation);
        group[node_expectation_asset] = values;
      }
      if (node_expectation_asset_group) {
        const values = group[node_expectation_asset_group] ?? [];
        values.push(expectation);
        group[node_expectation_asset_group] = values;
      }
      return group;
    }, {});
  };

  // Rendering
  if (attack_chain_run && injectExpectations) {
    return (
      <div>
        <AnimationMenu exerciseId={exerciseId} />
        <div style={{
          float: 'left',
          marginRight: 10,
        }}
        >
          <SearchFilter
            variant="small"
            onChange={handleSearch}
            keyword={keyword}
          />
        </div>
        <div style={{
          float: 'left',
          marginRight: 10,
        }}
        >
          <TagsFilter
            onAddTag={handleAddTag}
            onRemoveTag={handleRemoveTag}
            currentTags={tags}
          />
        </div>
        <div className="clearfix" />
        <List>
          {Object.entries(groupedByAttackChainNode).map(([injectId, expectationsByAttackChainNode]) => {
            const node = injectsMap[injectId] || {};
            const injectContract = node.node_injector_contract.convertedContent || {};
            return (
              <div key={node.node_id}>
                <ListItem divider={true} classes={{ root: classes.item }}>
                  <ListItemIcon style={{ paddingTop: 5 }}>
                    <AttackChainNodeIcon
                      isPayload={isNotEmptyField(node.node_injector_contract.injector_contract_payload)}
                      type={
                        node.node_injector_contract.injector_contract_payload
                          ? node.node_injector_contract.injector_contract_payload?.payload_collector_type
                          || node.node_injector_contract.injector_contract_payload?.payload_type
                          : node.node_type
                      }
                      disabled={!node.node_enabled}
                      size="small"
                    />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <>
                        <div className={classes.bodyItem} style={{ width: '55%' }}>
                          {node.node_title}
                        </div>
                        <div className={classes.bodyItem} style={{ width: '15%' }}>
                          {fndt(node.node_sent_at)}
                        </div>
                        <div className={classes.bodyItem} style={{ width: '30%' }}>
                          <ItemTags variant="list" tags={node.node_tags} />
                        </div>
                      </>
                    )}
                  />
                </ListItem>
                <List component="div" disablePadding>
                  {Object.entries(groupedByTeamOrAsset(expectationsByAttackChainNode)).map(([id, expectations]) => {
                    return (
                      <TeamOrAssetLine
                        key={id}
                        exerciseId={exerciseId}
                        node={node}
                        injectContract={injectContract}
                        expectationsByAttackChainNode={expectationsByAttackChainNode}
                        id={id}
                        expectations={expectations}
                      />
                    );
                  })}
                </List>
              </div>
            );
          })}
        </List>
      </div>
    );
  }
  return (
    <div className={classes.container}>
      <AnimationMenu exerciseId={exerciseId} />
      <Loader />
    </div>
  );
};

export default Validations;
