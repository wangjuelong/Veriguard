/* eslint-disable i18next/no-literal-string -- Phase 12b-B 二开 UI 硬编码中文，未来统一 i18n 清洗。 */
import { Box, Stack, Typography } from '@mui/material';
import { useCallback, useEffect, useState } from 'react';

import {
  fetchSocConnectors,
  type SocConnectorOutputDto,
} from '../../../actions/soc_connectors/soc-connector-actions';
import {
  createValidationParameterSet,
  deleteValidationParameterSet,
  duplicateValidationParameterSet,
  searchValidationParameterSets,
  updateValidationParameterSet,
} from '../../../actions/validation_parameter_sets/parameter-set-actions';
import ButtonCreate from '../../../components/common/ButtonCreate';
import { initSorting } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import {
  type PageValidationParameterSetOutput,
  type SocCorrelationRuleRef as ApiSocCorrelationRuleRef,
  type TargetRef as ApiTargetRef,
  type ValidationParameterSetInput,
  type ValidationParameterSetOutput,
} from '../../../utils/api-types';
import ParameterSetCard from './ParameterSetCard';
import ParameterSetEditDialog from './ParameterSetEditDialog';
import {
  type ParameterSetTargetRef,
  type SocCorrelationRuleRef,
  type ValidationParameterSetSummary,
  type ValidationParameterSetValue,
} from './validationParameterSetTypes';

const SEARCH_PAGE_SIZE = 100;

const emptyValue = (): ValidationParameterSetValue => ({
  name: '',
  description: '',
  is_template: false,
  default_targets: [],
  prevention_expected_score: 100,
  prevention_expiration_seconds: 1800,
  detection_expected_score: 100,
  detection_expiration_seconds: 1800,
  soc_correlation_rules: [],
  tags: [],
});

const fromApiTarget = (t: ApiTargetRef): ParameterSetTargetRef => ({
  kind: t.target_type === 'ASSET_GROUP' ? 'asset_group' : 'asset',
  id: t.target_id ?? '',
});

const toApiTarget = (t: ParameterSetTargetRef): ApiTargetRef => ({
  target_id: t.id,
  target_type: t.kind === 'asset_group' ? 'ASSET_GROUP' : 'ASSET',
});

const fromApiRule = (r: ApiSocCorrelationRuleRef): SocCorrelationRuleRef => ({
  connector_id: r.connector_id ?? '',
  rule_id: r.rule_id ?? '',
  display_name: r.display_name ?? '',
  match_window_seconds: r.match_window_seconds ?? 0,
});

const toApiRule = (r: SocCorrelationRuleRef): ApiSocCorrelationRuleRef => ({
  connector_id: r.connector_id,
  rule_id: r.rule_id,
  display_name: r.display_name,
  match_window_seconds: r.match_window_seconds,
});

const toSummary = (api: ValidationParameterSetOutput): ValidationParameterSetSummary => ({
  id: api.parameter_set_id ?? '',
  name: api.parameter_set_name ?? '',
  description: api.parameter_set_description ?? null,
  is_template: api.parameter_set_is_template ?? false,
  default_targets: (api.parameter_set_default_targets ?? []).map(fromApiTarget),
  prevention_expected_score: api.parameter_set_prevention_expected_score ?? 0,
  prevention_expiration_seconds: api.parameter_set_prevention_expiration_seconds ?? 0,
  detection_expected_score: api.parameter_set_detection_expected_score ?? 0,
  detection_expiration_seconds: api.parameter_set_detection_expiration_seconds ?? 0,
  soc_correlation_rules: (api.parameter_set_soc_correlation_rules ?? []).map(fromApiRule),
  tags: (api.parameter_set_tag_ids ?? []).map(id => ({
    id,
    name: id,
  })),
  created_at: api.parameter_set_created_at ?? '',
  updated_at: api.parameter_set_updated_at ?? '',
});

const toApiInput = (value: ValidationParameterSetValue): ValidationParameterSetInput => ({
  parameter_set_name: value.name,
  parameter_set_description: value.description ?? undefined,
  parameter_set_default_targets: value.default_targets.map(toApiTarget),
  parameter_set_prevention_expected_score: value.prevention_expected_score,
  parameter_set_prevention_expiration_seconds: value.prevention_expiration_seconds,
  parameter_set_detection_expected_score: value.detection_expected_score,
  parameter_set_detection_expiration_seconds: value.detection_expiration_seconds,
  parameter_set_soc_correlation_rules: value.soc_correlation_rules.map(toApiRule),
  parameter_set_tag_ids: value.tags.map(t => t.id),
});

const summaryToValue = (s: ValidationParameterSetSummary): ValidationParameterSetValue => ({
  name: s.name,
  description: s.description,
  is_template: s.is_template,
  default_targets: s.default_targets,
  prevention_expected_score: s.prevention_expected_score,
  prevention_expiration_seconds: s.prevention_expiration_seconds,
  detection_expected_score: s.detection_expected_score,
  detection_expiration_seconds: s.detection_expiration_seconds,
  soc_correlation_rules: s.soc_correlation_rules,
  tags: s.tags,
});

interface EditingState {
  mode: 'create' | 'edit' | 'duplicate';
  initialValue: ValidationParameterSetValue;
  sourceId?: string;
}

const ValidationParameterSets = () => {
  const [parameterSets, setParameterSets] = useState<ValidationParameterSetSummary[]>([]);
  const [editing, setEditing] = useState<EditingState | null>(null);
  const [healthyConnectorIds, setHealthyConnectorIds] = useState<string[]>([]);

  const refresh = useCallback(async () => {
    const response: { data: PageValidationParameterSetOutput } = await searchValidationParameterSets(
      buildSearchPagination({
        size: SEARCH_PAGE_SIZE,
        sorts: initSorting('parameter_set_name', 'ASC'),
      }),
    );
    setParameterSets((response.data.content ?? []).map(toSummary));
  }, []);

  const loadHealthyConnectorIds = useCallback(async () => {
    const response: { data: SocConnectorOutputDto[] } = await fetchSocConnectors();
    const healthy = (response.data ?? [])
      .filter(c => c.status === 'HEALTHY')
      .map(c => c.connector_id);
    setHealthyConnectorIds(healthy);
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (editing && healthyConnectorIds.length === 0) {
      loadHealthyConnectorIds();
    }
  }, [editing, healthyConnectorIds.length, loadHealthyConnectorIds]);

  const handleCreate = () => {
    setEditing({
      mode: 'create',
      initialValue: emptyValue(),
    });
  };

  const handleEdit = (parameterSet: ValidationParameterSetSummary) => {
    if (parameterSet.is_template) {
      setEditing({
        mode: 'duplicate',
        initialValue: {
          ...summaryToValue(parameterSet),
          is_template: false,
        },
        sourceId: parameterSet.id,
      });
      return;
    }
    setEditing({
      mode: 'edit',
      initialValue: summaryToValue(parameterSet),
      sourceId: parameterSet.id,
    });
  };

  const handleDuplicate = async (parameterSet: ValidationParameterSetSummary) => {
    const newName = `${parameterSet.name} (副本)`;
    await duplicateValidationParameterSet(parameterSet.id, newName);
    await refresh();
  };

  const handleDelete = async (parameterSet: ValidationParameterSetSummary) => {
    await deleteValidationParameterSet(parameterSet.id);
    await refresh();
  };

  const handleSubmit = async (value: ValidationParameterSetValue) => {
    const input = toApiInput(value);
    if (editing?.mode === 'edit' && editing.sourceId) {
      await updateValidationParameterSet(editing.sourceId, input);
    } else {
      await createValidationParameterSet(input);
    }
    setEditing(null);
    await refresh();
  };

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 2 }}>验证参数集</Typography>
      <Stack spacing={2}>
        {parameterSets.map(parameterSet => (
          <Box key={parameterSet.id} data-testid={`param-set-row-${parameterSet.id}`}>
            <ParameterSetCard
              parameterSet={parameterSet}
              onEdit={() => handleEdit(parameterSet)}
              onDuplicate={() => handleDuplicate(parameterSet)}
              onDelete={() => handleDelete(parameterSet)}
            />
          </Box>
        ))}
      </Stack>

      <ButtonCreate onClick={handleCreate} />

      {editing && (
        <ParameterSetEditDialog
          open
          mode={editing.mode}
          initialValue={editing.initialValue}
          availableConnectorIds={healthyConnectorIds}
          onCancel={() => setEditing(null)}
          onSubmit={handleSubmit}
        />
      )}
    </Box>
  );
};

export default ValidationParameterSets;
