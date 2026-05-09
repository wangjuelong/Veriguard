import { Chip, Grid, Paper, Typography, useTheme } from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useContext } from 'react';

import { type AttackChainsHelper } from '../../../../actions/attack_chains/attack_chain-helper';
import ContextLink from '../../../../components/ContextLink';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemCategory from '../../../../components/ItemCategory';
import ItemMainFocus from '../../../../components/ItemMainFocus';
import ItemSeverity from '../../../../components/ItemSeverity';
import ItemTags from '../../../../components/ItemTags';
import PlatformIcon from '../../../../components/PlatformIcon';
import TypeAffinityChip from '../../../../components/TypeAffinityChip';
import { ATTACK_CHAIN_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type AttackChainRun, type KillChainPhase } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

interface Props { attack_chain_run: AttackChainRun }

const SimulationMainInformation: FunctionComponent<Props> = ({ attack_chain_run }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useContext(AbilityContext);

  const sortByOrder = R.sortWith([R.ascend(R.prop('phase_order'))]);
  const { attack_chain } = useHelper((helper: AttackChainsHelper) => ({ attack_chain: helper.getAttackChain(attack_chain_run.attack_chain_run_attack_chain || '') }));

  const renderAttackChainContent = () => {
    if (!attack_chain) {
      return '-';
    }
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, attack_chain.attack_chain_id)) {
      return (
        <ContextLink
          title={attack_chain.attack_chain_name}
          url={`${ATTACK_CHAIN_BASE_URL}/${attack_chain.attack_chain_id}`}
        />
      );
    }
    return attack_chain.attack_chain_name;
  };

  return (
    <Paper sx={{ padding: theme.spacing(2) }} variant="outlined">
      <Grid id="main_information" container spacing={3}>
        <Grid size={{ xs: 8 }}>
          <Typography
            variant="h3"
            gutterBottom
            style={{ marginTop: 20 }}
          >
            {t('Description')}
          </Typography>
          <ExpandableMarkdown
            source={attack_chain_run.attack_chain_run_description}
            limit={300}
          />
        </Grid>
        <Grid size={{ xs: 4 }}>
          <Typography
            variant="h3"
            gutterBottom
            style={{ marginTop: 20 }}
          >
            {t('Parent attack_chain')}
          </Typography>
          {renderAttackChainContent()}
        </Grid>
        <Grid size={{ xs: 4 }}>
          <Typography
            variant="h3"
            gutterBottom
            style={{ marginTop: 20 }}
            sx={{ width: '100%' }}
          >
            {t('Severity')}
          </Typography>
          <ItemSeverity severity={attack_chain_run.attack_chain_run_severity} label={t(attack_chain_run.attack_chain_run_severity ?? 'Unknown')} />
        </Grid>
        <Grid size={{ xs: 4 }}>
          <Typography
            variant="h3"
            gutterBottom
            style={{ marginTop: 20 }}
          >
            {t('Category')}
          </Typography>
          <ItemCategory category={attack_chain_run?.attack_chain_run_category ?? ''} label={t(attack_chain_run.attack_chain_run_category ?? 'Unknown')} />
        </Grid>
        <Grid size={{ xs: 4 }}>
          <Typography
            variant="h3"
            gutterBottom
            style={{ marginTop: 20 }}
          >
            {t('Main Focus')}
          </Typography>
          <ItemMainFocus mainFocus={attack_chain_run?.attack_chain_run_main_focus ?? ''} label={t(attack_chain_run.attack_chain_run_main_focus ?? 'Unknown')} />
        </Grid>
        <Grid size={{ xs: 4 }}>
          <Typography
            variant="h3"
            gutterBottom
            style={{ marginTop: 20 }}
          >
            {t('Tags')}
          </Typography>
          <ItemTags tags={attack_chain_run.attack_chain_run_tags} limit={10} />
        </Grid>
        <Grid size={{ xs: 4 }}>
          <Typography
            variant="h3"
            gutterBottom
            style={{ marginTop: 20 }}
          >
            {t('Platforms')}
          </Typography>
          {(attack_chain_run.attack_chain_run_platforms ?? []).length === 0 ? (
            <PlatformIcon platform={t('No node in this attack_chain')} tooltip width={25} />
          ) : attack_chain_run.attack_chain_run_platforms?.map(
            (platform: string) => <PlatformIcon key={platform} platform={platform} tooltip width={25} marginRight={theme.spacing(2)} />,
          )}
        </Grid>
        <Grid size={{ xs: 4 }}>
          <Typography
            variant="h3"
            gutterBottom
            style={{ marginTop: 20 }}
          >
            {t('Type Affinity')}
          </Typography>
          <TypeAffinityChip affinity_text={attack_chain?.attack_chain_type_affinity} />
        </Grid>
        <Grid size={{ xs: 4 }}>
          <Typography
            variant="h3"
            gutterBottom
            style={{ marginTop: 20 }}
          >
            {t('Kill Chain Phases')}
          </Typography>
          {(attack_chain_run.attack_chain_run_kill_chain_phases ?? []).length === 0 && '-'}
          {sortByOrder(attack_chain_run.attack_chain_run_kill_chain_phases ?? []).map((killChainPhase: KillChainPhase) => (
            <Chip
              key={killChainPhase.phase_id}
              variant="outlined"
              style={{
                fontSize: 12,
                height: 25,
                margin: '0 7px 7px 0',
                textTransform: 'uppercase',
                borderRadius: 4,
                width: 180,
              }}
              color="error"
              label={killChainPhase.phase_name}
            />
          ))}
        </Grid>
      </Grid>
    </Paper>
  );
};

export default SimulationMainInformation;
