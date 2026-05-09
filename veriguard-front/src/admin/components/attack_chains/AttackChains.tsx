import { MovieFilterOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText, ToggleButtonGroup } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchAttackChains } from '../../../actions/attack_chains/attack_chain-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ExportButton from '../../../components/common/ExportButton';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import ItemCategory from '../../../components/ItemCategory';
import ItemSeverity from '../../../components/ItemSeverity';
import ItemTags from '../../../components/ItemTags';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import PlatformIcon from '../../../components/PlatformIcon';
import { type AttackChain, type SearchPaginationInput } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import AttackChainPopover from './attack_chain/AttackChainPopover';
import AttackChainStatus from './attack_chain/AttackChainStatus';
import AttackChainCreation from './AttackChainCreation';
import ImportUploaderAttackChain from './ImportUploaderAttackChain';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  attack_chain_name: { width: '25%' },
  attack_chain_severity: { width: '8%' },
  attack_chain_category: { width: '12%' },
  attack_chain_recurrence: { width: '12%' },
  attack_chain_platforms: { width: '10%' },
  attack_chain_tags: { width: '18%' },
  attack_chain_updated_at: { width: '10%' },
};

const AttackChains = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  const [loading, setLoading] = useState<boolean>(true);

  // Headers
  const headers = useMemo(() => [
    {
      field: 'attack_chain_name',
      label: 'Name',
      isSortable: true,
      value: (attack_chain: AttackChain) => attack_chain.attack_chain_name,
    },
    {
      field: 'attack_chain_severity',
      label: 'Severity',
      isSortable: true,
      value: (attack_chain: AttackChain) => (
        <ItemSeverity
          label={t(attack_chain.attack_chain_severity ?? 'Unknown')}
          severity={attack_chain.attack_chain_severity ?? 'Unknown'}
          variant="inList"
        />
      ),
    },
    {
      field: 'attack_chain_category',
      label: 'Category',
      isSortable: true,
      value: (attack_chain: AttackChain) => (
        <ItemCategory
          category={attack_chain.attack_chain_category ?? 'Unknown'}
          label={t(attack_chain.attack_chain_category ?? 'Unknown')}
          size="medium"
        />
      ),
    },
    {
      field: 'attack_chain_recurrence',
      label: 'Status',
      isSortable: false,
      value: (attack_chain: AttackChain) => <AttackChainStatus attack_chain={attack_chain} variant="list" />,
    },
    {
      field: 'attack_chain_platforms',
      label: 'Platforms',
      isSortable: false,
      value: (attack_chain: AttackChain) => {
        const platforms = attack_chain.attack_chain_platforms ?? [];
        if (platforms.length === 0) {
          return <PlatformIcon platform={t('No node in this attack_chain')} tooltip width={25} />;
        }
        return (
          <>
            {platforms.map(
              (platform: string) => <PlatformIcon key={platform} platform={platform} tooltip width={20} marginRight={theme.spacing(2)} />,
            )}
          </>
        );
      },
    },
    {
      field: 'attack_chain_tags',
      label: 'Tags',
      isSortable: false,
      value: (attack_chain: AttackChain) => <ItemTags tags={attack_chain.attack_chain_tags} variant="list" />,
    },
    {
      field: 'attack_chain_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (attack_chain: AttackChain) => nsdt(attack_chain.attack_chain_updated_at),
    },
  ], []);

  const [attack_chains, setAttackChains] = useState<AttackChain[]>([]);

  // Filters
  const availableFilterNames = [
    'attack_chain_category',
    'attack_chain_kill_chain_phases',
    'attack_chain_name',
    'attack_chain_platforms',
    'attack_chain_recurrence',
    'attack_chain_severity',
    'attack_chain_tags',
    'attack_chain_updated_at',
  ];

  const { queryableHelpers, searchPaginationInput, setSearchPaginationInput } = useQueryableWithLocalStorage('attack_chains', buildSearchPagination({ sorts: initSorting('attack_chain_updated_at', 'DESC') }));

  // Export
  const exportProps = {
    exportType: 'attack_chain',
    exportKeys: [
      'attack_chain_name',
      'attack_chain_severity',
      'attack_chain_category',
      'attack_chain_main_focus',
      'attack_chain_platforms',
      'attack_chain_tags',
      'attack_chain_updated_at',
    ],
    exportData: attack_chains,
    exportFileName: `${t('AttackChains')}.csv`,
  };

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchAttackChains(input).finally(() => setLoading(false));
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('AttackChains'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setAttackChains}
        entityPrefix="attack_chain"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1}>
            <ToggleButtonGroup value="fake" exclusive>
              <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
                <ImportUploaderAttackChain />
              </Can>
            </ToggleButtonGroup>
          </Box>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          style={{ paddingTop: 0 }}
          secondaryAction={<>&nbsp;</>}
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
            ? <PaginatedListLoader Icon={MovieFilterOutlined} headers={headers} headerStyles={inlineStyles} />
            : attack_chains.map((attack_chain: AttackChain) => {
                return (
                  <ListItem
                    key={attack_chain.attack_chain_id}
                    divider
                    secondaryAction={(
                      <AttackChainPopover
                        attack_chain={attack_chain}
                        actions={['Duplicate', 'Export', 'Delete']}
                        onDelete={(result) => {
                          setAttackChains(attack_chains.filter(e => (e.attack_chain_id !== result)));
                          setSearchPaginationInput(prev => ({
                            ...prev,
                            size: prev.size - 1,
                          }));
                        }}
                        inList
                      />
                    )}
                    disablePadding
                  >
                    <ListItemButton
                      component={Link}
                      to={`/admin/attack_chains/${attack_chain.attack_chain_id}`}
                      classes={{ root: classes.item }}
                    >
                      <ListItemIcon>
                        <MovieFilterOutlined color="primary" />
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
                                {header.value(attack_chain)}
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
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
        <AttackChainCreation />
      </Can>
    </>
  );
};

export default AttackChains;
