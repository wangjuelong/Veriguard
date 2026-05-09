import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, AlertTitle, Autocomplete, Button, Chip, GridLegacy, MenuItem, TextField as MuiTextField, Typography } from '@mui/material';
import { DateTimePicker as MuiDateTimePicker } from '@mui/x-date-pickers';
import { type FunctionComponent, useState } from 'react';
import { Controller, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import SelectField from '../../../../components/fields/SelectField';
import TagField from '../../../../components/fields/TagField';
import TextField from '../../../../components/fields/TextField';
import { useFormatter } from '../../../../components/i18n';
import { type CreateAttackChainRunInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';
import { scenarioCategories } from '../../attack_chains/constants';
import { EXERCISE_NAME_MAX_LENGTH, EXERCISE_NAME_MIN_LENGTH } from '../constants';

interface Props {
  onSubmit: SubmitHandler<CreateAttackChainRunInput>;
  handleClose: () => void;
  initialValues?: CreateAttackChainRunInput;
  disabled?: boolean;
  edit: boolean;
  simulationId?: string;
}

const AttackChainRunForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  disabled,
  edit,
  initialValues = {
    attack_chain_run_name: '',
    attack_chain_run_subtitle: '',
    attack_chain_run_description: '',
    attack_chain_run_category: 'attack-attack_chain',
    attack_chain_run_main_focus: 'incident-response',
    attack_chain_run_severity: 'high',
    attack_chain_run_tags: [],
    attack_chain_run_mail_from: '',
    attack_chain_run_mails_reply_to: [],
    attack_chain_run_message_header: '',
    attack_chain_run_message_footer: '',
  },
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const [inputValue, setInputValue] = useState('');

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<CreateAttackChainRunInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<CreateAttackChainRunInput>().with({
        attack_chain_run_name: z.string().min(EXERCISE_NAME_MIN_LENGTH, { message: t('Should not be empty') })
          .max(EXERCISE_NAME_MAX_LENGTH, { message: t('Should not exceed {max_length} characters', { max_length: EXERCISE_NAME_MAX_LENGTH.toString() }) }),
        attack_chain_run_subtitle: z.string().optional(),
        attack_chain_run_category: z.string().optional(),
        attack_chain_run_main_focus: z.string().optional(),
        attack_chain_run_severity: z.string().optional(),
        attack_chain_run_description: z.string().optional(),
        attack_chain_run_start_date: z.string().datetime().optional().nullable(),
        attack_chain_run_tags: z.string().array().optional(),
        attack_chain_run_mail_from: z.string().email(t('Should be a valid email address')).optional(),
        attack_chain_run_mails_reply_to: z.array(z.string().email(t('Should be a valid email address'))).optional(),
        attack_chain_run_message_header: z.string().optional(),
        attack_chain_run_message_footer: z.string().optional(),
        attack_chain_run_custom_dashboard: z.string().optional(),
      }),
    ),
    defaultValues: initialValues,
  });

  return (
    <form id="exerciseForm" onSubmit={handleSubmit(onSubmit)}>
      <Typography
        variant="h2"
        gutterBottom
        style={{ marginTop: 20 }}
      >
        {t('General')}
      </Typography>

      <TextField
        variant="standard"
        fullWidth
        label={t('Name')}
        style={{ marginTop: 20 }}
        error={!!errors.attack_chain_run_name}
        helperText={errors.attack_chain_run_name?.message}
        inputProps={register('attack_chain_run_name')}
        InputLabelProps={{ required: true }}
        control={control}
        maxLength={255}
      />
      <GridLegacy container spacing={2}>
        <GridLegacy item xs={7}>
          <SelectField
            variant="standard"
            fullWidth={true}
            name="attack_chain_run_category"
            label={t('Category')}
            style={{ marginTop: 20 }}
            error={!!errors.attack_chain_run_category}
            control={control}
            defaultValue={initialValues.attack_chain_run_category}
          >
            {Array.from(scenarioCategories).map(([key, value]) => (
              <MenuItem key={key} value={key}>
                {t(value)}
              </MenuItem>
            ))}
          </SelectField>
        </GridLegacy>
        <GridLegacy item xs={5}>
          <SelectField
            variant="standard"
            fullWidth={true}
            name="attack_chain_run_main_focus"
            label={t('Main focus')}
            style={{ marginTop: 20 }}
            error={!!errors.attack_chain_run_main_focus}
            control={control}
            defaultValue={initialValues.attack_chain_run_main_focus}
          >
            <MenuItem key="endpoint-protection" value="endpoint-protection">
              {t('Endpoint Protection')}
            </MenuItem>
            <MenuItem key="web-filtering" value="web-filtering">
              {t('Web Filtering')}
            </MenuItem>
            <MenuItem key="incident-response" value="incident-response">
              {t('Incident Response')}
            </MenuItem>
            <MenuItem key="standard-operating-procedure" value="standard-operating-procedure">
              {t('Standard Operating Procedures')}
            </MenuItem>
            <MenuItem key="crisis-communication" value="crisis-communication">
              {t('Crisis Communication')}
            </MenuItem>
            <MenuItem key="strategic-reaction" value="strategic-reaction">
              {t('Strategic Reaction')}
            </MenuItem>
          </SelectField>
        </GridLegacy>
      </GridLegacy>

      <SelectField
        variant="standard"
        fullWidth={true}
        name="attack_chain_run_severity"
        label={t('Severity')}
        style={{ marginTop: 20 }}
        error={!!errors.attack_chain_run_severity}
        control={control}
        defaultValue={initialValues.attack_chain_run_severity}
      >
        <MenuItem key="low" value="low">
          {t('Low')}
        </MenuItem>
        <MenuItem key="medium" value="medium">
          {t('Medium')}
        </MenuItem>
        <MenuItem key="high" value="high">
          {t('High')}
        </MenuItem>
        <MenuItem key="critical" value="critical">
          {t('Critical')}
        </MenuItem>
      </SelectField>
      <TextField
        variant="standard"
        fullWidth
        multiline
        rows={2}
        label={t('Description')}
        style={{ marginTop: 20 }}
        error={!!errors.attack_chain_run_description}
        helperText={errors.attack_chain_run_description?.message}
        inputProps={register('attack_chain_run_description')}
        control={control}
      />
      {!edit
        && (
          <Controller
            control={control}
            name="attack_chain_run_start_date"
            render={({ field }) => (
              <MuiDateTimePicker
                value={field.value ? new Date(field.value) : null}
                label={t('Start date (optional)')}
                minDateTime={new Date()}
                slotProps={{
                  textField: {
                    variant: 'standard',
                    fullWidth: true,
                    style: { marginTop: 20 },
                    error: !!errors.attack_chain_run_start_date,
                    helperText: errors.attack_chain_run_start_date?.message,
                  },
                }}
                onChange={date => field.onChange(date?.toISOString())}
                ampm={false}
                format="yyyy-MM-dd HH:mm:ss"
              />
            )}
          />
        )}
      <Controller
        control={control}
        name="attack_chain_run_tags"
        render={({ field: { onChange, value }, fieldState: { error } }) => (
          <TagField
            label={t('Tags')}
            fieldValue={value ?? []}
            fieldOnChange={onChange}
            error={error}
            style={{ marginTop: 20 }}
          />
        )}
      />

      <Typography
        variant="h2"
        gutterBottom
        style={{ marginTop: 40 }}
      >
        {t('Emails and SMS')}
      </Typography>

      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Sender email address')}
        style={{ marginTop: 20 }}
        error={!!errors.attack_chain_run_mail_from}
        helperText={errors.attack_chain_run_mail_from && errors.attack_chain_run_mail_from?.message}
        inputProps={register('attack_chain_run_mail_from')}
        disabled={disabled}
      />

      <Controller
        control={control}
        name="attack_chain_run_mails_reply_to"
        render={({ field, fieldState }) => {
          return (
            <Autocomplete
              multiple
              id="email-reply-to-input"
              freeSolo
              open={false}
              options={[]}
              value={field.value}
              onChange={() => {
                if (undefined !== field.value && inputValue !== '' && !field.value.includes(inputValue)) {
                  field.onChange([...(field.value || []), inputValue.trim()]);
                }
              }}
              onBlur={field.onBlur}
              inputValue={inputValue}
              onInputChange={(_event, newInputValue) => {
                setInputValue(newInputValue);
              }}
              disableClearable={true}
              renderTags={(tags: string[], getTagProps) => tags.map((email: string, index: number) => {
                return (
                  <Chip
                    variant="outlined"
                    label={email}
                    {...getTagProps({ index })}
                    key={email}
                    style={{ borderRadius: 4 }}
                    onDelete={() => {
                      const newValue = [...(field.value || [])];
                      newValue.splice(index, 1);
                      field.onChange(newValue);
                    }}
                  />
                );
              })}
              renderInput={params => (
                <MuiTextField
                  {...params}
                  variant="standard"
                  label={t('Reply to')}
                  style={{ marginTop: 20 }}
                  error={!!fieldState.error}
                  helperText={errors.attack_chain_run_mails_reply_to?.find ? errors.attack_chain_run_mails_reply_to?.find(value => value != null)?.message ?? '' : ''}
                />
              )}
            />
          );
        }}
      />
      <Alert
        severity="warning"
        variant="outlined"
        style={{
          position: 'relative',
          border: 'none',
        }}
      >
        <AlertTitle>
          {t('If you remove the default email address, the email reception for this attack_chain_run / attack_chain will be disabled.')}
        </AlertTitle>
      </Alert>
      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Messages header')}
        style={{ marginTop: 20 }}
        error={!!errors.attack_chain_run_message_header}
        helperText={errors.attack_chain_run_message_header && errors.attack_chain_run_message_header?.message}
        inputProps={register('attack_chain_run_message_header')}
        disabled={disabled}
      />
      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Messages footer')}
        style={{ marginTop: 20 }}
        error={!!errors.attack_chain_run_message_footer}
        helperText={errors.attack_chain_run_message_footer && errors.attack_chain_run_message_footer?.message}
        inputProps={register('attack_chain_run_message_footer')}
        disabled={disabled}
      />
      <div style={{
        float: 'right',
        marginTop: 20,
      }}
      >
        <Button
          variant="contained"
          onClick={handleClose}
          style={{ marginRight: 10 }}
          disabled={isSubmitting}
        >
          {t('Cancel')}
        </Button>
        <Button
          variant="contained"
          color="secondary"
          type="submit"
          disabled={!isDirty || isSubmitting}
        >
          {edit ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default AttackChainRunForm;
