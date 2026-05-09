// 二开移除 Channel/Article — 占位组件直至前端深度解耦。
import { type Article } from '../../../../../../utils/api-types';

interface Props {
  allArticles: Article[];
  readOnly?: boolean;
}

const AttackChainNodeArticlesList = (_props: Props) => null;

export default AttackChainNodeArticlesList;
