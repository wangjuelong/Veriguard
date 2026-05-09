import { Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { type SubmitHandler } from 'react-hook-form';

import { useFormatter } from '../../../../../components/i18n';
import ItemTargets from '../../../../../components/ItemTargets';
import { type AttackChainNodeResultOutput, type ReportAttackChainNodeComment } from '../../../../../utils/api-types';
import AtomicTestingResult from '../../../atomic_testings/atomic_testing/AtomicTestingResult';
import NodeContract from '../../../common/attack_chain_nodes/NodeContract';
import ReportComment from '../../../components/reports/ReportComment';

interface Props {
  style?: CSSProperties;
  nodes: AttackChainNodeResultOutput[];
  injectsComments?: ReportAttackChainNodeComment[];
  canEditComment?: boolean;
  onCommentSubmit?: SubmitHandler<ReportAttackChainNodeComment>;
}

const AttackChainNodeReportResult: FunctionComponent<Props> = ({
  style,
  nodes,
  injectsComments = [],
  canEditComment = false,
  onCommentSubmit = () => {},
}) => {
  // Standard hooks
  const { t, fldt, tPick } = useFormatter();
  const findAttackChainNodeCommentsByAttackChainNodeId = (injectId: AttackChainNodeResultOutput['node_id']) => (injectsComments ?? []).find(c => c.node_id === injectId) ?? null;

  const saveComment = (injectId: ReportAttackChainNodeComment['node_id'], value: string) => {
    onCommentSubmit({
      node_id: injectId,
      report_node_comment: value,
    });
  };

  const columns = [
    {
      label: 'Type',
      render: (node: AttackChainNodeResultOutput) => {
        return node.node_injector_contract
          ? <NodeContract variant="list" label={tPick(node.node_injector_contract?.injector_contract_labels)} />
          : <NodeContract variant="list" label={t('Deleted')} deleted={true} />;
      },
    },
    {
      label: 'Title',
      render: (node: AttackChainNodeResultOutput) => node.node_title,
    },
    {
      label: 'Execution date',
      render: (node: AttackChainNodeResultOutput) => {
        const trackingDate = node.node_status?.tracking_sent_date;
        return <>{trackingDate ? fldt(trackingDate) : '-'}</>;
      },
    },
    {
      label: 'Scores',
      render: (node: AttackChainNodeResultOutput) => <AtomicTestingResult expectations={node.node_expectation_results} injectId={node.node_id} />,
    },
    {
      label: 'Targets',
      render: (node: AttackChainNodeResultOutput) => <ItemTargets targets={node.node_targets} />,
    },
    {
      label: 'Comments',
      render: (node: AttackChainNodeResultOutput) => {
        const currentAttackChainNodeComment = findAttackChainNodeCommentsByAttackChainNodeId(node.node_id);
        return (
          <ReportComment
            canEditComment={canEditComment}
            initialComment={currentAttackChainNodeComment?.report_node_comment || ''}
            saveComment={value => saveComment(node.node_id, value)}
          />
        );
      },
    },
  ];

  return (
    <div style={style}>
      <Typography variant="h4" gutterBottom>
        {t('AttackChainNodes results')}
      </Typography>

      <Paper variant="outlined">
        <TableContainer style={{
          maxHeight: 'none',
          overflow: 'visible',
        }}
        >
          <Table aria-label="nodes results">
            <TableHead>
              <TableRow>
                {columns.map(col => (
                  <TableCell
                    sx={col.label === 'Comments' ? {
                      padding: '0px',
                      width: '35%',
                      flexGrow: 1,
                    } : {}}
                    key={col.label}
                  >
                    {' '}
                    {t(col.label)}
                    {' '}

                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {nodes.map(node => (
                <TableRow
                  key={node.node_id}
                >
                  {columns.map(col => (
                    <TableCell
                      sx={col.label === 'Comments' ? {
                        padding: '16px 0 16px 0',
                        width: '35%',
                        flexGrow: 1,
                        alignItems: 'flex-start',
                      } : { verticalAlign: 'top' }}
                      key={`${node.node_id}-${col.label}`}
                    >
                      {col.render(node)}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </div>
  );
};

export default AttackChainNodeReportResult;
