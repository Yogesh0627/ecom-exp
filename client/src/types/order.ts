/** Order + address + auth types — mirror the backend OrderDtos / AuthDtos. */

export interface Address {
  id: string;
  label?: string | null;
  recipientName: string;
  phone: string;
  line1: string;
  line2?: string | null;
  landmark?: string | null;
  city: string;
  state: string;
  pincode: string;
  country: string;
  type: string;
  isDefault: boolean;
}

export interface OrderItem {
  id: string;
  variantId: string;
  productName: string;
  variantName: string;
  sku: string;
  imageUrl?: string | null;
  qty: number;
  unitPrice: number;
  discountAmount: number;
  taxRatePct: number;
  taxAmount: number;
  lineTotal: number;
  hsnCode?: string | null;
}

export interface ShippingAddress {
  recipientName: string;
  phone: string;
  line1: string;
  line2?: string | null;
  landmark?: string | null;
  city: string;
  state: string;
  pincode: string;
  country: string;
}

export interface StatusHistoryItem {
  fromStatus: string | null;
  toStatus: string;
  note?: string | null;
  at: string;
}

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'CONFIRMED'
  | 'PACKED'
  | 'SHIPPED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'RETURNED'
  | 'REFUNDED';

export interface Order {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  items: OrderItem[];
  subtotal: number;
  discountTotal: number;
  cgstTotal: number;
  sgstTotal: number;
  igstTotal: number;
  taxTotal: number;
  shippingFee: number;
  grandTotal: number;
  currency: string;
  shipTo: ShippingAddress;
  placedAt?: string | null;
  customerNote?: string | null;
  history: StatusHistoryItem[];
}

export interface OrderSummary {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  grandTotal: number;
  currency: string;
  itemCount: number;
  placedAt?: string | null;
}

/**
 * The order state machine — mirrors OrderStatus.allowedNext() on the backend. The admin UI offers
 * only these transitions so it never sends a move the server will reject; the server is still the
 * authority and re-validates every transition.
 */
export const ORDER_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  PENDING_PAYMENT: ['PAID', 'CANCELLED'],
  PAID: ['CONFIRMED', 'CANCELLED', 'REFUNDED'],
  CONFIRMED: ['PACKED', 'CANCELLED', 'REFUNDED'],
  PACKED: ['SHIPPED', 'CANCELLED', 'REFUNDED'],
  SHIPPED: ['OUT_FOR_DELIVERY', 'RETURNED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'RETURNED'],
  DELIVERED: ['RETURNED'],
  RETURNED: ['REFUNDED'],
  CANCELLED: [],
  REFUNDED: [],
};
