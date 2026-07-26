/**
 * Product branding.
 *
 * Every user-visible occurrence of the product name resolves here, so a rename
 * is a one-line change rather than a hunt through the UI. Upstream identifiers
 * that are not branding - storage key prefixes, the `chat2db-community` package
 * and artefact names, JDBC and MCP identifiers - deliberately keep their
 * original values, since changing them would orphan stored settings and break
 * compatibility with the upstream project.
 */

/** Shown in the title bar, the browser tab and the About screen. */
export const PRODUCT_NAME = 'پلتفرم بینا';

/**
 * ASCII form for contexts that cannot render Persian reliably, such as window
 * manager class names and generated file names.
 */
export const PRODUCT_NAME_ASCII = 'Bina Platform';
