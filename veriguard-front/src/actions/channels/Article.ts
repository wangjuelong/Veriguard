// 二开移除 Channel/Article — 占位文件保留 TS 类型签名直至前端深度解耦。
import { type Article, type Inject } from '../../utils/api-types';

export type ArticleStore = Article;

export interface FullArticleStore extends Article {
  article_fullchannel?: { id: string };
  article_virtual_publication?: string | undefined;
  article_is_scheduled_publication?: boolean;
  article_inject?: Inject;
}
