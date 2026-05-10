import { zodResolver } from '@hookform/resolvers/zod';
import { Autocomplete, Button, Checkbox, Chip, FormControlLabel, MenuItem, TextField as MuiTextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';

import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import useTabs from '../../../components/common/tabs/useTabs';
import SelectField from '../../../components/fields/SelectField';
import TagField from '../../../components/fields/TagField';
import TextField from '../../../components/fields/TextField';
import { useFormatter } from '../../../components/i18n';
import { type AttackChainInput } from '../../../utils/api-types';
import { zodImplement } from '../../../utils/Zod';
import { scenarioCategories } from './constants';

interface Props {
  onSubmit: (data: AttackChainInput, isAttackChainAssistantChecked?: boolean) => void;
  handleClose: () => void;
  editing?: boolean;
  disabled?: boolean;
  initialValues: AttackChainInput;
  isCreation?: boolean;
}

const AttackChainForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing,
  initialValues,
  disabled,
  isCreation = false,
}) => {
  // Standard hooks
  const theme = useTheme();
  const { t } = useFormatter();
  const [inputValue, setInputValue] = useState('');
  const [isAttackChainAssistantChecked, setIsAttackChainAssistantChecked] = useState(false);

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<AttackChainInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<AttackChainInput>().with({
        attack_chain_name: z.string().min(1, { message: t('Should not be empty') }),
        attack_chain_category: z.string().optional(),
        attack_chain_main_focus: z.string().optional(),
        attack_chain_severity: z.enum(['low', 'medium', 'high', 'critical']).optional(),
        attack_chain_subtitle: z.string().optional(),
        attack_chain_description: z.string().optional(),
        attack_chain_tags: z.string().array().optional(),
        attack_chain_external_reference: z.string().optional(),
        attack_chain_external_url: z.string().optional(),
        attack_chain_mail_from: z.string().email(t('Should be a valid email address')).optional(),
        attack_chain_mails_reply_to: z.array(z.string().email(t('Should be a valid email address'))).optional(),
        attack_chain_message_header: z.string().optional(),
        attack_chain_message_footer: z.string().optional(),
        attack_chain_custom_dashboard: z.string().optional(),
      }),
    ),
    defaultValues: initialValues,
  });

  const tabEntries: TabsEntry[] = [{
    key: 'General',
    label: t('General'),
  }, {
    key: 'Emails and SMS',
    label: t('Emails and SMS'),
  }];
  const { currentTab, handleChangeTab } = useTabs(tabEntries[0].key);

  return (
    <>
      <Tabs
        entries={tabEntries}
        currentTab={currentTab}
        onChange={newValue => handleChangeTab(newValue)}
      />
      <form
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
          marginTop: theme.spacing(2),
        }}
        id="scenarioForm"
        onSubmit={handleSubmit((data: AttackChainInput) => onSubmit(data, isAttackChainAssistantChecked))}
      >
        {currentTab === 'General' && (
          <>
            <TextField
              variant="standard"
              fullWidth
              label={t('Name')}
              error={!!errors.attack_chain_name}
              helperText={errors.attack_chain_name?.message}
              inputProps={register('attack_chain_name')}
              InputLabelProps={{ required: true }}
              control={control}
            />
            <div style={{
              display: 'flex',
              flexDirection: 'row',
              gap: 20,
            }}
            >
              <SelectField
                variant="standard"
                fullWidth={true}
                name="attack_chain_category"
                label={t('Category')}
                error={!!errors.attack_chain_category}
                control={control}
                defaultValue={initialValues.attack_chain_category}
              >
                {Array.from(scenarioCategories).map(([key, value]) => (
                  <MenuItem key={key} value={key}>
                    {t(value)}
                  </MenuItem>
                ))}
              </SelectField>
              <SelectField
                variant="standard"
                fullWidth={true}
                name="attack_chain_main_focus"
                label={t('Main focus')}
                error={!!errors.attack_chain_main_focus}
                control={control}
                defaultValue={initialValues.attack_chain_main_focus}
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
            </div>
            <SelectField
              variant="standard"
              fullWidth={true}
              name="attack_chain_severity"
              label={t('Severity')}
              error={!!errors.attack_chain_severity}
              control={control}
              defaultValue={initialValues.attack_chain_severity}
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
              rows={5}
              label={t('Description')}
              error={!!errors.attack_chain_description}
              helperText={errors.attack_chain_description?.message}
              inputProps={register('attack_chain_description')}
              control={control}
            />
            <Controller
              control={control}
              name="attack_chain_tags"
              render={({ field: { onChange, value }, fieldState: { error } }) => (
                <TagField
                  label={t('Tags')}
                  fieldValue={value ?? []}
                  fieldOnChange={onChange}
                  error={error}
                />
              )}
            />
            {isCreation && (
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={isAttackChainAssistantChecked}
                    onChange={() => setIsAttackChainAssistantChecked(!isAttackChainAssistantChecked)}
                  />
                )}
                label={t('Use the attack_chain assistant')}
              />
            )}
          </>
        )}
        {currentTab === 'Emails and SMS' && (
          <>
            <MuiTextField
              variant="standard"
              fullWidth
              label={t('Sender email address')}
              error={!!errors.attack_chain_mail_from}
              helperText={
                errors.attack_chain_mail_from
                  ? errors.attack_chain_mail_from?.message
                  : (
                      <span
                        style={{ color: theme.palette.warning.main }}
                      >
                        {t('If you remove the default email address, the email reception for this attack_chain_run / attack_chain will be disabled.')}
                      </span>
                    )
              }
              inputProps={register('attack_chain_mail_from')}
              disabled={disabled}
            />
            <Controller
              control={control}
              name="attack_chain_mails_reply_to"
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
                        error={!!fieldState.error}
                        helperText={errors.attack_chain_mails_reply_to?.find ? errors.attack_chain_mails_reply_to?.find(value => value != null)?.message ?? '' : ''}
                      />
                    )}
                  />
                );
              }}
            />
            <MuiTextField
              variant="standard"
              fullWidth
              label={t('Messages header')}
              error={!!errors.attack_chain_message_header}
              helperText={errors.attack_chain_message_header && errors.attack_chain_message_header?.message}
              inputProps={register('attack_chain_message_header')}
              disabled={disabled}
            />
            <MuiTextField
              variant="standard"
              fullWidth
              label={t('Messages footer')}
              error={!!errors.attack_chain_message_footer}
              helperText={errors.attack_chain_message_footer && errors.attack_chain_message_footer?.message}
              inputProps={register('attack_chain_message_footer')}
              disabled={disabled}
            />
          </>
        )}
        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: theme.spacing(1),
        }}
        >
          <Button
            variant="contained"
            onClick={handleClose}
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
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </>
  );
}
;

export default AttackChainForm;
;
