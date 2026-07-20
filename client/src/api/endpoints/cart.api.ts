import { api } from '@/lib';
import type { Cart } from '@/types';

/** Cart endpoints. Every call returns the full cart (with the Smart Cart nutrition summary). */
export const cartApi = {
  async get(): Promise<Cart> {
    const { data } = await api.get<Cart>('/cart');
    return data;
  },
  async addItem(variantId: string, qty: number): Promise<Cart> {
    const { data } = await api.post<Cart>('/cart/items', { variantId, qty });
    return data;
  },
  async updateItem(variantId: string, qty: number): Promise<Cart> {
    const { data } = await api.put<Cart>(`/cart/items/${variantId}`, { qty });
    return data;
  },
  async removeItem(variantId: string): Promise<Cart> {
    const { data } = await api.delete<Cart>(`/cart/items/${variantId}`);
    return data;
  },
  async clear(): Promise<void> {
    await api.delete('/cart');
  },
};
