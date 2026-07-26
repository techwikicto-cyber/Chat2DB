// Keep in sync with PRODUCT_NAME in src/constants/branding.ts. This file is
// read by .umirc.ts at build time, before module aliases resolve, so it cannot
// import from src/.
const PRODUCT_NAME = 'پلتفرم بینا';

export const communityProductConfig = {
  product: 'community',
  // Browser tab title.
  title: PRODUCT_NAME,
  // Not branding: this names the on-disk data directory and build artefacts, so
  // renaming it would orphan existing installations.
  defaultAppName: 'chat2db-community',
  defaultProxyTarget: 'http://127.0.0.1:10825',
  storageKeyPrefix: 'Chat2DB_Community_',
  storageVersionKey: 'app-local-storage-versions-community',
  localLogo: false,
} as const;
