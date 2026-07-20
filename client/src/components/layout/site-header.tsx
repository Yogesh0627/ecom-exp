'use client';

import Link from 'next/link';
import { Leaf, ShoppingCart, User, LogOut, CalendarDays, Refrigerator, Camera, LayoutDashboard, BookOpen } from 'lucide-react';
import { ROUTES, APP_NAME } from '@/constants';
import { useAuth, useCart } from '@/hooks';
import { isStaff } from '@/lib';
import { Button, Badge } from '@/components/ui';
import { ThemeToggle } from './theme-toggle';
import { HeaderSearch } from './header-search';

const AI_LINKS = [
  { href: ROUTES.mealPlanner, label: 'Meal planner', icon: CalendarDays },
  { href: ROUTES.pantry, label: 'Pantry', icon: Refrigerator },
  { href: ROUTES.smartFridge, label: 'Smart Fridge', icon: Camera },
  { href: ROUTES.blog, label: 'Journal', icon: BookOpen },
];

/** Top navigation: brand, search, cart badge, and auth state. */
export function SiteHeader() {
  const { isAuthenticated, user, logout } = useAuth();
  const { data: cart } = useCart();

  const itemCount = cart?.totalUnits ?? 0;

  return (
    <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur">
      <div className="container flex h-16 items-center gap-4">
        <Link href={ROUTES.home} className="flex items-center gap-2 font-bold text-primary">
          <Leaf className="h-6 w-6" />
          <span className="text-lg">{APP_NAME}</span>
        </Link>

        <HeaderSearch className="ml-2 hidden flex-1 md:block" />

        <nav className="hidden items-center gap-1 lg:flex">
          {AI_LINKS.map((link) => (
            <Button key={link.href} asChild variant="ghost" size="sm">
              <Link href={link.href}>
                <link.icon className="mr-1 h-4 w-4" />
                {link.label}
              </Link>
            </Button>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-1">
          <ThemeToggle />
          <Button asChild variant="ghost" size="icon" className="relative">
            <Link href={ROUTES.cart} aria-label="Cart">
              <ShoppingCart className="h-5 w-5" />
              {itemCount > 0 && (
                <Badge className="absolute -right-1 -top-1 h-5 min-w-5 justify-center px-1 text-[10px]">
                  {itemCount}
                </Badge>
              )}
            </Link>
          </Button>

          {isAuthenticated ? (
            <>
              {isStaff(user) && (
                <Button asChild variant="ghost" size="sm" className="hidden sm:inline-flex">
                  <Link href={ROUTES.admin.dashboard}>
                    <LayoutDashboard className="mr-1 h-4 w-4" />
                    Admin
                  </Link>
                </Button>
              )}
              <Button asChild variant="ghost" size="sm" className="hidden sm:inline-flex">
                <Link href={ROUTES.account}>
                  <User className="mr-1 h-4 w-4" />
                  {user?.fullName?.split(' ')[0] ?? 'Account'}
                </Link>
              </Button>
              <Button variant="ghost" size="icon" onClick={() => logout()} aria-label="Log out">
                <LogOut className="h-5 w-5" />
              </Button>
            </>
          ) : (
            <Button asChild size="sm">
              <Link href={ROUTES.login}>Sign in</Link>
            </Button>
          )}
        </div>
      </div>
    </header>
  );
}
