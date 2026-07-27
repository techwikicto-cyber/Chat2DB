import createRequest from './base';

export interface ICommunityAuthStatus {
  /** True when a shared password is configured on the server. */
  required: boolean;
  /** True when this browser holds a valid session, or when none is required. */
  authenticated: boolean;
}

/**
 * The shared-password gate.
 *
 * `errorLevel: false` throughout: a wrong password and a signed-out status check
 * are both ordinary answers here, not failures to shout about. The screen shows
 * its own message.
 */
export const getCommunityAuthStatus = createRequest<void, ICommunityAuthStatus>('/api/community/auth/status', {
  errorLevel: false,
});

export const communityLogin = createRequest<{ password: string }, void>('/api/community/auth/login', {
  method: 'post',
  errorLevel: false,
});

export const communityLogout = createRequest<void, void>('/api/community/auth/logout', {
  method: 'post',
  errorLevel: false,
});
