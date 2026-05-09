import { fromJS, List, Map } from 'immutable';
import { schema } from 'normalizr';

import locale from '../utils/BrowserLanguage';

export const document = new schema.Entity(
  'documents',
  {},
  { idAttribute: 'document_id' },
);
export const arrayOfDocuments = new schema.Array(document);

export const tag = new schema.Entity('tags', {}, { idAttribute: 'tag_id' });
export const arrayOfTags = new schema.Array(tag);

export const injectorContract = new schema.Entity(
  'injector_contracts',
  {},
  { idAttribute: 'injector_contract_id' },
);
export const arrayOfInjectorContracts = new schema.Array(injectorContract);

export const injectStatus = new schema.Entity(
  'node_statuses',
  {},
  { idAttribute: 'status_id' },
);
export const arrayOfAttackChainNodeStatuses = new schema.Array(injectStatus);

export const platformParameters = new schema.Entity(
  'platformParameters',
  {},
  { idAttribute: () => 'parameters' },
);

export const token = new schema.Entity(
  'tokens',
  {},
  { idAttribute: 'token_id' },
);
export const arrayOfTokens = new schema.Array(token);

export const organization = new schema.Entity(
  'organizations',
  {},
  { idAttribute: 'organization_id' },
);
export const arrayOfOrganizations = new schema.Array(organization);

export const group = new schema.Entity(
  'groups',
  {},
  { idAttribute: 'group_id' },
);
export const arrayOfGroups = new schema.Array(group);

export const grant = new schema.Entity(
  'grants',
  {},
  { idAttribute: 'grant_id' },
);
export const arrayOfGrants = new schema.Array(grant);

export const user = new schema.Entity('users', {}, { idAttribute: 'user_id' });
export const arrayOfUsers = new schema.Array(user);

export const role = new schema.Entity(
  'roles',
  {},
  { idAttribute: 'role_id' },
);
export const arrayOfRoles = new schema.Array(role);

export const attack_chain_run = new schema.Entity(
  'attack_chain_runs',
  {},
  { idAttribute: 'attack_chain_run_id' },
);
export const arrayOfAttackChainRuns = new schema.Array(attack_chain_run);

export const objective = new schema.Entity(
  'objectives',
  {},
  { idAttribute: 'objective_id' },
);
export const arrayOfObjectives = new schema.Array(objective);

export const evaluation = new schema.Entity(
  'evaluations',
  {},
  { idAttribute: 'evaluation_id' },
);
export const arrayOfEvaluations = new schema.Array(evaluation);

export const comcheck = new schema.Entity(
  'comchecks',
  {},
  { idAttribute: 'comcheck_id' },
);
export const arrayOfComchecks = new schema.Array(comcheck);

export const comcheckStatus = new schema.Entity(
  'comcheckstatuses',
  {},
  { idAttribute: 'comcheckstatus_id' },
);
export const arrayOfComcheckStatuses = new schema.Array(comcheckStatus);

export const team = new schema.Entity(
  'teams',
  {},
  { idAttribute: 'team_id' },
);
export const arrayOfTeams = new schema.Array(team);

export const node = new schema.Entity(
  'nodes',
  {},
  { idAttribute: 'node_id' },
);
export const arrayOfAttackChainNodes = new schema.Array(node);

export const communication = new schema.Entity(
  'communications',
  {},
  { idAttribute: 'communication_id' },
);
export const arrayOfCommunications = new schema.Array(communication);

export const statistics = new schema.Entity(
  'statistics',
  {},
  { idAttribute: 'platform_id' },
);

export const log = new schema.Entity('logs', {}, { idAttribute: 'log_id' });
export const arrayOfLogs = new schema.Array(log);

export const channelReader = new schema.Entity(
  'channelreaders',
  {},
  { idAttribute: 'channel_id' },
);
export const simulationChallengesReaders = new schema.Entity(
  'simulationchallengesreaders',
  {},
  { idAttribute: 'attack_chain_run_id' },
);
export const scenarioChallengesReaders = new schema.Entity(
  'scenariochallengesreaders',
  {},
  { idAttribute: 'attack_chain_id' },
);
export const injectexpectation = new schema.Entity(
  'injectexpectations',
  {},
  { idAttribute: 'node_expectation_id' },
);
export const arrayOfInjectexpectations = new schema.Array(injectexpectation);

export const lessonsTemplate = new schema.Entity(
  'lessonstemplates',
  {},
  { idAttribute: 'lessonstemplate_id' },
);
export const arrayOfLessonsTemplates = new schema.Array(lessonsTemplate);

export const lessonsTemplateCategory = new schema.Entity(
  'lessonstemplatecategorys',
  {},
  { idAttribute: 'lessonstemplatecategory_id' },
);
export const arrayOfLessonsTemplateCategories = new schema.Array(
  lessonsTemplateCategory,
);

export const lessonsTemplateQuestion = new schema.Entity(
  'lessonstemplatequestions',
  {},
  { idAttribute: 'lessonstemplatequestion_id' },
);
export const arrayOfLessonsTemplateQuestions = new schema.Array(
  lessonsTemplateQuestion,
);

export const lessonsCategory = new schema.Entity(
  'lessonscategorys',
  {},
  { idAttribute: 'lessonscategory_id' },
);
export const arrayOfLessonsCategories = new schema.Array(lessonsCategory);

export const lessonsQuestion = new schema.Entity(
  'lessonsquestions',
  {},
  { idAttribute: 'lessonsquestion_id' },
);
export const arrayOfLessonsQuestions = new schema.Array(lessonsQuestion);

export const lessonsAnswer = new schema.Entity(
  'lessonsanswers',
  {},
  { idAttribute: 'lessonsanswer_id' },
);
export const arrayOfLessonsAnswers = new schema.Array(lessonsAnswer);

export const report = new schema.Entity(
  'reports',
  {},
  { idAttribute: 'report_id' },
);
export const arrayOfReports = new schema.Array(report);

export const variable = new schema.Entity(
  'variables',
  {},
  { idAttribute: 'variable_id' },
);
export const arrayOfVariables = new schema.Array(variable);

export const killChainPhase = new schema.Entity(
  'killchainphases',
  {},
  { idAttribute: 'phase_id' },
);
export const arrayOfKillChainPhases = new schema.Array(killChainPhase);

export const attackPattern = new schema.Entity(
  'attackpatterns',
  {},
  { idAttribute: 'attack_pattern_id' },
);
export const arrayOfAttackPatterns = new schema.Array(attackPattern);

export const injector = new schema.Entity(
  'injectors',
  {},
  { idAttribute: 'injector_id' },
);
export const arrayOfInjectors = new schema.Array(injector);

export const collector = new schema.Entity(
  'collectors',
  {},
  { idAttribute: 'collector_id' },
);
export const arrayOfCollectors = new schema.Array(collector);

export const executor = new schema.Entity(
  'executors',
  {},
  { idAttribute: 'executor_id' },
);
export const arrayOfExecutors = new schema.Array(executor);

export const payload = new schema.Entity(
  'payloads',
  {},
  { idAttribute: 'payload_id' },
);
export const arrayOfPayloads = new schema.Array(payload);

export const mitigation = new schema.Entity(
  'mitigations',
  {},
  { idAttribute: 'mitigation_id' },
);
export const arrayOfMitigations = new schema.Array(mitigation);

token.define({ token_user: user });
user.define({ user_organization: organization });

const maps = (key, state) => state.referential.getIn(['entities', key]);
const entities = (key, state) => maps(key, state).valueSeq();
const entity = (id, key, state) => state.referential.getIn(['entities', key, id]);
const me = state => state.referential.getIn(['entities', 'users', state.app.getIn(['logged', 'user'])]);

export const storeHelper = state => ({
  logged: () => state.app.get('logged'),
  getMe: () => me(state),
  getMeAdmin: () => me(state)?.get('user_admin') ?? false,
  getMeTokens: () => entities('tokens', state).filter(
    t => t.get('token_user') === me(state)?.get('user_id'),
  ),
  getUserLang: () => {
    const publicParams = state.referential.getIn(['entities', 'publicPlatformParameters', 'parameters']);
    const privateParams = state.referential.getIn(['entities', 'platformParameters', 'parameters']);
    const rawPlatformLang = (privateParams ?? publicParams)?.get('platform_lang') ?? 'auto';
    const rawUserLang = me(state)?.get('user_lang') ?? 'auto';
    const platformLang = rawPlatformLang !== 'auto' ? rawPlatformLang : locale;
    const userLang = rawUserLang !== 'auto' ? rawUserLang : platformLang;
    return userLang;
  },
  getStatistics: () => state.referential.getIn(['entities', 'statistics', 'veriguard']),
  // attack_chain_runs
  getAttackChainRuns: () => entities('attack_chain_runs', state),
  getAttackChainRunsMap: () => maps('attack_chain_runs', state),
  getAttackChainRun: id => entity(id, 'attack_chain_runs', state),
  getAttackChainRunComchecks: id => entities('comchecks', state).filter(i => i.get('comcheck_attack_chain_run') === id),
  getAttackChainRunTeams: id => entities('teams', state).filter(i => i.get('team_attack_chain_runs')?.includes(id)),
  getAttackChainRunVariables: id => entities('variables', state).filter(i => i.get('variable_attack_chain_run') === id),
  getAttackChainRunArticles: id => entities('articles', state).filter(i => i.get('article_attack_chain_run') === id),
  getAttackChainRunAttackChainNodes: id => entities('nodes', state).filter(i => i.get('node_attack_chain_run') === id),
  getAttackChainRunCommunications: id => entities('communications', state).filter(
    i => i.get('communication_attack_chain_run') === id,
  ),
  getAttackChainRunObjectives: id => entities('objectives', state).filter(o => o.get('objective_attack_chain_run') === id),
  getAttackChainRunLogs: id => entities('logs', state).filter(l => l.get('log_attack_chain_run') === id),
  getAttackChainRunLessonsCategories: id => entities('lessonscategorys', state).filter(
    l => l.get('lessons_category_attack_chain_run') === id,
  ),
  getAttackChainRunLessonsQuestions: id => entities('lessonsquestions', state).filter(
    l => l.get('lessons_question_attack_chain_run') === id,
  ),
  getAttackChainRunLessonsAnswers: exerciseId => entities('lessonsanswers', state).filter(
    l => l.get('lessons_answer_attack_chain_run') === exerciseId,
  ),
  getAttackChainRunUserLessonsAnswers: (exerciseId, userId) => entities('lessonsanswers', state).filter(
    l => l.get('lessons_answer_attack_chain_run') === exerciseId
      && l.get('lessons_answer_user') === userId,
  ),
  isAttackChainRun: id => !maps('attack_chain_runs', state)?.get(id)?.isEmpty(),
  getAttackChainRunReports: exerciseId => entities('reports', state).filter(l => l.get('report_attack_chain_run') === exerciseId),
  // report
  getReport: id => entity(id, 'reports', state),
  // comcheck
  getComcheck: id => entity(id, 'comchecks', state),
  getComcheckStatus: id => entity(id, 'comcheckstatuses', state),
  getComcheckStatuses: id => entities('comcheckstatuses', state).filter(
    i => i.get('comcheckstatus_comcheck') === id,
  ),
  getChannelReader: id => entity(id, 'channelreaders', state),
  getSimulationChallengesReader: id => entity(id, 'simulationchallengesreaders', state),
  getAttackChainChallengesReader: id => entity(id, 'scenariochallengesreaders', state),
  // users
  getUsers: () => entities('users', state),
  getGroup: id => entity(id, 'groups', state),
  getGroups: () => entities('groups', state),
  getRoles: () => entities('roles', state),
  getUsersMap: () => maps('users', state),
  getOrganizations: () => entities('organizations', state),
  getOrganizationsMap: () => maps('organizations', state),
  // objectives
  getObjective: id => entity(id, 'objectives', state),
  getObjectiveEvaluations: id => entities('evaluations', state).filter(e => e.get('evaluation_objective') === id),
  // tags
  getTag: id => entity(id, 'tags', state),
  getTags: () => entities('tags', state),
  getTagsMap: () => maps('tags', state),

  // nodes
  getAttackChainNode: id => entity(id, 'nodes', state),
  getAtomicTesting: id => entity(id, 'atomics', state),
  getAtomicTestingDetail: id => entity(id, 'atomicdetails', state),
  getAtomicTestings: () => entities('atomics', state),
  getTargetResults: (id, injectId) => entities('targetresults', state).filter(r => (r.get('target_id') === id) && (r.get('target_node_id') === injectId)),
  getAttackChainNodesMap: () => maps('nodes', state),
  getAttackChainNodeCommunications: id => entities('communications', state).filter(
    i => i.get('communication_node') === id,
  ),
  // injectexpectation
  getAttackChainNodeExpectations: () => entities('injectexpectations', state),
  getAttackChainRunAttackChainNodeExpectations: id => entities('injectexpectations', state).filter(
    i => i.get('node_expectation_attack_chain_run') === id,
  ),
  getAttackChainNodeExpectationsByAsset: (id, type) => entities('injectexpectations', state).filter(
    i => (i.get('node_expectation_asset') === id && i.get('node_expectation_type') === type),
  ),
  getAttackChainNodeExpectationsMap: () => maps('injectexpectations', state),
  // documents
  getDocuments: () => entities('documents', state),
  getDocumentsMap: () => maps('documents', state),
  // teams
  getTeam: id => entity(id, 'teams', state),
  getTeamUsers: (id) => {
    const team = entity(id, 'teams', state);
    if (!team) return List([]);
    return team.get('team_users').map(tu => entity(tu, 'users', state)).filter(u => !!u);
  },
  getTeamAttackChainRunAttackChainNodes: (id) => {
    const team = entity(id, 'teams', state);
    if (!team) return List([]);
    return team.get('team_attack_chain_run_nodes').map(te => entity(te, 'nodes', state)).filter(i => !!i);
  },
  getTeams: () => entities('teams', state),
  getTeamsMap: () => maps('teams', state),
  getPlatformSettings: () => {
    const publicParams = state.referential.getIn(['entities', 'publicPlatformParameters', 'parameters']) || Map({});
    const privateParams = state.referential.getIn(['entities', 'platformParameters', 'parameters']) || Map({});
    return publicParams.merge(privateParams);
  },
  getPlatformName: () => {
    const privateParams = state.referential.getIn(['entities', 'platformParameters', 'parameters']);
    return privateParams?.get('platform_name') || 'Veriguard - Open Adversarial Exposure Validation Platform';
  },
  // kill chain phases
  getKillChainPhase: id => entity(id, 'killchainphases', state),
  getKillChainPhases: () => entities('killchainphases', state),
  getKillChainPhasesMap: () => maps('killchainphases', state),
  // attack patterns
  getAttackPattern: id => entity(id, 'attackpatterns', state),
  getAttackPatterns: () => entities('attackpatterns', state),
  getAttackPatternsMap: () => maps('attackpatterns', state),
  // mitigations
  getMitigation: id => entity(id, 'mitigations', state),
  getMitigations: () => entities('mitigations', state),
  getMitigationsMap: () => maps('mitigations', state),
  // injectors
  getInjector: id => entity(id, 'injectors', state),
  getInjectorsIncludingPending: () => entities('injectors', state),
  getInjectorsMap: () => maps('injectors', state),
  // injector contracts
  getInjectorContract: (id) => {
    const i = entity(id, 'injector_contracts', state);
    if (!i || i.isEmpty()) {
      return i;
    }
    return i.merge(fromJS(JSON.parse(i.get('injector_contract_content'))));
  },
  getInjectorContracts: () => entities('injector_contracts', state),
  // collectors
  getCollector: id => entity(id, 'collectors', state),
  getExistingCollectors: () => entities('collectors', state).filter(c => c.get('existing_collector') === true),
  getCollectorsIncludingPending: () => entities('collectors', state),
  getCollectorsMap: () => maps('collectors', state),
  // executors
  getExecutor: id => entity(id, 'executors', state),
  getExistingExecutors: () => entities('executors', state).filter(c => c.get('existing_executor') === true),
  getExecutorsIncludingPending: () => entities('executors', state),
  getExecutorsMap: () => maps('executors', state),
  // channels
  getChannels: () => entities('channels', state),
  getChannel: id => entity(id, 'channels', state),
  getChannelsMap: () => maps('channels', state),
  // payloads
  getPayloads: () => entities('payloads', state),
  getPayload: id => entity(id, 'payloads', state),
  getPayloadsMap: () => maps('payloads', state),
  // articles
  getArticles: () => entities('articles', state),
  getArticle: id => entity(id, 'articles', state),
  getArticlesMap: () => maps('articles', state),
  // challenges
  getChallenges: () => entities('challenges', state),
  getAttackChainRunChallenges: id => entities('challenges', state).filter(c => c.get('challenge_attack_chain_runs').includes(id)),
  getChallengesMap: () => maps('challenges', state),
  // lessons templates
  getLessonsTemplate: id => entity(id, 'lessonstemplates', state),
  getLessonsTemplates: () => entities('lessonstemplates', state),
  getLessonsTemplatesMap: () => maps('lessonstemplates', state),
  getLessonsTemplateCategories: id => entities('lessonstemplatecategorys', state).filter(
    c => c.get('lessons_template_category_template') === id,
  ),
  getLessonsTemplateQuestions: () => entities('lessonstemplatequestions', state),
  getLessonsTemplateQuestionsMap: () => maps('lessonstemplatequestions', state),
  getLessonsTemplateCategoryQuestions: id => entities('lessonstemplatequestions', state).filter(
    c => c.get('lessons_template_question_category') === id,
  ),
  // assets
  getEndpoint: id => entity(id, 'endpoints', state),
  getEndpoints: () => entities('endpoints', state),
  getEndpointsMap: () => maps('endpoints', state),
  // asset groups
  getAssetGroups: () => entities('asset_groups', state),
  getAssetGroupMaps: () => maps('asset_groups', state),
  getAssetGroup: id => entity(id, 'asset_groups', state),
  // security platforms
  getSecurityPlatforms: () => entities('securityplatforms', state),
  getSecurityPlatformsMap: () => maps('securityplatforms', state),
  getSecurityPlatform: id => entity(id, 'securityplatforms', state),
  // attack_chains
  getAttackChains: () => entities('attack_chains', state),
  getAttackChainsMap: () => maps('attack_chains', state),
  getAttackChain: id => entity(id, 'attack_chains', state),
  getAttackChainTeams: id => entities('teams', state).filter(i => i.get('team_attack_chains').includes(id)),
  getAttackChainVariables: id => entities('variables', state).filter(i => i.get('variable_attack_chain') === id),
  getAttackChainArticles: id => entities('articles', state).filter(i => i.get('article_attack_chain') === id),
  getAttackChainChallenges: id => entities('challenges', state).filter(c => c.get('challenge_attack_chains').includes(id)),
  getAttackChainAttackChainNodes: id => entities('nodes', state).filter(i => i.get('node_attack_chain') === id),
  getTeamAttackChainAttackChainNodes: (id) => {
    const team = entity(id, 'teams', state);
    if (!team) return List([]);
    return team.get('team_attack_chain_nodes').map(te => entity(te, 'nodes', state)).filter(i => !!i);
  },
  getAttackChainObjectives: id => entities('objectives', state).filter(o => o.get('objective_attack_chain') === id),
  getAttackChainLessonsCategories: id => entities('lessonscategorys', state).filter(
    l => l.get('lessons_category_attack_chain') === id,
  ),
  getAttackChainLessonsQuestions: id => entities('lessonsquestions', state).filter(
    l => l.get('lessons_question_attack_chain') === id,
  ),
  getAgents: () => entities('agents', state),
  // domains
  getDomains: () => entities('domains', state),
  // catalog
  getCatalogConnectors: () => entities('catalog_connectors', state),
  getUnDeployedCatalogConnectors: () => entities('catalog_connectors', state).filter(c => c.get('instance_deployed_count') === 0),
  getCatalogConnector: id => entity(id, 'catalog_connectors', state),
  getConnectorInstance: id => entity(id, 'connectorinstances', state),
});
