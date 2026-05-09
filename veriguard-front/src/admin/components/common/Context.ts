import { createContext, type ReactElement } from 'react';

import { type AttackChainNodeOutputType, type AttackChainNodeStore } from '../../../actions/attack_chain_nodes/AttackChainNode';
import { type FullArticleStore } from '../../../actions/channels/Article';
import { type Page } from '../../../components/common/queryable/Page';
import {
  type Article,
  type ArticleCreateInput,
  type ArticleUpdateInput,
  type Challenge,
  type Channel,
  type Evaluation,
  type EvaluationInput,
  type ImportTestSummary,
  type AttackChainNode,
  type AttackChainNodeBulkProcessingInput,
  type AttackChainNodeBulkUpdateInputs, type AttackChainNodeInput,
  type AttackChainNodesImportInput,
  type AttackChainNodeTestStatusOutput,
  type LessonsAnswer,
  type LessonsAnswerCreateInput,
  type LessonsCategory,
  type LessonsCategoryCreateInput,
  type LessonsCategoryTeamsInput,
  type LessonsCategoryUpdateInput,
  type LessonsQuestion,
  type LessonsQuestionCreateInput,
  type LessonsQuestionUpdateInput,
  type LessonsSendInput,
  type Objective,
  type ObjectiveInput,
  type PublicAttackChainRun,
  type PublicAttackChain,
  type Report,
  type ReportInput,
  type SearchPaginationInput,
  type Team,
  type TeamCreateInput,
  type TeamOutput,
  type Variable,
  type VariableInput,
} from '../../../utils/api-types';
import { INHERITED_CONTEXT, type InheritedContext } from '../../../utils/permissions/types';
import { type UserStore } from '../teams/players/Player';

export type PermissionsContextType = {
  permissions: {
    readOnly: boolean;
    canManage: boolean;
    canAccess: boolean;
    canLaunch: boolean;
    canDelete: boolean;
    isRunning: boolean;
  };
  inherited_context: InheritedContext;
};

export type ArticleContextType = {
  previewArticleUrl: (article: FullArticleStore) => string;
  fetchArticles: () => Promise<{
    result: string[];
    entities: { articles: Record<string, Article> };
  }>;
  fetchChannels: () => Promise<{
    result: string[];
    entities: { channels: Record<string, Channel> };
  }>;
  fetchDocuments: () => Promise<Document[]>;
  onAddArticle: (data: ArticleCreateInput) => Promise<{ result: string }>;
  onUpdateArticle: (article: Article, data: ArticleUpdateInput) => string;
  onDeleteArticle: (article: Article) => string;
};

export type ChallengeContextType = {
  previewChallengeUrl?: () => string;
  fetchChallenges?: () => Promise<{
    result: string[];
    entities: { challenges: Record<string, Challenge> };
  }>;
};

export type PreviewChallengeContextType = {
  linkToPlayerMode: string;
  linkToAdministrationMode: string;
  scenarioOrAttackChainRun: PublicAttackChain | PublicAttackChainRun | undefined;
};

export type AttackChainNodeTestContextType = {
  contextId: string;
  url?: string;
  searchAttackChainNodeTests?: (contextId: string, searchPaginationInput: SearchPaginationInput) => Promise<{ data: Page<AttackChainNodeTestStatusOutput> }>;
  fetchAttackChainNodeTestStatus?: (testId: string) => Promise<{ data: AttackChainNodeTestStatusOutput }>;
  testAttackChainNode?: (contextId: string, injectId: string) => Promise<{ data: AttackChainNodeTestStatusOutput }>;
  bulkTestAttackChainNodes?: (contextId: string, data: AttackChainNodeBulkProcessingInput) => Promise<{ data: AttackChainNodeTestStatusOutput[] }>;
  deleteAttackChainNodeTest?: (contextId: string, testId: string) => void;
};

export type DocumentContextType = {
  onInitDocument: () => {
    document_tags: {
      id: string;
      label: string;
    }[];
    document_attack_chain_runs: {
      id: string;
      label: string;
    }[];
    document_attack_chains: {
      id: string;
      label: string;
    }[];
  };
};

export type VariableContextType = {
  onCreateVariable: (data: VariableInput) => void;
  onEditVariable: (variable: Variable, data: VariableInput) => void;
  onDeleteVariable: (variable: Variable) => void;
};

export type ReportContextType = {
  onDeleteReport: (report: Report) => void;
  onUpdateReport: (reportId: Report['report_id'], report: ReportInput) => void;
  renderReportForm: (onSubmitForm: (data: ReportInput) => void, onHandleCancel: () => void, report: Report) => ReactElement;
};

export type TeamContextType = {
  onAddUsersTeam?: (teamId: Team['team_id'], userIds: UserStore['user_id'][]) => Promise<void>;
  onRemoveUsersTeam?: (teamId: Team['team_id'], userIds: UserStore['user_id'][]) => Promise<void>;
  onCreateTeam?: (team: TeamCreateInput) => Promise<{ result: string }>;
  onRemoveTeam?: (teamId: Team['team_id']) => Promise<{
    result: string[];
    entities: { teams: Record<string, Team> };
  }>;
  onReplaceTeam?: (teamIds: Team['team_id'][]) => Promise<{
    result: string[];
    entities: { teams: Record<string, Team> };
  }>;
  onToggleUser?: (teamId: Team['team_id'], userId: UserStore['user_id'], userEnabled: boolean) => void;
  checkUserEnabled?: (teamId: Team['team_id'], userId: UserStore['user_id']) => boolean;
  computeTeamUsersEnabled?: (teamId: Team['team_id']) => number;
  searchTeams: (input: SearchPaginationInput, contextualOnly?: boolean) => Promise<{ data: Page<TeamOutput> }>;
  allUsersEnabledNumber?: number;
  allUsersNumber?: number;
};

export type AttackChainNodeContextType = {
  nodes: AttackChainNodeOutputType[];
  setAttackChainNodes: (input: AttackChainNodeOutputType[]) => void;
  searchAttackChainNodes: (input: SearchPaginationInput) => Promise<{ data: Page<AttackChainNodeOutputType> }>;
  onAddAttackChainNode: (node: AttackChainNode) => Promise<{
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }>;
  onAddMultipleAttackChainNodes: (inputs: AttackChainNodeInput[]) => Promise<{
    result: string[];
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }>;
  onBulkUpdateAttackChainNode: (param: AttackChainNodeBulkUpdateInputs) => Promise<AttackChainNode[] | void>;
  onUpdateAttackChainNode: (injectId: AttackChainNode['node_id'], node: AttackChainNode) => Promise<{
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }>;
  onUpdateAttackChainNodeTrigger?: (injectId: AttackChainNode['node_id']) => Promise<{
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }>;
  onUpdateAttackChainNodeActivation: (injectId: AttackChainNode['node_id'], injectEnabled: { node_enabled: boolean }) => Promise<{
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }>;
  onAttackChainNodeDone?: (injectId: AttackChainNode['node_id']) => Promise<{
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }>;
  onDeleteAttackChainNode: (injectId: AttackChainNode['node_id']) => Promise<void>;
  onImportAttackChainNodeFromJson?: (file: File) => Promise<void>;
  onImportAttackChainNodeFromXls?: (importId: string, input: AttackChainNodesImportInput) => Promise<ImportTestSummary>;
  onDryImportAttackChainNodeFromXls?: (importId: string, input: AttackChainNodesImportInput) => Promise<ImportTestSummary>;
  onBulkDeleteAttackChainNodes: (param: AttackChainNodeBulkProcessingInput) => Promise<AttackChainNode[]>;
  bulkTestAttackChainNodes: (param: AttackChainNodeBulkProcessingInput) => Promise<{
    uri: string;
    data: AttackChainNodeTestStatusOutput[];
  }>;
};
export type LessonContextType = {
  onApplyLessonsTemplate: (data: string) => Promise<LessonsCategory[]>;
  onResetLessonsAnswers?: () => Promise<LessonsCategory[]>;
  onEmptyLessonsCategories: () => Promise<LessonsCategory[]>;
  onUpdateSourceLessons: (data: boolean) => Promise<{ result: string }>;
  onSendLessons?: (data: LessonsSendInput) => void;
  onAddLessonsCategory: (data: LessonsCategoryCreateInput) => Promise<LessonsCategory>;
  onDeleteLessonsCategory: (data: string) => void;
  onUpdateLessonsCategory: (lessonCategoryId: string, data: LessonsCategoryUpdateInput) => Promise<LessonsCategory>;
  onUpdateLessonsCategoryTeams: (lessonCategoryId: string, data: LessonsCategoryTeamsInput) => Promise<LessonsCategory>;
  onDeleteLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string) => void;
  onUpdateLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => Promise<LessonsQuestion>;
  onAddLessonsQuestion: (lessonsCategoryId: string, data: LessonsQuestionCreateInput) => Promise<LessonsQuestion>;
  onAddObjective: (data: ObjectiveInput) => Promise<Objective>;
  onUpdateObjective: (objectiveId: string, data: ObjectiveInput) => Promise<Objective>;
  onDeleteObjective: (objectiveId: string) => void;
  onAddEvaluation: (objectiveId: string, data: EvaluationInput) => Promise<Evaluation>;
  onUpdateEvaluation: (objectiveId: string, evaluationId: string, data: EvaluationInput) => Promise<Evaluation>;
  onFetchEvaluation: (objectiveId: string) => Promise<Evaluation[]>;
};
export type ViewLessonContextType = {
  onAddLessonsAnswers?: (questionCategory: string, lessonsQuestionId: string, answerData: LessonsAnswerCreateInput) => Promise<LessonsAnswer>;
  onFetchPlayerLessonsAnswers?: () => Promise<LessonsAnswer[]>;
};

export const PermissionsContext = createContext<PermissionsContextType>({
  permissions: {
    canAccess: false,
    canManage: false,
    canLaunch: false,
    canDelete: false,
    readOnly: false,
    isRunning: false,
  },
  inherited_context: INHERITED_CONTEXT.NONE,
});
export const ArticleContext = createContext<ArticleContextType>({
  fetchArticles(): Promise<{
    result: string[];
    entities: { articles: Record<string, Article> };
  }> {
    return Promise.resolve({
      result: [],
      entities: { articles: {} },
    });
  },
  fetchChannels(): Promise<{
    result: string[];
    entities: { channels: Record<string, Channel> };
  }> {
    return Promise.resolve({
      result: [],
      entities: { channels: {} },
    });
  },
  fetchDocuments(): Promise<Document[]> {
    return new Promise<Document[]>(() => {
    });
  },
  onAddArticle(_data: ArticleCreateInput): Promise<{ result: string }> {
    return Promise.resolve({ result: '' });
  },
  onDeleteArticle(_article: Article): string {
    return '';
  },
  onUpdateArticle(_article: Article, _data: ArticleUpdateInput): string {
    return '';
  },
  previewArticleUrl(_article: FullArticleStore): string {
    return '';
  },
});
export const ChallengeContext = createContext<ChallengeContextType>({
  previewChallengeUrl(): string {
    return '';
  },
  fetchChallenges(): Promise<{
    result: string[];
    entities: { challenges: Record<string, Challenge> };
  }> {
    return Promise.resolve({
      result: [],
      entities: { challenges: {} },
    });
  },
});
export const PreviewChallengeContext = createContext<PreviewChallengeContextType>({
  linkToPlayerMode: '',
  linkToAdministrationMode: '',
  scenarioOrAttackChainRun: {
    description: '',
    id: '',
    name: '',
  },
});

export const AttackChainNodeTestContext = createContext<AttackChainNodeTestContextType>({
  contextId: '',
  url: '',
  searchAttackChainNodeTests: undefined,
  fetchAttackChainNodeTestStatus: undefined,
  testAttackChainNode: undefined,
  bulkTestAttackChainNodes: undefined,
  deleteAttackChainNodeTest: undefined,
});
export const DocumentContext = createContext<DocumentContextType>({
  onInitDocument(): {
    document_tags: {
      id: string;
      label: string;
    }[];
    document_attack_chain_runs: {
      id: string;
      label: string;
    }[];
    document_attack_chains: {
      id: string;
      label: string;
    }[];
  } {
    return {
      document_attack_chain_runs: [],
      document_attack_chains: [],
      document_tags: [],
    };
  },
});
export const VariableContext = createContext<VariableContextType>({
  onCreateVariable(_data: VariableInput): void {
  },
  onDeleteVariable(_variable: Variable): void {
  },
  onEditVariable(_variable: Variable, _data: VariableInput): void {
  },
});
export const ReportContext = createContext<ReportContextType>(<ReportContextType>{
  onDeleteReport(_report: Report): void {
  },
  onUpdateReport(_reportId: Report['report_id'], _report: ReportInput): void {
  },
  renderReportForm(_onSubmit: (data: ReportInput) => void, _onCancel: () => void, _report: Report): void {
  },
});
export const TeamContext = createContext<TeamContextType>({
  onAddUsersTeam(_teamId: Team['team_id'], _userIds: UserStore['user_id'][]): Promise<void> {
    return new Promise<void>(() => {
    });
  },
  onRemoveUsersTeam(_teamId: Team['team_id'], _userIds: UserStore['user_id'][]): Promise<void> {
    return new Promise<void>(() => {
    });
  },
  searchTeams(_: SearchPaginationInput, _contextualOnly?: boolean): Promise<{ data: Page<TeamOutput> }> {
    return new Promise<{ data: Page<TeamOutput> }>(() => {
    });
  },
});
export const AttackChainNodeContext = createContext<AttackChainNodeContextType>({
  nodes: [],
  setAttackChainNodes: () => {
  },
  searchAttackChainNodes(_: SearchPaginationInput): Promise<{ data: Page<AttackChainNodeOutputType> }> {
    return new Promise<{ data: Page<AttackChainNodeOutputType> }>(() => {
    });
  },
  onAddAttackChainNode(_node: AttackChainNode): Promise<{
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }> {
    return Promise.resolve({
      result: '',
      entities: { nodes: {} },
    });
  },
  onAddMultipleAttackChainNodes(_inputs: AttackChainNodeInput[]): Promise<{
    result: string[];
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }> {
    return Promise.resolve({
      result: [],
      entities: { nodes: {} },
    });
  },
  onBulkUpdateAttackChainNode(_param: AttackChainNodeBulkUpdateInputs): Promise<AttackChainNode[] | void> {
    return Promise.resolve([]);
  },
  onUpdateAttackChainNode(_injectId: AttackChainNode['node_id'], _node: AttackChainNode): Promise<{
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }> {
    return Promise.resolve({
      result: '',
      entities: { nodes: {} },
    });
  },
  onUpdateAttackChainNodeTrigger(_injectId: AttackChainNode['node_id']): Promise<{
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }> {
    return Promise.resolve({
      result: '',
      entities: { nodes: {} },
    });
  },
  onUpdateAttackChainNodeActivation(_injectId: AttackChainNode['node_id'], _injectEnabled: { node_enabled: boolean }): Promise<{
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }> {
    return Promise.resolve({
      result: '',
      entities: { nodes: {} },
    });
  },
  onAttackChainNodeDone(_injectId: AttackChainNode['node_id']): Promise<{
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }> {
    return Promise.resolve({
      result: '',
      entities: { nodes: {} },
    });
  },
  onDeleteAttackChainNode(_injectId: AttackChainNode['node_id']): Promise<void> {
    return Promise.resolve();
  },
  onImportAttackChainNodeFromXls(_importId: string, _input: AttackChainNodesImportInput): Promise<ImportTestSummary> {
    return new Promise<ImportTestSummary>(() => {
    });
  },
  onDryImportAttackChainNodeFromXls(_importId: string, _input: AttackChainNodesImportInput): Promise<ImportTestSummary> {
    return new Promise<ImportTestSummary>(() => {
    });
  },
  onBulkDeleteAttackChainNodes(_param: AttackChainNodeBulkProcessingInput): Promise<AttackChainNode[]> {
    return new Promise<AttackChainNode[]>(() => {
    });
  },
  bulkTestAttackChainNodes(_param: AttackChainNodeBulkProcessingInput): Promise<{
    uri: string;
    data: AttackChainNodeTestStatusOutput[];
  }> {
    return new Promise<{
      uri: string;
      data: AttackChainNodeTestStatusOutput[];
    }>(() => {
    });
  },
});
export const LessonContext = createContext<LessonContextType>({
  onApplyLessonsTemplate(_data: string): Promise<LessonsCategory[]> {
    return new Promise<LessonsCategory[]>(() => {
    });
  },
  onResetLessonsAnswers(): Promise<LessonsCategory[]> {
    return new Promise<LessonsCategory[]>(() => {
    });
  },
  onEmptyLessonsCategories(): Promise<LessonsCategory[]> {
    return new Promise<LessonsCategory[]>(() => {
    });
  },
  onUpdateSourceLessons(_data: boolean): Promise<{ result: string }> {
    return Promise.resolve({ result: '' });
  },
  onSendLessons(_data: LessonsSendInput): void {
  },
  onAddLessonsCategory(_data: LessonsCategoryCreateInput): Promise<LessonsCategory> {
    return new Promise<LessonsCategory>(() => {
    });
  },
  onDeleteLessonsCategory(_data: string): void {
  },
  onUpdateLessonsCategory(_lessonCategoryId: string, _data: LessonsCategoryUpdateInput): Promise<LessonsCategory> {
    return new Promise<LessonsCategory>(() => {
    });
  },
  onUpdateLessonsCategoryTeams(_lessonCategoryId: string, _data: LessonsCategoryTeamsInput): Promise<LessonsCategory> {
    return new Promise<LessonsCategory>(() => {
    });
  },
  onDeleteLessonsQuestion(_lessonsCategoryId: string, _lessonsQuestionId: string): void {
  },
  onUpdateLessonsQuestion(_lessonsCategoryId: string, _lessonsQuestionId: string, _data: LessonsQuestionUpdateInput): Promise<LessonsQuestion> {
    return new Promise<LessonsQuestion>(() => {
    });
  },
  onAddLessonsQuestion(_lessonsCategoryId: string, _data: LessonsQuestionCreateInput): Promise<LessonsQuestion> {
    return new Promise<LessonsQuestion>(() => {
    });
  },
  onAddObjective(_data: ObjectiveInput): Promise<Objective> {
    return new Promise<Objective>(() => {
    });
  },
  onUpdateObjective(_objectiveId: string, _data: ObjectiveInput): Promise<Objective> {
    return new Promise<Objective>(() => {
    });
  },
  onDeleteObjective(_objectiveId: string): void {
  },
  onAddEvaluation(_objectiveId: string, _data: EvaluationInput): Promise<Evaluation> {
    return new Promise<Evaluation>(() => {
    });
  },
  onUpdateEvaluation(_objectiveId: string, _evaluationId: string, _data: EvaluationInput): Promise<Evaluation> {
    return new Promise<Evaluation>(() => {
    });
  },
  onFetchEvaluation(_objectiveId: string): Promise<Evaluation[]> {
    return new Promise<Evaluation[]>(() => {
    });
  },
});
export const ViewLessonContext = createContext<ViewLessonContextType>({
  onAddLessonsAnswers(_questionCategory: string, _lessonsQuestionId: string, _answerData: LessonsAnswerCreateInput): Promise<LessonsAnswer> {
    return new Promise<LessonsAnswer>(() => {
    });
  },
  onFetchPlayerLessonsAnswers(): Promise<LessonsAnswer[]> {
    return new Promise<LessonsAnswer[]>(() => {
    });
  },
});
export const ViewModeContext = createContext('list');
