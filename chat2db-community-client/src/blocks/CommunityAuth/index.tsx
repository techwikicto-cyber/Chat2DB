import { ReactNode, useCallback, useEffect, useState } from 'react';
import { Button, Input, Spin } from 'antd';
import i18n from '@/i18n';
import ProductLogo from '@/components/Logo';
import { PRODUCT_NAME } from '@/constants/branding';
import { communityLogin, getCommunityAuthStatus } from '@/service/communityAuth';
import { useStyles } from './style';

/**
 * Shared-password gate for the Community web deployment.
 *
 * Stands between the browser and the application shell, so nothing behind it
 * mounts - and nothing behind it calls the API - until the password is entered.
 * It is a convenience, not the enforcement: the server rejects unauthenticated
 * API calls whether or not this screen was shown.
 *
 * When no password is configured the server reports the gate as satisfied and
 * this renders its children immediately, which is how every existing
 * installation and the desktop build keep behaving as before.
 */
export default function CommunityAuthGate({ children }: { children: ReactNode }) {
  const { styles } = useStyles();
  const [checking, setChecking] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);

  const refreshStatus = useCallback(async () => {
    try {
      const status = await getCommunityAuthStatus(undefined as void);
      setAuthenticated(!status?.required || !!status?.authenticated);
    } catch {
      // The status endpoint is unreachable - the server is still starting, or
      // this build is served without one. Failing open here changes nothing:
      // the API enforces the gate regardless of what this screen decides.
      setAuthenticated(true);
    } finally {
      setChecking(false);
    }
  }, []);

  useEffect(() => {
    refreshStatus();
  }, [refreshStatus]);

  if (checking) {
    return (
      <div className={styles.loading}>
        <Spin />
      </div>
    );
  }

  if (authenticated) {
    return <>{children}</>;
  }

  return <CommunityLoginScreen onSignedIn={() => setAuthenticated(true)} />;
}

function CommunityLoginScreen({ onSignedIn }: { onSignedIn: () => void }) {
  const { styles } = useStyles();
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    if (!password || submitting) {
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await communityLogin({ password });
      onSignedIn();
    } catch {
      // The only expected failure is a wrong password; anything else still
      // leaves the user on this screen, so one message covers both.
      setError(i18n('login.community.invalidPassword'));
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
          <Input.Password
            autoFocus
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
