import { DescriptionOutlined, HelpOutlineOutlined, RowingOutlined } from '@mui/icons-material';
import { Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemSecondaryAction, ListItemText, Tooltip } from '@mui/material';
import { useEffect, useState } from 'react';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchAttackChainsById } from '../../../../actions/attack_chains/attack_chain-actions';
import { fetchAttackChainRunsById } from '../../../../actions/AttackChainRun';
import { searchDocuments } from '../../../../actions/Document';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponent from '../../../../components/common/pagination/PaginationComponent';
import SortHeadersComponent from '../../../../components/common/pagination/SortHeadersComponent';
import { initSorting } from '../../../../components/common/queryable/Page';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { useHelper } from '../../../../store';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CreateDocument from './CreateDocument';
import DocumentPopover from './DocumentPopover';
import DocumentType from './DocumentType';

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
  attack_chain_run: {
    fontSize: 12,
    height: 20,
    float: 'left',
    marginRight: 7,
    width: 120,
  },
  attack_chain: {
    fontSize: 12,
    height: 20,
    float: 'left',
    marginRight: 7,
    width: 120,
  },
}));

const inlineStyles = {
  document_name: { width: '20%' },
  document_description: { width: '15%' },
  document_attack_chain_runs: {
    width: '20%',
    cursor: 'default',
  },
  document_attack_chains: {
    width: '20%',
    cursor: 'default',
  },
  document_type: { width: '12%' },
  document_tags: { width: '13%' },
};

const Documents = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { t } = useFormatter();
  const { exercisesMap, scenariosMap } = useHelper(helper => ({
    exercisesMap: helper.getAttackChainRunsMap(),
    scenariosMap: helper.getAttackChainsMap(),
  }));

  // Headers
  const headers = [
    {
      field: 'document_name',
      label: 'Name',
      isSortable: true,
    },
    {
      field: 'document_description',
      label: 'Description',
      isSortable: true,
    },
    {
      field: 'document_attack_chain_runs',
      label: 'Simulations',
      isSortable: false,
    },
    {
      field: 'document_attack_chains',
      label: 'AttackChains',
      isSortable: false,
    },
    {
      field: 'document_type',
      label: 'Type',
      isSortable: true,
    },
    {
      field: 'document_tags',
      label: 'Tags',
      isSortable: true,
    },
  ];

  const [documents, setDocuments] = useState([]);
  const [searchPaginationInput, setSearchPaginationInput] = useState({ sorts: initSorting('document_name') });
  const [loadingDocuments, setLoadingDocuments] = useState(true);
  const [loadingAttackChainRunsAndAttackChains, setLoadingAttackChainRunsAndAttackChains] = useState(false);

  useEffect(() => {
    if (documents.length === 0) return;

    setLoadingAttackChainRunsAndAttackChains(true);

    const exerciseIds = new Set(
      documents.flatMap(d => d.document_attack_chain_runs?.slice(0, 3) ?? []),
    );

    const scenarioIds = new Set(
      documents.flatMap(d => d.document_attack_chains?.slice(0, 3) ?? []),
    );

    const promises = [];

    if (exerciseIds.size > 0) {
      promises.push(
        dispatch(fetchAttackChainRunsById({ attack_chain_run_ids: [...exerciseIds] })),
      );
    }

    if (scenarioIds.size > 0) {
      promises.push(
        dispatch(fetchAttackChainsById({ attack_chain_ids: [...scenarioIds] })),
      );
    }

    Promise.all(promises).finally(() => {
      setLoadingAttackChainRunsAndAttackChains(false);
    });
  }, [documents, dispatch]);

  /**
   * Callback when a new document has been created or an previous one updated with a new version
   * @param result the result of the call
   */
  const handleCreateDocuments = (result) => {
    // If the documents was already in the list displayed, we don't add it to the list
    if (documents.find(element => element.document_id === result.document_id) === undefined) {
      setDocuments([result, ...documents]);
    }
  };

  // Export
  const exportProps = {
    exportType: 'tags',
    exportKeys: [
      'document_name',
      'document_description',
      'document_type',
    ],
    exportData: documents,
    exportFileName: `${t('Documents')}.csv`,
  };

  const searchDocumentsToLoad = (input) => {
    setLoadingDocuments(true);
    return searchDocuments(input).finally(() => setLoadingDocuments(false));
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Components') }, {
          label: t('Documents'),
          current: true,
        }]}
      />
      <PaginationComponent
        fetch={searchDocumentsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setDocuments}
        exportProps={exportProps}
      />
      <List>
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
              <SortHeadersComponent
                headers={headers}
                inlineStylesHeaders={inlineStyles}
                searchPaginationInput={searchPaginationInput}
                setSearchPaginationInput={setSearchPaginationInput}
              />
            )}
          />
          <ListItemSecondaryAction> &nbsp; </ListItemSecondaryAction>
        </ListItem>
        {(loadingDocuments || loadingAttackChainRunsAndAttackChains)
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
          : documents.map((document) => {
              const displayedAttackChainRuns = document.document_attack_chain_runs?.slice(0, 3) ?? [];
              const displayedAttackChains = document.document_attack_chains?.slice(0, 3) ?? [];
              return (
                <ListItem
                  key={document.document_id}
                  divider
                  secondaryAction={(
                    <DocumentPopover
                      document={document}
                      onUpdate={result => setDocuments(documents.map(d => (d.document_id !== result.document_id ? d : result)))}
                      onDelete={result => setDocuments(documents.filter(d => (d.document_id !== result)))}
                      scenariosAndAttackChainRunsFetched
                      inList
                    />
                  )}
                  disablePadding
                >
                  <ListItemButton
                    classes={{ root: classes.item }}
                    component="a"
                    href={`/api/documents/${document.document_id}/file`}
                  >
                    <ListItemIcon>
                      <DescriptionOutlined color="primary" />
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div style={bodyItemsStyles.bodyItems}>
                          <div
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles.document_name,
                            }}
                          >
                            {document.document_name}
                          </div>
                          <div
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles.document_description,
                            }}
                          >
                            {document.document_description || '-'}
                          </div>
                          <div
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles.document_attack_chain_runs,
                            }}
                          >
                            {displayedAttackChainRuns && displayedAttackChainRuns.length > 0 ? (
                              displayedAttackChainRuns.map((e) => {
                                const attack_chain_run = exercisesMap[e];

                                if (!attack_chain_run) {
                                  return <span key={e}>-</span>;
                                }

                                return (
                                  <Tooltip key={attack_chain_run.attack_chain_run_id} title={attack_chain_run.attack_chain_run_name}>
                                    <Chip
                                      icon={<RowingOutlined style={{ fontSize: 12 }} />}
                                      classes={{ root: classes.attack_chain_run }}
                                      variant="outlined"
                                      label={attack_chain_run.attack_chain_run_name}
                                      clickable
                                      onClick={(event) => {
                                        event.stopPropagation();
                                        event.preventDefault();
                                        navigate(`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}`);
                                      }}
                                    />
                                  </Tooltip>
                                );
                              })
                            ) : (
                              <span>-</span>
                            )}
                          </div>
                          <div
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles.document_attack_chains,
                            }}
                          >
                            {displayedAttackChains && displayedAttackChains.length > 0 ? (
                              displayedAttackChains.map((e) => {
                                const attack_chain = scenariosMap[e];

                                if (!attack_chain) {
                                  return <span key={e}>-</span>;
                                }

                                return (
                                  <Tooltip key={attack_chain.attack_chain_id} title={attack_chain.attack_chain_name}>
                                    <Chip
                                      icon={<RowingOutlined style={{ fontSize: 12 }} />}
                                      classes={{ root: classes.attack_chain }}
                                      variant="outlined"
                                      label={attack_chain.attack_chain_name}
                                      clickable
                                      onClick={(event) => {
                                        event.stopPropagation();
                                        event.preventDefault();
                                        navigate(`/admin/attack_chains/${attack_chain.attack_chain_id}`);
                                      }}
                                    />
                                  </Tooltip>
                                );
                              })
                            ) : (
                              <span>-</span>
                            )}
                          </div>
                          <div
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles.document_type,
                            }}
                          >
                            <DocumentType
                              type={document.document_type}
                              variant="list"
                            />
                          </div>
                          <div
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles.document_tags,
                            }}
                          >
                            <ItemTags variant="list" tags={document.document_tags} />
                          </div>
                        </div>
                      )}
                    />
                  </ListItemButton>
                </ListItem>
              );
            },
            )}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.DOCUMENTS}>
        <CreateDocument
          onCreate={handleCreateDocuments}
        />
      </Can>
    </>
  );
};

export default Documents;
