import { enUS, zhCN } from 'date-fns/locale';

import enVeriguard from './lang/en.json';
import zhVeriguard from './lang/zh.json';

export type LanguageCode = 'en' | 'zh';

// OAEV Supported Local Language
export const oaevLocaleMap: Record<LanguageCode, Record<string, string>> = {
  en: enVeriguard,
  zh: zhVeriguard,
};

// Date-fns locale map
export const dateFnsLocaleMap = {
  en: enUS,
  zh: zhCN,
};

// Moment locale map
export const momentMap: Record<LanguageCode, string> = {
  en: 'en-us',
  zh: 'zh-cn',
};
