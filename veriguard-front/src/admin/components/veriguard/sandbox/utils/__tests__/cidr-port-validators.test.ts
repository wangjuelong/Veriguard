import { describe, expect, test } from 'vitest';
import { isValidCidr, isValidPortExpression } from '../cidr-port-validators';

describe('isValidCidr', () => {
  test.each([
    ['10.0.0.0/8', true],
    ['0.0.0.0/0', true],
    ['192.168.1.1/32', true],
    ['::/0', true],
    ['fe80::/10', true],
    ['256.0.0.0/8', false],
    ['10.0.0.0/33', false],
    ['hello', false],
    ['', false],
  ])('isValidCidr(%s) -> %s', (input, expected) => {
    expect(isValidCidr(input)).toBe(expected);
  });
});

describe('isValidPortExpression', () => {
  test.each([
    ['all', true],
    ['80', true],
    ['1-65535', true],
    ['80,443', true],
    ['80,443,8080-8090', true],
    ['none', true],
    ['65536', false],
    ['80-79', false],
    ['abc', false],
    ['', false],
  ])('isValidPortExpression(%s) -> %s', (input, expected) => {
    expect(isValidPortExpression(input)).toBe(expected);
  });
});
