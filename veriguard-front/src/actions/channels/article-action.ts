// 二开移除 Article — 占位 no-op action 直至前端深度解耦。
export const fetchExerciseArticles = (_exerciseId: string) => () => Promise.resolve();
export const fetchScenarioArticles = (_scenarioId: string) => () => Promise.resolve();
