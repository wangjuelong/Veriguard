// 二开移除 Article — 占位 no-op action 直至前端深度解耦。
export const fetchAttackChainRunArticles = (_exerciseId: string) => () => Promise.resolve();
export const fetchAttackChainArticles = (_scenarioId: string) => () => Promise.resolve();
