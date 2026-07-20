'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authApi } from '@/api';
import { tokenStore } from '@/lib';
import type { LoginPayload, RegisterPayload, UserSummary } from '@/types';

interface AuthContextValue {
  user: UserSummary | null;
  isAuthenticated: boolean;
  isReady: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  completeOAuthLogin: (code: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Merge fresh fields (e.g. name/email after a profile edit) into the cached user. */
  patchUser: (partial: Partial<UserSummary>) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Auth state for the app. The user summary is kept in memory (and rehydrated from a stored token
 * on load); the tokens live in {@link tokenStore}. On login/register the auth response carries
 * both, so we set them together — the app never has a token without knowing who it belongs to.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [isReady, setIsReady] = useState(false);
  const queryClient = useQueryClient();

  useEffect(() => {
    // Rehydrate: if a token exists from a previous session, treat the user as logged in. The
    // stored summary is a convenience; a 401 on the first real call will refresh or bounce them.
    const raw = typeof window !== 'undefined' ? window.localStorage.getItem('eco_user') : null;
    if (raw && tokenStore.getAccessToken()) {
      try {
        setUser(JSON.parse(raw));
      } catch {
        /* ignore a corrupt cache */
      }
    }
    setIsReady(true);
  }, []);

  const persist = useCallback((u: UserSummary, accessToken: string, refreshToken: string) => {
    tokenStore.set(accessToken, refreshToken);
    window.localStorage.setItem('eco_user', JSON.stringify(u));
    setUser(u);
  }, []);

  const login = useCallback(
    async (payload: LoginPayload) => {
      const res = await authApi.login(payload);
      persist(res.user, res.accessToken, res.refreshToken);
      await queryClient.invalidateQueries();
    },
    [persist, queryClient],
  );

  const register = useCallback(
    async (payload: RegisterPayload) => {
      const res = await authApi.register(payload);
      persist(res.user, res.accessToken, res.refreshToken);
      await queryClient.invalidateQueries();
    },
    [persist, queryClient],
  );

  const completeOAuthLogin = useCallback(
    async (code: string) => {
      const res = await authApi.oauthExchange(code);
      persist(res.user, res.accessToken, res.refreshToken);
      await queryClient.invalidateQueries();
    },
    [persist, queryClient],
  );

  const patchUser = useCallback((partial: Partial<UserSummary>) => {
    setUser((prev) => {
      if (!prev) return prev;
      const next = { ...prev, ...partial };
      window.localStorage.setItem('eco_user', JSON.stringify(next));
      return next;
    });
  }, []);

  const logout = useCallback(async () => {
    const refresh = tokenStore.getRefreshToken();
    if (refresh) {
      await authApi.logout(refresh).catch(() => undefined);
    }
    tokenStore.clear();
    window.localStorage.removeItem('eco_user');
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: !!user,
      isReady,
      login,
      register,
      completeOAuthLogin,
      logout,
      patchUser,
    }),
    [user, isReady, login, register, completeOAuthLogin, logout, patchUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>');
  return ctx;
}
