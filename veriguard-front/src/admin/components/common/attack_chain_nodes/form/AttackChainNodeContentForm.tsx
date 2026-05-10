import { HelpOutlineOutlined, RotateLeftOutlined } from '@mui/icons-material';
import { Button, IconButton, InputLabel, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext, useState } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';

import type { ContractVariable } from '../../../../../actions/contract/contract';
import SwitchFieldController from '../../../../../components/fields/SwitchFieldController';
import { useFormatter } from '../../../../../components/i18n';
import type { Variable } from '../../../../../utils/api-types';
import { type ContractElement, type EnhancedContractElement } from '../../../../../utils/api-types-custom';
import { AbilityContext, Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import AssetGroupPopover from '../../../assets/asset_groups/AssetGroupPopover';
import AssetGroupsList from '../../../assets/asset_groups/AssetGroupsList';
import AttackChainNodeAddAssetGroups from '../../../attack_chain_runs/attack_chain_run/attack_chain_nodes/asset_groups/AttackChainNodeAddAssetGroups';
import AvailableVariablesDialog from '../../../attack_chain_runs/attack_chain_run/variables/AvailableVariablesDialog';
import AttackChainNodeExpectations from '../expectations/AttackChainNodeExpectations';
import type { ExpectationInput } from '../expectations/Expectation';
import AttackChainNodeContentFieldComponent from './AttackChainNodeContentFieldComponent';
import AttackChainNodeChallengesList from './challenges/AttackChainNodeChallengesList';
import AttackChainNodeDocumentsList from './documents/AttackChainNodeDocumentsList';
import AttackChainNodeEndpointsList from './endpoints/AttackChainNodeEndpointsList';
import AttackChainNodeTeamsList from './teams/AttackChainNodeTeamsList';

interface Props {
  enhancedFields: EnhancedContractElement[];
  enhancedFieldsMapByType: Map<ContractElement['type'], EnhancedContractElement>;
  injectorContractVariables: ContractVariable[];
  injectId: string;
  isAtomic: boolean;
  isCreation: boolean;
  readOnly?: boolean;
  uriVariable?: string;
  variables?: Variable[];
}

const AttackChainNodeContentForm = ({
  enhancedFields,
  enhancedFieldsMapByType,
  injectorContractVariables,
  injectId,
  isAtomic,
  readOnly,
  uriVariable = '',
  variables = [],
}: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { control, setValue, getValues, formState: { errors } } = useFormContext();
  const ability = useContext(AbilityContext);

  const renderTitle = (title: string, required: boolean = false, err: boolean = false) => {
    return (
      <Typography variant="h5" color={err ? 'error' : 'textPrimary'}>
        {title}
        {required ? '*' : '' }
      </Typography>
    );
  };

  // -- TEAMS --
  const renderTeams = (err?: string | null) => (
    <AttackChainNodeTeamsList
      readOnly={enhancedFieldsMapByType.get('team')?.readOnly || readOnly}
      hideEnabledUsersNumber={isAtomic}
      error={err}
    />
  );

  // -- ENDPOINTS --
  const renderSourceAssets = (err?: string | null, isInMandatoryGroup?: boolean, mandatoryGroupContractElementLabels?: string) => (
    <div key="asset">
      <InputLabel required={enhancedFieldsMapByType.get('asset')?.settings?.required} error={!!err}>{t(enhancedFieldsMapByType.get('asset')?.label || 'Assets')}</InputLabel>
      <AttackChainNodeEndpointsList
        name="node_assets"
        disabled={enhancedFieldsMapByType.get('asset')?.readOnly || readOnly}
        platforms={getValues('node_injector_contract.injector_contract_platforms')}
        architectures={getValues('node_injector_contract.injector_contract_arch')}
        errorLabel={err && isInMandatoryGroup ? t('At least one is required ({labels})', { labels: mandatoryGroupContractElementLabels || '' }) : err}
        label={isInMandatoryGroup && t('At least one is required ({labels})', { labels: mandatoryGroupContractElementLabels || '' })}
      />
    </div>
  );

  // -- ASSETS GROUPS --
  const injectAssetGroupIds = useWatch({
    control,
    name: 'node_asset_groups',
  }) as string[];
  const onAssetGroupChange = (assetGroupIds: string[]) => setValue('node_asset_groups', assetGroupIds, { shouldValidate: true });
  const removeAssetGroup = (assetGroupId: string) => setValue('node_asset_groups', injectAssetGroupIds.filter(id => id !== assetGroupId), { shouldValidate: true });

  const renderSourceAssetGroups = (err?: string | null, isInMandatoryGroup?: boolean, mandatoryGroupContractElementLabels?: string) => (
    <div key="asset-group">
      <InputLabel required={enhancedFieldsMapByType.get('asset-group')?.settings?.required} error={!!err}>{t(enhancedFieldsMapByType.get('asset-group')?.label || 'Asset groups')}</InputLabel>
      <AssetGroupsList
        assetGroupIds={injectAssetGroupIds}
        renderActions={assetGroup => (
          <AssetGroupPopover
            disabled={enhancedFieldsMapByType.get('asset-group')?.readOnly || readOnly}
            assetGroup={assetGroup}
            inline
            onRemoveAssetGroupFromList={removeAssetGroup}
          />
        )}
      />

      <Can I={ACTIONS.ACCESS} a={SUBJECTS.ASSETS}>
        <AttackChainNodeAddAssetGroups
          disabled={enhancedFieldsMapByType.get('asset-group')?.readOnly || readOnly}
          assetGroupIds={injectAssetGroupIds}
          onSubmit={onAssetGroupChange}
          errorLabel={err && isInMandatoryGroup ? t('At least one is required ({labels})', { labels: mandatoryGroupContractElementLabels || '' }) : err}
          label={isInMandatoryGroup && t('At least one is required ({labels})', { labels: mandatoryGroupContractElementLabels || '' })}
        />
      </Can>
    </div>
  );

  // -- CHALLENGES --
  const renderChallenges = (err?: string | null) => (
    <AttackChainNodeChallengesList
      readOnly={enhancedFieldsMapByType.get('challenge')?.readOnly || readOnly}
      error={err}
    />
  );

  // -- EXPECTATIONS --
  const injectExpectations = useWatch({
    control,
    name: 'node_content.expectations',
  }) as ExpectationInput[];
  const onExpectationChange = (expectationIds: ExpectationInput[]) => setValue('node_content.expectations', expectationIds, { shouldValidate: true });

  const renderExpectations = () => (
    <AttackChainNodeExpectations
      expectationDatas={injectExpectations}
      handleExpectations={onExpectationChange}
      readOnly={enhancedFieldsMapByType.get('expectation')?.readOnly || readOnly}
      injectId={injectId}
      injectorContractId={getValues('node_injector_contract.injector_contract_id')}
    />
  );

  // -- DOCUMENTS --
  const renderDocuments = () => (
    <AttackChainNodeDocumentsList
      hasAttachments={enhancedFieldsMapByType.has('attachment')}
      readOnly={enhancedFieldsMapByType.get('attachment')?.readOnly || readOnly}
    />
  );

  // -- DYNAMIC FIELDS --
  const [openVariables, setOpenVariables] = useState(false);
  const openVariablesDialog = () => setOpenVariables(true);
  const dynamicFields = enhancedFields.filter(field => field.isAttackChainNodeContentType || field.type === 'asset-group' || field.type === 'asset');

  const resetDefaultValue = () => {
    dynamicFields
      .forEach((field) => {
        let defaultValue = field.cardinality === '1' ? (field.defaultValue?.[0] || '') : field.defaultValue;
        if (
          field.type === 'textarea'
          && field.richText
          && defaultValue
          && defaultValue.length > 0
        ) {
          defaultValue = (defaultValue as string ?? '').replaceAll('<#list challenges as challenge>', '&lt;#list challenges as challenge&gt;')
            .replaceAll('<#list articles as article>', '&lt;#list articles as article&gt;')
            .replaceAll('</#list>', '&lt;/#list&gt;');
        }
        setValue(field.key, defaultValue);
      });
  };

  const renderDynamicFields = () => (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      {
        (dynamicFields ?? []).filter(field => field.isVisible).map((field) => {
          if (field.type === 'asset') {
            const key = enhancedFieldsMapByType.get('asset')?.key;
            return renderSourceAssets(key ? errors[key]?.message as string : null, enhancedFieldsMapByType.get('asset')?.isInMandatoryGroup, enhancedFieldsMapByType.get('asset')?.mandatoryGroupContractElementLabels);
          } else if (field.type === 'asset-group') {
            const key = enhancedFieldsMapByType.get('asset-group')?.key;
            return renderSourceAssetGroups(key ? errors[key]?.message as string : null, enhancedFieldsMapByType.get('asset-group')?.isInMandatoryGroup, enhancedFieldsMapByType.get('asset-group')?.mandatoryGroupContractElementLabels);
          }

          return (
            <AttackChainNodeContentFieldComponent
              key={field.key}
              field={field}
              readOnly={readOnly || field.readOnly}
            />
          );
        })
      }
    </div>
  );

  const injectContentParts = [
    {
      key: 'teams',
      title: () => renderTitle(t('Targeted teams'), enhancedFieldsMapByType.get('team')?.settings?.required, !!errors[enhancedFieldsMapByType.get('team')!.key]),
      renderRightButton: !isAtomic && (
        <SwitchFieldController
          name="node_all_teams"
          label={<strong>{t('All teams')}</strong>}
          disabled={enhancedFieldsMapByType.get('team')?.readOnly || readOnly}
          size="small"
        />
      ),
      render: () => renderTeams(errors[enhancedFieldsMapByType.get('team')!.key]?.message as string || null),
      show: enhancedFieldsMapByType.has('team'),
    },
    {
      key: 'challenge',
      title: () => renderTitle(t('Challenges to publish'), enhancedFieldsMapByType.get('challenge')?.settings?.required),
      render: () => renderChallenges(errors[enhancedFieldsMapByType.get('challenge')!.key]?.message as string || null),
      show: enhancedFieldsMapByType.has('challenge') && enhancedFieldsMapByType.get('challenge')?.isVisible,
    },
    {
      key: 'node_data_title',
      title: () => <Typography variant="h5" style={{ marginTop: 0 }}>{t('AttackChainNode data')}</Typography>,
      parentStyle: {
        marginTop: theme.spacing(1),
        marginBottom: theme.spacing(-1),
      },
      renderLeftButton: (!isAtomic || (ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT) || (injectId && ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId))))
        && (
          <Tooltip title={t('Reset to default values')}>
            <IconButton
              color="primary"
              disabled={enhancedFieldsMapByType.get('expectation')?.readOnly || readOnly}
              onClick={resetDefaultValue}
              size="small"
            >
              <RotateLeftOutlined />
            </IconButton>
          </Tooltip>
        ),
      renderRightButton: (
        <Button
          color="primary"
          startIcon={<HelpOutlineOutlined />}
          variant="outlined"
          size="small"
          onClick={openVariablesDialog}
        >
          {t('Available variables')}
        </Button>
      ),
      show: dynamicFields.length,
    },
    {
      key: 'node_data',
      render: renderDynamicFields,
      show: true,
    },
    {
      key: 'expectations',
      title: () => renderTitle(t('AttackChainNode expectations'), enhancedFieldsMapByType.get('expectation')?.settings?.required),
      render: renderExpectations,
      show: enhancedFieldsMapByType.has('expectation') && enhancedFieldsMapByType.get('expectation')?.isVisible,
      parentStyle: {
        marginTop: theme.spacing(1),
        marginBottom: theme.spacing(-1),
      },
    },
    {
      key: 'documents',
      title: () => renderTitle(t('AttackChainNode documents'), enhancedFieldsMapByType.get('attachment')?.settings?.required),
      render: renderDocuments,
      show: enhancedFieldsMapByType.has('attachment') && enhancedFieldsMapByType.get('attachment')?.isVisible,
      parentStyle: {
        marginTop: theme.spacing(1),
        marginBottom: theme.spacing(-1),
      },
    },
  ];

  return (
    <>
      {injectContentParts.filter(part => part.show).map(part => (
        <div
          key={part.key}
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            flexWrap: 'wrap',
            ...part.parentStyle,
          }}
        >
          {part.title && part.title()}
          {part.renderLeftButton}
          <div style={{
            flex: 1,
            display: 'flex',
            justifyContent: 'flex-end',
          }}
          >
            {part.renderRightButton}
          </div>
          {
            part.render
            && (
              <div style={{ width: '100%' }}>
                {part.render()}
              </div>
            )
          }
        </div>
      ))}
      <AvailableVariablesDialog
        uriVariable={uriVariable}
        variables={variables}
        open={openVariables}
        handleClose={() => setOpenVariables(false)}
        variablesFromInjectorContract={injectorContractVariables ?? []}
      />
    </>
  );
};

export default AttackChainNodeContentForm;
