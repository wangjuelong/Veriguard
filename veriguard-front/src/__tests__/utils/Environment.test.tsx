import { faker } from '@faker-js/faker';
import { describe, expect, it } from 'vitest';

import { exportData } from '../../utils/Environment';
import {
  createAttackChainMap,
  createAttackChainRunsMap,
  createOrganisationsMap,
  createTagMap,
} from '../fixtures/api-types.fixtures';

/* eslint-disable-next-line  @typescript-eslint/no-explicit-any */
type testobj = { [key: string]: any };
function createObjWithDefaultKeys(objtype: string): testobj {
  const obj: testobj = {};
  ['name', 'extra_prop_1', 'extra_prop_2'].forEach((prop) => {
    obj[`${objtype}_${prop}`] = faker.lorem.sentence();
  });
  return obj;
}

describe('exportData tests', () => {
  describe('when exporting a test object', () => {
    const objtype = 'testobj';

    describe('when only a single key from filter found in object', () => {
      const obj = createObjWithDefaultKeys(objtype);

      const keys = [
        `${objtype}_name`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
      );
      const line = result[0];

      it('returns line with single column', async () => {
        expect(line[`${objtype}_name`]).toBe(obj[`${objtype}_name`]);
      });

      it('returns line with no other keys than specified', () => {
        Object.keys(obj).forEach(k =>
          keys.includes(k)
          // eslint-disable-next-line vitest/no-conditional-expect
            ? expect(Object.keys(line)).toContain(k)
              // eslint-disable-next-line vitest/no-conditional-expect
            : expect(Object.keys(line)).not.toContain(k),
        );
      });
    });
    describe('when testobj_type is null', () => {
      const obj = createObjWithDefaultKeys(objtype);

      obj[`${objtype}_type`] = null;

      const keys = [
        `${objtype}_name`,
        `${objtype}_type`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
      );
      const line = result[0];

      it('sets testobj_type to deleted', () => {
        expect(line[`${objtype}_type`]).toBe('deleted');
      });
    });

    describe('when object does not have tags', () => {
      const obj = createObjWithDefaultKeys(objtype);

      const keys = [
        `${objtype}_name`,
        `${objtype}_tags`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        createTagMap(3),
      );
      const line = result[0];
      it('does not incorporate tags in line', () => {
        expect(Object.keys(line)).not.toContain(`${objtype}_tags`);
      });
    });

    describe('when object has tags', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const tagMap = createTagMap(3);
      obj[`${objtype}_tags`] = Object.keys(tagMap);

      // the goal is to concatenate tag names in the export
      const expected_tag_names = Object.keys(tagMap)
        .map(k => tagMap[k].tag_name)
        .join(',');

      const keys = [
        `${objtype}_name`,
        `${objtype}_tags`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        tagMap,
      );

      const line = result[0];

      it('has key _tags in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_tags`);
      });

      it('incorporates matching tags from map into line', () => {
        expect(line[`${objtype}_tags`]).toBe(expected_tag_names);
      });
    });

    describe('when object has unknown tag', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const tagMap = createTagMap(3);
      obj[`${objtype}_tags`] = [faker.string.uuid(), faker.string.uuid()]; // not found in tag map

      // the goal is to concatenate tag names in the export
      const expected_tag_names = '';

      const keys = [
        `${objtype}_name`,
        `${objtype}_tags`,
      ];

      const result = exportData(
        objtype,
        keys,
        [obj],
        tagMap,
      );

      const line = result[0];

      it('has key _tags in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_tags`);
      });

      it('incorporates matching tags from map into line', () => {
        expect(line[`${objtype}_tags`]).toBe(expected_tag_names);
      });
    });

    describe('when object does not have organisation', () => {
      const obj = createObjWithDefaultKeys(objtype);

      const keys = [
        `${objtype}_name`,
        `${objtype}_organization`,
      ];

      const result = exportData(
        objtype,
        keys,
        [obj],
      );

      const line = result[0];

      it('does not incorporate orgs in line', () => {
        expect(Object.keys(line)).not.toContain(`${objtype}_organization`);
      });
    });

    describe('when object has organizations', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const orgMap = createOrganisationsMap(3);
      obj[`${objtype}_organization`] = Object.keys(orgMap)[1];

      // the goal is to concatenate org names in the export
      const expected_org_name = orgMap[Object.keys(orgMap)[1]].organization_name;

      const keys = [
        `${objtype}_name`,
        `${objtype}_organization`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        undefined, // tagMap
        orgMap,
      );

      const line = result[0];

      it('has key _organization in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_organization`);
      });

      it('incorporates matching orgs from map into line', () => {
        expect(line[`${objtype}_organization`]).toBe(expected_org_name);
      });
    });

    describe('when object has unknown organisation', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const orgMap = createOrganisationsMap(3);
      obj[`${objtype}_organization`] = faker.string.uuid(); // not found in org map

      // the goal is to concatenate tag names in the export
      const expected_org_name = '';

      const keys = [
        `${objtype}_name`,
        `${objtype}_organization`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        undefined, // tagMap
        orgMap,
      );

      const line = result[0];

      it('has key _organization in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_organization`);
      });

      it('incorporates matching org from map into line', () => {
        expect(line[`${objtype}_organization`]).toBe(expected_org_name);
      });
    });

    describe('when object does not have attack_chain_runs', () => {
      const obj = createObjWithDefaultKeys(objtype);

      const keys = [
        `${objtype}_name`,
        `${objtype}_attack_chain_runs`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
      );

      const line = result[0];

      it('does not incorporate attack_chain_runs in line', () => {
        expect(Object.keys(line)).not.toContain(`${objtype}_attack_chain_runs`);
      });
    });

    describe('when object has attack_chain_runs', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const exerciseMap = createAttackChainRunsMap(3);
      obj[`${objtype}_attack_chain_runs`] = Object.keys(exerciseMap);

      // the goal is to concatenate tag names in the export
      const expected_attack_chain_run_names = Object.keys(exerciseMap)
        .map(k => exerciseMap[k].attack_chain_run_name)
        .join(',');

      const keys = [
        `${objtype}_name`,
        `${objtype}_attack_chain_runs`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        undefined, // tagMap
        undefined, // orgMap
        exerciseMap,
      );

      const line = result[0];

      it('has key _attack_chain_runs in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_attack_chain_runs`);
      });

      it('incorporates matching tags from map into line', () => {
        expect(line[`${objtype}_attack_chain_runs`]).toBe(expected_attack_chain_run_names);
      });
    });

    describe('when object has unknown attack_chain_run', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const exerciseMap = createAttackChainRunsMap(3);
      obj[`${objtype}_attack_chain_runs`] = [faker.string.uuid(), faker.string.uuid()]; // not found in tag map

      // the goal is to concatenate tag names in the export
      const expected_attack_chain_run_names = '';

      const keys = [
        `${objtype}_name`,
        `${objtype}_attack_chain_runs`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        undefined, // tagMap
        undefined, // orgMap
        exerciseMap,
      );

      const line = result[0];

      it('has key _attack_chain_runs in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_attack_chain_runs`);
      });

      it('incorporates matching attack_chain_runs from map into line', () => {
        expect(line[`${objtype}_attack_chain_runs`]).toBe(expected_attack_chain_run_names);
      });
    });

    describe('when object does not have attack_chains', () => {
      const obj = createObjWithDefaultKeys(objtype);

      const keys = [
        `${objtype}_name`,
        `${objtype}_attack_chains`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
      );

      const line = result[0];

      it('does not incorporate attack_chains in line', () => {
        expect(Object.keys(line)).not.toContain(`${objtype}_attack_chains`);
      });
    });

    describe('when object has attack_chains', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const scenarioMap = createAttackChainMap(3);
      obj[`${objtype}_attack_chains`] = Object.keys(scenarioMap);

      // the goal is to concatenate tag names in the export
      const expected_attack_chain_names = Object.keys(scenarioMap)
        .map(k => scenarioMap[k].attack_chain_name)
        .join(',');

      const keys = [
        `${objtype}_name`,
        `${objtype}_attack_chains`,
      ];

      const result = exportData(
        objtype,
        keys,
        [obj],
        undefined, // tagMap
        undefined, // orgMap
        undefined, // exerciseMap
        scenarioMap,
      );

      const line = result[0];

      it('has key _attack_chains in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_attack_chains`);
      });

      it('incorporates matching tags from map into line', () => {
        expect(line[`${objtype}_attack_chains`]).toBe(expected_attack_chain_names);
      });
    });

    describe('when object has unknown attack_chain', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const scenarioMap = createAttackChainMap(3);
      obj[`${objtype}_attack_chains`] = [faker.string.uuid(), faker.string.uuid()]; // not found in tag map

      // the goal is to concatenate tag names in the export
      const expected_attack_chain_names = '';

      const keys = [
        `${objtype}_name`,
        `${objtype}_attack_chains`,
      ];

      const result = exportData(
        objtype,
        keys,
        [obj],
        undefined, // tagMap
        undefined, // orgMap
        undefined, // exerciseMap
        scenarioMap,
      );

      const line = result[0];

      it('has key _attack_chains in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_attack_chains`);
      });

      it('incorporates matching attack_chains from map into line', () => {
        expect(line[`${objtype}_attack_chains`]).toBe(expected_attack_chain_names);
      });
    });
  });

  describe('when exporting an object of type node', () => {
    const objtype = 'node';

    describe('when node has an object content', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const object_content = {
        key1: 'content1',
        key2: 'content2',
      };
      obj[`${objtype}_content`] = object_content;
      // mirror what's being done in the tested method
      const expected_string_content = JSON.stringify(object_content).toString().replaceAll('"', '""');

      const keys = [
        `${objtype}_name`,
        `${objtype}_content`,
      ];

      const result = exportData(
        objtype,
        keys,
        [obj],
      );

      const line = result[0];

      it('transforms content into escaped string', async () => {
        expect(line[`${objtype}_content`]).toBe(expected_string_content);
      });
    });
  });
});
