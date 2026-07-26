import { AIType } from '@/typings/ai';
import { GlobalAISettings, GlobalBaseSettings, GlobalAppConfig, DataTableSettings } from '@/typings/settings';
import { DEFAULT_RESULT_PAGE_SIZE } from './pagination';

export enum LangType {
  FA_IR = 'fa-IR',
  EN_US = 'en-US',
  ZH_CN = 'zh-CN',
  JA_JP = 'ja-JP',
  ES_ES = 'es-ES',
  KO_KR = 'ko-KR',
}

export enum UpdatedStatus {
  // default
  Default = 'default',
  // There are updates available
  Available = 'available',
  // Not available
  NotAvailable = 'notAvailable',
  // Updating
  Updating = 'updating',
  // Update completed
  Updated = 'updated',
  // Installing
  Installing = 'installing',
  // Installation completed
  Installed = 'installed',
  // Update failed
  UpdateFailed = 'updateFailed',
}

export const DEFAULT_BASE_SETTINGS: GlobalBaseSettings = {
  appearance: 'light',
  // Persian is the product language, not a guess from the browser. Detecting it
  // would hand an English UI to anyone whose browser reports en-US, which is
  // most of them. Users who want English can still pick it in Settings.
  language: LangType.FA_IR,
  customFont: '',
  customFontSize: 13,
  defaultPageSize: DEFAULT_RESULT_PAGE_SIZE,
  enableMcp: false,
};

export const DEFAULT_AI_SETTINGS: GlobalAISettings = {
  remainingUse: undefined,
  aiConfig: {
    aiSqlSource: AIType.CHAT2DBAI,
  },
  hasWhite: false,
};

export const DEFAULT_APP_CONFIG: GlobalAppConfig = {
  version: '5.3.0',
  countries: null,
  gatewayUrl: null,
  curCountry: null,
  isCN: false,
  isReady: false,
  appUrl: '',
};

export const DATA_TABLE_SETTINGS: DataTableSettings = {
  showComment: false,
  selectionMetrics: ['average', 'count', 'sum'],
};
