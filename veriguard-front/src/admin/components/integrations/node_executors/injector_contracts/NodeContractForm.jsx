import { Button, MenuItem } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as PropTypes from 'prop-types';
import { Field, Form } from 'react-final-form';

import DomainsAutocompleteField from '../../../../../components/DomainsAutocompleteField.tsx';
import OldSelectField from '../../../../../components/fields/OldSelectField';
import OldSwitchField from '../../../../../components/fields/OldSwitchField';
import OldTextField from '../../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../../components/i18n';
import OldAttackPatternField from '../../../../../components/OldAttackPatternField';
import { useHelper } from '../../../../../store';

const SOFTWARE_CATEGORY_OPTIONS = [
  'web_component',
  'security_product',
  'application',
  'domestic_commercial',
  'cms',
  'network_device',
  'os',
  'database',
];

const DEFENSE_LAYER_OPTIONS = ['boundary', 'traffic', 'application', 'host', 'data'];

const NETWORK_PROTOCOL_FAMILY_OPTIONS = ['ipv4', 'ipv6', 'both'];

const TARGET_OS_OPTIONS = ['linux', 'windows', 'both', 'none'];

const ROLLBACK_STEPS_FIELD = 'injector_contract_rollback_steps';

const stringifyRollbackSteps = (value) => {
  if (value === null || value === undefined) {
    return '';
  }
  if (typeof value === 'string') {
    return value;
  }
  return JSON.stringify(value, null, 2);
};

const parseRollbackSteps = (raw) => {
  if (raw === null || raw === undefined) {
    return { ok: true, value: null };
  }
  const trimmed = typeof raw === 'string' ? raw.trim() : raw;
  if (trimmed === '') {
    return { ok: true, value: null };
  }
  if (typeof trimmed !== 'string') {
    return { ok: true, value: trimmed };
  }
  try {
    return { ok: true, value: JSON.parse(trimmed) };
  } catch (_e) {
    return { ok: false };
  }
};

const NodeContractForm = (props) => {
  const { onSubmit, initialValues, editing, handleClose, isPayloadInjector } = props;

  const { t } = useFormatter();
  const theme = useTheme();

  const formInitialValues = initialValues
    ? {
        ...initialValues,
        [ROLLBACK_STEPS_FIELD]: stringifyRollbackSteps(initialValues[ROLLBACK_STEPS_FIELD]),
      }
    : initialValues;

  const validate = (values) => {
    const errors = {};

    if (!Array.isArray(values.injector_contract_domains) || values.injector_contract_domains.length === 0) {
      errors.injector_contract_domains = t('This field is required.');
    }

    const rollback = values[ROLLBACK_STEPS_FIELD];
    if (typeof rollback === 'string' && rollback.trim() !== '') {
      const parsed = parseRollbackSteps(rollback);
      if (!parsed.ok) {
        errors[ROLLBACK_STEPS_FIELD] = t('Invalid JSON');
      }
    }

    return errors;
  };

  const handleFormSubmit = (data) => {
    const parsed = parseRollbackSteps(data[ROLLBACK_STEPS_FIELD]);
    if (!parsed.ok) {
      return { [ROLLBACK_STEPS_FIELD]: t('Invalid JSON') };
    }
    return onSubmit({
      ...data,
      [ROLLBACK_STEPS_FIELD]: parsed.value,
    });
  };

  const domainOptions = useHelper((helper) => {
    return helper.getDomains();
  });

  return (
    <Form
      keepDirtyOnReinitialize={true}
      initialValues={formInitialValues}
      validate={validate}
      onSubmit={handleFormSubmit}
      mutators={{
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ handleSubmit, form, values, submitting, pristine }) => (
        <form id="injectorContractForm" onSubmit={handleSubmit}>
          <OldAttackPatternField
            name="injector_contract_attack_patterns"
            label={t('Attack patterns')}
            values={values}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: theme.spacing(2) }}
          />
          {!isPayloadInjector && (
            <Field name="injector_contract_domains">
              {({ input, meta }) => (
                <DomainsAutocompleteField
                  input={input}
                  meta={meta}
                  domainOptions={domainOptions}
                  label={t('Domains')}
                  style={{ marginTop: theme.spacing(2) }}
                />
              )}
            </Field>
          )}

          <OldSelectField
            variant="standard"
            name="injector_contract_software_category"
            label={t('Software category')}
            fullWidth={true}
            style={{ marginTop: theme.spacing(2) }}
          >
            {SOFTWARE_CATEGORY_OPTIONS.map(value => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </OldSelectField>

          <OldSelectField
            variant="standard"
            name="injector_contract_defense_layer"
            label={t('Defense layer')}
            fullWidth={true}
            style={{ marginTop: theme.spacing(2) }}
          >
            {DEFENSE_LAYER_OPTIONS.map(value => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </OldSelectField>

          <OldSelectField
            variant="standard"
            name="injector_contract_network_protocol_family"
            label={t('Network protocol family')}
            fullWidth={true}
            style={{ marginTop: theme.spacing(2) }}
          >
            {NETWORK_PROTOCOL_FAMILY_OPTIONS.map(value => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </OldSelectField>

          <OldSelectField
            variant="standard"
            name="injector_contract_target_os"
            label={t('Target OS')}
            fullWidth={true}
            style={{ marginTop: theme.spacing(2) }}
          >
            {TARGET_OS_OPTIONS.map(value => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </OldSelectField>

          <OldSwitchField
            name="injector_contract_network_dependent"
            label={t('Network dependent')}
            style={{ marginTop: theme.spacing(2) }}
          />

          <OldTextField
            variant="standard"
            name={ROLLBACK_STEPS_FIELD}
            label={t('Rollback steps (JSON)')}
            fullWidth={true}
            multiline={true}
            rows={6}
            style={{ marginTop: theme.spacing(2) }}
          />

          <div style={{
            float: 'right',
            marginTop: theme.spacing(2),
          }}
          >

            <Button
              variant="contained"
              onClick={handleClose}
              style={{ marginRight: theme.spacing(2) }}
              disabled={submitting}
            >
              {t('Cancel')}
            </Button>
            <Button
              color="secondary"
              type="submit"
              variant="contained"
              disabled={pristine || submitting}
            >
              {editing ? t('Update') : t('Create')}
            </Button>
          </div>
        </form>
      )}
    </Form>
  );
};

NodeContractForm.propTypes = {
  onSubmit: PropTypes.func.isRequired,
  handleClose: PropTypes.func,
  editing: PropTypes.bool,
};

export default NodeContractForm;
