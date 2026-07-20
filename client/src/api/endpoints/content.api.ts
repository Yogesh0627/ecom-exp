import { api } from '@/lib';

export interface Banner {
  id: string;
  title: string;
  subtitle?: string | null;
  imageUrl: string;
  mobileImageUrl?: string | null;
  linkUrl?: string | null;
  placement: string;
  position: number;
}

/** Storefront content: banners and public settings. */
export const contentApi = {
  async banners(placement = 'HOME_HERO'): Promise<Banner[]> {
    const { data } = await api.get<Banner[]>('/banners', { params: { placement } });
    return data;
  },
  async publicSettings(): Promise<Record<string, unknown>> {
    const { data } = await api.get<Record<string, unknown>>('/settings/public');
    return data;
  },
};
