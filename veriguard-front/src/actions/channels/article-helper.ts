// 二开移除 Article — 占位 helper 类型直至前端深度解耦。
import { type Article } from '../../utils/api-types';

export interface ArticlesHelper {
  getAttackChainArticles: (scenarioId: string) => Article[];
  getAttackChainRunArticles: (exerciseId: string) => Article[];
  getArticlesMap: () => Record<string, Article>;
}
