import { useCallback, useEffect, useState } from 'react';
import { Button, Input, Popconfirm, Popover, Select, Switch, Table, Tag } from 'antd';
import i18n from '@/i18n';
import feedback from '@/utils/feedback';
import { useCommunityAuth } from '@/blocks/CommunityAuth';
import {
  CommunityRole,
  ICommunityUser,
  communityChangePassword,
  createCommunityUser,
  deleteCommunityUser,
  listCommunityUsers,
  updateCommunityUser,
} from '@/service/communityAuth';
import { useStyles } from './style';

/**
 * The signed-in account, and - for admins - everyone else's.
 *
 * Each account owns a workspace: its connections, consoles and history are its
 * own, and an admin sees no more of them than anyone else does. What the role
 * decides is who may manage accounts. The hint on the screen says as much, so
 * the presence of roles is not read as a database permission it is not.
 */
export default function AccountSetting() {
  const { styles } = useStyles();
  const { required, username, role, isAdmin, signOut } = useCommunityAuth();

  if (!required) {
    return <div className={styles.disabledNotice}>{i18n('setting.account.signInDisabled')}</div>;
  }

  return (
    <div className={styles.container}>
      <section>
        <div className={styles.sectionTitle}>{i18n('setting.account.title')}</div>
        <div className={styles.identity}>
          <span className={styles.username}>{username}</span>
          <Tag color={role === 'ADMIN' ? 'gold' : undefined}>
            {i18n(role === 'ADMIN' ? 'setting.account.roleAdmin' : 'setting.account.roleUser')}
          </Tag>
          <Button size="small" onClick={signOut}>
            {i18n('login.label.signOut')}
          </Button>
        </div>
        <ChangePasswordForm />
      </section>

      {isAdmin && <UserManagement currentUsername={username} />}
    </div>
  );
}

function ChangePasswordForm() {
  const { styles } = useStyles();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    setSaving(true);
    try {
      await communityChangePassword({ currentPassword, newPassword });
      feedback.success(i18n('setting.account.passwordChanged'));
      setCurrentPassword('');
      setNewPassword('');
    } catch (error: any) {
      feedback.error(describe(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className={styles.passwordForm}>
      <Input.Password
        value={currentPassword}
        placeholder={i18n('setting.account.currentPassword')}
        onChange={(event) => setCurrentPassword(event.target.value)}
      />
      <Input.Password
        value={newPassword}
        placeholder={i18n('setting.account.newPassword')}
        onChange={(event) => setNewPassword(event.target.value)}
      />
      <Button type="primary" loading={saving} disabled={!currentPassword || !newPassword} onClick={submit}>
        {i18n('setting.account.changePassword')}
      </Button>
    </div>
  );
}

function UserManagement({ currentUsername }: { currentUsername: string | null }) {
  const { styles } = useStyles();
  const [users, setUsers] = useState<ICommunityUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [newUsername, setNewUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newRole, setNewRole] = useState<CommunityRole>('USER');
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setUsers((await listCommunityUsers(undefined as void)) || []);
    } catch (error: any) {
      feedback.error(describe(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const create = async () => {
    setCreating(true);
    try {
      await createCommunityUser({ username: newUsername, password: newPassword, role: newRole });
      feedback.success(i18n('setting.account.userCreated'));
      setNewUsername('');
      setNewPassword('');
      setNewRole('USER');
      await load();
    } catch (error: any) {
      feedback.error(describe(error));
    } finally {
      setCreating(false);
    }
  };

  const amend = async (user: ICommunityUser, change: { role?: CommunityRole; enabled?: boolean }) => {
    try {
      await updateCommunityUser({ username: user.username, ...change });
      await load();
    } catch (error: any) {
      feedback.error(describe(error));
    }
  };

  const remove = async (user: ICommunityUser) => {
    try {
      await deleteCommunityUser({ username: user.username });
      feedback.success(i18n('setting.account.userDeleted'));
      await load();
    } catch (error: any) {
      feedback.error(describe(error));
    }
  };

  return (
    <section>
      <div className={styles.sectionTitle}>{i18n('setting.account.usersTitle')}</div>
      <div className={styles.sectionHint}>{i18n('setting.account.usersHint')}</div>

      <div className={styles.createRow}>
        <Input
          value={newUsername}
          placeholder={i18n('login.form.user.placeholder')}
          onChange={(event) => setNewUsername(event.target.value)}
        />
        <Input.Password
          value={newPassword}
          placeholder={i18n('setting.account.newPassword')}
          onChange={(event) => setNewPassword(event.target.value)}
        />
        <Select<CommunityRole>
          value={newRole}
          onChange={setNewRole}
          options={[
            { value: 'USER', label: i18n('setting.account.roleUser') },
            { value: 'ADMIN', label: i18n('setting.account.roleAdmin') },
          ]}
        />
        <Button type="primary" loading={creating} disabled={!newUsername || !newPassword} onClick={create}>
          {i18n('setting.account.addUser')}
        </Button>
      </div>

      <Table<ICommunityUser>
        rowKey="username"
        size="small"
        loading={loading}
        dataSource={users}
        pagination={false}
        columns={[
          { title: i18n('login.form.user'), dataIndex: 'username' },
          {
            title: i18n('setting.account.role'),
            dataIndex: 'role',
            width: 140,
            render: (_, user) => (
              <Select<CommunityRole>
                size="small"
                value={user.role}
                // The last remaining admin cannot be demoted: locking every
                // admin out cannot be undone from inside the application.
                disabled={user.lastAdmin}
                onChange={(value) => amend(user, { role: value })}
                options={[
                  { value: 'USER', label: i18n('setting.account.roleUser') },
                  { value: 'ADMIN', label: i18n('setting.account.roleAdmin') },
                ]}
              />
            ),
          },
          {
            title: i18n('setting.account.enabled'),
            dataIndex: 'enabled',
            width: 90,
            render: (_, user) => (
              <Switch
                size="small"
                checked={user.enabled}
                disabled={user.lastAdmin}
                onChange={(checked) => amend(user, { enabled: checked })}
              />
            ),
          },
          { title: i18n('setting.account.createdAt'), dataIndex: 'createdAt', width: 160 },
          {
            title: '',
            width: 180,
            render: (_, user) => (
              <div className={styles.rowActions}>
                {/* Not offered on your own row: a reset ends that account's
                    sessions, so doing it to yourself would sign you out
                    mid-click. The form above changes your own password and
                    asks for the current one, which is the right shape for it. */}
                {user.username === currentUsername ? null : (
                  <ResetPasswordButton username={user.username} onDone={load} />
                )}
                {user.lastAdmin || user.username === currentUsername ? null : (
                  <Popconfirm title={i18n('setting.account.deleteConfirm')} onConfirm={() => remove(user)}>
                    <Button size="small" danger>
                      {i18n('common.button.delete')}
                    </Button>
                  </Popconfirm>
                )}
              </div>
            ),
          },
        ]}
      />
    </section>
  );
}

/**
 * Sets another account's password, without asking for the old one.
 *
 * That is the point of it: an admin resets a password precisely when nobody
 * knows the current one any more. The server ends that account's sessions on
 * the way through, so a reset also locks out whoever prompted it.
 *
 * A popover rather than a dialog - one field, no consequences to weigh, and
 * nothing else on the screen worth blocking for.
 */
function ResetPasswordButton({ username, onDone }: { username: string; onDone: () => Promise<void> | void }) {
  const { styles } = useStyles();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [saving, setSaving] = useState(false);

  const close = () => {
    setOpen(false);
    setPassword('');
  };

  const submit = async () => {
    setSaving(true);
    try {
      await updateCommunityUser({ username, password });
      feedback.success(i18n('setting.account.passwordReset'));
      close();
      await onDone();
    } catch (error: any) {
      // Left open with the value intact: the usual refusal is a password below
      // the minimum, and retyping it from scratch helps nobody.
      feedback.error(describe(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Popover
      open={open}
      trigger="click"
      placement="topRight"
      destroyTooltipOnHide
      onOpenChange={(next) => (next ? setOpen(true) : close())}
      content={
        <div className={styles.resetPasswordForm}>
          <div className={styles.resetPasswordTitle}>{i18n('setting.account.resetPasswordFor', username)}</div>
          <Input.Password
            autoFocus
            value={password}
            placeholder={i18n('setting.account.newPassword')}
            onChange={(event) => setPassword(event.target.value)}
            onPressEnter={() => password && submit()}
          />
          <div className={styles.resetPasswordHint}>{i18n('setting.account.resetPasswordHint')}</div>
          <div className={styles.resetPasswordActions}>
            <Button size="small" onClick={close}>
              {i18n('common.button.cancel')}
            </Button>
            <Button size="small" type="primary" loading={saving} disabled={!password} onClick={submit}>
              {i18n('common.button.confirm')}
            </Button>
          </div>
        </div>
      }
    >
      <Button size="small">{i18n('setting.account.resetPassword')}</Button>
    </Popover>
  );
}

/** Turns the server's error code into something worth reading. */
function describe(error: any): string {
  const code = error?.errorCode;
  const messages: Record<string, string> = {
    'community.auth.invalidCredentials': i18n('login.community.invalidCredentials'),
    'community.auth.passwordTooShort': i18n('setting.account.passwordTooShort'),
    'community.user.invalidUsername': i18n('setting.account.invalidUsername'),
    'community.user.alreadyExists': i18n('setting.account.alreadyExists'),
    'community.user.lastAdmin': i18n('setting.account.lastAdmin'),
    'community.user.notFound': i18n('setting.account.notFound'),
  };
  return messages[code] || error?.errorMessage || i18n('common.text.failure');
}
