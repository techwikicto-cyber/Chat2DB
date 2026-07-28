import { memo, useEffect, useMemo } from 'react';
import { ConfigProvider as AntdConfigProvider, ConfigProviderProps } from 'antd';
import { useGlobalStore } from '@/store/global';
import { settingSelectors } from '@/store/global/selectors';
import { LangType } from '@/constants/settings';
import enUS from 'antd/locale/en_US';
import esES from 'antd/locale/es_ES';
import faIR from 'antd/locale/fa_IR';
import jaJP from 'antd/locale/ja_JP';
import koKR from 'antd/locale/ko_KR';
import zhCN from 'antd/locale/zh_CN';
import dayjs from 'dayjs';

import 'dayjs/locale/en';
import 'dayjs/locale/es';
import 'dayjs/locale/fa';
import 'dayjs/locale/ja';
import 'dayjs/locale/ko';
import 'dayjs/locale/zh-cn';

dayjs.locale('en');

const dayjsLocales: Record<LangType, string> = {
  [LangType.FA_IR]: 'fa',
  [LangType.EN_US]: 'en',
  [LangType.ZH_CN]: 'zh-cn',
  [LangType.JA_JP]: 'ja',
  [LangType.ES_ES]: 'es',
  [LangType.KO_KR]: 'ko',
};

const ConfigProvider = memo<ConfigProviderProps>(({ children }) => {
  const { language } = useGlobalStore((state) => {
    return {
      ...settingSelectors.currentBaseSetting(state),
    };
  });

  const locale = useMemo(() => {
    switch (language) {
      case LangType.FA_IR:
        return faIR;
      case LangType.ZH_CN:
        return zhCN;
      case LangType.JA_JP:
        return jaJP;
      case LangType.ES_ES:
        return esES;
      case LangType.KO_KR:
        return koKR;
      default:
        return enUS;
    }
  }, [language]);

  useEffect(() => {
    dayjs.locale(dayjsLocales[language] || dayjsLocales[LangType.EN_US]);
    // Publishes the interface language to CSS: the bidirectional-text rules in
    // styles/global.ts select on html[lang]. The browser and screen readers
    // read the same attribute for hyphenation and pronunciation.
    document.documentElement.lang = language;
  }, [language]);

  // The frame stays left-to-right in every language. What mirrors is the text:
  // styles/global.ts gives each block of Persian its own base direction, which
  // is what a sentence needs to read correctly when a table name sits in the
  // middle of it. The SQL editor, the result grid and the object tree are left
  // alone - they hold identifiers and code, which are left-to-right whatever
  // language surrounds them.
  return (
    <AntdConfigProvider locale={locale} direction="ltr">
      {children}
    </AntdConfigProvider>
  );
});

export default ConfigProvider;
