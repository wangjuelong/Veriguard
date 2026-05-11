import { type Dispatch } from 'redux';

import {
  delReferential,
  getReferential,
  postReferential,
  putReferential,
  simpleCall,
  simplePostCall,
} from '../../utils/Action';
import {
  type AttackChain,
  type AttackChainInput,
  type AttackChainNodesImportInput,
  type AttackChainRecurrenceInput,
  type AttackChainTeamPlayersEnableInput,
  type Filter,
  type FilterGroup,
  type GetAttackChainsInput,
  type LessonsCategoryCreateInput,
  type LessonsCategoryTeamsInput,
  type LessonsCategoryUpdateInput,
  type LessonsInput,
  type LessonsQuestionCreateInput,
  type LessonsQuestionUpdateInput,
  type NodeContract,
  type SearchPaginationInput,
  type Team,
  type UpdateAttackChainInput,
  type WidgetToEntitiesInput,
} from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import * as schema from '../Schema';
import { arrayOfAttackChains, attack_chain } from './attack_chain-schema';

export const SCENARIO_URI = '/api/attack_chains';

export const addAttackChain = (data: AttackChainInput) => (dispatch: Dispatch) => {
  return postReferential(attack_chain, SCENARIO_URI, data)(dispatch);
};

export const fetchAttackChains = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfAttackChains, SCENARIO_URI)(dispatch);
};

export const fetchAttackChainsById = (exerciseIds: GetAttackChainsInput) => (dispatch: Dispatch) => {
  return postReferential(arrayOfAttackChains, SCENARIO_URI + '/search-by-id', exerciseIds, undefined, false)(dispatch);
};

export const searchAttackChains = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = '/api/attack_chains/search';
  return simplePostCall(uri, data);
};

export const fetchAttackChain = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return getReferential(attack_chain, uri)(dispatch);
};

export const updateAttackChain = (
  scenarioId: AttackChain['attack_chain_id'],
  data: UpdateAttackChainInput,
) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return putReferential(attack_chain, uri, data)(dispatch);
};

/**
 * Wire-format snapshot for PUT /api/attack_chains/{id}/settings (Phase 12b-B3).
 *
 * Mirrors backend io.veriguard.rest.attack_chain.form.AttackChainSettingsInput;
 * not yet regenerated into api-types.d.ts. Kept here as a thin transport DTO
 * so the editor settings drawer can submit V3 orchestration fields without
 * touching the existing UpdateAttackChainInput surface (which covers basic
 * info: name / description / mail / tags).
 */
export interface AttackChainSettingsInputDto {
  attack_chain_execution_mode: 'STOP_ON_BLOCK' | 'CONTINUE';
  attack_chain_validation_parameter_set_id: string | null;
  attack_chain_soc_correlation_rules: {
    connector_id: string;
    rule_id: string;
    display_name: string;
    match_window_seconds: number;
  }[];
}

export const updateAttackChainSettings = (
  scenarioId: AttackChain['attack_chain_id'],
  data: AttackChainSettingsInputDto,
) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/settings`;
  return putReferential(attack_chain, uri, data)(dispatch);
};

export const deleteAttackChain = (scenarioId: AttackChain['attack_chain_id']) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return delReferential(uri, attack_chain.key, scenarioId)(dispatch);
};

export const exportAttackChainUri = (scenarioId: AttackChain['attack_chain_id'], exportTeams: boolean, exportPlayers: boolean, exportVariableValues: boolean) => {
  return `${SCENARIO_URI}/${scenarioId}/export?isWithTeams=${exportTeams}&isWithPlayers=${exportPlayers}&isWithVariableValues=${exportVariableValues}`;
};

export const importAttackChain = (formData: FormData) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/import`;
  return postReferential(null, uri, formData)(dispatch);
};

export const duplicateAttackChain = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return postReferential(attack_chain, uri, null)(dispatch);
};

// -- SCENARIO TO EXERCISE

export const createRunningAttackChainRunFromAttackChain = (scenarioId: string) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/attack_chain_run/running`;
  return simplePostCall(uri);
};

// -- TEAMS --

export const fetchPlayersByAttackChain = (scenarioId: AttackChain['attack_chain_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/players`;
  return getReferential(schema.arrayOfUsers, uri)(dispatch);
};

export const fetchAttackChainTeams = (scenarioId: AttackChain['attack_chain_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/teams`;
  return getReferential(schema.arrayOfTeams, uri)(dispatch);
};

export const enableAttackChainTeamPlayers = (scenarioId: AttackChain['attack_chain_id'], teamId: Team['team_id'], data: AttackChainTeamPlayersEnableInput) => (dispatch: Dispatch) => putReferential(
  attack_chain,
  `/api/attack_chains/${scenarioId}/teams/${teamId}/players/enable`,
  data,
)(dispatch);

export const disableAttackChainTeamPlayers = (scenarioId: AttackChain['attack_chain_id'], teamId: Team['team_id'], data: AttackChainTeamPlayersEnableInput) => (dispatch: Dispatch) => putReferential(
  attack_chain,
  `/api/attack_chains/${scenarioId}/teams/${teamId}/players/disable`,
  data,
)(dispatch);

export const addAttackChainTeamPlayers = (scenarioId: AttackChain['attack_chain_id'], teamId: Team['team_id'], data: AttackChainTeamPlayersEnableInput) => (dispatch: Dispatch) => putReferential(
  attack_chain,
  `/api/attack_chains/${scenarioId}/teams/${teamId}/players/add`,
  data,
)(dispatch);

export const removeAttackChainTeamPlayers = (scenarioId: AttackChain['attack_chain_id'], teamId: Team['team_id'], data: AttackChainTeamPlayersEnableInput) => (dispatch: Dispatch) => putReferential(
  attack_chain,
  `/api/attack_chains/${scenarioId}/teams/${teamId}/players/remove`,
  data,
)(dispatch);

// -- EXERCISES --

export const searchAttackChainAttackChainRuns = (scenarioId: AttackChain['attack_chain_id'], paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = `/api/attack_chains/${scenarioId}/attack_chain_runs/search`;
  return simplePostCall(uri, data);
};

// -- HEALTHCHEKS --

export const searchAttackChainHealthcheks = (scenarioId: AttackChain['attack_chain_id']) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/healthchecks`;
  return simpleCall(uri);
};

// -- RECURRENCE --

export const updateAttackChainRecurrence = (
  scenarioId: AttackChain['attack_chain_id'],
  data: AttackChainRecurrenceInput,
) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/recurrence`;
  return putReferential(attack_chain, uri, data)(dispatch);
};

// -- STATISTIC --

export const fetchAttackChainStatistic = (scenarioId: AttackChain['attack_chain_id']) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/statistics`;
  return simpleCall(uri);
};

// -- IMPORT --

export const importXlsForAttackChain = (scenarioId: AttackChain['attack_chain_id'], importId: string, input: AttackChainNodesImportInput) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/xls/${importId}/import`;
  return simplePostCall(uri, input)
    .then((response) => {
      const injectCount = response.data.total_nodes;
      if (injectCount === 0) {
        MESSAGING$.notifySuccess('No node imported');
      } else {
        MESSAGING$.notifySuccess(`${injectCount} node imported`);
      }
      return response;
    });
};

export const dryImportXlsForAttackChain = (scenarioId: AttackChain['attack_chain_id'], importId: string, input: AttackChainNodesImportInput) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/xls/${importId}/dry`;
  return simplePostCall(uri, input)
    .then((response) => {
      return response;
    });
};

// -- OPTION --

export const searchAttackChainAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${SCENARIO_URI}/options`, { params });
};

export const searchAttackChainByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${SCENARIO_URI}/options`, ids);
};

export const searchAttackChainCategoryAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${SCENARIO_URI}/category/options`, { params });
};

// -- LESSONS --

export const updateAttackChainLessons = (scenarioId: string, data: LessonsInput) => (dispatch: Dispatch) => putReferential(
  attack_chain,
  `/api/attack_chains/${scenarioId}/lessons`,
  data,
)(dispatch);

export const fetchLessonsCategories = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_categories`;
  return getReferential(schema.arrayOfLessonsCategories, uri)(dispatch);
};

export const updateLessonsCategory = (scenarioId: string, lessonsCategoryId: string, data: LessonsCategoryUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_categories/${lessonsCategoryId}`;
  return putReferential(schema.lessonsCategory, uri, data)(dispatch);
};

export const updateLessonsCategoryTeams = (scenarioId: string, lessonsCategoryId: string, data: LessonsCategoryTeamsInput) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_categories/${lessonsCategoryId}/teams`;
  return putReferential(schema.lessonsCategory, uri, data)(dispatch);
};

export const addLessonsCategory = (scenarioId: string, data: LessonsCategoryCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_categories`;
  return postReferential(schema.lessonsCategory, uri, data)(dispatch);
};

export const deleteLessonsCategory = (scenarioId: string, lessonsCategoryId: string) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_categories/${lessonsCategoryId}`;
  return delReferential(uri, 'lessonscategorys', lessonsCategoryId)(dispatch);
};

export const applyLessonsTemplate = (scenarioId: string, lessonsTemplateId: string) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_apply_template/${lessonsTemplateId}`;
  return postReferential(schema.arrayOfLessonsCategories, uri, {})(dispatch);
};

export const fetchLessonsQuestions = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_questions`;
  return getReferential(schema.arrayOfLessonsQuestions, uri)(dispatch);
};

export const updateLessonsQuestion = (scenarioId: string, lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}`;
  return putReferential(schema.lessonsQuestion, uri, data)(dispatch);
};

export const addLessonsQuestion = (scenarioId: string, lessonsCategoryId: string, data: LessonsQuestionCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_categories/${lessonsCategoryId}/lessons_questions`;
  return postReferential(schema.lessonsQuestion, uri, data)(dispatch);
};

export const deleteLessonsQuestion = (scenarioId: string, lessonsCategoryId: string, lessonsQuestionId: string) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}`;
  return delReferential(uri, 'lessonsquestions', lessonsQuestionId)(dispatch);
};

export const emptyLessonsCategories = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/lessons_empty`;
  return postReferential(schema.arrayOfLessonsCategories, uri, {})(dispatch);
};

export const checkAttackChainTagRules = (scenarioId: string, newTagIds: string[]) => {
  const uri = `/api/attack_chains/${scenarioId}/check-rules`;
  const input = { new_tags: newTagIds };
  return simplePostCall(uri, input);
};

export const fetchCustomDashboardFromAttackChain = (scenarioId: string) => {
  return simpleCall(`/api/attack_chains/${scenarioId}/dashboard`);
};

export const countByAttackChain = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`/api/attack_chains/${scenarioId}/dashboard/count/${widgetId}`, parameters);
};

export const averageByAttackChain = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`/api/attack_chains/${scenarioId}/dashboard/average/${widgetId}`, parameters);
};

export const seriesByAttackChain = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`/api/attack_chains/${scenarioId}/dashboard/series/${widgetId}`, parameters);
};

export const entitiesByAttackChain = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`/api/attack_chains/${scenarioId}/dashboard/entities/${widgetId}`, parameters);
};

export const widgetToEntitiesByByAttackChain = (scenarioId: string, widgetId: string, input: WidgetToEntitiesInput) => {
  return simplePostCall(`/api/attack_chains/${scenarioId}/dashboard/entities-runtime/${widgetId}`, input);
};

export const attackPathsByAttackChain = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`/api/attack_chains/${scenarioId}/dashboard/attack-paths/${widgetId}`, parameters);
};

// -- DYNAMIC FILTER (Phase 12c-Biii) --
// Phase 12c-Biii follow-up：原 FilterGroupWire / FilterWire 等是 B1 step 自造的
// 镜像类型；改用 api-types FilterGroup / Filter 后这些 alias 仅作 backward-compat
// export 保留，避免破坏潜在 import。

/** @deprecated 用 api-types Filter['operator']. 保留以避免破坏 import。 */
export type FilterOperatorWire
  = | 'eq'
    | 'not_eq'
    | 'contains'
    | 'not_contains'
    | 'starts_with'
    | 'not_starts_with'
    | 'empty'
    | 'not_empty';

/** @deprecated 用 api-types FilterGroup['mode']. 保留以避免破坏 import。 */
export type FilterModeWire = 'and' | 'or';

/** @deprecated 用 api-types Filter. 保留以避免破坏 import。 */
export type FilterWire = Filter;

/** @deprecated 用 api-types FilterGroup. 保留以避免破坏 import。 */
export type FilterGroupWire = FilterGroup;

export interface AttackChainDynamicFilterInputWire { dynamic_filter: FilterGroup }

/**
 * AttackChain extended with the 二开 dynamic-filter wire fields (Phase 12c-Biii).
 *
 * 后端 fetchAttackChain 在响应中注入 `attack_chain_dynamic_filter`（JSONB
 * 持久化）+ `attack_chain_dynamic_contracts`（@Transient 派生）。这些字段
 * 不在生成的 api-types.d.ts 中（来自 OpenAPI），故在此声明扩展接口供前端
 * 消费方就近 cast，避免在每个调用点重复 `as unknown as Record<...>` 类型穿越.
 */
export interface AttackChainWithDynamic extends AttackChain {
  attack_chain_dynamic_filter?: FilterGroup;
  attack_chain_dynamic_contracts?: NodeContract[];
}

export const updateAttackChainDynamicFilter
  = (
    attackChainId: AttackChain['attack_chain_id'],
    input: AttackChainDynamicFilterInputWire,
  ) =>
    (dispatch: Dispatch) => {
      const uri = `${SCENARIO_URI}/${attackChainId}/dynamic_filter`;
      return putReferential(attack_chain, uri, input)(dispatch);
    };
