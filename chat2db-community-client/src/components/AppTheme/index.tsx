import { ReactNode, memo, useMemo } from 'react';
import { PrimaryColors, ThemeProvider, ThemeAppearance } from '@chat2db/ui';
import { UI_FONT_FAMILY } from '@/constants/font';
import { buildThemeTokens } from '@/constants/palette';
import { useGlobalStore } from '@/store/global';
import { settingSelectors } from '@/store/global/selectors';

export interface AppThemeProps {
  children?: ReactNode;
}

const AppTheme = memo<AppThemeProps>(({ children }) => {
  const { primaryColor, appearance, customFont, customFontSize } = useGlobalStore((state) => {
    return {
      ...settingSelectors.currentBaseSetting(state),
    };
  });

  const themeMode = useMemo(() => {
    if (appearance.includes('dark')) {
      return 'dark';
    } else if (appearance.includes('light')) {
      return 'light';
    }
    return 'auto';
  }, [appearance]);

  // 'orange' is the closest preset to the terracotta accent, so the states the
  // palette does not spell out still derive from a warm hue rather than purple.
  const hasCustomAccent = Boolean(primaryColor?.label);
  const themeTokens = useMemo(
    () => buildThemeTokens(appearance, hasCustomAccent),
    [appearance, hasCustomAccent],
  );

  return (
    <ThemeProvider
      primaryColor={(primaryColor?.label as PrimaryColors) || 'orange'}
      themeMode={themeMode}
      appearance={appearance === ThemeAppearance.Auto ? undefined : appearance}
      defaultAppearance={appearance}
      customBaseToken={{
        // A font chosen in Settings still wins; Vazirmatn is only the default.
        fontFamily: customFont || UI_FONT_FAMILY,
        fontSize: customFontSize,
        ...themeTokens,
      }}
    >
      {children}
    </ThemeProvider>
  );
});

export default AppTheme;
