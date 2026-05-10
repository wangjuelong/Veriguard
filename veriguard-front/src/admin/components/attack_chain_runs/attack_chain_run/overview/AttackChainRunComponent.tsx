import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchAttackChainRunAttackChainNodeExpectationResults, fetchAttackChainRunExpectationResult, searchAttackChainRunAttackChainNodes } from '../../../../../actions/attack_chain_runs/attack_chain_run-action';
import { type AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { initSorting } from '../../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type AttackChainRun, type ExpectationResultsByType, type NodeExpectationResultsByAttackPattern } from '../../../../../utils/api-types';
import AttackChainNodeResultList from '../../../atomic_testings/AttackChainNodeResultList';
import ResponsePie from '../../../common/attack_chain_nodes/ResponsePie';
import MitreMatrix from '../../../common/matrix/MitreMatrix';
import SimulationMainInformation from '../AttackChainRunMainInformation';
import AttackChainRunDistribution from './AttackChainRunDistribution';

// Deprecated - https://mui.com/system/styles/basics/
// Do not use it for new code.
const useStyles = makeStyles()(theme => ({
  paper: {
    height: '100%',
    minHeight: '100%',
    padding: theme.spacing(2),
    borderRadius: 4,
  },
}));

const SimulationComponent = () => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();
  const [scrolledToAnchor, setScrolledToAnchor] = useState<boolean>(false);

  // Fetching data
  const [searchParams] = useSearchParams();
  // We do not use the traditional anchor (`#`) as the pagination hook overrides it
  const anchor = searchParams.get('anchor');
  const { exerciseId } = useParams() as { exerciseId: AttackChainRun['attack_chain_run_id'] };
  const { attack_chain_run } = useHelper((helper: AttackChainRunsHelper) => ({ attack_chain_run: helper.getAttackChainRun(exerciseId) }));
  const [results, setResults] = useState<ExpectationResultsByType[] | null>(null);
  const [injectResults, setAttackChainNodeResults] = useState<NodeExpectationResultsByAttackPattern[] | null>(null);

  useEffect(() => {
    fetchAttackChainRunExpectationResult(exerciseId).then((result: { data: ExpectationResultsByType[] }) => setResults(result.data));
    fetchAttackChainRunAttackChainNodeExpectationResults(exerciseId).then((result: { data: NodeExpectationResultsByAttackPattern[] }) => setAttackChainNodeResults(result.data));
  }, [exerciseId]);

  const goToLink = `/admin/attack_chain_runs/${exerciseId}/nodes`;
  let resultAttackPatternIds = [];
  if (injectResults) {
    resultAttackPatternIds = R.uniq(
      injectResults
        .filter(injectResult => !!injectResult.node_attack_pattern)
        .flatMap(injectResult => injectResult.node_attack_pattern) as unknown as string[],
    );
  }

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('attack_chain_run-nodes-results', buildSearchPagination({ sorts: initSorting('node_updated_at', 'DESC') }));

  useEffect(() => {
    if (scrolledToAnchor) {
      return;
    }
    if (anchor && injectResults && resultAttackPatternIds.length > 0) {
      const element = document.getElementById(anchor);
      if (element) {
        const header = document.querySelector('header');
        const headerHeight = header ? header.offsetHeight : 0;
        const elementPosition = element.getBoundingClientRect().top + window.pageYOffset;
        const offsetPosition = elementPosition - headerHeight;

        setScrolledToAnchor(true);
        window.scrollTo({
          top: offsetPosition,
          behavior: 'smooth',
        });
      }
    }
  }, [anchor, injectResults, resultAttackPatternIds, scrolledToAnchor, setScrolledToAnchor]);

  return (
    <div style={{ paddingBottom: theme.spacing(5) }}>
      <div style={{
        display: 'grid',
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: `calc((100% - ${theme.spacing(3)})/2) calc((100% - ${theme.spacing(3)})/2)`,
      }}
      >
        <Typography variant="h4">{t('Information')}</Typography>
        <Typography variant="h4">{t('Results')}</Typography>
        <SimulationMainInformation attack_chain_run={attack_chain_run} />
        <Paper
          variant="outlined"
          style={{
            display: 'flex',
            alignItems: 'center',
            height: '100%',
            justifyContent: 'center',
          }}
        >
          {!results
            ? <Loader variant="inElement" />
            : <ResponsePie expectationResultsByTypes={results} humanValidationLink={`/admin/attack_chain_runs/${exerciseId}/animation/validations`} />}
        </Paper>
      </div>
      {injectResults && resultAttackPatternIds.length > 0 && (
        <div style={{
          display: 'grid',
          marginTop: theme.spacing(3),
          gap: `0px ${theme.spacing(3)}`,
          gridTemplateColumns: '1fr',
        }}
        >
          <Typography variant="h4">{t('MITRE ATT&CK Results')}</Typography>
          <Paper
            variant="outlined"
            classes={{ root: classes.paper }}
            style={{ minWidth: '100%' }}
          >
            <MitreMatrix goToLink={goToLink} injectResults={injectResults} />
          </Paper>
        </div>
      )}
      {attack_chain_run.attack_chain_run_status !== 'SCHEDULED' && (
        <div
          style={{
            display: 'grid',
            marginTop: theme.spacing(3),
            gap: `0px ${theme.spacing(3)}`,
            gridTemplateColumns: '1fr',
          }}
          id="nodes-results"
        >
          <Typography variant="h4">{t('AttackChainNodes results')}</Typography>
          <Paper classes={{ root: classes.paper }} variant="outlined">
            <AttackChainNodeResultList
              fetchAttackChainNodes={input => searchAttackChainRunAttackChainNodes(exerciseId, input)}
              goTo={injectId => `/admin/attack_chain_runs/${exerciseId}/nodes/${injectId}`}
              queryableHelpers={queryableHelpers}
              searchPaginationInput={searchPaginationInput}
              contextId={attack_chain_run.attack_chain_run_id}
            />
          </Paper>
        </div>
      )}
      <AttackChainRunDistribution exerciseId={exerciseId} />
    </div>
  );
};

export default SimulationComponent;
