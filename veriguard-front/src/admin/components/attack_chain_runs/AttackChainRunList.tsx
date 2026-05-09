import { HubOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type FunctionComponent, type ReactNode, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchAttackChainRunsGlobalScores } from '../../../actions/attack_chain_runs/attack_chain_run-action';
import { type QueryableHelpers } from '../../../components/common/queryable/QueryableHelpers';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { type Header } from '../../../components/common/SortHeadersList';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import ItemTargets from '../../../components/ItemTargets';
import Loader from '../../../components/Loader';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { type AttackChainRunsGlobalScoresOutput, type AttackChainRunSimple, type ExpectationResultsByType } from '../../../utils/api-types';
import AtomicTestingResult from '../atomic_testings/atomic_testing/AtomicTestingResult';
import AttackChainRunStatus from './attack_chain_run/AttackChainRunStatus';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const getInlineStyles = (variant: string): Record<string, CSSProperties> => ({
  attack_chain_run_name: { width: variant === 'reduced-view' ? '15%' : '15%' },
  attack_chain_run_start_date: { width: variant === 'reduced-view' ? '12%' : '13%' },
  attack_chain_run_status: { width: variant === 'reduced-view' ? '12%' : '10%' },
  attack_chain_run_targets: {
    width: variant === 'reduced-view' ? '15%' : '17%',
    cursor: 'default',
  },
  attack_chain_run_global_score: {
    width: variant === 'reduced-view' ? '18%' : '12%',
    cursor: 'default',
  },
  attack_chain_run_tags: {
    width: variant === 'reduced-view' ? '12%' : '17%',
    cursor: 'default',
  },
  attack_chain_run_updated_at: { width: variant === 'reduced-view' ? '12%' : '13%' },
});

function getGlobalScoreComponent(
  attack_chain_run: AttackChainRunSimple,
) {
  return (<AtomicTestingResult expectations={attack_chain_run.attack_chain_run_global_score} />);
}

function getGlobalScoreComponentAsync(
  attack_chain_run: AttackChainRunSimple,
  loadingGlobalScores: boolean,
  globalScores: Record<string, ExpectationResultsByType[]> | undefined,
) {
  return (
    <>
      {(loadingGlobalScores) && <Loader variant="inElement" size="xs" />}
      {(!loadingGlobalScores && globalScores) && <AtomicTestingResult expectations={globalScores[attack_chain_run.attack_chain_run_id]} />}
    </>
  );
}

interface Props {
  attack_chain_runs: AttackChainRunSimple[];
  queryableHelpers?: QueryableHelpers;
  hasHeader?: boolean;
  variant?: string;
  secondaryAction?: (attack_chain_run: AttackChainRunSimple) => ReactNode;
  loading: boolean;
  isGlobalScoreAsync?: boolean;
}

const SimulationList: FunctionComponent<Props> = ({
  attack_chain_runs = [],
  queryableHelpers,
  hasHeader = true,
  variant = 'list',
  secondaryAction,
  loading,
  isGlobalScoreAsync = false,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const inlineStyles = getInlineStyles(variant);
  const { nsdt, vnsdt } = useFormatter();

  const [loadingGlobalScores, setLoadingGlobalScores] = useState(true);
  const [globalScores, setGlobalScores] = useState<Record<string, ExpectationResultsByType[]>>();
  const fetchGlobalScores = (exerciseIds: string[]) => {
    setLoadingGlobalScores(true);
    fetchAttackChainRunsGlobalScores({ attack_chain_run_ids: exerciseIds })
      .then((result: { data: AttackChainRunsGlobalScoresOutput }) => setGlobalScores(result.data.global_scores_by_attack_chain_run_ids))
      .finally(() => setLoadingGlobalScores(false));
  };

  useEffect(() => {
    if (attack_chain_runs.length > 0) {
      fetchGlobalScores(attack_chain_runs.map(attack_chain_run => attack_chain_run.attack_chain_run_id));
    }
  }, [attack_chain_runs]);

  // Headers
  const headers: Header[] = [
    {
      field: 'attack_chain_run_name',
      label: 'Name',
      isSortable: true,
      value: (attack_chain_run: AttackChainRunSimple) => <>{attack_chain_run.attack_chain_run_name}</>,
    },
    {
      field: 'attack_chain_run_start_date',
      label: 'Start date',
      isSortable: true,
      value: (attack_chain_run: AttackChainRunSimple) => {
        if (!attack_chain_run.attack_chain_run_start_date) {
          return '-';
        }
        return <>{(variant === 'reduced-view' ? vnsdt(attack_chain_run.attack_chain_run_start_date) : nsdt(attack_chain_run.attack_chain_run_start_date))}</>;
      },
    },
    {
      field: 'attack_chain_run_status',
      label: 'Status',
      isSortable: true,
      value: (attack_chain_run: AttackChainRunSimple) => <AttackChainRunStatus variant="list" exerciseStartDate={attack_chain_run.attack_chain_run_start_date} exerciseStatus={attack_chain_run.attack_chain_run_status} />,
    },
    {
      field: 'attack_chain_run_targets',
      label: 'Target',
      isSortable: false,
      value: (attack_chain_run: AttackChainRunSimple) => <ItemTargets variant={variant} targets={attack_chain_run.attack_chain_run_targets} />,
    },
    {
      field: 'attack_chain_run_global_score',
      label: 'Global score',
      isSortable: false,
      value: (attack_chain_run: AttackChainRunSimple) => (isGlobalScoreAsync
        ? getGlobalScoreComponentAsync(attack_chain_run, loadingGlobalScores, globalScores)
        : getGlobalScoreComponent(attack_chain_run)),
    },
    {
      field: 'attack_chain_run_tags',
      label: 'Tags',
      isSortable: false,
      value: (attack_chain_run: AttackChainRunSimple) => <ItemTags variant={variant} tags={attack_chain_run.attack_chain_run_tags} />,
    },
    {
      field: 'attack_chain_run_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (attack_chain_run: AttackChainRunSimple) => {
        if (!attack_chain_run.attack_chain_run_updated_at) {
          return '-';
        }
        return <>{(variant === 'reduced-view' ? vnsdt(attack_chain_run.attack_chain_run_updated_at) : nsdt(attack_chain_run.attack_chain_run_updated_at))}</>;
      },
    },
  ];

  return (
    <List>
      {hasHeader && queryableHelpers
        && (
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
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
        )}
      {
        loading
          ? <PaginatedListLoader Icon={HubOutlined} headers={headers} headerStyles={inlineStyles} />
          : attack_chain_runs.map((attack_chain_run: AttackChainRunSimple) => (
              <ListItem
                key={attack_chain_run.attack_chain_run_id}
                secondaryAction={secondaryAction && secondaryAction(attack_chain_run)}
                disablePadding
                divider
              >
                <ListItemButton
                  classes={{ root: classes.item }}
                  component={Link}
                  to={`/admin/attack_chain_runs/${attack_chain_run.attack_chain_run_id}`}
                >
                  <ListItemIcon>
                    <HubOutlined color="primary" />
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
                            {header.value?.(attack_chain_run)}
                          </div>
                        ))}
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            ))
      }
    </List>
  );
};

export default SimulationList;
