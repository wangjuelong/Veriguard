import { schema } from 'normalizr';

export const attack_chain = new schema.Entity(
  'attack_chains',
  {},
  { idAttribute: 'attack_chain_id' },
);
export const arrayOfAttackChains = new schema.Array(attack_chain);
