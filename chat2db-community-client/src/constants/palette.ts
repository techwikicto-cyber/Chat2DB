/**
 * Claude-inspired palette.
 *
 * Warm neutrals in place of the stock blue-greys, with a terracotta accent
 * instead of the purple.
 *
 * Surfaces are applied unconditionally because they are not user-configurable.
 * The accent is applied only when the user has not chosen their own primary
 * colour in Settings, so the colour picker there keeps working.
 */

/** Terracotta accent, lifted in dark mode to hold contrast against the ground. */
const ACCENT_LIGHT = '#c2603d';
const ACCENT_DARK = '#d97757';

type TokenOverrides = Record<string, string>;

/**
 * Accent ramp.
 *
 * Spelled out rather than left to antd's derivation: the theme provider seeds
 * its own ramp from the preset primary colour, so without explicit values the
 * derived purple leaks back through on hover, active and background states.
 */
function accentTokens(isDark: boolean): TokenOverrides {
  return isDark
    ? {
        colorPrimary: ACCENT_DARK,
        colorPrimaryHover: '#e08a6e',
        colorPrimaryActive: '#c2603d',
        colorPrimaryBorder: '#7a4030',
        colorPrimaryBorderHover: '#96513a',
        colorPrimaryBg: '#3a2620',
        colorPrimaryBgHover: '#4a2f26',
        colorPrimaryText: ACCENT_DARK,
        colorPrimaryTextHover: '#e5a184',
        colorPrimaryTextActive: '#c2603d',
      }
    : {
        colorPrimary: ACCENT_LIGHT,
        colorPrimaryHover: '#d97757',
        colorPrimaryActive: '#a44d2f',
        colorPrimaryBorder: '#e8bda9',
        colorPrimaryBorderHover: '#d97757',
        colorPrimaryBg: '#f7ece7',
        colorPrimaryBgHover: '#f0dcd2',
        colorPrimaryText: ACCENT_LIGHT,
        colorPrimaryTextHover: '#d97757',
        colorPrimaryTextActive: '#a44d2f',
      };
}

/**
 * Warm surfaces and borders.
 *
 * colorBgLayout is the cream page ground; containers stay lighter so panels
 * read as raised against it. This is the pairing that gives Claude its warmth -
 * a pure-white page with white panels would flatten it.
 */
function surfaceTokens(isDark: boolean): TokenOverrides {
  return isDark
    ? {
        colorBgBase: '#1f1e1d',
        colorBgLayout: '#1f1e1d',
        colorBgContainer: '#262624',
        colorBgElevated: '#30302e',
        colorBgSpotlight: '#3a3a37',
        colorTextBase: '#faf9f5',
        colorBorder: '#3e3e3b',
        colorBorderSecondary: '#332f2d',
        colorSplit: 'rgba(250, 249, 245, 0.09)',
      }
    : {
        colorBgBase: '#ffffff',
        colorBgLayout: '#faf9f5',
        colorBgContainer: '#ffffff',
        colorBgElevated: '#ffffff',
        colorBgSpotlight: '#f0efe9',
        colorTextBase: '#141413',
        colorBorder: '#dedcd3',
        colorBorderSecondary: '#e9e7e0',
        colorSplit: 'rgba(20, 20, 19, 0.08)',
      };
}

/**
 * Token overrides for ThemeProvider's `customBaseToken`.
 *
 * @param appearance      resolved appearance; only light-vs-dark matters here
 * @param hasCustomAccent true when the user picked a primary colour in
 *                        Settings, in which case the accent is left untouched
 */
export function buildThemeTokens(appearance: string, hasCustomAccent: boolean): TokenOverrides {
  const isDark = appearance.includes('dark');
  return {
    ...surfaceTokens(isDark),
    ...(hasCustomAccent ? {} : accentTokens(isDark)),
  };
}
