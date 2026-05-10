import { type AttackChainNodeExpectation } from '../../../../../utils/api-types';

export interface AttackChainNodeExpectationsStore extends Omit<AttackChainNodeExpectation, 'node_expectation_team' | 'node_expectation_user' | 'node_expectation_article' | 'node_expectation_challenge' | 'node_expectation_asset'> {
  node_expectation_team: string | undefined;
  node_expectation_user: string | undefined;
  node_expectation_article: string | undefined;
  node_expectation_challenge: string | undefined;
  node_expectation_asset: string | undefined;
}

export interface ExpectationInput {
  expectation_type: string;
  expectation_name: string;
  expectation_description?: string;
  expectation_score: number;
  expectation_expectation_group: boolean;
  expectation_expiration_time: number;
}

export interface ExpectationInputForm extends Omit<ExpectationInput, 'expectation_expiration_time'> {
  expiration_time_days: number;
  expiration_time_hours: number;
  expiration_time_minutes: number;
}

export enum ExpectationType {
  PREVENTION = 'PREVENTION',
  DETECTION = 'DETECTION',
  VULNERABILITY = 'VULNERABILITY',
  MANUAL = 'MANUAL',
  ARTICLE = 'ARTICLE',
  CHALLENGE = 'CHALLENGE',
}

export type ExpectationResultType = 'PREVENTION' | 'DETECTION' | 'VULNERABILITY' | 'HUMAN_RESPONSE';

export const expectationResultTypes: ExpectationResultType [] = ['PREVENTION', 'DETECTION', 'VULNERABILITY', 'HUMAN_RESPONSE'];

export const mitreMatrixExpectationTypes: ExpectationResultType [] = ['PREVENTION', 'DETECTION', 'VULNERABILITY'];
