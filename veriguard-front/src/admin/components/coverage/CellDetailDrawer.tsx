/* eslint-disable i18next/no-literal-string -- IPv6 PR C3 边界覆盖度 UI */
import { Box, Chip, Drawer, Stack, Typography } from '@mui/material';

import type { CoverageCellOutput, CoverageHitState } from '../../../actions/coverage/coverage-actions';

interface CellDetailDrawerProps {
  cell: CoverageCellOutput | null;
  onClose: () => void;
}

const HIT_STATE_LABEL: Record<CoverageHitState, string> = {
  hit: '有覆盖（hit）',
  miss: '无覆盖（miss）',
  timeout: '超时（timeout）',
  out_of_scope: '不适用（out_of_scope）',
};

const HIT_STATE_COLOR: Record<CoverageHitState, 'success' | 'error' | 'warning' | 'default'> = {
  hit: 'success',
  miss: 'error',
  timeout: 'warning',
  out_of_scope: 'default',
};

const formatDate = (iso: string | null): string => {
  if (!iso) {
    return '-';
  }
  return new Date(iso).toLocaleString();
};

const Detail = ({ label, value }: {
  label: string;
  value: string;
}) => (
  <Box>
    <Typography variant="caption" color="text.secondary">{label}</Typography>
    <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>{value}</Typography>
  </Box>
);

const CellDetailDrawer = ({ cell, onClose }: CellDetailDrawerProps) => {
  return (
    <Drawer anchor="right" open={cell !== null} onClose={onClose}>
      <Box sx={{
        width: 400,
        p: 3,
      }}
      >
        {cell && (
          <Stack spacing={2}>
            <Typography variant="h6">单元格详情</Typography>
            <Box>
              <Chip
                size="small"
                label={HIT_STATE_LABEL[cell.coverage_result_hit_state]}
                color={HIT_STATE_COLOR[cell.coverage_result_hit_state]}
              />
            </Box>
            <Detail label="资产 ID" value={cell.coverage_result_asset_id} />
            <Detail label="策略 ID" value={cell.coverage_result_policy_id} />
            <Detail label="用例 ID" value={cell.coverage_result_case_id ?? '-'} />
            <Detail label="告警 rule_id" value={cell.coverage_result_alert_rule_id ?? '-'} />
            <Detail label="观测时间" value={formatDate(cell.coverage_result_observed_at)} />
            <Detail label="错误信息" value={cell.coverage_result_error_message ?? '-'} />
            <Detail label="创建时间" value={formatDate(cell.coverage_result_created_at)} />
          </Stack>
        )}
      </Box>
    </Drawer>
  );
};

export default CellDetailDrawer;
