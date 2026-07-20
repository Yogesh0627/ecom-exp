'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LayoutDashboard,
  ShoppingBag,
  Package,
  FolderTree,
  Warehouse,
  Truck,
  Star,
  Ticket,
  ImageIcon,
  Sparkles,
  Settings,
  Leaf,
  Store,
} from 'lucide-react';
import { ROUTES } from '@/constants';
import { cn, hasAnyPermission, PERMISSIONS } from '@/lib';
import { useAuth } from '@/hooks';
import type { UserSummary } from '@/types';

interface NavItem {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  /** Permissions that reveal this item; empty = visible to all staff. */
  permits: string[];
}

const NAV: NavItem[] = [
  { href: ROUTES.admin.dashboard, label: 'Dashboard', icon: LayoutDashboard, permits: [PERMISSIONS.analyticsRead] },
  { href: ROUTES.admin.orders, label: 'Orders', icon: ShoppingBag, permits: [PERMISSIONS.orderWrite] },
  { href: ROUTES.admin.products, label: 'Products', icon: Package, permits: [PERMISSIONS.productWrite] },
  { href: ROUTES.admin.categories, label: 'Categories', icon: FolderTree, permits: [PERMISSIONS.categoryWrite] },
  { href: ROUTES.admin.inventory, label: 'Inventory', icon: Warehouse, permits: [PERMISSIONS.inventoryRead, PERMISSIONS.inventoryWrite] },
  { href: ROUTES.admin.purchaseOrders, label: 'Purchase Orders', icon: Truck, permits: [PERMISSIONS.inventoryWrite] },
  { href: ROUTES.admin.reviews, label: 'Reviews', icon: Star, permits: [PERMISSIONS.reviewModerate] },
  { href: ROUTES.admin.coupons, label: 'Coupons', icon: Ticket, permits: [PERMISSIONS.couponWrite] },
  { href: ROUTES.admin.banners, label: 'Banners', icon: ImageIcon, permits: [PERMISSIONS.bannerWrite] },
  { href: ROUTES.admin.aiSpend, label: 'AI spend', icon: Sparkles, permits: [PERMISSIONS.analyticsRead] },
  { href: ROUTES.admin.settings, label: 'Settings', icon: Settings, permits: [PERMISSIONS.settingsWrite] },
];

function visibleFor(user: UserSummary | null): NavItem[] {
  return NAV.filter((item) => item.permits.length === 0 || hasAnyPermission(user, item.permits));
}

export function AdminSidebar() {
  const pathname = usePathname();
  const { user } = useAuth();
  const items = visibleFor(user);

  return (
    <aside className="hidden w-60 shrink-0 border-r bg-muted/30 md:block">
      <div className="sticky top-0 flex h-screen flex-col">
        <Link href={ROUTES.admin.dashboard} className="flex items-center gap-2 border-b px-5 py-4 font-bold text-primary">
          <Leaf className="h-5 w-5" />
          <span>EcoExpress</span>
          <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-primary">
            Admin
          </span>
        </Link>

        <nav className="flex-1 space-y-1 overflow-y-auto p-3">
          {items.map((item) => {
            const active = pathname === item.href || pathname.startsWith(item.href + '/');
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  active
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                )}
              >
                <item.icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <Link
          href={ROUTES.home}
          className="flex items-center gap-2 border-t px-5 py-3 text-sm text-muted-foreground hover:text-foreground"
        >
          <Store className="h-4 w-4" /> Back to store
        </Link>
      </div>
    </aside>
  );
}
