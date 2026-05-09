/* eslint-disable i18next/no-literal-string -- Phase 8: 二开 UI 硬编码中文（与 SandboxDialog
   现有约定一致），未来 Phase 12 (i18n 清洗) 统一迁移到 react-intl。 */
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material';
import {
  Box,
  Button,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Popover,
  Select,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';

import {
  type EdgeBoolean,
  type EdgeConditionGroup,
  type EdgeConditionLeaf,
  type EdgeConditionTree,
  type ExpectationConditionStatus,
  type ExpectationDimension,
} from './attackChainEditorTypes';

interface Props {
  open: boolean;
  anchorEl: HTMLElement | null;
  initialValue: EdgeConditionTree;
  onCancel: () => void;
  onSubmit: (value: EdgeConditionTree) => void;
}

const DIMENSION_OPTIONS: {
  value: ExpectationDimension;
  label: string;
}[] = [
  {
    value: 'PREVENTION',
    label: 'PREVENTION',
  },
  {
    value: 'DETECTION',
    label: 'DETECTION',
  },
  {
    value: 'MANUAL',
    label: 'MANUAL',
  },
];

const STATUS_OPTIONS: {
  value: ExpectationConditionStatus;
  label: string;
}[] = [
  {
    value: 'ANY_SUCCESS',
    label: '任一成功',
  },
  {
    value: 'ANY_FAILED',
    label: '任一失败',
  },
  {
    value: 'ALL_SUCCESS',
    label: '全部成功',
  },
  {
    value: 'ALL_FAILED',
    label: '全部失败',
  },
  {
    value: 'SETTLED',
    label: '已结算',
  },
];

const defaultLeaf = (): EdgeConditionLeaf => ({
  kind: 'leaf',
  dimension: 'PREVENTION',
  status: 'ANY_FAILED',
});

/** 把单条叶子提升为带一个孩子的组（点 "+ 加条件" 时调用）. */
const promoteToGroup = (
  leaf: EdgeConditionLeaf,
  op: EdgeBoolean = 'AND',
): EdgeConditionGroup => ({
  kind: 'group',
  op,
  children: [leaf, defaultLeaf()],
});

const removeAtPath = (tree: EdgeConditionTree, path: number[]): EdgeConditionTree => {
  if (path.length === 0 || tree.kind !== 'group') {
    return tree;
  }
  if (path.length === 1) {
    const next = tree.children.filter((_, i) => i !== path[0]);
    if (next.length === 1) {
      return next[0]; // 只剩一个孩子 → 退化为叶子
    }
    return {
      ...tree,
      children: next,
    };
  }
  const [head, ...rest] = path;
  return {
    ...tree,
    children: tree.children.map((child, i) =>
      i === head ? removeAtPath(child, rest) : child,
    ),
  };
};

interface ConditionNodeProps {
  tree: EdgeConditionTree;
  path: number[];
  onChange: (next: EdgeConditionTree) => void;
  onRemove: () => void;
  depth: number;
}

const ConditionNode = ({
  tree,
  path,
  onChange,
  onRemove,
  depth,
}: ConditionNodeProps) => {
  if (tree.kind === 'leaf') {
    const leaf = tree;
    return (
      <Stack direction="row" spacing={1} alignItems="center">
        <FormControl size="small" sx={{ minWidth: 140 }}>
          <InputLabel id={`dim-${path.join('-')}`}>维度</InputLabel>
          <Select
            labelId={`dim-${path.join('-')}`}
            label="维度"
            value={leaf.dimension}
            onChange={e =>
              onChange({
                ...leaf,
                dimension: e.target.value as ExpectationDimension,
              })}
          >
            {DIMENSION_OPTIONS.map(opt => (
              <MenuItem key={opt.value} value={opt.value}>
                {opt.label}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 140 }}>
          <InputLabel id={`stat-${path.join('-')}`}>状态</InputLabel>
          <Select
            labelId={`stat-${path.join('-')}`}
            label="状态"
            value={leaf.status}
            onChange={e =>
              onChange({
                ...leaf,
                status: e.target.value as ExpectationConditionStatus,
              })}
          >
            {STATUS_OPTIONS.map(opt => (
              <MenuItem key={opt.value} value={opt.value}>
                {opt.label}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <Button
          size="small"
          startIcon={<AddIcon />}
          onClick={() => onChange(promoteToGroup(leaf))}
        >
          加条件
        </Button>
        {depth > 0 && (
          <IconButton
            size="small"
            aria-label={`删除条件 ${path.join('.')}`}
            onClick={onRemove}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        )}
      </Stack>
    );
  }
  // group
  const group = tree;
  return (
    <Stack
      spacing={1}
      sx={{
        border: 1,
        borderColor: 'divider',
        borderRadius: 1,
        p: 1,
        bgcolor: depth % 2 === 0 ? 'background.paper' : 'background.default',
      }}
    >
      <Stack direction="row" alignItems="center" spacing={1}>
        <ToggleButtonGroup
          exclusive
          size="small"
          value={group.op}
          onChange={(_, v) => v && onChange({
            ...group,
            op: v as EdgeBoolean,
          })}
        >
          <ToggleButton value="AND">AND</ToggleButton>
          <ToggleButton value="OR">OR</ToggleButton>
        </ToggleButtonGroup>
        <Button
          size="small"
          startIcon={<AddIcon />}
          onClick={() =>
            onChange({
              ...group,
              children: [...group.children, defaultLeaf()],
            })}
        >
          加条件
        </Button>
        {depth > 0 && (
          <IconButton
            size="small"
            aria-label={`删除组 ${path.join('.')}`}
            onClick={onRemove}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        )}
      </Stack>
      <Stack spacing={1} sx={{ pl: 2 }}>
        {group.children.map((child, idx) => (
          <ConditionNode
            key={`child-${idx}`}
            tree={child}
            path={[...path, idx]}
            depth={depth + 1}
            onChange={next =>
              onChange({
                ...group,
                children: group.children.map((c, i) => (i === idx ? next : c)),
              })}
            onRemove={() => {
              const next = removeAtPath(group, [idx]);
              onChange(next);
            }}
          />
        ))}
      </Stack>
    </Stack>
  );
};

const ConditionEdgePopover = ({ open, anchorEl, initialValue, onCancel, onSubmit }: Props) => {
  const [tree, setTree] = useState<EdgeConditionTree>(initialValue);

  useEffect(() => {
    setTree(initialValue);
  }, [initialValue]);

  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onCancel}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
    >
      <Paper sx={{
        width: 320,
        p: 2,
      }}
      >
        <Typography variant="subtitle2" sx={{ mb: 1 }}>边条件</Typography>
        <Box>
          <ConditionNode
            tree={tree}
            path={[]}
            depth={0}
            onChange={setTree}
            onRemove={() => {}}
          />
        </Box>
        <Stack direction="row" justifyContent="flex-end" spacing={1} sx={{ mt: 2 }}>
          <Button size="small" onClick={onCancel}>取消</Button>
          <Button
            size="small"
            variant="contained"
            onClick={() => onSubmit(tree)}
          >
            应用
          </Button>
        </Stack>
      </Paper>
    </Popover>
  );
};

export default ConditionEdgePopover;
