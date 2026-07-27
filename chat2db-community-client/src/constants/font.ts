import { LangType } from '@/constants/settings';

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
const FALLBACKS = [
  'Vazirmatn',
  '-apple-system',
  'BlinkMacSystemFont',
  "'Segoe UI'",
  'Roboto',
  "'Helvetica Neue'",
  'Arial',
  'sans-serif',
];

/**
 * The stack to use for a given interface language.
 *
 * The bundled cut of IRANYekan is the "fanum" one, which draws ASCII digits as
 * Persian numerals - a property of the font, not of any CSS setting. That is
 * right for a Persian interface and wrong for every other one, where a date
 * came out as ۲۰۲۶-۰۷-۲۷ in an otherwise English screen. `iranyekan-latn` is
 * the same face with the digit range excluded, so digits fall through to
 * Vazirmatn and render as written.
 */
export function uiFontFamily(language: LangType): string {
  const face = language === LangType.FA_IR ? 'iranyekan' : 'iranyekan-latn';
  return [face, ...FALLBACKS].join(', ');
}
