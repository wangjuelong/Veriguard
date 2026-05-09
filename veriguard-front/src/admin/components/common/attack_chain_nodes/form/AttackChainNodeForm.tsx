import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext, useEffect, useState } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z, type ZodIssue, type ZodObject } from 'zod/v4';

import TagFieldController from '../../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import {
  type AttackChainNode,
  type AttackChainNodeInput,
  type AttackPattern,
  type Variable,
} from '../../../../../utils/api-types';
import { type ContractElement, type EnhancedContractElement, type NodeContractConverted } from '../../../../../utils/api-types-custom';
import { splitDuration } from '../../../../../utils/Time';
import { PermissionsContext } from '../../Context';
import { getValidatingRule, isAttackChainNodeContentType, isRequiredField, isVisibleField } from '../utils';
import AttackChainNodeContentForm from './AttackChainNodeContentForm';

const useStyles = makeStyles()(theme => ({
  injectFormContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2),
    marginTop: theme.spacing(2),
  },
  injectFormButtonsContainer: {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: theme.spacing(1),
    marginTop: theme.spacing(1),
  },
  injectContentButton: {
    width: '100%',
    height: theme.spacing(5),
  },
  triggerBox: {
    borderRadius: 4,
    display: 'flex',
    alignItems: 'center',
    padding: theme.spacing(1),
    textWrap: 'nowrap',
    gap: theme.spacing(3),
  },
  triggerBoxColor: { border: `1px solid ${theme.palette.primary.main}` },
  triggerBoxColorDisabled: { border: `1px solid ${theme.palette.action.disabled}` },
  triggerText: {
    fontFamily: 'Consolas, monaco, monospace',
    fontSize: 12,
  },
  triggerTextColor: { color: theme.palette.primary.main },
  triggerTextColorDisabled: { color: theme.palette.action.disabled },
}));

type FieldValue = string | number | boolean | string[] | AttackPattern[] | object | {
  key: string;
  value: string;
  type?: string;
}
| {
  key: string;
  value: string;
  type?: string;
}[];

type AttackChainNodeInputForm = Omit<AttackChainNodeInput, 'node_depends_duration'> & {
  node_depends_duration_days?: string;
  node_depends_duration_hours?: string;
  node_depends_duration_minutes?: string;
};

interface Props {
  handleClose: () => void;
  disabled?: boolean;
  isAtomic: boolean;
  isCreation?: boolean;
  defaultAttackChainNode: AttackChainNode | Omit<AttackChainNode, 'node_created_at' | 'node_updated_at'>;
  onSubmitAttackChainNode: (data: AttackChainNodeInput) => Promise<void>;
  injectorContractContent?: NodeContractConverted['convertedContent'];
  uriVariable: string;
  variablesFromAttackChainRunOrAttackChain: Variable[];
}

const initialZodSchema = z.object({ node_content: z.object({}) });

const AttackChainNodeForm = ({
  handleClose,
  disabled = false,
  isAtomic,
  isCreation = false,
  defaultAttackChainNode = {} as AttackChainNode,
  injectorContractContent,
  onSubmitAttackChainNode,
  uriVariable,
  variablesFromAttackChainRunOrAttackChain,
}: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);
  const [fieldsMapByKey, setFieldsMapByKey] = useState<Record<ContractElement['key'], ContractElement>>({});
  const [enhancedFields, setEnhancedFields] = useState<EnhancedContractElement[]>([]);
  const [enhancedFieldsMapByType, setEnhancedFieldsMapByType] = useState<Map<ContractElement['type'], EnhancedContractElement>>(new Map());
  const [defaultValues, setDefaultValues] = useState<Partial<AttackChainNodeInputForm>>({});
  const [mandatoryKeys, setMandatoryKeys] = useState<ZodObject>(initialZodSchema);
  const [mandatoryGroupKeys, setMandatoryGroupKeys] = useState<ZodObject>(initialZodSchema);
  const notDynamicFields = [
    'teams',
    'assets',
    'asset_groups',
    'articles',
    'challenges',
    'attachments',
    'expectations',
  ];

  const getInitialValues = (): Record<string, FieldValue> => {
    const duration = splitDuration(defaultAttackChainNode?.node_depends_duration || 0);
    const initialValues = {
      ...defaultAttackChainNode,
      node_content: (defaultAttackChainNode?.node_content ?? {}) as Record<string, FieldValue>,
      node_tags: defaultAttackChainNode?.node_tags || [],
      node_depends_duration_days: duration.days,
      node_depends_duration_hours: duration.hours,
      node_depends_duration_minutes: duration.minutes,
      node_all_teams: defaultAttackChainNode?.node_all_teams ?? false,
      node_teams: defaultAttackChainNode?.node_teams ?? [],
    };

    // Enrich initialValues with default contract value
    if (injectorContractContent) {
      injectorContractContent.fields
        .filter(f => !notDynamicFields.includes(f.key))
        .forEach((field: ContractElement) => {
          if (!initialValues.node_content[field.key]) {
            initialValues.node_content = {
              ...initialValues.node_content,
              [field.key]: (field.cardinality === '1' ? field.defaultValue?.[0] : field.defaultValue) || '',
            };
          }

          // Specific richText type field
          if (
            field.type === 'textarea'
            && field.richText
          ) {
            initialValues.node_content[field.key] = (initialValues.node_content[field.key] as string)
              .replaceAll('<#list challenges as challenge>', '&lt;#list challenges as challenge&gt;')
              .replaceAll('<#list articles as article>', '&lt;#list articles as article&gt;')
              .replaceAll('</#list>', '&lt;/#list&gt;');
          }
        });
    }
    return initialValues;
  };

  const formatAttackChainNodeContentData = (content: Record<string, FieldValue>): object | null => {
    const formattedContent = { ...content };
    injectorContractContent?.fields
      .filter(f => !notDynamicFields.includes(f.key))
      .forEach((field) => {
        if (field.type === 'number' && typeof formattedContent[field.key] === 'string') {
          formattedContent[field.key] = parseInt(formattedContent[field.key].toString(), 10);

        // Specific richText type field
        } else if (
          field.type === 'textarea'
          && field.richText
          && (String(formattedContent[field.key]))?.length > 0
        ) {
          const regex = /&lt;#list\s+(\w+)\s+as\s+(\w+)&gt;/g;
          formattedContent[field.key] = (formattedContent[field.key] as string)
            .replace(regex, (_, listName, identifier) => `<#list ${listName} as ${identifier}>`)
            .replaceAll('&lt;/#list&gt;', '</#list>');
        }
      });
    return formattedContent;
  };

  const strictKeys = {
    node_title: z.string().min(1, { message: t('This field is required.') }),
    node_depends_duration_days: z.string().min(1, { message: t('This field is required.') }).optional(),
    node_depends_duration_hours: z.string().min(1, { message: t('This field is required.') }).optional(),
    node_depends_duration_minutes: z.string().min(1, { message: t('This field is required.') }).optional(),
  };

  const methods = useForm<AttackChainNodeInputForm>({
    mode: isCreation ? 'onSubmit' : 'all',
    reValidateMode: isCreation ? 'onSubmit' : 'onChange',
    resolver: zodResolver(z.object({
      ...strictKeys,
      ...mandatoryKeys.shape,
    }).check(({ value, issues }) => {
      if (isCreation) return;

      const parsedTeamError = z.object({ node_teams: z.array(z.string()).min(1, { message: t('Required') }).default([]) }).safeParse(value);
      const injectTeamsError = parsedTeamError?.error?.issues.find(i => i.path.includes('node_teams'));
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      if (injectTeamsError && !value.node_all_teams) {
        issues.push({
          ...injectTeamsError,
          message: t('At least one of these fields is required.'),
        });
      }

      const parsed = mandatoryGroupKeys.safeParse(value);
      if (!parsed?.error?.issues) return;
      injectorContractContent?.fields.forEach((field) => {
        if (field.mandatoryGroups) {
          const newIssues: (ZodIssue & { currentField?: boolean })[] = [];
          field.mandatoryGroups.forEach((mandatoryField) => {
            const issue = parsed.error.issues.find(err => isAttackChainNodeContentType(fieldsMapByKey[mandatoryField].type) ? err.path[1] === mandatoryField : err.path[0] === `node_${mandatoryField}`);
            if (issue) {
              newIssues.push({
                ...issue,
                message: t('At least one of these fields is required.'),
                ...(mandatoryField === field.key && { currentField: true }),
              });
            }
          });
          if (newIssues.length === field.mandatoryGroups.length) {
            newIssues.filter(i => !i.currentField).forEach(i => issues.push(i));
          }
        }
      });
    })),
    defaultValues: defaultValues,
  });

  const cleanInvisibleFields = (injectValues: AttackChainNodeInput) => {
    injectorContractContent?.fields.forEach((fieldContract) => {
      if (isVisibleField(fieldContract, injectorContractContent.fields, injectValues)) {
        return;
      }

      const isNotDynamic = notDynamicFields.includes(fieldContract.key);
      const targetObj = (isNotDynamic ? injectValues : injectValues.node_content) as Record<string, unknown>;
      const keyProp = isNotDynamic ? `node_${fieldContract.key}` : fieldContract.key;
      targetObj[keyProp] = Array.isArray(targetObj[keyProp]) ? [] : '';
    });
  };

  const { handleSubmit, reset, subscribe, getValues, setError, clearErrors, trigger, formState: { isSubmitting } } = methods;
  const onSubmit: SubmitHandler<AttackChainNodeInputForm> = async (data) => {
    // we cannot save, even in draft, without title
    if (!data.node_title?.length) {
      setError('node_title', { message: t('This field is required.') });
      return;
    }
    if (injectorContractContent) {
      const node_depends_duration = Number(data.node_depends_duration_days) * 3600 * 24
        + Number(data.node_depends_duration_hours) * 3600
        + Number(data.node_depends_duration_minutes) * 60;
      const values = {
        node_title: data.node_title,
        node_injector_contract: injectorContractContent.contract_id,
        node_description: data.node_description as string,
        node_tags: data.node_tags,
        node_content: formatAttackChainNodeContentData(data.node_content as Record<string, FieldValue>),
        node_all_teams: data.node_all_teams,
        node_teams: data.node_all_teams ? [] : data.node_teams,
        node_assets: data.node_assets,
        node_asset_groups: data.node_asset_groups,
        node_documents: data.node_documents,
        node_depends_duration,
        node_depends_on: data.node_depends_on ? data.node_depends_on : [],
      } as AttackChainNodeInput;
      cleanInvisibleFields(values);
      await onSubmitAttackChainNode(values);
    }
    handleClose();
  };

  useEffect(() => {
    const fieldsToSubscribe: (keyof AttackChainNodeInputForm)[] = [];
    injectorContractContent?.fields.forEach((field) => {
      if (field.key === 'teams') {
        fieldsToSubscribe.push('node_all_teams');
      }
      if (field.mandatoryConditionFields?.length) {
        field.mandatoryConditionFields.forEach((mandatoryConditionField) => {
          const mandatoryConditionFieldType = injectorContractContent?.fields.find(f => f.key === mandatoryConditionField)?.type;
          const fieldToSubscribe = ((mandatoryConditionFieldType && isAttackChainNodeContentType(mandatoryConditionFieldType)) ? `node_content.${mandatoryConditionField}` : `node_${mandatoryConditionField}`) as (keyof AttackChainNodeInputForm);
          if (fieldsToSubscribe.indexOf(fieldToSubscribe) === -1) {
            fieldsToSubscribe.push(fieldToSubscribe);
          }
        });
      } else if (field.visibleConditionFields?.length) {
        field.visibleConditionFields.forEach((visibleConditionField) => {
          const visibleConditionFieldType = injectorContractContent?.fields.find(f => f.key === visibleConditionField)?.type;
          const fieldToSubscribe = ((visibleConditionFieldType && isAttackChainNodeContentType(visibleConditionFieldType)) ? `node_content.${visibleConditionField}` : `node_${visibleConditionField}`) as (keyof AttackChainNodeInputForm);
          if (fieldsToSubscribe.indexOf(fieldToSubscribe) === -1) {
            fieldsToSubscribe.push(fieldToSubscribe);
          }
        });
      }
    });

    const unsubscribe = subscribe({
      name: fieldsToSubscribe,
      exact: true,
      formState: { values: true },
      callback: ({ values }) => {
        const newEnhancedFields: EnhancedContractElement[] = [];
        const newEnhancedFieldsMapByType: Map<ContractElement['type'], EnhancedContractElement> = new Map();

        let manda: ZodObject = initialZodSchema;
        let mandaGroup: ZodObject = initialZodSchema;

        injectorContractContent?.fields.forEach((field) => {
          const isAttackChainNodeContent = isAttackChainNodeContentType(field.type);
          const isRequired = isRequiredField(field, injectorContractContent?.fields, values);
          const isVisible = isVisibleField(field, injectorContractContent?.fields, values);
          const enhancedField = {
            ...field,
            key: isAttackChainNodeContent ? `node_content.${field.key}` : `node_${field.key}`,
            originalKey: field.key,
            isAttackChainNodeContentType: isAttackChainNodeContent && field.type !== 'expectation',
            isVisible,
            isInMandatoryGroup: !!field.mandatoryGroups?.length,
            mandatoryGroupContractElementLabels: injectorContractContent?.fields.filter(f => field.mandatoryGroups?.includes(f.key)).reduce((acc, f, index) => {
              let newAcc = acc;
              if (index !== 0) newAcc += ', ';
              newAcc += t(f.label);
              return newAcc;
            }, ''),
            settings: { required: isRequired },
          };
          newEnhancedFields.push(enhancedField);
          newEnhancedFieldsMapByType.set(field.type, enhancedField);

          if (field.key === 'teams') {
            clearErrors(`node_teams` as (keyof AttackChainNodeInputForm));
            manda = z.object({
              ...manda.shape,
              node_all_teams: z.boolean(),
              node_teams: z.string().array().optional(),
            });
            return;
          }

          if (!isCreation) {
            if (isRequired) {
              const validatingRule = getValidatingRule(field, t);
              if (isAttackChainNodeContent) {
                clearErrors(`node_content.${field.key}` as (keyof AttackChainNodeInputForm));
                manda = z.object({
                  ...manda.shape,
                  node_content: z.object({
                    ...manda.shape.node_content.shape,
                    [field.key]: validatingRule,
                  }),
                });
              } else {
                clearErrors(`node_${field.key}` as (keyof AttackChainNodeInputForm));
                manda = z.object({
                  ...manda.shape,
                  [`node_${field.key}`]: validatingRule,
                });
              }
            } else if (field.mandatoryGroups) {
              const validatingRule = getValidatingRule(field, t);

              if (isAttackChainNodeContent) {
                mandaGroup = z.object({
                  ...mandaGroup.shape,
                  node_content: z.object({
                    ...mandaGroup.shape.node_content.shape,
                    [field.key]: validatingRule,
                  }),
                });
                manda = z.object({
                  ...manda.shape,
                  node_content: z.object({
                    ...manda.shape.node_content.shape,
                    [field.key]: z.any(),
                  }),
                });
              } else {
                mandaGroup = z.object({
                  ...mandaGroup.shape,
                  [`node_${field.key}`]: validatingRule,
                });
                manda = z.object({
                  ...manda.shape,
                  [`node_${field.key}`]: z.any(),
                });
              }
            }
          }
        });
        if (!isCreation) {
          setMandatoryKeys(manda);
          setMandatoryGroupKeys(mandaGroup);
        }
        setEnhancedFields(newEnhancedFields);
        setEnhancedFieldsMapByType(newEnhancedFieldsMapByType);
      },
    });
    return unsubscribe;
  }, [subscribe, injectorContractContent]);

  useEffect(() => {
    let unsubscribe;
    if (!isCreation) {
      const mandatoryGroupFields = (injectorContractContent?.fields.filter(field => field.mandatoryGroups?.length).map(field => isAttackChainNodeContentType(field.type) ? `node_content.${field.key}` : `node_${field.key}`) || []) as (keyof AttackChainNodeInputForm)[];
      unsubscribe = subscribe({
        name: mandatoryGroupFields,
        exact: true,
        formState: { values: true },
        callback: () => {
          trigger(mandatoryGroupFields as (keyof AttackChainNodeInputForm)[]);
        },
      });
    }
    return unsubscribe;
  }, [subscribe, injectorContractContent]);

  useEffect(() => {
    if (!isCreation) {
      trigger();
    }
  }, [isCreation, mandatoryKeys]);

  useEffect(() => {
    if (injectorContractContent?.fields) {
      setFieldsMapByKey(injectorContractContent.fields.reduce<Record<ContractElement['key'], ContractElement>>((acc, field) => {
        acc[field.key] = field;
        return acc;
      }, {}));
    }
  }, [injectorContractContent]);

  // Update mode:
  // In update mode, we want to initialize the form with the current node values,
  // but we do NOT want to reset again if injectorContractContent changes later,
  // otherwise user edits would be lost.
  useEffect(() => {
    if (!isCreation) {
      const initialValues = getInitialValues();
      reset(initialValues);
      setDefaultValues(initialValues);
    }
  }, []);

  // Create mode:
  // In create mode, when the user selects a new contract, we need to reset
  // the form with the new default values provided by this contract.
  // This ensures the form is pre-filled according to the selected contract.
  useEffect(() => {
    if (isCreation) {
      const initialValues = getInitialValues();
      reset(initialValues);
      setDefaultValues(initialValues);
    }
  }, [injectorContractContent, isCreation]);

  if (Object.keys(defaultValues).length === 0) {
    return <Loader />;
  }

  return (
    <FormProvider {...methods}>
      <form
        id="injectForm"
        noValidate // disabled tooltip
        className={classes.injectFormContainer}
        onSubmit={handleSubmit(onSubmit)}
      >
        <TextFieldController name="node_title" label={t('Title')} required disabled={isSubmitting || disabled || permissions.readOnly} />
        <TextFieldController name="node_description" label={t('Description')} multiline rows={2} disabled={isSubmitting || disabled || permissions.readOnly} />
        <TagFieldController name="node_tags" label={t('Tags')} disabled={isSubmitting || disabled || permissions.readOnly} />

        {!isAtomic && (
          <div className={`${classes.triggerBox} ${isSubmitting || disabled || permissions.readOnly ? classes.triggerBoxColorDisabled : classes.triggerBoxColor}`}>
            <div className={`${classes.triggerText} ${isSubmitting || disabled || permissions.readOnly ? classes.triggerTextColorDisabled : classes.triggerTextColor}`}>{t('Trigger after')}</div>
            <TextFieldController name="node_depends_duration_days" label={t('Days')} type="number" disabled={permissions.readOnly} />
            <TextFieldController name="node_depends_duration_hours" label={t('Hours')} type="number" disabled={permissions.readOnly} />
            <TextFieldController name="node_depends_duration_minutes" label={t('Minutes')} type="number" disabled={permissions.readOnly} />
          </div>
        )}

        {injectorContractContent && (
          <AttackChainNodeContentForm
            enhancedFields={enhancedFields}
            enhancedFieldsMapByType={enhancedFieldsMapByType}
            injectorContractVariables={injectorContractContent.variables || []}
            readOnly={isSubmitting || disabled || permissions.readOnly}
            injectId={defaultAttackChainNode.node_id}
            isAtomic={isAtomic}
            isCreation={isCreation}
            uriVariable={uriVariable}
            variables={variablesFromAttackChainRunOrAttackChain}
          />
        )}

        <div
          className={classes.injectFormButtonsContainer}
          style={{
            marginBottom: theme.spacing(2),
            marginRight: theme.spacing(2),
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
            onClick={() => {
              onSubmit(getValues());
            }}
            disabled={isSubmitting || disabled || permissions.readOnly}
          >
            {isCreation ? t('Create') : t('Update')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default AttackChainNodeForm;
