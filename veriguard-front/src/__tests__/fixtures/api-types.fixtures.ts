import { faker } from '@faker-js/faker';

import { type AttackChainRun, type Organization, type AttackChain, type Tag } from '../../utils/api-types';

export function createTagMap(numberTags: number): { [key: string]: Tag } {
  const tagMap: { [key: string]: Tag } = {};
  for (let i = 0; i < numberTags; i++) {
    const id = faker.string.uuid();
    tagMap[id] = {
      tag_id: faker.string.uuid(),
      tag_name: faker.lorem.sentence(),
    };
  }
  return tagMap;
}

export function createOrganisationsMap(numberTags: number): { [key: string]: Organization } {
  const orgMap: { [key: string]: Organization } = {};
  for (let i = 0; i < numberTags; i++) {
    const id = faker.string.uuid();
    orgMap[id] = {
      organization_created_at: faker.date.recent().toISOString(),
      organization_name: faker.hacker.noun(),
      organization_updated_at: faker.date.soon().toISOString(),
      organization_id: id,
    };
  }
  return orgMap;
}

export function createAttackChainRunsMap(numberTags: number): { [key: string]: AttackChainRun } {
  const exerciseMap: { [key: string]: AttackChainRun } = {};
  for (let i = 0; i < numberTags; i++) {
    const id = faker.string.uuid();
    exerciseMap[id] = {
      attack_chain_run_created_at: faker.date.recent().toISOString(),
      attack_chain_run_id: id,
      attack_chain_run_mail_from: faker.internet.email(),
      attack_chain_run_name: faker.hacker.phrase(),
      attack_chain_run_status: 'SCHEDULED',
      attack_chain_run_updated_at: faker.date.soon().toISOString(),
    };
  }
  return exerciseMap;
}

export function createAttackChainMap(numberTags: number): { [key: string]: AttackChain } {
  const scenarioMap: { [key: string]: AttackChain } = {};
  for (let i = 0; i < numberTags; i++) {
    const id = faker.string.uuid();
    scenarioMap[id] = {
      attack_chain_created_at: faker.date.recent().toISOString(),
      attack_chain_id: id,
      attack_chain_mail_from: faker.internet.email(),
      attack_chain_name: faker.hacker.phrase(),
      attack_chain_updated_at: faker.date.soon().toISOString(),
    };
  }
  return scenarioMap;
}
