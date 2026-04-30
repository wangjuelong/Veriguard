// 二开移除 Article — 占位 context 工厂直至前端深度解耦。
import { type ArticleContextType } from '../../../common/Context';

const articleContextForScenario = (_scenarioId: string): ArticleContextType => ({
  previewArticleUrl: () => '',
  fetchArticles: () => Promise.resolve({ result: [], entities: { articles: {} } }),
  fetchChannels: () => Promise.resolve({ result: [], entities: { channels: {} } }),
  fetchDocuments: () => Promise.resolve([]),
  onAddArticle: () => Promise.resolve({ result: '' }),
  onUpdateArticle: () => '',
  onDeleteArticle: () => '',
});

export default articleContextForScenario;
