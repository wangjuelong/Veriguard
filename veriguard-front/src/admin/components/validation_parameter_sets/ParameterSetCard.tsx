/* eslint-disable i18next/no-literal-string -- Phase 10 二开 UI 硬编码中文，未来 Phase 12 i18n 清洗。 */
import {
  ContentCopy as CopyIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
  Lock as LockIcon,
} from '@mui/icons-material';
import {
  Box,
  Card,
  CardContent,
  Chip,
  IconButton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';

import { type ValidationParameterSetSummary } from './validationParameterSetTypes';

interface Props {
  parameterSet: ValidationParameterSetSummary;
  onEdit?: () => void;
  onDuplicate?: () => void;
  onDelete?: () => void;
}

/**
 * ParameterSet 列表卡片（spec §6.3.5）.
 *
 * - 模板（is_template = true）：显示锁图标，禁用 Edit / Delete，启用 Duplicate
 * - 普通：Edit / Duplicate / Delete 都可用
 * - 副信息：分数 / 超时 / SOC 规则数 / tag 概览
 */
const ParameterSetCard = ({ parameterSet, onEdit, onDuplicate, onDelete }: Props) => {
  const isTemplate = parameterSet.is_template;
  return (
    <Card
      data-testid={`param-set-card-${parameterSet.id}`}
      variant="outlined"
      sx={{ position: 'relative' }}
    >
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
          {isTemplate && (
            <Tooltip title="模板参数集（不可直接编辑，可复制）">
              <LockIcon fontSize="small" color="action" data-testid="param-set-card-lock" />
            </Tooltip>
          )}
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
            {parameterSet.name}
          </Typography>
          {isTemplate && (
            <Chip label="模板" size="small" color="default" />
          )}
        </Stack>
        {parameterSet.description && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
            {parameterSet.description}
          </Typography>
        )}
        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
          <Chip
            size="small"
            variant="outlined"
            label={`PREVENTION ${parameterSet.prevention_expected_score} / ${Math.round(parameterSet.prevention_expiration_seconds / 60)}min`}
          />
          <Chip
            size="small"
            variant="outlined"
            label={`DETECTION ${parameterSet.detection_expected_score} / ${Math.round(parameterSet.detection_expiration_seconds / 60)}min`}
          />
          <Chip
            size="small"
            variant="outlined"
            label={`SOC ${parameterSet.soc_correlation_rules.length} 条`}
          />
          <Chip
            size="small"
            variant="outlined"
            label={`目标 ${parameterSet.default_targets.length}`}
          />
        </Stack>
        {parameterSet.tags.length > 0 && (
          <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
            {parameterSet.tags.map(tag => (
              <Chip key={tag.id} label={tag.name} size="small" />
            ))}
          </Stack>
        )}
        <Box sx={{
          position: 'absolute',
          top: 8,
          right: 8,
        }}
        >
          <Stack direction="row">
            {!isTemplate && onEdit && (
              <Tooltip title="编辑">
                <IconButton
                  size="small"
                  onClick={onEdit}
                  aria-label="编辑参数集"
                  data-testid="param-set-card-edit"
                >
                  <EditIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
            {onDuplicate && (
              <Tooltip title={isTemplate ? '复制并修改' : '复制'}>
                <IconButton
                  size="small"
                  onClick={onDuplicate}
                  aria-label="复制参数集"
                  data-testid="param-set-card-duplicate"
                >
                  <CopyIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
            {!isTemplate && onDelete && (
              <Tooltip title="删除">
                <IconButton
                  size="small"
                  onClick={onDelete}
                  aria-label="删除参数集"
                  data-testid="param-set-card-delete"
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
          </Stack>
        </Box>
      </CardContent>
    </Card>
  );
};

export default ParameterSetCard;
