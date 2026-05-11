import { HelpOutlineOutlined } from '@mui/icons-material';
import { Checkbox, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type CSSProperties, type FunctionComponent, type SyntheticEvent, useContext, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AttackChainNodeOutputType, type AttackChainNodeStore } from '../../../../actions/attack_chain_nodes/AttackChainNode';
import { exportAttackChainNodeSearch } from '../../../../actions/attack_chain_nodes/node-action';
import ChainedTimeline from '../../../../components/ChainedTimeline';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import ItemBoolean from '../../../../components/ItemBoolean';
import ItemDomains from '../../../../components/ItemDomains';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import PlatformIcon from '../../../../components/PlatformIcon';
import {
  type AttackChainNode,
  type AttackChainNodeBulkUpdateOperation,
  type AttackChainNodeExportFromSearchRequestInput,
  type AttackChainNodeInput,
  type AttackChainNodeTestStatusOutput,
  type NodeContract as NodeContractType,
  type SearchPaginationInput,
  type Team,
  type Variable,
} from '../../../../utils/api-types';
import { type NodeContractConverted } from '../../../../utils/api-types-custom';
import { MESSAGING$ } from '../../../../utils/Environment';
import useEntityToggle from '../../../../utils/hooks/useEntityToggle';
import { splitDuration } from '../../../../utils/Time';
import { download, isNotEmptyField } from '../../../../utils/utils';
import { AttackChainNodeContext, AttackChainNodeTestContext, PermissionsContext, ViewModeContext } from '../Context';
import ToolBar from '../ToolBar';
import AttackChainNodeIcon from './AttackChainNodeIcon';
import AttackChainNodePopover from './AttackChainNodePopover';
import AttackChainNodesListButtons from './AttackChainNodesListButtons';
import CreateAttackChainNode from './CreateAttackChainNode';
import NodeContract from './NodeContract';
import UpdateAttackChainNode from './UpdateAttackChainNode';

const useStyles = makeStyles()(() => ({
  disabled: {
    opacity: 0.38,
    pointerEvents: 'none',
  },
  duration: {
    fontSize: 12,
    lineHeight: '12px',
    height: 20,
    float: 'left',
    marginRight: 7,
    borderRadius: 4,
    width: 180,
    backgroundColor: 'rgba(0, 177, 255, 0.08)',
    color: '#00b1ff',
    border: '1px solid #00b1ff',
  },
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
  bodyItems: { display: 'flex' },
  bodyItem: {
    height: 20,
    fontSize: 13,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    paddingRight: 10,
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  node_type: { width: '15%' },
  node_title: { width: '20%' },
  node_contract_domains: { width: '15%' },
  node_depends_duration: { width: '18%' },
  node_platforms: { width: '10%' },
  node_enabled: { width: '12%' },
  node_tags: { width: '10%' },
};

interface Props {
  setViewMode?: (mode: string) => void;
  availableButtons: string[];
  teams: Team[];
  variables: Variable[];
  uriVariable: string;
  /** 动态用例列表（Phase 12c-Biii B5）：传给 ChainedTimeline 渲染动态节点. */
  dynamicContracts?: NodeContractType[];
}

const AttackChainNodes: FunctionComponent<Props> = ({
  setViewMode,
  availableButtons,
  teams,
  variables,
  uriVariable,
  dynamicContracts = [],
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t, tPick } = useFormatter();
  const theme = useTheme();
  const injectContext = useContext(AttackChainNodeContext);
  const { nodes, setAttackChainNodes } = injectContext;
  const viewModeContext = useContext(ViewModeContext);
  const { permissions } = useContext(PermissionsContext);
  const { contextId } = useContext(AttackChainNodeTestContext);

  // Headers
  const headers = useMemo(() => [
    {
      field: 'node_type',
      label: 'Type',
      isSortable: false,
      value: (_: AttackChainNodeOutputType, injectContract: NodeContractConverted['convertedContent']) => {
        const injectorContractName = tPick(injectContract?.label);
        return injectContract
          ? (
              <NodeContract
                variant="list"
                config={injectContract?.config}
                label={injectorContractName}
              />
            )
          : <NodeContract variant="list" label={t('Deleted')} deleted />;
      },
    },
    {
      field: 'node_title',
      label: 'Title',
      isSortable: true,
      value: (node: AttackChainNodeOutputType, _: NodeContractConverted['convertedContent']) => <>{node.node_title}</>,
    },
    {
      field: 'node_contract_domains',
      label: t('Domains'),
      isSortable: true,
      value: (node: AttackChainNodeOutputType, _: NodeContractConverted['convertedContent']) => {
        return node.node_contract_domains?.length
          ? (
              <ItemDomains domains={node.node_contract_domains} variant="reduced-view" />
            )
          : <></>;
      },
    },
    {
      field: 'node_depends_duration',
      label: 'Trigger',
      isSortable: true,
      value: (node: AttackChainNodeOutputType, _: NodeContractConverted['convertedContent']) => {
        const duration = splitDuration(
          node.node_depends_duration || 0,
        );
        return (
          <Chip
            classes={{ root: classes.duration }}
            label={`${duration.days}
                          ${t('d')}, ${duration.hours}
                          ${t('h')}, ${duration.minutes}
                          ${t('m')}`}
          />
        );
      },
    },
    {
      field: 'node_platforms',
      label: 'Platform(s)',
      isSortable: false,
      value: (node: AttackChainNodeOutputType, _: NodeContractConverted['convertedContent']) => (
        <>
          {
            node.node_injector_contract?.injector_contract_platforms?.map(
              platform => (
                <PlatformIcon
                  key={platform}
                  width={20}
                  platform={platform}
                  marginRight={theme.spacing(2)}
                />
              ),
            )
          }
        </>
      ),
    },
    {
      field: 'node_enabled',
      label: 'Status',
      isSortable: false,
      value: (node: AttackChainNodeOutputType, _: NodeContractConverted['convertedContent']) => {
        let injectStatus = node.node_enabled
          ? t('Enabled')
          : t('Disabled');
        if (!node.node_ready) {
          injectStatus = t('Missing content');
        }
        return (
          <ItemBoolean
            status={node.node_ready
              ? node.node_enabled : false}
            label={injectStatus}
            variant="inList"
            tooltip={injectStatus}
          />
        );
      },
    },
    {
      field: 'node_tags',
      label: 'Tags',
      isSortable: false,
      value: (node: AttackChainNodeOutputType, _: NodeContractConverted['convertedContent']) => (
        <ItemTags
          variant="list"
          tags={node.node_tags}
        />
      ),
    },
  ], []);

  // Filters
  const availableFilterNames = [
    'node_platforms',
    'node_kill_chain_phases',
    'node_injector_contract',
    'node_type',
    'node_title',
    'node_assets',
    'node_asset_groups',
    'node_teams',
    'node_tags',
    'node_contract_domains',
  ];

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`${contextId}-nodes`, buildSearchPagination({
    sorts: initSorting('node_depends_duration', 'ASC'),
    size: 20,
  }));

  const [loading, setLoading] = useState<boolean>(true);
  const searchAttackChainNodesToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return injectContext.searchAttackChainNodes(input).finally(() => setLoading(false));
  };

  // AttackChainNodes
  // scoped to page
  // Bulk loading indicator for tests and delete
  const [isBulkLoading, setIsBulkLoading] = useState<boolean>(false);
  const [selectedAttackChainNodeId, setSelectedAttackChainNodeId] = useState<string | null>(null);
  const [reloadAttackChainNodeCount, setReloadAttackChainNodeCount] = useState(0);

  // Optimistic update
  const onCreate = (result: {
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }) => {
    if (result.entities) {
      const created = result.entities.nodes[result.result];
      setAttackChainNodes([created as AttackChainNodeOutputType, ...nodes]);
      queryableHelpers.paginationHelpers.handleChangeTotalElements(queryableHelpers.paginationHelpers.getTotalElements() + 1);
    }
  };

  const onUpdate = (result: {
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }) => {
    if (result.entities) {
      const updatedResults = result.entities.nodes[result.result];
      setAttackChainNodes(nodes.map(i => i.node_id !== updatedResults.node_id ? i : updatedResults as AttackChainNodeOutputType));
    }
  };

  const onBulkUpdate = (updatedResults: AttackChainNode[]) => {
    setAttackChainNodes(nodes.map((originalAttackChainNode) => {
      const match = updatedResults.find(
        updatedAttackChainNode => updatedAttackChainNode.node_id === originalAttackChainNode.node_id,
      );
      return (match as unknown as AttackChainNodeOutputType) || originalAttackChainNode;
    }));
  };

  const onDelete = (result: string) => {
    if (result) {
      setAttackChainNodes(nodes.filter(i => (i.node_id !== result)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(queryableHelpers.paginationHelpers.getTotalElements() - 1);
    }
  };

  const onCreateAttackChainNode = async (data: AttackChainNodeInput) => {
    await injectContext.onAddAttackChainNode(data as AttackChainNode).then((result: {
      result: string;
      entities: { nodes: Record<string, AttackChainNodeStore> };
    }) => {
      onCreate(result);
    });
  };

  const onCreateAttackChainNodes = (created: AttackChainNodeOutputType[]) => {
    setAttackChainNodes([...created, ...nodes]);
    queryableHelpers.paginationHelpers.handleChangeTotalElements(queryableHelpers.paginationHelpers.getTotalElements() + created.length);
  };

  const onUpdateAttackChainNode = async (data: AttackChainNode) => {
    if (selectedAttackChainNodeId) {
      await injectContext.onUpdateAttackChainNode(selectedAttackChainNodeId, data).then((result: {
        result: string;
        entities: { nodes: Record<string, AttackChainNodeStore> };
      }) => {
        onUpdate(result);
        return result;
      });
    }
  };

  const massUpdateAttackChainNode = async (data: AttackChainNode[]) => {
    const promises: Promise<AttackChainNodeStore | undefined>[] = [];
    data.forEach((node) => {
      promises.push(injectContext.onUpdateAttackChainNode(node.node_id, node).then((result: {
        result: string;
        entities: { nodes: Record<string, AttackChainNodeStore> };
      }) => {
        if (result.entities) {
          return result.entities.nodes[result.result];
        }
        return undefined;
      }));
    });

    Promise.all(promises).then((values) => {
      if (values !== undefined) {
        const updatedAttackChainNodes = nodes
          .map(node => (values.find(value => value !== undefined && value.node_id === node.node_id)
            ? (values.find(value => value !== undefined && value?.node_id === node.node_id) as AttackChainNodeOutputType)
            : node as AttackChainNodeOutputType));
        setAttackChainNodes(updatedAttackChainNodes);
      }
    });
  };

  const [openCreateDrawer, setOpenCreateDrawer] = useState(false);

  const [presetAttackChainNodeDuration, setPresetAttackChainNodeDuration] = useState<number>(0);
  const openCreateAttackChainNodeDrawer = (duration: number) => {
    setOpenCreateDrawer(true);
    setPresetAttackChainNodeDuration(duration);
  };

  // Toolbar
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<AttackChainNodeOutputType>('node', nodes, queryableHelpers.paginationHelpers.getTotalElements());
  const onRowShiftClick = (currentIndex: number, currentEntity: { node_id: string }, event: SyntheticEvent | null = null) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    if (selectedElements && !R.isEmpty(selectedElements)) {
      // Find the indexes of the first and last selected entities
      let firstIndex = R.findIndex(
        (n: AttackChainNode) => n.node_id === R.head(R.values(selectedElements)).node_id,
        nodes,
      );
      if (currentIndex > firstIndex) {
        let entities: AttackChainNodeOutputType[] = [];
        while (firstIndex <= currentIndex) {
          entities = [...entities, nodes[firstIndex]];

          firstIndex++;
        }
        const forcedRemove = R.values(selectedElements).filter(
          (n: AttackChainNode) => !entities.map(o => o.node_id).includes(n.node_id),
        );
        // eslint-disable-next-line @typescript-eslint/ban-ts-comment
        // @ts-expect-error
        return onToggleEntity(entities, event, forcedRemove);
      }
      let entities: AttackChainNodeOutputType[] = [];
      while (firstIndex >= currentIndex) {
        entities = [...entities, nodes[firstIndex]];

        firstIndex--;
      }
      const forcedRemove = R.values(selectedElements).filter(
        (n: AttackChainNode) => !entities.map(o => o.node_id).includes(n.node_id),
      );
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-expect-error
      return onToggleEntity(entities, event, forcedRemove);
    }
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-expect-error
    return onToggleEntity(currentEntity, event);
  };

  const injectIdsToProcess = (selectAll: boolean) => {
    return selectAll
      ? []
      : Object.keys(selectedElements).filter(k => !Object.keys(deSelectedElements).includes(k));
  };

  const injectIdsToIgnore = (selectAll: boolean) => {
    return selectAll
      ? Object.keys(deSelectedElements)
      : Object.keys(deSelectedElements).filter(k => !Object.keys(selectedElements).includes(k));
  };

  const massUpdateAttackChainNodes = async (actions: {
    field: string;
    type: string;
    values: { value: string }[];
  }[]) => {
    const operationsToPerform: AttackChainNodeBulkUpdateOperation[] = [];
    for (const action of actions) {
      // Case where no values where given
      if (!action.values.length || !action.values[0].value) continue;
      operationsToPerform.push({
        operation: action.type.toLowerCase(),
        field: action.field,
        values: R.uniq(action.values.map(n => n.value)),
      } as AttackChainNodeBulkUpdateOperation); // Cast is necessary because typeof enum don't work with operation and fields
    }
    await injectContext.onBulkUpdateAttackChainNode({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      node_ids_to_process: selectAll ? undefined : injectIdsToProcess(selectAll),
      node_ids_to_ignore: injectIdsToIgnore(selectAll),
      attack_chain_run_or_attack_chain_id: contextId,
      update_operations: operationsToPerform,
    })
      .then((result) => {
        if (result) onBulkUpdate(result);
      });
  };

  const bulkDeleteAttackChainNodes = () => {
    setIsBulkLoading(true);
    const deleteIds = injectIdsToProcess(selectAll);
    const ignoreIds = injectIdsToIgnore(selectAll);
    injectContext.onBulkDeleteAttackChainNodes({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      node_ids_to_process: selectAll ? undefined : deleteIds,
      node_ids_to_ignore: ignoreIds,
      attack_chain_run_or_attack_chain_id: contextId,
    }).then((result) => {
      // We update the numbers of elements in the pagination
      const newNumbers = Math.max(0, (queryableHelpers.paginationHelpers.getTotalElements() - result.length));
      // We remove the deleted nodes from the current data table
      const deletedIds = result.map(node => node.node_id);
      setAttackChainNodes(newNumbers !== 0 ? nodes.filter(node => !deletedIds.includes(node.node_id)) : []);
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newNumbers);
    }).finally(() => {
      setIsBulkLoading(false);
    });
  };

  const massTestAttackChainNodes = () => {
    setIsBulkLoading(true);
    const testIds = injectIdsToProcess(selectAll);
    const ignoreIds = injectIdsToIgnore(selectAll);
    injectContext.bulkTestAttackChainNodes({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      node_ids_to_process: selectAll ? undefined : testIds,
      node_ids_to_ignore: ignoreIds,
      attack_chain_run_or_attack_chain_id: contextId,
    }).then((result: {
      uri: string;
      data: AttackChainNodeTestStatusOutput[];
    }) => {
      if (numberOfSelectedElements === 1) {
        MESSAGING$.notifySuccess(t('AttackChainNode test has been sent, you can view test logs details on {itsDedicatedPage}.', {
          itsDedicatedPage: (
            <Link
              to={`${result.uri}/${result.data[0].status_id}`}
            >
              {t('its dedicated page')}
            </Link>
          ),
        }));
      } else {
        MESSAGING$.notifySuccess(t('AttackChainNode test has been sent, you can view test logs details on {itsDedicatedPage}.', { itsDedicatedPage: <Link to={`${result.uri}`}>{t('its dedicated page')}</Link> }));
      }
    }).finally(() => {
      setIsBulkLoading(false);
    });
  };

  const handleExport = (withPlayers: boolean, withTeams: boolean, withVariableValues: boolean) => {
    setIsBulkLoading(true);
    const testIds = injectIdsToProcess(selectAll);
    const ignoreIds = injectIdsToIgnore(selectAll);
    const data: AttackChainNodeExportFromSearchRequestInput = {
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      node_ids_to_process: selectAll ? undefined : testIds,
      node_ids_to_ignore: ignoreIds,
      attack_chain_run_or_attack_chain_id: contextId,
      options: {
        with_players: withPlayers,
        with_teams: withTeams,
        with_variable_values: withVariableValues,
      },
    };
    exportAttackChainNodeSearch(data).then((result) => {
      const contentDisposition = result.headers['content-disposition'];
      const match = contentDisposition.match(/filename\s*=\s*(.*)/i);
      const filename = match[1];
      download(result.data, filename, result.headers['content-type']);
    }).finally(() => {
      setIsBulkLoading(false);
    });
  };

  if (isBulkLoading) {
    return <Loader />;
  }
  return (
    <>
      <PaginationComponentV2
        fetch={searchAttackChainNodesToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setAttackChainNodes}
        entityPrefix="node"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        reloadContentCount={reloadAttackChainNodeCount}
        topBarButtons={(
          <AttackChainNodesListButtons
            availableButtons={availableButtons}
            setViewMode={setViewMode}
            onImportedAttackChainNodes={() => setReloadAttackChainNodeCount(prev => prev + 1)}
          />
        )}
        contextId={contextId}
      />
      {viewModeContext === 'chain' && (
        <div style={{ marginBottom: 10 }}>
          <ChainedTimeline
            nodes={nodes}
            onUpdateAttackChainNode={massUpdateAttackChainNode}
            onTimelineClick={openCreateAttackChainNodeDrawer}
            onSelectedAttackChainNode={(node) => {
              const injectContract = node?.node_injector_contract.convertedContent;
              if (injectContract) {
                setSelectedAttackChainNodeId(node?.node_id);
              }
            }}
            onCreate={onCreate}
            onUpdate={onUpdate}
            onDelete={onDelete}
            dynamicContracts={dynamicContracts}
          />
          <div className="clearfix" />
        </div>
      )}
      {viewModeContext === 'list' && (
        <List>
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            style={{ paddingTop: 0 }}
            secondaryAction={<>&nbsp;</>}
          >
            <ListItemIcon style={{ minWidth: 40 }}>
              <Checkbox
                edge="start"
                checked={selectAll}
                disableRipple
                onChange={handleToggleSelectAll}
                disabled={typeof handleToggleSelectAll !== 'function'}
              />
            </ListItemIcon>

            <ListItemIcon />
            <ListItemText
              primary={(
                <SortHeadersComponentV2
                  headers={headers}
                  inlineStylesHeaders={inlineStyles}
                  sortHelpers={queryableHelpers.sortHelpers}
                />
              )}
            />
          </ListItem>
          {loading
            ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
            : nodes.map((node: AttackChainNodeOutputType, index) => {
                const injectContract = node.node_injector_contract?.convertedContent;
                return (
                  <ListItem
                    key={node.node_id}
                    divider
                    secondaryAction={(
                      <AttackChainNodePopover
                        node={node}
                        canBeTested
                        setSelectedAttackChainNodeId={setSelectedAttackChainNodeId}
                        isDisabled={!injectContract}
                        onCreate={onCreate}
                        onUpdate={onUpdate}
                        onDelete={onDelete}
                      />
                    )}
                    disablePadding
                  >
                    <ListItemButton
                      onClick={() => {
                        if (injectContract) {
                          setSelectedAttackChainNodeId(node.node_id);
                        }
                      }}
                    >

                      <ListItemIcon
                        style={{ minWidth: 40 }}
                        onClick={event => (event.shiftKey
                          ? onRowShiftClick(index, node, event)
                          : onToggleEntity(node, event))}
                      >
                        <Checkbox
                          edge="start"
                          checked={
                            (selectAll && !(node.node_id
                              in (deSelectedElements || {})))
                              || node.node_id in (selectedElements || {})
                          }
                          disableRipple
                        />
                      </ListItemIcon>
                      <ListItemIcon style={{ paddingTop: 5 }}>
                        <AttackChainNodeIcon
                          isPayload={isNotEmptyField(node.node_injector_contract?.injector_contract_payload)}
                          type={
                            node.node_injector_contract?.injector_contract_payload
                              ? node.node_injector_contract?.injector_contract_payload?.payload_collector_type
                              || node.node_injector_contract?.injector_contract_payload?.payload_type
                              : node.node_type
                          }
                          disabled={!injectContract || !node.node_enabled}
                        />
                      </ListItemIcon>
                      <ListItemText
                        primary={(
                          <div className={(!injectContract
                            || !node.node_enabled) ? classes.disabled : ''}
                          >
                            <div className={classes.bodyItems}>
                              {headers.map(header => (
                                <div
                                  key={header.field}
                                  className={classes.bodyItem}
                                  style={inlineStyles[header.field]}
                                >
                                  {header.value(node, injectContract)}
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                );
              })}
        </List>
      )}
      <>
        {selectedAttackChainNodeId !== null
          && (
            <UpdateAttackChainNode
              open
              handleClose={() => setSelectedAttackChainNodeId(null)}
              onUpdateAttackChainNode={onUpdateAttackChainNode}
              massUpdateAttackChainNode={massUpdateAttackChainNode}
              injectId={selectedAttackChainNodeId}
              nodes={nodes}
              variablesFromAttackChainRunOrAttackChain={variables}
              uriVariable={uriVariable}
            />
          )}
        <>
          {permissions.canManage && (
            <ButtonCreate onClick={() => {
              setOpenCreateDrawer(true);
              setPresetAttackChainNodeDuration(0);
            }}
            />
          )}

          {
            numberOfSelectedElements > 0 && (
              <ToolBar
                numberOfSelectedElements={numberOfSelectedElements}
                totalNumberOfElements={queryableHelpers.paginationHelpers.getTotalElements()}
                selectedElements={selectedElements}
                deSelectedElements={deSelectedElements}
                selectAll={selectAll}
                handleClearSelectedElements={handleClearSelectedElements}
                teamsFromAttackChainRunOrAttackChain={teams}
                id={contextId}
                handleUpdate={massUpdateAttackChainNodes}
                handleBulkDelete={bulkDeleteAttackChainNodes}
                handleBulkTest={massTestAttackChainNodes}
                handleExport={handleExport}
                canManage={permissions.canManage}
              />
            )
          }
          {openCreateDrawer
            && (
              <CreateAttackChainNode
                title={t('Create a new node')}
                open
                handleClose={() => setOpenCreateDrawer(false)}
                onCreateAttackChainNode={onCreateAttackChainNode}
                onCreateAttackChainNodes={onCreateAttackChainNodes}
                presetAttackChainNodeDuration={presetAttackChainNodeDuration}
                uriVariable={uriVariable}
                variablesFromAttackChainRunOrAttackChain={variables}
              />
            )}
        </>

      </>
    </>
  );
};

export default AttackChainNodes;
