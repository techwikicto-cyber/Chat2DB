import { ReactNode, memo, useEffect, useMemo, useState } from 'react';
import { PrimaryColors, ThemeProvider, ThemeAppearance } from '@chat2db/ui';
import { darkAlgorithm } from '@chat2db/ui/es/ThemeProvider/algorithms/darkAlgorithm';
import { darkDimmedAlgorithm } from '@chat2db/ui/es/ThemeProvider/algorithms/darkDimmedAlgorithm';
import { lightAlgorithm } from '@chat2db/ui/es/ThemeProvider/algorithms/lightAlgorithm';
import { UI_FONT_FAMILY } from '@/constants/font';
import { buildThemeTokens } from '@/constants/palette';
import { useGlobalStore } from '@/store/global';
import { settingSelectors } from '@/store/global/selectors';

export interface AppThemeProps {
  children?: ReactNode;
}

const DARK_QUERY = '(prefers-color-scheme: dark)';

/**
 * Resolve `auto` to the appearance actually on screen. The palette has to know
 * light from dark before the theme is built, and `auto` does not say which.
 */
function useResolvedAppearance(appearance: string) {
  const [systemDark, setSystemDark] = useState(() => !!window.matchMedia?.(DARK_QUERY).matches);

  useEffect(() => {
    if (appearance !== ThemeAppearance.Auto || !window.matchMedia) {
      return;
    }
    const query = window.matchMedia(DARK_QUERY);
    const onChange = (event: MediaQueryListEvent) => setSystemDark(event.matches);
    setSystemDark(query.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, [appearance]);

  if (appearance !== ThemeAppearance.Auto) {
    return appearance;
  }
  return systemDark ? ThemeAppearance.Dark : ThemeAppearance.Light;
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

  const resolvedAppearance = useResolvedAppearance(appearance);

  // 'orange' is the closest preset to the terracotta accent, so the states the
  // palette does not spell out still derive from a warm hue rather than purple.
  const hasCustomAccent = Boolean(primaryColor?.label);
  const themeTokens = useMemo(
    () => buildThemeTokens(resolvedAppearance, hasCustomAccent),
    [resolvedAppearance, hasCustomAccent],
  );

  // The design-system algorithms spread their own preset ramps over whatever the
  // seed carries, so tokens handed to `customBaseToken` are discarded before any
  // component sees them - which is why the terracotta accent still rendered as
  // the stock orange. Re-apply the palette once the appearance algorithm has run.
  const algorithm = useMemo(() => {
    const base =
      resolvedAppearance === ThemeAppearance.Dark
        ? darkAlgorithm
        : resolvedAppearance === ThemeAppearance.DarkDimmed
          ? darkDimmedAlgorithm
          : lightAlgorithm;
    return (seedToken: any, mapToken?: any) => ({ ...base(seedToken, mapToken), ...themeTokens });
  }, [resolvedAppearance, themeTokens]);

  return (
    <ThemeProvider
      primaryColor={(primaryColor?.label as PrimaryColors) || 'orange'}
      themeMode={themeMode}
      appearance={appearance === ThemeAppearance.Auto ? undefined : appearance}
      defaultAppearance={appearance}
      algorithm={algorithm}
      customBaseToken={{
        // A font chosen in Settings still wins; the Persian stack is the default.
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
