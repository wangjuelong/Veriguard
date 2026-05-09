import { CloudUploadOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText, ToggleButton, Tooltip } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { importAtomicTesting } from '../../../actions/atomic_testings/atomic-testing-actions';
import { type Page } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { type QueryableHelpers } from '../../../components/common/queryable/QueryableHelpers';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { type Header } from '../../../components/common/SortHeadersList';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import ItemDomains from '../../../components/ItemDomains';
import ItemStatus from '../../../components/ItemStatus';
import ItemTargets from '../../../components/ItemTargets';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { type AttackChainNodeResultOutput, type SearchPaginationInput } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isNotEmptyField } from '../../../utils/utils';
import AttackChainNodeIcon from '../common/attack_chain_nodes/AttackChainNodeIcon';
import AttackChainNodeImportJsonDialog from '../common/attack_chain_nodes/AttackChainNodeImportJsonDialog';
import InjectorContract from '../common/attack_chain_nodes/InjectorContract';
import AtomicTestingPopover from './atomic_testing/AtomicTestingPopover';
import AtomicTestingResult from './atomic_testing/AtomicTestingResult';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  'node_type': { width: '10%' },
  'node_title': { width: '15%' },
  'node_contract_domains': { width: '15%' },
  'node_status.tracking_sent_date': { width: '15%' },
  'node_status': { width: '10%' },
  'node_targets': { width: '15%' },
  'node_expectations': { width: '10%' },
  'node_updated_at': { width: '10%' },
};

interface Props {
  showActions?: boolean;
  fetchAttackChainNodes: (input: SearchPaginationInput) => Promise<{ data: Page<AttackChainNodeResultOutput> }>;
  goTo: (injectId: string) => string;
  queryableHelpers: QueryableHelpers;
  searchPaginationInput: SearchPaginationInput;
  availableFilterNames?: string[];
  contextId?: string;
}

const AttackChainNodeResultList: FunctionComponent<Props> = ({
  showActions,
  fetchAttackChainNodes,
  goTo,
  queryableHelpers,
  searchPaginationInput,
  contextId,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, fldt, tPick, nsdt } = useFormatter();

  const [loading, setLoading] = useState<boolean>(true);
  const [openJsonImportDialog, setOpenJsonImportDialog] = useState(false);
  const [reloadCount, setReloadCount] = useState(0);

  // Filter and sort hook
  const availableFilterNames = [
    'node_attack_patterns',
    'node_kill_chain_phases',
    'node_tags',
    'node_title',
    'node_type',
    'node_updated_at',
    'node_assets',
    'node_asset_groups',
    'node_teams',
    'node_contract_domains',
  ];
  const [nodes, setAttackChainNodes] = useState<AttackChainNodeResultOutput[]>([]);

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'node_type',
      label: 'Type',
      isSortable: false,
      value: (injectResultOutput: AttackChainNodeResultOutput) => {
        if (injectResultOutput.node_injector_contract) {
          return (
            <InjectorContract variant="list" label={tPick(injectResultOutput.node_injector_contract?.injector_contract_labels)} />
          );
        }
        return <InjectorContract variant="list" label={t('Deleted')} deleted={true} />;
      },
    },
    {
      field: 'node_title',
      label: 'Name',
      isSortable: true,
      value: (injectResultOutput: AttackChainNodeResultOutput) => {
        return <>{injectResultOutput.node_title}</>;
      },
    },
    {
      field: 'node_contract_domains',
      label: t('Domains'),
      isSortable: true,
      value: (injectResultOutput: AttackChainNodeResultOutput) => {
        return injectResultOutput.node_contract_domains?.length
          ? (
              <ItemDomains domains={injectResultOutput.node_contract_domains} variant="reduced-view" />
            )
          : <></>;
      },
    },
    {
      field: 'node_status.tracking_sent_date',
      label: 'Execution Date',
      isSortable: false,
      value: (injectResultOutput: AttackChainNodeResultOutput) => {
        const trackingDate = injectResultOutput.node_status?.tracking_sent_date;
        return <>{trackingDate ? fldt(trackingDate) : '-'}</>;
      },
    },
    {
      field: 'node_status',
      label: 'Execution status',
      isSortable: false,
      value: (injectResultOutput: AttackChainNodeResultOutput) => {
        return (<ItemStatus status={injectResultOutput.node_status?.status_name} label={t(injectResultOutput.node_status?.status_name || '-')} variant="inList" />);
      },
    },
    {
      field: 'node_targets',
      label: 'Target',
      isSortable: false,
      value: (injectResultOutput: AttackChainNodeResultOutput) => {
        return (<ItemTargets targets={injectResultOutput.node_targets} />);
      },
    },
    {
      field: 'node_expectations',
      label: 'Global score',
      isSortable: false,
      value: (injectResultOutput: AttackChainNodeResultOutput) => {
        return (
          <AtomicTestingResult expectations={injectResultOutput.node_expectation_results} />
        );
      },
    },
    {
      field: 'node_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (injectResultOutput: AttackChainNodeResultOutput) => {
        return <>{nsdt(injectResultOutput.node_updated_at)}</>;
      },
    },
  ], []);

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return fetchAttackChainNodes(input).finally(() => setLoading(false));
  };

  const handleOpenJsonImportDialog = () => {
    setOpenJsonImportDialog(true);
  };
  const handleCloseJsonImportDialog = () => {
    setOpenJsonImportDialog(false);
  };
  const handleSubmitJsonImportFile = (values: { file: File }) => {
    importAtomicTesting(values.file).then(() => {
      handleCloseJsonImportDialog();
      setReloadCount(prev => prev + 1);
    });
  };

  return (
    <>
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setAttackChainNodes}
        entityPrefix="node"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        contextId={contextId}
        reloadContentCount={reloadCount}
        topBarButtons={showActions ? (
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
            <Tooltip title={t('node_import_json_action')}>
              <ToggleButton
                value="import"
                aria-label="import"
                size="small"
                onClick={handleOpenJsonImportDialog}
              >
                <CloudUploadOutlined
                  color="primary"
                  fontSize="small"
                />
              </ToggleButton>
            </Tooltip>
          </Can>
        ) : null}
      />
      <AttackChainNodeImportJsonDialog open={openJsonImportDialog} handleClose={handleCloseJsonImportDialog} handleSubmit={handleSubmitJsonImportFile} />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
          secondaryAction={showActions ? <>&nbsp;</> : null}
        >
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
        {
          loading
            ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
            : nodes.map((injectResultOutput) => {
                return (
                  <ListItem
                    key={injectResultOutput.node_id}
                    divider
                    secondaryAction={showActions ? (
                      <AtomicTestingPopover
                        atomic={injectResultOutput}
                        actions={['Duplicate', 'Export', 'Delete']}
                        onDelete={result => setAttackChainNodes(nodes.filter(e => e.node_id !== result))}
                        inList
                      />
                    ) : null}
                    disablePadding
                  >
                    <ListItemButton
                      component={Link}
                      classes={{ root: classes.item }}
                      to={goTo(injectResultOutput.node_id)}
                    >
                      <ListItemIcon>
                        <AttackChainNodeIcon
                          isPayload={isNotEmptyField(injectResultOutput.node_injector_contract?.injector_contract_payload?.payload_id)}
                          type={
                            injectResultOutput.node_injector_contract?.injector_contract_payload?.payload_id
                              ? injectResultOutput.node_injector_contract.injector_contract_payload?.payload_collector_type
                              || injectResultOutput.node_injector_contract.injector_contract_payload?.payload_type
                              : injectResultOutput.node_type
                          }
                          variant="list"
                        />
                      </ListItemIcon>
                      <ListItemText
                        primary={(
                          <div style={bodyItemsStyles.bodyItems}>
                            {headers.map(header => (
                              <div
                                key={header.field}
                                style={{
                                  ...bodyItemsStyles.bodyItem,
                                  ...inlineStyles[header.field],
                                }}
                              >
                                {header.value?.(injectResultOutput)}
                              </div>
                            ))}
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                );
              })
        }
        {!nodes ? (<Empty message={t('No data available')} />) : null}
      </List>
    </>
  );
};

export default AttackChainNodeResultList;
