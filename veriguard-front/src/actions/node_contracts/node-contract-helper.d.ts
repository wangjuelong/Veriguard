import { type NodeContract, type NodeContractDomainCountOutput } from '../../utils/api-types';

export interface NodeContractHelper {
  getInjectorContract: (injectorContractId: string) => NodeContract;
  getInjectorContracts: () => NodeContract[];
  getInjectorContractsWithNoTeams: () => Contract['config']['type'][];
  getInjectorContractsMapByType: () => Record<string, Contract>;
  getInjectorContractsDomainCounts: () => NodeContractDomainCountOutput[];
}
