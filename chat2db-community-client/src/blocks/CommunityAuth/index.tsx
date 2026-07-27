import { ReactNode, createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Button, Input, Spin } from 'antd';
import i18n from '@/i18n';
import ProductLogo from '@/components/Logo';
import { PRODUCT_NAME } from '@/constants/branding';
import {
  CommunityRole,
  communityLogin,
  communityLogout,
  getCommunityAuthStatus,
} from '@/service/communityAuth';
import { useStyles } from './style';

interface CommunityAuthValue {
  /** False when sign-in has been switched off outright on the server. */
  required: boolean;
  username: string | null;
  role: CommunityRole | null;
  isAdmin: boolean;
  refresh: () => Promise<void>;
  signOut: () => Promise<void>;
}

const CommunityAuthContext = createContext<CommunityAuthValue>({
  required: false,
  username: null,
  role: null,
  isAdmin: false,
  refresh: async () => undefined,
  signOut: async () => undefined,
});

/** Who is signed in, for the screens that need to know. */
export const useCommunityAuth = () => useContext(CommunityAuthContext);

/**
 * Sign-in gate for the Community web deployment.
 *
 * Stands between the browser and the application shell, so nothing behind it
 * mounts - and nothing behind it calls the API - until someone has signed in.
 * It is a convenience, not the enforcement: the server rejects unauthenticated
 * API calls whether or not this screen was shown.
 */
export default function CommunityAuthGate({ children }: { children: ReactNode }) {
  const { styles } = useStyles();
  const [checking, setChecking] = useState(true);
  const [required, setRequired] = useState(false);
  const [username, setUsername] = useState<string | null>(null);
  const [role, setRole] = useState<CommunityRole | null>(null);

  const authenticated = !required || !!username;

  const refresh = useCallback(async () => {
    try {
      const status = await getCommunityAuthStatus(undefined as void);
      setRequired(!!status?.required);
      setUsername(status?.authenticated ? status?.username ?? null : null);
      setRole(status?.authenticated ? status?.role ?? null : null);
    } catch {
      // The status endpoint is unreachable - the server is still starting, or
      // this build is served without one. Failing open here changes nothing:
      // the API enforces the gate regardless of what this screen decides.
      setRequired(false);
      setUsername(null);
      setRole(null);
    } finally {
      setChecking(false);
    }
  }, []);

  const signOut = useCallback(async () => {
    try {
      await communityLogout(undefined as void);
    } finally {
      setUsername(null);
      setRole(null);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const value = useMemo<CommunityAuthValue>(
    () => ({ required, username, role, isAdmin: role === 'ADMIN', refresh, signOut }),
    [required, username, role, refresh, signOut],
  );

  if (checking) {
    return (
      <div className={styles.loading}>
        <Spin />
      </div>
    );
  }

  return (
    <CommunityAuthContext.Provider value={value}>
      {authenticated ? children : <CommunityLoginScreen onSignedIn={refresh} />}
    </CommunityAuthContext.Provider>
  );
}

function CommunityLoginScreen({ onSignedIn }: { onSignedIn: () => void }) {
  const { styles } = useStyles();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    if (!username || !password || submitting) {
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await communityLogin({ username, password });
      onSignedIn();
    } catch {
      // The server answers a wrong password and an unknown account the same
      // way, so this screen cannot be used to find out which accounts exist.
      setError(i18n('login.community.invalidCredentials'));
      setPassword('');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.screen}>
      <div className={styles.card}>
        <ProductLogo size={56} />
        <div className={styles.title}>{PRODUCT_NAME}</div>
        <div className={styles.subtitle}>{i18n('login.community.subtitle')}</div>

        <div className={styles.form}>
          <Input
            autoFocus
            size="large"
            value={username}
            disabled={submitting}
            placeholder={i18n('login.form.user.placeholder')}
            onChange={(event) => setUsername(event.target.value)}
            onPressEnter={submit}
          />
          <Input.Password
            size="large"
            value={password}
            disabled={submitting}
            placeholder={i18n('login.form.password.placeholder')}
            onChange={(event) => setPassword(event.target.value)}
            onPressEnter={submit}
          />
          <div className={styles.error}>{error}</div>
          <Button type="primary" size="large" block loading={submitting} onClick={submit}>
            {i18n('login.button.login')}
          </Button>
        </div>
      </div>
    </div>
  );
}
