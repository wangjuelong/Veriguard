import { PlayArrowOutlined } from '@mui/icons-material';
import {
  Avatar,
  Button,
  Chip,
  GridLegacy,
  Paper,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type Dispatch, type SetStateAction, useContext, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AgentHelper } from '../../../../actions/agents/agent-helper';
import { type AttackChainNodeHelper } from '../../../../actions/attack_chain_nodes/node-helper';
import { type AttackChainRunsHelper } from '../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { searchAttackChainAttackChainRuns, searchAttackChainHealthcheks } from '../../../../actions/attack_chains/attack_chain-actions';
import { type AttackChainsHelper } from '../../../../actions/attack_chains/attack_chain-helper';
import { fetchAttackChainAttackChainNodes } from '../../../../actions/AttackChainNode';
import type { CollectorHelper } from '../../../../actions/collectors/collector-helper';
import type { LoggedHelper } from '../../../../actions/helper';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemCategory from '../../../../components/ItemCategory';
import ItemMainFocus from '../../../../components/ItemMainFocus';
import ItemSeverity from '../../../../components/ItemSeverity';
import ItemTags from '../../../../components/ItemTags';
import PlatformIcon from '../../../../components/PlatformIcon';
import TypeAffinityChip from '../../../../components/TypeAffinityChip';
import octiDark from '../../../../static/images/xtm/octi_dark.png';
import octiLight from '../../../../static/images/xtm/octi_light.png';
import { useHelper } from '../../../../store';
import {
  type Agent,
  type AttackChain as AttackChainType,
  type AttackChainNode,
  type AttackChainRunSimple, type HealthCheck, type KillChainPhase,
  type SearchPaginationInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isEmptyField } from '../../../../utils/utils';
import AttackChainRunPopover from '../../attack_chain_runs/attack_chain_run/AttackChainRunPopover';
import SimulationList from '../../attack_chain_runs/AttackChainRunList';
import Healthchecks from '../../common/healthchecks/Healthchecks';
import AttackChainDistributionByAttackChainRun from './AttackChainDistributionByAttackChainRun';

// Deprecated - https://mui.com/system/styles/basics/
// Do not use it for new code.
const useStyles = makeStyles()(theme => ({
  chip: {
    fontSize: 12,
    height: 25,
    margin: '0 7px 7px 0',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 180,
  },
  paper: { padding: theme.spacing(2) },
}));

const AttackChain = ({ setOpenInstantiateSimulationAndStart }: { setOpenInstantiateSimulationAndStart: Dispatch<SetStateAction<boolean>> }) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();
  const { scenarioId } = useParams() as { scenarioId: AttackChainType['attack_chain_id'] };
  const ability = useContext(AbilityContext);
  const dispatch = useAppDispatch();

  // Fetching data
  const {
    attack_chain,
    settings,
    nodes,
    collectors,
    agents,
  } = useHelper((helper: AttackChainsHelper & AttackChainRunsHelper & LoggedHelper & AttackChainNodeHelper & CollectorHelper & AgentHelper) => ({
    attack_chain: helper.getAttackChain(scenarioId),
    settings: helper.getPlatformSettings(),
    nodes: helper.getAttackChainAttackChainNodes(scenarioId),
    collectors: helper.getExistingCollectors(),
    agents: helper.getAgents(),
  }));
  const areAnyAttackChainRunsInAttackChain = attack_chain.attack_chain_runs?.length > 0;
  const sortByOrder = R.sortWith([R.ascend(R.prop('phase_order'))]);

  // Spy on modifications to reload healthchecks
  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);
  const agentsActive = useMemo(() => {
    const injectAssetIds: string[] = nodes.flatMap((node: AttackChainNode) => node.node_assets);
    return agents
      .filter((agent: Agent) => injectAssetIds.includes(agent.agent_asset))
      .map((agent: Agent) => agent.agent_active);
  }, [agents, nodes]);

  useDataLoader(() => {
    if (!nodes) {
      dispatch(fetchAttackChainAttackChainNodes(scenarioId));
    }
  });

  useEffect(() => {
    searchAttackChainHealthcheks(scenarioId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [
    settings?.smtp_service_available,
    settings?.imap_service_available,
    attack_chain,
    nodes,
    collectors.length,
    agentsActive,
  ]);

  // AttackChainRuns
  const [loadingAttackChainRuns, setLoadingAttackChainRuns] = useState(true);
  const [attack_chain_runs, setAttackChainRuns] = useState<AttackChainRunSimple[]>([]);
  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`attack_chain-${scenarioId}-attack_chain_runs`, buildSearchPagination({ sorts: initSorting('attack_chain_run_updated_at', 'DESC') }));
  const search = (scenarioId: AttackChainType['attack_chain_id'], input: SearchPaginationInput) => {
    setLoadingAttackChainRuns(true);
    return searchAttackChainAttackChainRuns(scenarioId, input).finally(() => {
      setLoadingAttackChainRuns(false);
    });
  };
  const secondaryAction = (attack_chain_run: AttackChainRunSimple) => (
    <AttackChainRunPopover
      // @ts-expect-error: should pass AttackChainRun model IF we have update as action
      attack_chain_run={attack_chain_run}
      actions={['Duplicate', 'Export', 'Delete']}
      onDelete={result => setAttackChainRuns(attack_chain_runs.filter(e => (e.attack_chain_run_id !== result)))}
      inList
    />
  );

  return (
    <div style={{ paddingBottom: theme.spacing(5) }}>
      {!!healthchecks?.length && (
        <Healthchecks
          healthchecks={healthchecks}
          scenarioId={scenarioId}
        />
      )}
      <div style={{
        display: 'grid',
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: '1fr 1fr',
      }}
      >
        <div style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '10px',
        }}
        >
          <Typography variant="h4" marginBottom={0}>{t('Information')}</Typography>
          <Button
            component={Link}
            to={attack_chain.attack_chain_external_url}
            target="_blank"
            size="small"
            variant="outlined"
            startIcon={(
              <Avatar
                style={{
                  width: 20,
                  height: 20,
                }}
                src={theme.palette.mode === 'dark' ? octiDark : octiLight}
                alt="OCTI"
              />
            )}
            disabled={isEmptyField(attack_chain.attack_chain_external_url)}
          >
            {t('Threat intelligence')}
          </Button>
        </div>
        <Typography variant="h4" style={{ alignContent: 'center' }}>{t('Latest 10 Finished Simulations')}</Typography>
        <Paper classes={{ root: classes.paper }} variant="outlined">
          <GridLegacy container spacing={3}>
            <GridLegacy item xs={12} style={{ paddingTop: 10 }}>
              <Typography
                variant="h3"
                gutterBottom
                style={{ marginTop: 20 }}
              >
                {t('Description')}
              </Typography>
              <ExpandableMarkdown
                source={attack_chain.attack_chain_description}
                limit={300}
              />
            </GridLegacy>
            <GridLegacy item xs={4} style={{ paddingTop: 10 }}>
              <Typography
                variant="h3"
                gutterBottom
                style={{ marginTop: 20 }}
              >
                {t('Severity')}
              </Typography>
              <ItemSeverity severity={attack_chain.attack_chain_severity} label={t(attack_chain.attack_chain_severity ?? 'Unknown')} />
            </GridLegacy>
            <GridLegacy item xs={4} style={{ paddingTop: 10 }}>
              <Typography
                variant="h3"
                gutterBottom
                style={{ marginTop: 20 }}
              >
                {t('Category')}
              </Typography>
              <ItemCategory category={attack_chain.attack_chain_category} label={t(attack_chain.attack_chain_category ?? 'Unknown')} />
            </GridLegacy>
            <GridLegacy item xs={4} style={{ paddingTop: 10 }}>
              <Typography
                variant="h3"
                gutterBottom
                style={{ marginTop: 20 }}
              >
                {t('Main Focus')}
              </Typography>
              <ItemMainFocus
                mainFocus={attack_chain.attack_chain_main_focus}
                label={t(attack_chain.attack_chain_main_focus ?? 'Unknown')}
              />
            </GridLegacy>
            <GridLegacy item xs={4} style={{ paddingTop: 10 }}>
              <Typography
                variant="h3"
                gutterBottom
                style={{ marginTop: 20 }}
              >
                {t('Tags')}
              </Typography>
              <ItemTags tags={attack_chain.attack_chain_tags} limit={10} />
            </GridLegacy>
            <GridLegacy item xs={4} style={{ paddingTop: 10 }}>
              <Typography
                variant="h3"
                gutterBottom
                style={{ marginTop: 20 }}
              >
                {t('Platforms')}
              </Typography>
              {(attack_chain.attack_chain_platforms ?? []).length === 0 ? (
                <PlatformIcon platform={t('No node in this attack_chain')} tooltip width={25} />
              ) : attack_chain.attack_chain_platforms.map(
                (platform: string) => (
                  <PlatformIcon
                    key={platform}
                    platform={platform}
                    tooltip
                    width={25}
                    marginRight={theme.spacing(2)}
                  />
                ),
              )}
            </GridLegacy>
            <GridLegacy item xs={4} style={{ paddingTop: 10 }}>
              <Typography
                variant="h3"
                gutterBottom
                style={{ marginTop: 20 }}
              >
                {t('Type Affinity')}
              </Typography>
              <TypeAffinityChip affinity_text={attack_chain?.attack_chain_type_affinity} />
            </GridLegacy>
            <GridLegacy item xs={4} style={{ paddingTop: 10 }}>
              <Typography
                variant="h3"
                gutterBottom
                style={{ marginTop: 20 }}
              >
                {t('Kill Chain Phases')}
              </Typography>
              {(attack_chain.attack_chain_kill_chain_phases ?? []).length === 0 && '-'}
              {sortByOrder(attack_chain.attack_chain_kill_chain_phases ?? [])?.map((killChainPhase: KillChainPhase) => (
                <Chip
                  key={killChainPhase.phase_id}
                  variant="outlined"
                  classes={{ root: classes.chip }}
                  color="error"
                  label={killChainPhase.phase_name}
                />
              ))}
            </GridLegacy>
          </GridLegacy>
        </Paper>
        <Paper classes={{ root: classes.paper }} variant="outlined">
          <AttackChainDistributionByAttackChainRun scenarioId={scenarioId} />
        </Paper>
      </div>
      {areAnyAttackChainRunsInAttackChain && (
        <div style={{
          display: 'grid',
          marginTop: theme.spacing(3),
          gap: `0px ${theme.spacing(3)}`,
          gridTemplateColumns: '1fr',
        }}
        >
          <Typography variant="h4">{t('Simulations')}</Typography>
          <Paper classes={{ root: classes.paper }} variant="outlined">
            <PaginationComponentV2
              fetch={input => search(scenarioId, input)}
              searchPaginationInput={searchPaginationInput}
              setContent={setAttackChainRuns}
              entityPrefix="attack_chain_run"
              availableFilterNames={['attack_chain_run_kill_chain_phases', 'attack_chain_run_name', 'attack_chain_run_tags']}
              queryableHelpers={queryableHelpers}
              searchEnable={false}
            />
            <SimulationList
              attack_chain_runs={attack_chain_runs}
              queryableHelpers={queryableHelpers}
              secondaryAction={secondaryAction}
              loading={loadingAttackChainRuns}
              isGlobalScoreAsync={true}
            />
          </Paper>
        </div>
      )}
      {!areAnyAttackChainRunsInAttackChain && !attack_chain.attack_chain_recurrence && ability.can(ACTIONS.LAUNCH, SUBJECTS.RESOURCE, attack_chain.attack_chain_id) && (
        <div style={{
          marginTop: 100,
          textAlign: 'center',
        }}
        >
          <div style={{ fontSize: 20 }}>
            {t('This attack_chain has never run, schedule or run it now!')}
          </div>
          <Button
            style={{ marginTop: 20 }}
            startIcon={<PlayArrowOutlined />}
            variant="contained"
            color="primary"
            size="large"
            onClick={() => setOpenInstantiateSimulationAndStart(true)}
          >
            {t('Launch attack_chain_run now')}
          </Button>
        </div>
      )}
      {!areAnyAttackChainRunsInAttackChain && attack_chain.attack_chain_recurrence && (
        <div style={{
          marginTop: 100,
          textAlign: 'center',
        }}
        >
          <div style={{ fontSize: 20 }}>
            {t('This attack_chain is scheduled to run, results will appear soon.')}
          </div>
        </div>
      )}
    </div>
  );
};

export default AttackChain;
