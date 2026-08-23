import { getCommunityPreferences, putCommunityPreferences } from '@/service/communityAuth';
import { useAIStore } from '@/store/ai/store';
import { useGlobalStore } from '@/store/global';
import { refreshPage } from '@/utils';

/**
 * Keeps the settings that belong to a person on the server rather than in one
 * browser's localStorage.
 *
 * Zustand persists these locally, which is right for a desktop build and wrong
 * for a deployment several people sign in to: the theme, language, fonts,
 * editor and grid options and the chosen AI model were all attached to the
 * machine, so the same account on a second browser started from defaults and
 * two accounts sharing a browser overwrote each other.
 *
 * localStorage stays as it is. It is the cache that makes a reload instant,
 * and the fallback when there is no account to attribute anything to - a
 * desktop build, or a deployment with sign-in switched off.
 */

/** The bump that says "start again from defaults", if the shape ever changes. */
const VERSION = 1;

interface StoredPreferences {
  version?: number;
  baseSetting?: unknown;
  editorSettings?: unknown;
  dataTableSettings?: unknown;
  selectedModel?: unknown;
}

let pending: ReturnType<typeof setTimeout> | null = null;
let unsubscribe: (() => void)[] = [];
let flushOnUnload: (() => void) | null = null;
/** Bumped on every start, so a slow load cannot land on a later account. */
let started = 0;
/**
 * The last selection worth keeping.
 *
 * The picker clears its selection whenever the model list is empty, which it is
 * for the moment before the list arrives - and that cleared value was being
 * saved straight over the account's real choice, so signing in wiped the very
 * thing that had just been loaded.
 */
let lastKnownSelectedModel: unknown;

/** What is worth carrying between browsers. Open tabs and layout are not: those describe a session. */
function collect(): StoredPreferences {
  const global = useGlobalStore.getState();
  const ai = useAIStore.getState();
  // No models to choose from is not the same as choosing none, and only the
  // second is worth recording. The payload replaces the stored document whole,
  // so the remembered value has to be sent rather than the field omitted.
  const noBasisForAChoice = !ai.selectedModel && !ai.modelList?.length;
  if (!noBasisForAChoice) {
    lastKnownSelectedModel = ai.selectedModel;
  }
  return {
    version: VERSION,
    baseSetting: global.baseSetting,
    editorSettings: global.editorSettings,
    dataTableSettings: global.dataTableSettings,
    selectedModel: noBasisForAChoice ? lastKnownSelectedModel : ai.selectedModel,
  };
}

/**
 * @returns true when the interface language changed, which needs a reload
 */
function apply(stored: StoredPreferences): boolean {
  const global = useGlobalStore.getState();
  let languageChanged = false;
  if (stored.baseSetting) {
    // i18n() reads the store when it is called and does not subscribe, so a
    // language set after the screen is drawn relabels nothing. Settings has
    // always reloaded the page for this; loading an account's language has the
    // same problem and needs the same answer.
    languageChanged =
      !!(stored.baseSetting as any).language && (stored.baseSetting as any).language !== global.baseSetting.language;
    global.setBaseSetting(stored.baseSetting as any);
  }
  if (stored.editorSettings) {
    global.updateEditorSettings(stored.editorSettings as any);
  }
  if (stored.dataTableSettings) {
    global.updateDataTableSettings(stored.dataTableSettings as any);
  }
  if (stored.selectedModel !== undefined) {
    lastKnownSelectedModel = stored.selectedModel;
    useAIStore.getState().setSelectedModel(stored.selectedModel as any);
  }
  return languageChanged;
}

function push() {
  if (pending) {
    clearTimeout(pending);
  }
  // Settings change in bursts - dragging a font size, stepping through themes -
  // and each one would otherwise be a request.
  pending = setTimeout(() => {
    pending = null;
    putCommunityPreferences(collect()).catch(() => {
      // A failed save is not worth interrupting anyone over: localStorage still
      // holds the change, and the next one tries again.
    });
  }, 800);
}

/**
 * Sends whatever is waiting, right now, while the page is going away.
 *
 * Changing the language reloads the page on the next tick, so the debounce
 * above would never fire for the one setting people are most likely to notice
 * had not followed them. `keepalive` is what lets a request outlive the
 * document; the ordinary client cannot be used here because its promise dies
 * with the page.
 */
function flush() {
  if (!pending) {
    return;
  }
  clearTimeout(pending);
  pending = null;
  try {
    fetch('/api/community/preferences', {
      method: 'PUT',
      keepalive: true,
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(collect()),
    }).catch(() => undefined);
  } catch {
    // Nothing useful to do while the page is unloading.
  }
}

function watch() {
  stopWatching();
  let previous = JSON.stringify(collect());
  const onChange = () => {
    const next = JSON.stringify(collect());
    if (next === previous) {
      return;
    }
    previous = next;
    push();
  };
  unsubscribe = [useGlobalStore.subscribe(onChange), useAIStore.subscribe(onChange)];
  flushOnUnload = flush;
  window.addEventListener('pagehide', flushOnUnload);
}

export function stopWatching() {
  started += 1;
  // Belongs to whoever was signed in; carrying it into the next account would
  // save their choice under someone else's name.
  lastKnownSelectedModel = undefined;
  unsubscribe.forEach((off) => off());
  unsubscribe = [];
  if (flushOnUnload) {
    window.removeEventListener('pagehide', flushOnUnload);
    flushOnUnload = null;
  }
  if (pending) {
    clearTimeout(pending);
    pending = null;
  }
}

/**
 * Loads this account's settings and then keeps them saved.
 *
 * An account that has saved nothing yet - the first sign-in after this arrived,
 * or a brand new account - keeps whatever is already in the browser and has it
 * pushed up, so nobody's existing setup is reset by the upgrade.
 */
export async function startCommunityPreferencesSync() {
  const generation = ++started;
  let stored: StoredPreferences | null = null;
  try {
    stored = (await getCommunityPreferences(undefined as void)) as StoredPreferences;
  } catch {
    // Signed out, sign-in switched off, or an older server. Carry on with the
    // browser's own settings; nothing is lost and nothing is overwritten.
    return;
  }
  // Signed out, or signed in as somebody else, while this was in flight.
  // Applying now would put the previous account's settings on their screen.
  if (generation !== started) {
    return;
  }

  if (stored && stored.version === VERSION) {
    const languageChanged = apply(stored);
    if (languageChanged) {
      // The language is in localStorage by now, so the reloaded page reads it
      // straight away and this cannot repeat.
      refreshPage();
      return;
    }
  } else {
    push();
  }
  watch();
}
