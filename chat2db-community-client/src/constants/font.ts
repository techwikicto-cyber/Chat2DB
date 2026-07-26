/**
 * UI font stack for the Persian edition.
 *
 * Vazirmatn leads because it carries a complete Persian glyph set; the Latin
 * fallbacks follow so English text, identifiers and numerals in the interface
 * chrome keep their native shapes. The @font-face declarations that back this
 * stack live in `src/styles/global.ts`.
 *
 * This is the interface font only. The SQL editor keeps its own monospace
 * setting, since code alignment depends on fixed-width glyphs.
 */
export const UI_FONT_FAMILY = [
  'Vazirmatn',
  '-apple-system',
  'BlinkMacSystemFont',
  "'Segoe UI'",
  'Roboto',
  "'Helvetica Neue'",
  'Arial',
  'sans-serif',
].join(', ');
