import { api } from '@/lib';

/** Back-in-stock alerts: subscribe to an out-of-stock variant and get notified when it returns. */
export const stockAlertApi = {
  /** Variant ids the signed-in user is currently waiting on. */
  async mine(): Promise<string[]> {
    const { data } = await api.get<{ variantIds: string[] }>('/stock-alerts');
    return data.variantIds;
  },
  async subscribe(variantId: string): Promise<void> {
    await api.post('/stock-alerts', { variantId });
  },
  async unsubscribe(variantId: string): Promise<void> {
    await api.delete(`/stock-alerts/${variantId}`);
  },
};
