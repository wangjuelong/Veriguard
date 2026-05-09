import { HelpOutlined } from '@mui/icons-material';
import { TabContext, TabPanel } from '@mui/lab';
import { Avatar, Tab, Tabs } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackChainNodeOutputType, type AttackChainNodeStore } from '../../../../actions/attack_chain_nodes/AttackChainNode';
import { fetchDocumentsPayloadByAttackChainNode } from '../../../../actions/attack_chain_nodes/node-action';
import { type AttackChainNodeHelper } from '../../../../actions/attack_chain_nodes/node-helper';
import { fetchAttackChainNode } from '../../../../actions/AttackChainNode';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { useHelper } from '../../../../store';
import {
  type Article,
  type AttackPattern, type Document,
  type AttackChainNode,
  type AttackChainNodeInput,
  type KillChainPhase, type Variable,
} from '../../../../utils/api-types';
import { type InjectorContractConverted } from '../../../../utils/api-types-custom';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, INHERITED_CONTEXT, SUBJECTS } from '../../../../utils/permissions/types';
import { arrayToRecord, isNotEmptyField } from '../../../../utils/utils';
import PayloadComponent from '../../payloads/PayloadComponent';
import { PermissionsContext } from '../Context';
import AttackChainNodeForm from './form/AttackChainNodeForm';
import AttackChainNodeCardComponent from './AttackChainNodeCardComponent';
import AttackChainNodeIcon from './AttackChainNodeIcon';
import UpdateAttackChainNodeLogicalChains from './UpdateAttackChainNodeLogicalChains';

interface Props {
  open: boolean;
  handleClose: () => void;
  onUpdateAttackChainNode: (data: AttackChainNode) => Promise<void>;
  massUpdateAttackChainNode?: (data: AttackChainNode[]) => Promise<void>;
  injectId: string;
  isAtomic?: boolean;
  nodes?: AttackChainNodeOutputType[];
  articlesFromAttackChainRunOrAttackChain?: Article[];
  uriVariable?: string;
  variablesFromAttackChainRunOrAttackChain?: Variable[];
}

const useStyles = makeStyles()(() => ({ tabPanel: { padding: 0 } }));

const UpdateAttackChainNode: React.FC<Props> = ({
  open,
  handleClose,
  onUpdateAttackChainNode,
  massUpdateAttackChainNode,
  injectId,
  isAtomic = false,
  nodes,
  articlesFromAttackChainRunOrAttackChain = [],
  uriVariable = '',
  variablesFromAttackChainRunOrAttackChain = [],
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const [isAttackChainNodeLoading, setIsAttackChainNodeLoading] = useState(true);

  const { permissions, inherited_context } = useContext(PermissionsContext);
  const ability = useContext(AbilityContext);

  // Setup tabs
  const [availableTabs, setAvailableTabs] = useState<string[]>(['AttackChainNode details', 'Logical chains']);
  const [activeTab, setActiveTab] = useState<string>(availableTabs[0]);

  // Fetching data
  const { node }: { node: AttackChainNodeStore } = useHelper((helper: AttackChainNodeHelper) => ({ node: helper.getAttackChainNode(injectId) }));
  const contractPayload = node?.node_injector_contract?.injector_contract_payload;
  const injectorContract = node?.node_injector_contract;
  const [documentsMap, setDocumentsMap] = useState<Record<string, Document> | null>(null);

  useDataLoader(() => {
    setIsAttackChainNodeLoading(true);
    dispatch(fetchAttackChainNode(injectId)).then(() => {
      const payloadId = node?.node_injector_contract?.injector_contract_payload?.payload_id;
      if (payloadId) {
        setAvailableTabs(['AttackChainNode details', 'Payload info', 'Logical chains']);
      }
      setIsAttackChainNodeLoading(false);
    });
  });

  // Selection
  const handleTabChange = (_: SyntheticEvent, newValue: string) => {
    setActiveTab(newValue);

    if (newValue === 'Payload info' && !documentsMap) {
      fetchDocumentsPayloadByAttackChainNode(injectId, contractPayload?.payload_id)
        .then(documents => setDocumentsMap(arrayToRecord<Document, 'document_id'>(documents, 'document_id')));
    }
  };

  const [injectorContractContent, setInjectorContractContent] = useState<InjectorContractConverted['convertedContent']>();
  useEffect(() => {
    if (node?.node_injector_contract?.convertedContent) {
      setInjectorContractContent(node.node_injector_contract?.convertedContent);
    }
  }, [node]);

  const getAttackChainNodeHeaderTitle = (): string => {
    if (injectorContract?.injector_contract_needs_executor && node?.node_attack_patterns?.length !== 0) {
      return `${node?.node_kill_chain_phases?.map((value: KillChainPhase) => value.phase_name)?.join(', ')} / ${node?.node_attack_patterns?.map((value: AttackPattern) => value.attack_pattern_external_id)?.join(', ')}`;
    }
    if (injectorContract?.injector_contract_needs_executor) {
      return t('TTP Unknown');
    }
    return injectorContract?.injector_contract_injector_type_name ? t(injectorContract?.injector_contract_injector_type_name) : '';
  };

  const injectFormContent = (
    <AttackChainNodeCardComponent
      avatar={injectorContractContent
        ? (
            <AttackChainNodeIcon
              type={contractPayload ? (contractPayload.payload_collector_type ?? contractPayload.payload_type) : injectorContract?.injector_contract_injector_type}
              isPayload={isNotEmptyField(contractPayload?.payload_collector_type ?? contractPayload?.payload_type)}
            />
          ) : (
            <Avatar sx={{
              width: 24,
              height: 24,
            }}
            >
              <HelpOutlined />
            </Avatar>
          )}
      title={getAttackChainNodeHeaderTitle()}
      action={(
        <div style={{
          display: 'flex',
          alignItems: 'center',
        }}
        >
          {node?.node_injector_contract?.injector_contract_platforms?.map(
            platform => (
              <PlatformIcon
                key={platform}
                width={20}
                platform={platform}
                marginRight={theme.spacing(2)}
              />
            ),
          )}
        </div>
      )}
      content={node?.node_title}
    />

  );
  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Update the node')}
      disableEnforceFocus
      containerStyle={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
    >
      <TabContext value={activeTab}>
        {!isAtomic && (
          <Tabs value={activeTab} onChange={handleTabChange} variant="fullWidth">
            {availableTabs.map(tab => (
              <Tab key={tab} label={t(tab)} value={tab} />
            ))}
          </Tabs>
        )}
        {/* AttackChainNode details */}
        <TabPanel value="AttackChainNode details" keepMounted className={classes.tabPanel}>
          {injectFormContent}
          {!isAttackChainNodeLoading && (
            <AttackChainNodeForm
              handleClose={handleClose}
              disabled={
                !injectorContractContent
                || permissions.readOnly
                || (inherited_context === INHERITED_CONTEXT.NONE
                  && ability.cannot(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId))
              }
              isAtomic={isAtomic}
              defaultAttackChainNode={node}
              injectorContractContent={injectorContractContent}
              onSubmitAttackChainNode={(data: AttackChainNodeInput) => onUpdateAttackChainNode(data as AttackChainNode)}
              articlesFromAttackChainRunOrAttackChain={articlesFromAttackChainRunOrAttackChain}
              uriVariable={uriVariable}
              variablesFromAttackChainRunOrAttackChain={variablesFromAttackChainRunOrAttackChain}
            />
          )}
        </TabPanel>

        {/* Payload info */}
        {contractPayload && !isAtomic && (
          <TabPanel value="Payload info" keepMounted className={classes.tabPanel}>
            {!isAttackChainNodeLoading && (
              <PayloadComponent
                documentsMap={documentsMap}
                selectedPayload={contractPayload}
              />
            )}
          </TabPanel>
        )}

        {/* Logical chains */}
        <TabPanel value="Logical chains" keepMounted className={classes.tabPanel}>
          {injectFormContent}
          {!isAttackChainNodeLoading && !isAtomic && (
            <UpdateAttackChainNodeLogicalChains
              node={node}
              handleClose={handleClose}
              onUpdateAttackChainNode={massUpdateAttackChainNode}
              nodes={nodes}
              isDisabled={
                !permissions.canManage
                && ability.cannot(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId)
              }
            />
          )}
        </TabPanel>
      </TabContext>
    </Drawer>
  );
};

export default UpdateAttackChainNode;
