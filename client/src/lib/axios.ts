import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios';
import { API_BASE_URL } from '@/constants';
import { tokenStore } from './token-store';

/**
 * The single axios instance for the app. Two interceptors carry the auth flow:
 *
 *  - request: attach the access token as a Bearer header.
 *  - response: on a 401, try ONCE to refresh (the backend rotates refresh tokens with theft
 *    detection), replay the original request, and if refresh fails, clear tokens and let the
 *    caller handle the redirect to login.
 *
 * A module-level `refreshPromise` de-duplicates concurrent refreshes: if five requests 401 at
 * once, they share one refresh call instead of spamming five — which would trip the backend's
 * replay detection and revoke the whole session.
 */
export const api: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokenStore.getRefreshToken();
  if (!refreshToken) return null;
  try {
    // Bare axios (not `api`) so this call does not recurse through the interceptor.
    const { data } = await axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken });
    tokenStore.set(data.accessToken, data.refreshToken);
    return data.accessToken as string;
  } catch {
    tokenStore.clear();
    return null;
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;

    // Only attempt refresh once per request, and never for the refresh call itself.
    const isAuthEndpoint = original?.url?.includes('/auth/');
    if (error.response?.status === 401 && original && !original._retried && !isAuthEndpoint) {
      original._retried = true;
      refreshPromise = refreshPromise ?? refreshAccessToken();
      const newToken = await refreshPromise;
      refreshPromise = null;

      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      }
    }
    return Promise.reject(error);
  },
);
