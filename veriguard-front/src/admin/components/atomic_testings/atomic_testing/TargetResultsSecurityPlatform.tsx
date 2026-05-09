import { OpenInNew } from '@mui/icons-material';
import { Link, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchExpectationTraces } from '../../../../actions/atomic_testings/atomic-testing-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { AttackChainNodeExpectationResult, AttackChainNodeExpectationTrace } from '../../../../utils/api-types';
import { getSourceLabel } from '../../attack_chain_runs/attack_chain_run/validation/expectations/ExpectationUtils';
import { type AttackChainNodeExpectationsStore } from '../../common/attack_chain_nodes/expectations/Expectation';

const useStyles = makeStyles()(() => ({ flexContainer: { display: 'flex' } }));

interface Props {
  injectExpectation: AttackChainNodeExpectationsStore;
  sourceId: string;
  expectationResult: AttackChainNodeExpectationResult | null;
  open: boolean;
  handleClose: () => void;
}

const TargetResultsSecurityPlatform: FunctionComponent<Props> = ({
  injectExpectation,
  sourceId,
  expectationResult,
  handleClose,
  open,
}) => {
  const { classes } = useStyles();
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const [expectationTraces, setExpectationTraces] = useState<AttackChainNodeExpectationTrace[]>([]);

  useEffect(() => {
    fetchExpectationTraces(injectExpectation.node_expectation_id, sourceId).then((result: { data: AttackChainNodeExpectationTrace[] }) => setExpectationTraces(result.data ?? []));
  }, [injectExpectation.node_expectation_id, sourceId]);

  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={getSourceLabel(expectationResult)}
    >
      <>
        <Typography variant="body1">
          {`${injectExpectation.node_expectation_type} ${t('Alerts')}`}
        </Typography>
        <TableContainer sx={{ marginTop: theme.spacing(4) }}>
          <Table
            sx={{ minWidth: 650 }}
            size="small"
          >
            <TableHead>
              <TableRow sx={{ textTransform: 'uppercase' }}>
                <TableCell>{t('Name')}</TableCell>
                <TableCell>{`${injectExpectation.node_expectation_type} ${t('Date')}`}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {
                expectationTraces.map((expectationTrace: AttackChainNodeExpectationTrace) => {
                  return (
                    <TableRow
                      key={expectationTrace.node_expectation_trace_id}
                      sx={{ height: '50px' }}
                    >
                      <TableCell sx={{ fontSize: '14px' }}>
                        <Link underline="always" href={expectationTrace.node_expectation_trace_alert_link} target="_blank">
                          <div className={classes.flexContainer}>
                            <div>
                              {expectationTrace.node_expectation_trace_alert_name}
                            </div>
                            <div style={{
                              paddingTop: '2px',
                              marginLeft: '2px',
                            }}
                            >
                              <OpenInNew fontSize="inherit" />
                            </div>
                          </div>
                        </Link>
                      </TableCell>
                      <TableCell sx={{ fontSize: '14px' }}>
                        {fldt(expectationTrace.node_expectation_trace_date)}
                      </TableCell>
                    </TableRow>
                  );
                })
              }
            </TableBody>
          </Table>
        </TableContainer>
      </>

    </Drawer>

  );
};

export default TargetResultsSecurityPlatform;
