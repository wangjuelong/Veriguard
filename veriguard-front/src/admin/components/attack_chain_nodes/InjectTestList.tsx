import { HelpOutlineOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText, useTheme } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import PaginationComponent from '../../../components/common/pagination/PaginationComponent';
import SortHeadersComponent from '../../../components/common/pagination/SortHeadersComponent';
import { type Page } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import ItemStatus from '../../../components/ItemStatus';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { type AttackChainNodeTestStatusOutput, type SearchPaginationInput } from '../../../utils/api-types';
import AttackChainNodeIcon from '../common/attack_chain_nodes/AttackChainNodeIcon';
import { AttackChainNodeTestContext, PermissionsContext } from '../common/Context';
import AttackChainNodeTestDetail from './AttackChainNodeTestDetail';
import AttackChainNodeTestPopover from './AttackChainNodeTestPopover';
import AttackChainNodeTestReplayAll from './AttackChainNodeTestReplayAll';

const useStyles = makeStyles()(() => ({
  bodyItems: {
    display: 'flex',
    alignItems: 'center',
  },
  bodyItem: {
    height: 20,
    fontSize: 13,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    paddingRight: 10,
  },
  itemHead: {
    paddingLeft: 10,
    marginBottom: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  node_title: {
    width: '40%',
    cursor: 'default',
  },
  tracking_sent_date: { width: '40%' },
  status_name: { width: '20%' },
};

interface Props { statusId: string | undefined }

const AttackChainNodeTestList: FunctionComponent<Props> = ({ statusId }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);

  const [selectedAttackChainNodeTestStatus, setSelectedAttackChainNodeTestStatus] = useState<AttackChainNodeTestStatusOutput | null>(null);

  const {
    contextId,
    searchAttackChainNodeTests,
    fetchAttackChainNodeTestStatus,
  } = useContext(AttackChainNodeTestContext);

  // Fetching test
  useEffect(() => {
    if (statusId !== null && statusId !== undefined && fetchAttackChainNodeTestStatus) {
      fetchAttackChainNodeTestStatus(statusId).then((result: { data: AttackChainNodeTestStatusOutput }) => {
        setSelectedAttackChainNodeTestStatus(result.data);
      });
    }
  }, [statusId]);

  // Headers
  const headers = [
    {
      field: 'node_title',
      label: 'AttackChainNode title',
      isSortable: true,
      value: (test: AttackChainNodeTestStatusOutput) => test.node_title,
    },
    {
      field: 'tracking_sent_date',
      label: 'Test execution time',
      isSortable: true,
      value: (test: AttackChainNodeTestStatusOutput) => fldt(test.tracking_sent_date),
    },
    {
      field: 'status_name',
      label: 'Test status',
      isSortable: true,
      value: (test: AttackChainNodeTestStatusOutput) => {
        return (<ItemStatus isAttackChainNode status={test.status_name} label={t(test.status_name || '-')} variant="inList" />);
      },
    },
  ];

  // Filter and sort hook
  const [tests, setTests] = useState<AttackChainNodeTestStatusOutput[] | null>([]);
  const [searchPaginationInput, setSearchPaginationInput] = useState<SearchPaginationInput>(buildSearchPagination({}));

  const [loading, setLoading] = useState<boolean>(true);
  const searchAttackChainNodeTestsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    try {
      if (searchAttackChainNodeTests) {
        return searchAttackChainNodeTests(contextId, input);
      } else {
        return new Promise<{ data: Page<AttackChainNodeTestStatusOutput> }>(() => {
        });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <PaginationComponent
        fetch={searchAttackChainNodeTestsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setTests}
      >
        <AttackChainNodeTestReplayAll
          searchPaginationInput={searchPaginationInput}
          injectIds={tests?.map((test: AttackChainNodeTestStatusOutput) => test.node_id!)}
          onTest={result => setTests(result)}
        />
      </PaginationComponent>
      <List style={{ marginTop: theme.spacing(2) }} disablePadding>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
          secondaryAction={<>&nbsp;</>}
        >
          <ListItemIcon />
          <ListItemText
            primary={(
              <SortHeadersComponent
                headers={headers}
                inlineStylesHeaders={inlineStyles}
                searchPaginationInput={searchPaginationInput}
                setSearchPaginationInput={setSearchPaginationInput}
                defaultSortAsc
              />
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
          : tests?.map((test) => {
              return (
                <ListItem
                  key={test.status_id}
                  divider
                  secondaryAction={(
                    permissions.canManage && (
                      <AttackChainNodeTestPopover
                        injectTest={test}
                        onTest={result =>
                          setTests(tests?.map(existing => existing.status_id !== result.status_id ? existing : result))}
                        onDelete={injectStatusId => setTests(tests.filter(existing => (existing.status_id !== injectStatusId)))}
                      />
                    )

                  )}
                  disablePadding
                >
                  <ListItemButton
                    classes={{ root: classes.item }}
                    onClick={() => setSelectedAttackChainNodeTestStatus(test)}
                    selected={test.status_id === selectedAttackChainNodeTestStatus?.status_id}
                  >
                    <ListItemIcon>
                      <AttackChainNodeIcon
                        type={test.node_type}
                        variant="list"
                      />
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div className={classes.bodyItems}>
                          {headers.map(header => (
                            <div
                              key={header.field}
                              className={classes.bodyItem}
                              style={inlineStyles[header.field]}
                            >
                              {header.value(test)}
                            </div>
                          ))}
                        </div>
                      )}
                    />
                  </ListItemButton>
                </ListItem>
              );
            })}
        {!tests ? (<Empty message={t('No data available')} />) : null}
      </List>
      {
        selectedAttackChainNodeTestStatus !== null
        && <AttackChainNodeTestDetail open handleClose={() => setSelectedAttackChainNodeTestStatus(null)} injectTestStatus={selectedAttackChainNodeTestStatus} />
      }
    </>
  );
};

export default AttackChainNodeTestList;
