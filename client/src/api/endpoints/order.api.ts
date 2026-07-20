import { api } from '@/lib';
import type { Address, Order, OrderSummary, PageResponse } from '@/types';

export interface CreateAddressPayload {
  label?: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2?: string;
  landmark?: string;
  city: string;
  state: string;
  pincode: string;
  type?: string;
  isDefault?: boolean;
}

/** My-orders status buckets (map to the tabs). */
export type OrderBucket = 'all' | 'active' | 'delivered' | 'cancelled';

export interface CheckoutPayload {
  addressId: string;
  shippingFee?: number;
  customerNote?: string;
  couponCode?: string;
}

/** Addresses + checkout + orders. */
export const orderApi = {
  async addresses(): Promise<Address[]> {
    const { data } = await api.get<Address[]>('/addresses');
    return data;
  },
  async createAddress(payload: CreateAddressPayload): Promise<Address> {
    const { data } = await api.post<Address>('/addresses', payload);
    return data;
  },
  async updateAddress(id: string, payload: CreateAddressPayload): Promise<Address> {
    const { data } = await api.put<Address>(`/addresses/${id}`, payload);
    return data;
  },
  async deleteAddress(id: string): Promise<void> {
    await api.delete(`/addresses/${id}`);
  },
  async setDefaultAddress(id: string): Promise<Address> {
    const { data } = await api.post<Address>(`/addresses/${id}/set-default`);
    return data;
  },
  async checkout(payload: CheckoutPayload): Promise<Order> {
    const { data } = await api.post<Order>('/orders/checkout', payload);
    return data;
  },
  async myOrders(bucket: OrderBucket = 'all', page = 0, size = 10): Promise<PageResponse<OrderSummary>> {
    const { data } = await api.get<PageResponse<OrderSummary>>('/orders', {
      params: { bucket, page, size },
    });
    return data;
  },
  async order(id: string): Promise<Order> {
    const { data } = await api.get<Order>(`/orders/${id}`);
    return data;
  },
  async cancel(id: string, reason?: string): Promise<Order> {
    const { data } = await api.post<Order>(`/orders/${id}/cancel`, { reason });
    return data;
  },
  /** The invoice PDF (authenticated stream). Returns a Blob the caller can open or download. */
  async invoicePdf(id: string): Promise<Blob> {
    const { data } = await api.get(`/orders/${id}/invoice`, { responseType: 'blob' });
    return data as Blob;
  },
};
