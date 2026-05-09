import { type AttackChainNode, type InjectorContract, type AttackChainNodeOutput } from '../../utils/api-types';
import { type InjectorContractConverted } from '../../utils/api-types-custom';

export type AttackChainNodeStore = Omit<AttackChainNode, 'node_content' | 'node_injector_contract'> & {
  node_content?: {
    expectationScore: number;
    challenges: string[] | undefined;
  };
  node_injector_contract: Omit<InjectorContract, 'convertedContent'> & { convertedContent: InjectorContractConverted['convertedContent'] };
};

export type AttackChainNodeOutputType = AttackChainNodeOutput & { node_injector_contract: { convertedContent: InjectorContractConverted['convertedContent'] } & AttackChainNode['node_injector_contract'] };

export interface ConditionElement {
  name: string;
  value: boolean;
  key: string;
  index: number;
}

export interface ConditionType {
  parentId?: string;
  childrenId?: string;
  mode?: string;
  conditionElement?: ConditionElement[];
}

export interface Dependency {
  node?: AttackChainNodeOutputType;
  index: number;
}

export interface Content {
  expectations: {
    expectation_type: string;
    expectation_name: string;
  }[];
}

export interface ConvertedContentType {
  fields: {
    key: string;
    value: string;
    predefinedExpectations: {
      expectation_type: string;
      expectation_name: string;
    }[];
  }[];
}
