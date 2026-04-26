import { de, enUS, es, fr, it, ja, ko, ru, zhCN } from 'date-fns/locale';

import deVeriguard from './lang/de.json';
import enVeriguard from './lang/en.json';
import esVeriguard from './lang/es.json';
import frVeriguard from './lang/fr.json';
import itVeriguard from './lang/it.json';
import jaVeriguard from './lang/ja.json';
import koVeriguard from './lang/ko.json';
import ruVeriguard from './lang/ru.json';
import zhVeriguard from './lang/zh.json';

export type LanguageCode = 'de' | 'en' | 'es' | 'fr' | 'it' | 'ja' | 'ko' | 'ru' | 'zh';

// OAEV Supported Local Language
export const oaevLocaleMap: Record<LanguageCode, Record<string, string>> = {
  de: deVeriguard,
  en: enVeriguard,
  es: esVeriguard,
  fr: frVeriguard,
  it: itVeriguard,
  ja: jaVeriguard,
  ko: koVeriguard,
  ru: ruVeriguard,
  zh: zhVeriguard,
};

// Date-fns locale map
export const dateFnsLocaleMap = {
  de,
  en: enUS,
  es,
  fr,
  it,
  ja,
  ko,
  ru,
  zh: zhCN,
};

// Moment locale map
export const momentMap: Record<LanguageCode, string> = {
  de: 'de-de',
  en: 'en-us',
  es: 'es-es',
  fr: 'fr-fr',
  it: 'it-it',
  ja: 'ja-jp',
  ko: 'ko-kr',
  ru: 'ru-ru',
  zh: 'zh-cn',
};
