import { STORAGE_KEYS } from '@/constants';

/**
 * Token persistence. Kept in localStorage so a refresh survives a page reload; the axios instance
 * reads/writes through here rather than touching storage directly, so there is one place to change
 * if we ever move to httpOnly cookies.
 *
 * <p>Access token is short-lived (15 min); the refresh token rotates on every use with server-side
 * theft detection (see the backend auth module).
 */
export const tokenStore = {
  getAccessToken(): string | null {
    if (typeof window === 'undefined') return null;
    return window.localStorage.getItem(STORAGE_KEYS.accessToken);
  },
  getRefreshToken(): string | null {
    if (typeof window === 'undefined') return null;
    return window.localStorage.getItem(STORAGE_KEYS.refreshToken);
  },
  set(accessToken: string, refreshToken: string): void {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(STORAGE_KEYS.accessToken, accessToken);
    window.localStorage.setItem(STORAGE_KEYS.refreshToken, refreshToken);
  },
  clear(): void {
    if (typeof window === 'undefined') return;
    window.localStorage.removeItem(STORAGE_KEYS.accessToken);
    window.localStorage.removeItem(STORAGE_KEYS.refreshToken);
  },
};
