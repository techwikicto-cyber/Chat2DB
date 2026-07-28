/**
 * Product branding.
 *
 * Every user-visible occurrence of the product name resolves here, so a rename
 * is a one-line change rather than a hunt through the UI. Upstream identifiers
 * that are not branding - storage key prefixes, the `chat2db-community` package
 * and artefact names, JDBC and MCP identifiers - deliberately keep their
 * original values, since changing them would orphan stored settings and break
 * compatibility with the upstream project.
 *
 * Nothing is imported here, on purpose. The global store reaches this file
 * through `constants/appConfig`, so importing the store or i18n back would
 * close a cycle. Components read the name through `useProductName`, which is
 * where the interface language is looked up.
 */

/** The name the Persian interface shows. */
export const PRODUCT_NAME_FA = 'پلتفرم بینا';

/**
 * The Latin form: what every other interface language shows, and what goes in
 * contexts that cannot render Persian reliably, such as window manager class
 * names and generated file names.
 */
export const PRODUCT_NAME_ASCII = 'Bina Platform';

/**
 * The product name for a given interface language.
 *
 * Written rather than translated - "Bina" is the brand either way - so this is
 * a transliteration and belongs here, not in the locale files.
 *
 * @param language the interface language, as held in the base settings.
 */
export function productName(language?: string): string {
  return language === 'fa-IR' ? PRODUCT_NAME_FA : PRODUCT_NAME_ASCII;
}
