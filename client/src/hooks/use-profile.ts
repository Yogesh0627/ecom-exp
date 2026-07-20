'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi } from '@/api';
import { QUERY_KEYS } from '@/constants';
import { useAuth } from './use-auth';
import type { ChangeEmailPayload, UpdateProfilePayload } from '@/types';

/** The signed-in user's own profile (name, phone, email, verification + pending-email state). */
export function useProfile() {
  const { isAuthenticated, isReady } = useAuth();
  return useQuery({
    queryKey: QUERY_KEYS.profile,
    queryFn: authApi.me,
    enabled: isReady && isAuthenticated,
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  const { patchUser } = useAuth();
  return useMutation({
    mutationFn: (payload: UpdateProfilePayload) => authApi.updateProfile(payload),
    onSuccess: (profile) => {
      // Keep the cached UserSummary (drives the header name) in sync.
      patchUser({ fullName: profile.fullName, email: profile.email });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.profile });
    },
  });
}

/** Starts a verify-before-switch email change; the new address must confirm via emailed link. */
export function useChangeEmail() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ChangeEmailPayload) => authApi.changeEmail(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.profile }),
  });
}

/** Cancels a pending email change and invalidates the emailed confirmation link. */
export function useCancelEmailChange() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => authApi.cancelEmailChange(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.profile }),
  });
}

export function useResendVerification() {
  return useMutation({ mutationFn: () => authApi.resendVerification() });
}
