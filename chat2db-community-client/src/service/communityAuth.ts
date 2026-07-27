import createRequest from './base';

export type CommunityRole = 'ADMIN' | 'USER';

export interface ICommunityAuthStatus {
  /** True when sign-in is required at all. */
  required: boolean;
  /** True when this browser holds a valid session, or when none is required. */
  authenticated: boolean;
  username?: string | null;
  role?: CommunityRole | null;
}

export interface ICommunityUser {
  username: string;
  role: CommunityRole;
  enabled: boolean;
  createdAt: string;
  /** The one account that cannot be deleted, disabled or demoted. */
  lastAdmin: boolean;
}

/**
 * Sign-in and account management.
 *
 * `errorLevel: false` throughout: wrong credentials, a signed-out status check
 * and a rejected account change are all ordinary answers here, not failures to
 * shout about. Each screen shows its own message.
 */
export const getCommunityAuthStatus = createRequest<void, ICommunityAuthStatus>('/api/community/auth/status', {
  errorLevel: false,
});

export const communityLogin = createRequest<{ username: string; password: string }, void>(
  '/api/community/auth/login',
  { method: 'post', errorLevel: false },
);

export const communityLogout = createRequest<void, void>('/api/community/auth/logout', {
  method: 'post',
  errorLevel: false,
});

export const communityChangePassword = createRequest<{ currentPassword: string; newPassword: string }, void>(
  '/api/community/auth/password',
  { method: 'post', errorLevel: false },
);

export const listCommunityUsers = createRequest<void, ICommunityUser[]>('/api/community/users', {
  errorLevel: false,
});

export const createCommunityUser = createRequest<
  { username: string; password: string; role: CommunityRole },
  void
>('/api/community/users', { method: 'post', errorLevel: false });

export const updateCommunityUser = createRequest<
  { username: string; password?: string; role?: CommunityRole; enabled?: boolean },
  void
>('/api/community/users/:username', { method: 'put', errorLevel: false });

export const deleteCommunityUser = createRequest<{ username: string }, void>('/api/community/users/:username', {
  method: 'delete',
  errorLevel: false,
});
