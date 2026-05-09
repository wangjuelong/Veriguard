import { Add, DeleteOutlined, ExpandMore } from '@mui/icons-material';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  type SelectChangeEvent,
  Tooltip,
  Typography,
} from '@mui/material';
import { type FormApi } from 'final-form';
import { type FunctionComponent, type ReactElement, type ReactNode, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type ConditionElement, type ConditionType, type Content, type ConvertedContentType, type Dependency, type AttackChainNodeOutputType } from '../../../../actions/attack_chain_nodes/AttackChainNode';
import ClickableChip, { type Element } from '../../../../components/common/chips/ClickableChip';
import ClickableModeChip from '../../../../components/common/chips/ClickableModeChip';
import { useFormatter } from '../../../../components/i18n';
import { type AttackChainNode, type AttackChainNodeDependency, type AttackChainNodeDependencyCondition, type AttackChainNodeOutput } from '../../../../utils/api-types';
import { capitalize } from '../../../../utils/String';

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'inline-flex',
    alignItems: 'center',
  },
  importerStyle: {
    display: 'flex',
    alignItems: 'center',
  },
  labelExecutionCondition: { color: theme.palette.text.secondary },
}));

interface Props {
  values: AttackChainNode & { node_depends_to: AttackChainNodeDependency[] };
  form: FormApi<AttackChainNode & { node_depends_to: AttackChainNodeDependency[] }, Partial<AttackChainNode & { node_depends_to: AttackChainNodeDependency[] }>>;
  nodes?: AttackChainNodeOutputType[];
  isDisabled: boolean;
}

const AttackChainNodeChainsForm: FunctionComponent<Props> = ({ values, form, nodes, isDisabled }) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  // List of parents
  const [parents, setParents] = useState<Dependency[]>(
    () => {
      if (values.node_depends_on) {
        return values.node_depends_on?.filter(searchAttackChainNode => searchAttackChainNode.dependency_relationship?.node_children_id === values.node_id)
          .map((node, index) => {
            return {
              node: nodes?.find(currentAttackChainNode => currentAttackChainNode.node_id === node.dependency_relationship?.node_parent_id),
              index,
            };
          });
      }
      return [];
    },

  );

  // List of children
  const [childrenList, setChildrenList] = useState<Dependency[]>(
    () => {
      if (nodes !== undefined) {
        return nodes?.filter(
          searchAttackChainNode => searchAttackChainNode.node_depends_on?.find(
            dependsOnSearch => dependsOnSearch.dependency_relationship?.node_parent_id === values.node_id,
          ) !== undefined,
        )
          .map((node, index) => {
            return {
              node,
              index,
            };
          });
      }
      return [];
    },
  );

  // Property to deactivate the add children button if there are no children available anymore
  const [addChildrenButtonDisabled, setAddChildrenButtonDisabled] = useState(false);
  useEffect(() => {
    const availableChildrenCount = nodes ? nodes.filter(currentAttackChainNode => currentAttackChainNode.node_depends_duration > values.node_depends_duration).length : 0;
    setAddChildrenButtonDisabled(childrenList ? childrenList.length >= availableChildrenCount : true);
  }, [childrenList, nodes, values.node_depends_duration]);

  /**
   * Transform an node dependency into ConditionElement
   * @param injectDependsOn an array of injectDependency
   */
  const getConditionContentParent = (injectDependsOn: (AttackChainNodeDependency | undefined)[]) => {
    const conditions: ConditionType[] = [];
    if (injectDependsOn) {
      injectDependsOn.forEach((parent) => {
        if (parent !== undefined) {
          conditions.push({
            parentId: parent.dependency_relationship?.node_parent_id,
            childrenId: parent.dependency_relationship?.node_children_id,
            mode: parent.dependency_condition?.mode,
            conditionElement: parent.dependency_condition?.conditions?.map((dependencyCondition, indexCondition) => {
              return {
                name: dependencyCondition.key,
                value: dependencyCondition.value!,
                key: dependencyCondition.key,
                index: indexCondition,
              };
            }),
          });
        }
      });
    }
    return conditions;
  };

  /**
   * Transform an node dependency into ConditionElement
   * @param injectDependsTo an array of injectDependency
   */
  const getConditionContentChildren = (injectDependsTo: (AttackChainNodeDependency | undefined)[]) => {
    const conditions: ConditionType[] = [];
    injectDependsTo.forEach((children) => {
      if (children !== undefined) {
        conditions.push({
          parentId: values.node_id,
          childrenId: children.dependency_relationship?.node_children_id,
          mode: children.dependency_condition?.mode,
          conditionElement: children.dependency_condition?.conditions?.map((dependencyCondition, indexCondition) => {
            return {
              name: dependencyCondition.key,
              value: dependencyCondition.value!,
              key: dependencyCondition.key,
              index: indexCondition,
            };
          }),
        });
      }
    });
    return conditions;
  };

  const [parentConditions, setParentConditions] = useState(getConditionContentParent(values.node_depends_on ? values.node_depends_on : []));
  const [childrenConditions, setChildrenConditions] = useState(getConditionContentChildren(values.node_depends_to));

  /**
   * Get the node dependency object from dependency ones
   * @param deps the node depencies
   */
  const injectDependencyFromDependency = (deps: Dependency[]) => {
    return deps.flatMap(dependency => (dependency.node?.node_depends_on !== null ? dependency.node?.node_depends_on : []));
  };

  /**
   * Handle the change of the parent
   * @param _event the event
   * @param parent the parent key
   */
  const handleChangeParent = (_event: SelectChangeEvent<string>, parent: ReactNode) => {
    const rx = /\.\$select-parent-(.*)-node-(.*)/g;
    if (!parent) return;
    let key = '';
    const parentElement = parent as ReactElement;
    if ('key' in parentElement && parentElement.key !== null) {
      key = parentElement.key;
    }
    if (key === null) {
      return;
    }
    const arr = rx.exec(key);

    if (parents === undefined || arr === null || nodes === undefined) return;
    const newAttackChainNode = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === arr[2]);
    const newParents = parents
      .map((element) => {
        if (element.index === parseInt(arr[1], 10)) {
          const previousAttackChainNode = nodes.find(value => value.node_id === element.node?.node_id);
          if (previousAttackChainNode?.node_depends_on !== undefined) {
            previousAttackChainNode!.node_depends_on = previousAttackChainNode!.node_depends_on?.filter(
              dependsOn => dependsOn.dependency_relationship?.node_children_id !== values.node_id,
            );
          }
          return {
            node: newAttackChainNode!,
            index: element.index,
          };
        }
        return element;
      });
    setParents(newParents);

    const baseAttackChainNodeDependency: AttackChainNodeDependency = {
      dependency_relationship: {
        node_parent_id: newAttackChainNode?.node_id,
        node_children_id: values.node_id,
      },
      dependency_condition: {
        conditions: [
          {
            key: 'Execution',
            operator: 'eq',
            value: true,
          },
        ],
        mode: 'and',
      },
    };
    setParentConditions(getConditionContentParent([baseAttackChainNodeDependency]));

    form.mutators.setValue(
      'node_depends_on',
      [baseAttackChainNodeDependency],
    );
  };

  /**
   * Add a new parent node
   */
  const addParent = () => {
    setParents([...parents, {
      node: undefined,
      index: parents.length,
    }]);
  };

  /**
   * Handle the change of a children
   * @param _event
   * @param child
   */
  const handleChangeChildren = (_event: SelectChangeEvent<string>, child: ReactNode) => {
    const rx = /\.\$select-children-(.*)-node-(.*)/g;
    if (!child) return;
    let key = '';
    const childElement = child as ReactElement;
    if ('key' in (childElement as ReactElement) && childElement.key !== null) {
      key = childElement.key;
    }
    if (key === null) {
      return;
    }
    const arr = rx.exec(key);

    if (childrenList === undefined || arr === null || nodes === undefined) return;
    const newAttackChainNode = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === arr[2]);
    const newChildren = childrenList
      .map((element) => {
        if (element.index === parseInt(arr[1], 10)) {
          const baseAttackChainNodeDependency: AttackChainNodeDependency = {
            dependency_relationship: {
              node_parent_id: values.node_id,
              node_children_id: newAttackChainNode?.node_id,
            },
            dependency_condition: {
              conditions: [
                {
                  key: 'Execution',
                  operator: 'eq',
                  value: true,
                },
              ],
              mode: 'and',
            },
          };
          newAttackChainNode!.node_depends_on = [baseAttackChainNodeDependency];
          return {
            node: newAttackChainNode!,
            index: element.index,
          };
        }
        return element;
      });

    setChildrenList(newChildren);

    const dependsTo = injectDependencyFromDependency(newChildren);
    form.mutators.setValue('node_depends_to', dependsTo);

    if (newAttackChainNode?.node_depends_on !== null) {
      setChildrenConditions(getConditionContentChildren(dependsTo.filter(dep => dep !== undefined)));
    }
  };

  /**
   * Add a new children node
   */
  const addChildren = () => {
    setChildrenList([...childrenList, {
      node: undefined,
      index: childrenList.length,
    }]);
  };

  /**
   * Delete a parent node
   * @param parent
   */
  const deleteParent = (parent: Dependency) => {
    const parentIndexInArray = parents.findIndex(currentParent => currentParent.index === parent.index);

    if (parentIndexInArray > -1) {
      const newParents = [
        ...parents.slice(0, parentIndexInArray),
        ...parents.slice(parentIndexInArray + 1),
      ];
      setParents(newParents);

      form.mutators.setValue(
        'node_depends_on',
        injectDependencyFromDependency(newParents),
      );
    }
  };

  /**
   * Delete a children node
   * @param children
   */
  const deleteChildren = (children: Dependency) => {
    const childrenIndexInArray = childrenList.findIndex(currentChildren => currentChildren.node?.node_id === children.node?.node_id);

    if (childrenIndexInArray > -1) {
      const newChildren = [
        ...childrenList.slice(0, childrenIndexInArray),
        ...childrenList.slice(childrenIndexInArray + 1),
      ];
      setChildrenList(newChildren);

      form.mutators.setValue('node_depends_to', injectDependencyFromDependency(newChildren));
    }
  };

  /**
   * Returns an updated depends on from a ConditionType
   * @param conditions
   * @param switchIds
   */
  const updateDependsCondition = (conditions: ConditionType) => {
    const result: AttackChainNodeDependencyCondition = {
      mode: conditions.mode === 'and' ? 'and' : 'or',
      conditions: conditions.conditionElement?.map((value) => {
        return {
          value: value.value,
          key: value.key,
          operator: 'eq',
        };
      }),
    };
    return result;
  };

  /**
   * Returns an updated depends on from a ConditionType
   * @param conditions
   * @param switchIds
   */
  const updateDependsOn = (conditions: ConditionType) => {
    const result: AttackChainNodeDependency = {
      dependency_relationship: {
        node_parent_id: conditions.parentId,
        node_children_id: conditions.childrenId,
      },
      dependency_condition: updateDependsCondition(conditions),
    };
    return result;
  };

  /**
   * Get the list of available expectations
   * @param node
   */
  const getAvailableExpectations = (node: AttackChainNodeOutputType | undefined) => {
    if (node?.node_content !== null && node?.node_content !== undefined && (node.node_content as Content).expectations !== undefined) {
      const expectations = (node.node_content as Content).expectations.map(expectation => (expectation.expectation_type === 'MANUAL' ? expectation.expectation_name : capitalize(expectation.expectation_type)));
      return ['Execution', ...expectations];
    }
    if (node?.node_injector_contract !== undefined
      && (node?.node_injector_contract.convertedContent as unknown as ConvertedContentType).fields.find(field => field.key === 'expectations')) {
      const predefinedExpectations = (node.node_injector_contract.convertedContent as unknown as ConvertedContentType).fields?.find(field => field.key === 'expectations')
        ?.predefinedExpectations.map(expectation => (expectation.expectation_type === 'MANUAL' ? expectation.expectation_name : capitalize(expectation.expectation_type)));
      if (predefinedExpectations !== undefined) {
        return ['Execution', ...predefinedExpectations];
      }
    }
    return ['Execution'];
  };

  /**
   * Add a new condition to a parent node
   * @param parent
   */
  const addConditionParent = (parent: Dependency) => {
    const currentConditions = parentConditions.find(currentCondition => parent.node!.node_id === currentCondition.parentId);

    if (parent.node !== undefined && currentConditions !== undefined) {
      let expectationString: string = 'Execution';
      if (currentConditions?.conditionElement !== undefined) {
        expectationString = getAvailableExpectations(parent.node)
          .find(expectation => !currentConditions?.conditionElement?.find(conditionElement => conditionElement.key === expectation)) ?? 'Execution';
      }
      currentConditions.conditionElement?.push({
        key: expectationString,
        name: expectationString,
        value: true,
        index: currentConditions.conditionElement?.length,
      });

      setParentConditions(parentConditions);

      const element = parentConditions.find(conditionElement => conditionElement.childrenId === values.node_id);

      const dep: AttackChainNodeDependency = {
        dependency_relationship: {
          node_parent_id: element?.parentId,
          node_children_id: element?.childrenId,
        },
        dependency_condition: {
          mode: element?.mode === '&&' ? 'and' : 'or',
          conditions: element?.conditionElement
            ? element?.conditionElement.map((value) => {
                return {
                  key: value.key,
                  value: value.value,
                  operator: 'eq',
                };
              })
            : [],
        },
      };

      form.mutators.setValue(
        'node_depends_on',
        [dep],
      );
    }
  };

  /**
   * Add a new condition to a children node
   * @param children
   */
  const addConditionChildren = (children: Dependency) => {
    const currentConditions = childrenConditions.find(currentCondition => children.node!.node_id === currentCondition.childrenId);

    if (children.node !== undefined && currentConditions !== undefined) {
      const updatedChildren = childrenList.find(currentChildren => currentChildren.node?.node_id === children.node?.node_id);
      let expectationString: string = 'Execution';
      if (currentConditions?.conditionElement !== undefined) {
        expectationString = getAvailableExpectations(values as AttackChainNodeOutput as AttackChainNodeOutputType)
          .find(expectation => !currentConditions?.conditionElement?.find(conditionElement => conditionElement.key === expectation)) ?? 'Execution';
      }
      currentConditions.conditionElement?.push({
        key: expectationString,
        name: expectationString,
        value: true,
        index: currentConditions.conditionElement?.length,
      });

      if (updatedChildren?.node?.node_depends_on !== undefined) {
        updatedChildren.node.node_depends_on = [updateDependsOn(currentConditions)];
      }

      setChildrenConditions(childrenConditions);
      form.mutators.setValue(
        'node_depends_to',
        injectDependencyFromDependency(childrenList),
      );
    }
  };

  /**
   * Handle a change in a condition of a parent element
   * @param newElement
   * @param conditions
   * @param condition
   * @param parent
   */
  const changeParentElement = (newElement: Element, conditions: ConditionType, condition: ConditionElement, parent: Dependency) => {
    const newConditionElements = conditions.conditionElement?.map((newConditionElement) => {
      if (newConditionElement.index === condition.index) {
        return {
          index: condition.index,
          key: newElement.key,
          name: `${conditions.parentId}-${newElement.key}-Success`,
          value: newElement.value === 'Success',
        };
      }
      return newConditionElement;
    });
    const newParentConditions = parentConditions.map((parentCondition) => {
      if (parentCondition.parentId === parent.node?.node_id) {
        return {
          ...parentCondition,
          conditionElement: newConditionElements,
        };
      }
      return parentCondition;
    });
    setParentConditions(newParentConditions);

    const element = newParentConditions?.find(conditionElement => conditionElement.parentId === conditions.parentId);
    const dep: AttackChainNodeDependency = {
      dependency_relationship: {
        node_parent_id: element?.parentId,
        node_children_id: element?.childrenId,
      },
      dependency_condition: {
        mode: element?.mode === '&&' ? 'and' : 'or',
        conditions: element?.conditionElement
          ? element?.conditionElement.map((value) => {
              return {
                key: value.key,
                value: value.value,
                operator: 'eq',
              };
            })
          : [],
      },
    };

    form.mutators.setValue(
      'node_depends_on',
      [dep],
    );
  };

  /**
   * Handle a change in a condition of a children element
   * @param newElement
   * @param conditions
   * @param condition
   * @param children
   */
  const changeChildrenElement = (newElement: Element, conditions: ConditionType, condition: ConditionElement, children: Dependency) => {
    const newConditionElements = conditions.conditionElement?.map((newConditionElement) => {
      if (newConditionElement.index === condition.index) {
        return {
          index: condition.index,
          key: newElement.key,
          name: `${conditions.childrenId}-${newElement.key}-Success`,
          value: newElement.value === 'Success',
        };
      }
      return newConditionElement;
    });
    const newChildrenConditions = childrenConditions.map((childrenCondition) => {
      if (childrenCondition.childrenId === children.node?.node_id) {
        return {
          ...childrenCondition,
          conditionElement: newConditionElements,
        };
      }
      return childrenCondition;
    });
    setChildrenConditions(newChildrenConditions);

    const updatedChildren = childrenList.find(currentChildren => currentChildren.node?.node_id === children.node?.node_id);
    const newCondition = newChildrenConditions.find(childrenCondition => childrenCondition.childrenId === children.node?.node_id);
    if (updatedChildren?.node?.node_depends_on !== undefined && newCondition !== undefined) {
      updatedChildren.node.node_depends_on = [updateDependsOn(newCondition)];
    }
    form.mutators.setValue(
      'node_depends_to',
      injectDependencyFromDependency(childrenList),
    );
  };

  /**
   * Changes the mode (AND/OR) in a parent node
   * @param conditions
   * @param condition
   */
  const changeModeParent = (conditions: ConditionType[] | undefined, condition: ConditionType) => {
    const newConditionElements = conditions?.map((currentCondition) => {
      if (currentCondition.parentId === condition.parentId) {
        return {
          ...currentCondition,
          mode: currentCondition.mode === 'and' ? 'or' : 'and',
        };
      }
      return currentCondition;
    });
    if (newConditionElements !== undefined) {
      setParentConditions(newConditionElements);
    }

    const element = newConditionElements?.find(conditionElement => conditionElement.parentId === condition.parentId);
    const dep: AttackChainNodeDependency = {
      dependency_relationship: {
        node_parent_id: element?.parentId,
        node_children_id: element?.childrenId,
      },
      dependency_condition: {
        mode: element?.mode === '&&' ? 'and' : 'or',
        conditions: element?.conditionElement
          ? element?.conditionElement.map((value) => {
              return {
                key: value.key,
                value: value.value,
                operator: 'eq',
              };
            })
          : [],
      },
    };

    form.mutators.setValue(
      'node_depends_on',
      [dep],
    );
  };

  /**
   * Changes the mode (AND/OR) in a children node
   * @param conditions
   * @param condition
   */
  const changeModeChildren = (conditions: ConditionType[] | undefined, condition: ConditionType) => {
    const newConditionElements = conditions?.map((currentCondition) => {
      if (currentCondition.childrenId === condition.childrenId) {
        return {
          ...currentCondition,
          mode: currentCondition.mode === 'and' ? 'or' : 'and',
        };
      }
      return currentCondition;
    });
    if (newConditionElements !== undefined) {
      setChildrenConditions(newConditionElements);
    }

    const newCurrentCondition = newConditionElements?.find(currentCondition => currentCondition.childrenId === condition.childrenId);
    const updatedChildren = childrenList.find(currentChildren => currentChildren.node?.node_id === newCurrentCondition?.childrenId);
    if (updatedChildren?.node?.node_depends_on !== undefined && newCurrentCondition !== undefined) {
      updatedChildren.node.node_depends_on = [updateDependsOn(newCurrentCondition)];
    }
    form.mutators.setValue(
      'node_depends_to',
      injectDependencyFromDependency(childrenList),
    );
  };

  /**
   * Delete a condition from a parent node
   * @param conditions
   * @param condition
   */
  const deleteConditionParent = (conditions: ConditionType, condition: ConditionElement) => {
    const newConditionElements = parentConditions.map((currentCondition) => {
      if (currentCondition.parentId === conditions.parentId) {
        return {
          ...currentCondition,
          conditionElement: currentCondition.conditionElement?.filter(element => element.index !== condition.index),
        };
      }
      return currentCondition;
    });
    setParentConditions(newConditionElements);

    const element = newConditionElements.find(conditionElement => conditionElement.parentId === conditions.parentId);
    const dep: AttackChainNodeDependency = {
      dependency_relationship: {
        node_parent_id: element?.parentId,
        node_children_id: element?.childrenId,
      },
      dependency_condition: {
        mode: element?.mode === '&&' ? 'and' : 'or',
        conditions: element?.conditionElement
          ? element?.conditionElement.map((value) => {
              return {
                key: value.key,
                value: value.value,
                operator: 'eq',
              };
            })
          : [],
      },
    };

    form.mutators.setValue(
      'node_depends_on',
      [dep],
    );
  };

  /**
   * Delete a condition from a children node
   * @param conditions
   * @param condition
   */
  const deleteConditionChildren = (conditions: ConditionType, condition: ConditionElement) => {
    const newConditionElements = childrenConditions.map((currentCondition) => {
      if (currentCondition.childrenId === conditions.childrenId) {
        return {
          ...currentCondition,
          conditionElement: currentCondition.conditionElement?.filter(element => element.index !== condition.index),
        };
      }
      return currentCondition;
    });
    setChildrenConditions(newConditionElements);

    const updatedChildren = childrenList.find(currentChildren => currentChildren.node?.node_id === conditions.childrenId);
    if (updatedChildren?.node?.node_depends_on !== undefined && conditions !== undefined) {
      const newCondition = newConditionElements.find(currentCondition => currentCondition.childrenId === conditions.childrenId);
      if (newCondition !== undefined) updatedChildren.node.node_depends_on = [updateDependsOn(newCondition)];
    }
    form.mutators.setValue(
      'node_depends_to',
      injectDependencyFromDependency(childrenList),
    );
  };

  /**
   * Whether or not we can add a new condition
   * @param node
   * @param conditions
   */
  const canAddConditions = (node: AttackChainNodeOutputType, conditions?: ConditionType) => {
    const expectationsNumber = getAvailableExpectations(node).length;
    if (conditions === undefined || conditions.conditionElement === undefined) return true;

    return conditions?.conditionElement.length < expectationsNumber;
  };

  /**
   * Return a clickable parent chip
   * @param parent
   */
  const getClickableParentChip = (parent: Dependency) => {
    const parentChip = parentConditions.find(parentCondition => parent.node !== undefined && parentCondition.parentId === parent.node.node_id);
    if (parentChip === undefined || parentChip.conditionElement === undefined) return null;
    return parentChip.conditionElement.map((condition, conditionIndex) => {
      const conditions = parentConditions
        .find(parentCondition => parent.node !== undefined && parentCondition.parentId === parent.node.node_id);
      if (conditions?.conditionElement !== undefined) {
        return (
          <div key={`${condition.name}-${condition.index}`} style={{ display: 'contents' }}>
            <ClickableChip
              selectedElement={{
                key: condition.key,
                operator: 'is',
                value: condition.value ? 'Success' : 'Fail',
              }}
              pristine={true}
              availableKeys={getAvailableExpectations(parent.node)}
              availableOperators={['is']}
              availableValues={['Success', 'Fail']}
              onDelete={
                conditions.conditionElement.length > 1 ? () => {
                  deleteConditionParent(conditions, condition);
                } : undefined
              }
              onChange={(newElement) => {
                changeParentElement(newElement, conditions, condition, parent);
              }}
            />
            {conditionIndex < conditions.conditionElement.length - 1
              && (
                <ClickableModeChip
                  mode={conditions.mode}
                  onClick={() => {
                    changeModeParent(parentConditions, conditions);
                  }}
                />
              )}
          </div>
        );
      }
      return null;
    });
  };

  /**
   * Return a clickable children chip
   * @param parent
   */
  const getClickableChildrenChip = (children: Dependency) => {
    const childrenChip = childrenConditions.find(childrenCondition => children.node !== undefined && childrenCondition.childrenId === children.node.node_id);
    if (childrenChip?.conditionElement === undefined) return null;
    return childrenChip
      .conditionElement.map((condition, conditionIndex) => {
        const conditions = childrenConditions
          .find(childrenCondition => childrenCondition.childrenId === children.node?.node_id);
        if (conditions?.conditionElement !== undefined) {
          return (
            <div key={`${condition.name}-${condition.index}`} style={{ display: 'contents' }}>
              <ClickableChip
                selectedElement={{
                  key: condition.key,
                  operator: 'is',
                  value: condition.value ? 'Success' : 'Fail',
                }}
                pristine={true}
                availableKeys={getAvailableExpectations(nodes?.find(currentAttackChainNode => currentAttackChainNode.node_id === values.node_id))}
                availableOperators={['is']}
                availableValues={['Success', 'Fail']}
                onDelete={
                  conditions.conditionElement.length > 1 ? () => {
                    deleteConditionChildren(conditions, condition);
                  } : undefined
                }
                onChange={(newElement) => {
                  changeChildrenElement(newElement, conditions, condition, children);
                }}
              />
              {conditionIndex < conditions.conditionElement.length - 1
                && (
                  <ClickableModeChip
                    mode={conditions?.mode}
                    onClick={() => {
                      changeModeChildren(childrenConditions, conditions);
                    }}
                  />
                )}
            </div>
          );
        }
        return null;
      });
  };

  return (
    <>
      <div className={classes.importerStyle}>
        <Typography variant="h2" sx={{ m: 0 }}>
          {t('Parent')}
        </Typography>
        <IconButton
          color="secondary"
          aria-label="Add"
          size="large"
          disabled={parents.length > 0
            || nodes?.filter(currentAttackChainNode => currentAttackChainNode.node_depends_duration < values.node_depends_duration).length === 0 || isDisabled}
          onClick={addParent}
        >
          <Add fontSize="small" />
        </IconButton>
      </div>

      {parents.map((parent, index) => {
        return (
          <Accordion
            key={`accordion-parent-${parent.index}`}
            variant="outlined"
            style={{
              width: '100%',
              marginBottom: '10px',
            }}
            disabled={isDisabled}
          >
            <AccordionSummary
              expandIcon={<ExpandMore />}
            >
              <div className={classes.container}>
                <Typography>
                  #
                  {index + 1}
                  {' '}
                  {parent.node?.node_title}
                </Typography>
                <Tooltip title={t('Delete')}>
                  <IconButton
                    color="error"
                    onClick={() => {
                      deleteParent(parent);
                    }}
                  >
                    <DeleteOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              </div>
            </AccordionSummary>
            <AccordionDetails>
              <FormControl style={{ width: '100%' }}>
                <InputLabel id="node_id">{t('AttackChainNode')}</InputLabel>
                <Select
                  labelId="condition"
                  fullWidth={true}
                  value={parents[parent.index].node ? parents[parent.index].node?.node_id : ''}
                  onChange={handleChangeParent}
                >
                  {nodes?.filter(currentAttackChainNode => currentAttackChainNode.node_depends_duration < values.node_depends_duration
                    && (parents.find(parentSearch => currentAttackChainNode.node_id === parentSearch.node?.node_id) === undefined
                      || parents[parent.index].node?.node_id === currentAttackChainNode.node_id))
                    .map((currentAttackChainNode) => {
                      return (
                        <MenuItem
                          key={`select-parent-${index}-node-${currentAttackChainNode.node_id}`}
                          value={currentAttackChainNode.node_id}
                        >
                          {currentAttackChainNode.node_title}
                        </MenuItem>
                      );
                    })}
                </Select>
              </FormControl>
              <FormControl style={{
                width: '100%',
                marginTop: '15px',
              }}
              >
                <label className={classes.labelExecutionCondition}>{t('Execution condition:')}</label>
                <Box
                  sx={{
                    padding: '12px 4px',
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 1,
                  }}
                >
                  {getClickableParentChip(parent)}
                </Box>
                <div style={{ justifyContent: 'left' }}>
                  <Button
                    color="secondary"
                    aria-label="Add"
                    size="large"
                    onClick={() => {
                      addConditionParent(parent);
                    }}
                    style={{ justifyContent: 'start' }}
                    disabled={!canAddConditions(parent.node!, parentConditions.find(parentCondition => parentCondition.parentId === parent.node?.node_id))}
                  >
                    <Add fontSize="small" />
                    <Typography>
                      {t('Add condition')}
                    </Typography>
                  </Button>
                </div>
              </FormControl>
            </AccordionDetails>
          </Accordion>
        );
      })}

      <div className={classes.importerStyle}>
        <Typography variant="h2" sx={{ m: 0 }}>
          {t('Children')}
        </Typography>
        <IconButton
          color="secondary"
          aria-label="Add"
          size="large"
          disabled={addChildrenButtonDisabled || isDisabled}
          onClick={addChildren}
        >
          <Add fontSize="small" />
        </IconButton>
      </div>
      {childrenList.map((children, index) => {
        return (
          <Accordion
            key={`accordion-children-${children.index}`}
            variant="outlined"
            style={{
              width: '100%',
              marginBottom: '10px',
            }}
            disabled={isDisabled}
          >
            <AccordionSummary
              expandIcon={<ExpandMore />}
            >
              <div className={classes.container}>
                <Typography>
                  #
                  {index + 1}
                  {children.node?.node_title}
                </Typography>
                <Tooltip title={t('Delete')}>
                  <IconButton
                    color="error"
                    onClick={() => {
                      deleteChildren(children);
                    }}
                  >
                    <DeleteOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              </div>
            </AccordionSummary>
            <AccordionDetails>
              <FormControl style={{ width: '100%' }}>
                <InputLabel id="node_id">{t('AttackChainNode')}</InputLabel>
                <Select
                  labelId="condition"
                  fullWidth={true}
                  value={childrenList.find(childrenSearch => children.index === childrenSearch.index)?.node
                    ? childrenList.find(childrenSearch => children.index === childrenSearch.index)?.node?.node_id : ''}
                  onChange={handleChangeChildren}
                >
                  {nodes?.filter(currentAttackChainNode => currentAttackChainNode.node_depends_duration > values.node_depends_duration
                    && (childrenList.find(childrenSearch => currentAttackChainNode.node_id === childrenSearch.node?.node_id) === undefined
                      || childrenList.find(childrenSearch => children.index === childrenSearch.index)?.node?.node_id === currentAttackChainNode.node_id))
                    .map((currentAttackChainNode) => {
                      return (
                        <MenuItem
                          key={`select-children-${children.index}-node-${currentAttackChainNode.node_id}`}
                          value={currentAttackChainNode.node_id}
                        >
                          {currentAttackChainNode.node_title}
                        </MenuItem>
                      );
                    })}
                </Select>
              </FormControl>
              <FormControl style={{
                width: '100%',
                marginTop: '15px',
              }}
              >
                <label className={classes.labelExecutionCondition}>{t('Execution condition:')}</label>

                <Box
                  sx={{
                    padding: '12px 4px',
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 1,
                  }}
                >
                  {getClickableChildrenChip(children)}
                </Box>
                <div style={{ justifyContent: 'left' }}>
                  <Button
                    color="secondary"
                    aria-label="Add"
                    size="large"
                    onClick={() => {
                      addConditionChildren(children);
                    }}
                    disabled={!canAddConditions(
                      values as AttackChainNodeOutput as AttackChainNodeOutputType,
                      childrenConditions.find(childrenCondition => childrenCondition.childrenId === children.node?.node_id),
                    )}
                    style={{ justifyContent: 'start' }}
                  >
                    <Add fontSize="small" />
                    <Typography>
                      {t('Add condition')}
                    </Typography>
                  </Button>
                </div>
              </FormControl>
            </AccordionDetails>
          </Accordion>
        );
      })}
    </>
  );
};

export default AttackChainNodeChainsForm;
