/**
 * UI font stack for the Persian edition.
 *
 * IRANYekan leads: it is the face the rest of the product line is set in, so
 * the two applications read as one. Vazirmatn follows as a complete Persian
 * fallback, then the Latin faces so English text, identifiers and numerals in
 * the interface chrome keep their native shapes. The @font-face declarations
 * that back this stack live in `src/styles/global.ts`.
 *
 * This is the interface font only. The SQL editor keeps its own monospace
 * setting, since code alignment depends on fixed-width glyphs.
 */
export const UI_FONT_FAMILY = [
  'iranyekan',
  'Vazirmatn',
  '-apple-system',
  'BlinkMacSystemFont',
  "'Segoe UI'",
  'Roboto',
  "'Helvetica Neue'",
  'Arial',
  'sans-serif',
].join(', ');
