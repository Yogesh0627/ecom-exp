import type { UserSummary } from '@/types';

/**
 * Frontend mirror of the backend's permission authorities (checked with @PreAuthorize). The UI
 * gates the admin AREA on staff role and each ACTION on a specific permission — so the buttons a
 * user sees match what the server will actually allow, and an OPS user is never shown a Settings
 * action that would 403. The server remains the source of truth; this only avoids dead-end clicks.
 */
export const PERMISSIONS = {
  analyticsRead: 'analytics:read',
  productWrite: 'product:write',
  productDelete: 'product:delete',
  categoryWrite: 'category:write',
  inventoryRead: 'inventory:read',
  inventoryWrite: 'inventory:write',
  warehouseWrite: 'warehouse:write',
  bannerWrite: 'banner:write',
  reviewModerate: 'review:moderate',
  settingsWrite: 'settings:write',
  couponWrite: 'coupon:write',
  orderWrite: 'order:write',
} as const;

/** Roles that may see the admin console at all. A pure customer has only CUSTOMER. */
const STAFF_ROLES = ['ADMIN', 'OPS', 'SUPPORT'];

export function hasPermission(user: UserSummary | null, permission: string): boolean {
  return !!user?.permissions?.includes(permission);
}

export function hasAnyPermission(user: UserSummary | null, permissions: string[]): boolean {
  return permissions.some((p) => hasPermission(user, p));
}

/** True if the user is staff (any non-customer role) — the gate for the whole /admin area. */
export function isStaff(user: UserSummary | null): boolean {
  return !!user?.roles?.some((r) => STAFF_ROLES.includes(r));
}
