import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import arrayMutators from 'final-form-arrays';
import { type FunctionComponent, useContext } from 'react';
import { Form } from 'react-final-form';

import { type AttackChainNodeOutputType, type AttackChainNodeStore } from '../../../../actions/attack_chain_nodes/AttackChainNode';
import { type AttackChainNodeHelper } from '../../../../actions/attack_chain_nodes/node-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type AttackChainNode, type AttackChainNodeDependency } from '../../../../utils/api-types';
import { PermissionsContext } from '../Context';
import AttackChainNodeChainsForm from './AttackChainNodeChainsForm';

interface Props {
  node: AttackChainNodeStore;
  handleClose: () => void;
  onUpdateAttackChainNode?: (data: AttackChainNode[]) => Promise<void>;
  nodes?: AttackChainNodeOutputType[];
  isDisabled: boolean;
}

const UpdateAttackChainNodeLogicalChains: FunctionComponent<Props> = ({ node, handleClose, onUpdateAttackChainNode, nodes, isDisabled }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);

  const { injectsMap } = useHelper((helper: AttackChainNodeHelper) => ({ injectsMap: helper.getAttackChainNodesMap() }));

  const initialValues = {
    ...node,
    node_depends_to: nodes !== undefined
      ? nodes
          .filter(currentAttackChainNode => currentAttackChainNode.node_depends_on !== undefined
            && currentAttackChainNode.node_depends_on !== null
            && currentAttackChainNode.node_depends_on
              .find(searchAttackChainNode => searchAttackChainNode.dependency_relationship?.node_parent_id === node.node_id)
              !== undefined)
          .flatMap((currentAttackChainNode) => {
            return currentAttackChainNode.node_depends_on;
          })
      : undefined,
    node_depends_on: node.node_depends_on,
  };

  const onSubmit = async (data: AttackChainNode & { node_depends_to: AttackChainNodeDependency[] }) => {
    const injectUpdate = {
      ...data,
      node_id: data.node_id,
      node_injector_contract: data.node_injector_contract?.injector_contract_id,
      node_depends_on: data.node_depends_on,
    };

    const injectsToUpdate: AttackChainNode[] = [];

    const childrenIds = data.node_depends_to.map((childrenAttackChainNode: AttackChainNodeDependency) => childrenAttackChainNode.dependency_relationship?.node_children_id);

    const injectsWithoutDependencies = nodes
      ? nodes
          .filter(currentAttackChainNode => currentAttackChainNode.node_depends_on !== null
            && currentAttackChainNode.node_depends_on?.find(searchAttackChainNode => searchAttackChainNode.dependency_relationship?.node_parent_id === data.node_id) !== undefined
            && !childrenIds.includes(currentAttackChainNode.node_id))
          .map((currentAttackChainNode) => {
            return {
              ...injectsMap[currentAttackChainNode.node_id],
              node_id: currentAttackChainNode.node_id,
              node_injector_contract: currentAttackChainNode.node_injector_contract?.injector_contract_id,
              node_depends_on: undefined,
            } as unknown as AttackChainNode;
          })
      : [];

    injectsToUpdate.push(...injectsWithoutDependencies);

    childrenIds.forEach((childrenId) => {
      if (nodes === undefined || childrenId === undefined) return;
      const children = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === childrenId);
      if (children !== undefined) {
        const injectDependsOnUpdate = data.node_depends_to
          .find(dependsTo => dependsTo.dependency_relationship?.node_children_id === childrenId);

        const injectChildrenUpdate: AttackChainNode = {
          ...injectsMap[children.node_id],
          node_id: children.node_id,
          node_injector_contract: children.node_injector_contract?.injector_contract_id,
          node_depends_on: injectDependsOnUpdate ? [injectDependsOnUpdate] : [],
        };
        injectsToUpdate.push(injectChildrenUpdate);
      }
    });
    if (onUpdateAttackChainNode) {
      await onUpdateAttackChainNode([injectUpdate as AttackChainNode, ...injectsToUpdate]);
    }

    handleClose();
  };

  return (
    <Form
      keepDirtyOnReinitialize={true}
      initialValues={initialValues}
      onSubmit={onSubmit}
      mutators={{
        ...arrayMutators,
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ form, handleSubmit, values, errors }) => {
        return (
          <form id="injectContentForm" onSubmit={handleSubmit}>
            <AttackChainNodeChainsForm
              form={form}
              values={values}
              nodes={nodes}
              isDisabled={isDisabled}
            />
            <div style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: theme.spacing(1),
            }}
            >
              <Button
                variant="contained"
                onClick={handleClose}
              >
                {t('Cancel')}
              </Button>
              <Button
                variant="contained"
                color="secondary"
                type="submit"
                disabled={(errors !== undefined && Object.keys(errors).length > 0) || permissions.readOnly}
              >
                {t('Update')}
              </Button>
            </div>
          </form>
        );
      }}
    </Form>
  );
};

export default UpdateAttackChainNodeLogicalChains;
