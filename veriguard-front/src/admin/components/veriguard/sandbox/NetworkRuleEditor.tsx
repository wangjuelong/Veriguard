/* eslint-disable i18next/no-literal-string -- spec §6.7: M1 sandbox UI uses
   hardcoded Chinese to match existing VeriguardConsole.tsx pattern; future
   M-x will migrate to react-intl when sandbox UI stabilizes. */
import { AddOutlined, DeleteOutline } from '@mui/icons-material';
import {
  Box, Button, FormControl, IconButton, InputLabel, MenuItem, Select,
  Stack, TextField, Typography,
} from '@mui/material';

import type { SandboxNetworkRule, SandboxRuleAction, SandboxRuleDirection } from '../../../../actions/veriguard/veriguard-actions';
import { isValidCidr, isValidPortExpression } from './utils/cidr-port-validators';

type Props = {
  value: SandboxNetworkRule[];
  onChange: (next: SandboxNetworkRule[]) => void;
  disabled?: boolean;
};

const PROTOCOLS = ['TCP', 'UDP', 'ICMP', 'ALL'] as const;
const DIRECTIONS: SandboxRuleDirection[] = ['INGRESS', 'EGRESS'];
const ACTIONS: SandboxRuleAction[] = ['ALLOW', 'DENY'];

const defaultRule: SandboxNetworkRule = {
  rule_direction: 'EGRESS',
  rule_action: 'DENY',
  rule_protocol: 'TCP',
  rule_cidr: '0.0.0.0/0',
  rule_ports: 'all',
};

const NetworkRuleEditor = ({ value, onChange, disabled }: Props) => {
  const replace = (idx: number, partial: Partial<SandboxNetworkRule>) => {
    const next = value.map((r, i) => (i === idx
      ? {
          ...r,
          ...partial,
        }
      : r));
    onChange(next);
  };
  const remove = (idx: number) => onChange(value.filter((_, i) => i !== idx));
  const add = () => onChange([...value, defaultRule]);

  if (value.length === 0) {
    return (
      <Box sx={{
        p: 2,
        border: '1px dashed',
        borderColor: 'divider',
        borderRadius: 1,
      }}
      >
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          尚未配置规则。沙箱平台主机将沿用默认网络策略。
        </Typography>
        <Button startIcon={<AddOutlined />} onClick={add} disabled={disabled}>
          添加规则
        </Button>
      </Box>
    );
  }

  return (
    <Stack gap={1}>
      {value.map((rule, idx) => {
        const cidrInvalid = !isValidCidr(rule.rule_cidr);
        const portsInvalid = !isValidPortExpression(rule.rule_ports);
        const isIcmp = rule.rule_protocol.toUpperCase() === 'ICMP';
        return (
          <Stack key={idx} direction="row" gap={1} alignItems="center">
            <FormControl size="small" sx={{ minWidth: 100 }}>
              <InputLabel>方向</InputLabel>
              <Select
                label="方向"
                value={rule.rule_direction}
                onChange={e => replace(idx, { rule_direction: e.target.value as SandboxRuleDirection })}
                disabled={disabled}
              >
                {DIRECTIONS.map(d => <MenuItem key={d} value={d}>{d}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 100 }}>
              <InputLabel>动作</InputLabel>
              <Select
                label="动作"
                value={rule.rule_action}
                onChange={e => replace(idx, { rule_action: e.target.value as SandboxRuleAction })}
                disabled={disabled}
              >
                {ACTIONS.map(a => <MenuItem key={a} value={a}>{a}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 100 }}>
              <InputLabel>协议</InputLabel>
              <Select
                label="协议"
                value={rule.rule_protocol}
                onChange={(e) => {
                  const proto = String(e.target.value);
                  if (proto.toUpperCase() === 'ICMP') {
                    replace(idx, {
                      rule_protocol: proto,
                      rule_ports: 'none',
                    });
                  } else {
                    replace(idx, { rule_protocol: proto });
                  }
                }}
                disabled={disabled}
              >
                {PROTOCOLS.map(p => <MenuItem key={p} value={p}>{p}</MenuItem>)}
              </Select>
            </FormControl>
            <TextField
              size="small"
              label="CIDR"
              value={rule.rule_cidr}
              onChange={e => replace(idx, { rule_cidr: e.target.value })}
              error={cidrInvalid}
              helperText={cidrInvalid ? 'CIDR 格式无效' : undefined}
              disabled={disabled}
            />
            <TextField
              size="small"
              label="端口"
              value={rule.rule_ports}
              onChange={e => replace(idx, { rule_ports: e.target.value })}
              error={portsInvalid}
              helperText={portsInvalid ? '端口表达式无效' : undefined}
              disabled={disabled || isIcmp}
            />
            <IconButton onClick={() => remove(idx)} aria-label="删除规则" disabled={disabled}>
              <DeleteOutline />
            </IconButton>
          </Stack>
        );
      })}
      <Button startIcon={<AddOutlined />} onClick={add} disabled={disabled} sx={{ alignSelf: 'flex-start' }}>
        添加规则
      </Button>
    </Stack>
  );
};

export default NetworkRuleEditor;
