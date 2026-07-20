import { api } from '@/lib';
import type {
  AuthResponse,
  ChangeEmailPayload,
  LoginPayload,
  Profile,
  RegisterPayload,
  UpdateProfilePayload,
} from '@/types';

/** Auth endpoints. The token side effects (store/clear) live in the auth hook, not here. */
export const authApi = {
  async login(payload: LoginPayload): Promise<AuthResponse> {
    const { data } = await api.post<AuthResponse>('/auth/login', payload);
    return data;
  },
  async register(payload: RegisterPayload): Promise<AuthResponse> {
    const { data } = await api.post<AuthResponse>('/auth/register', payload);
    return data;
  },
  async logout(refreshToken: string): Promise<void> {
    await api.post('/auth/logout', { refreshToken });
  },
  /** Exchange the one-time code from the Google redirect for a real token pair. */
  async oauthExchange(code: string): Promise<AuthResponse> {
    const { data } = await api.post<AuthResponse>('/auth/oauth/exchange', { code });
    return data;
  },
  async verifyEmail(token: string): Promise<void> {
    await api.post('/auth/verify-email', { token });
  },
  async resendVerification(): Promise<void> {
    await api.post('/auth/resend-verification');
  },
  async me(): Promise<Profile> {
    const { data } = await api.get<Profile>('/auth/me');
    return data;
  },
  async updateProfile(payload: UpdateProfilePayload): Promise<Profile> {
    const { data } = await api.patch<Profile>('/auth/me', payload);
    return data;
  },
  async changeEmail(payload: ChangeEmailPayload): Promise<void> {
    await api.post('/auth/change-email', payload);
  },
  async cancelEmailChange(): Promise<Profile> {
    const { data } = await api.post<Profile>('/auth/cancel-email-change');
    return data;
  },
};
