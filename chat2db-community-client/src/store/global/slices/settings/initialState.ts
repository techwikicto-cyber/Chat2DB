import {
  GlobalAISettings,
  GlobalAppConfig,
  GlobalBaseSettings,
  DataTableSettings,
} from '@/typings/settings';
import {
  DEFAULT_AI_SETTINGS,
  DEFAULT_BASE_SETTINGS,
  DEFAULT_APP_CONFIG,
  DATA_TABLE_SETTINGS,
} from '@/constants/settings';
import { DEFAULT_EDITOR_SETTINGS } from '@/components/SQLEditor/constants';
import { EditorSettings } from '@/components/SQLEditor/type';

export interface GlobalSettings {
  baseSetting: GlobalBaseSettings;
  aiSettings: GlobalAISettings;
  appConfig: GlobalAppConfig;
  editorSettings: EditorSettings;
  dataTableSettings: DataTableSettings;
}

export interface GlobalSettingState extends GlobalSettings {}

export const initialSettingState: GlobalSettingState = {
  baseSetting: DEFAULT_BASE_SETTINGS,
  aiSettings: DEFAULT_AI_SETTINGS,
  appConfig: DEFAULT_APP_CONFIG,
  editorSettings: DEFAULT_EDITOR_SETTINGS,
  dataTableSettings: DATA_TABLE_SETTINGS,
};
