'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState } from 'react';
import {
  Leaf,
  ShoppingCart,
  User,
  LogOut,
  CalendarDays,
  Refrigerator,
  Camera,
  LayoutDashboard,
  BookOpen,
  Menu,
  Package,
} from 'lucide-react';
import { ROUTES, APP_NAME } from '@/constants';
import { useAuth, useCart } from '@/hooks';
import { isStaff, cn } from '@/lib';
import {
  Button,
  Badge,
  Tooltip,
  Sheet,
  SheetContent,
  SheetTrigger,
  SheetHeader,
  SheetTitle,
} from '@/components/ui';
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
  const pathname = usePathname();

  const itemCount = cart?.totalUnits ?? 0;
  const isActive = (href: string) => pathname === href || pathname.startsWith(href + '/');

  return (
    <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur">
      <div className="container flex h-16 items-center gap-2 sm:gap-4">
        {/* Mobile menu (below lg) */}
        <MobileNav
          isAuthenticated={isAuthenticated}
          user={user}
          logout={logout}
          isActive={isActive}
        />

        <Link href={ROUTES.home} className="flex items-center gap-2 font-bold text-primary">
          <Leaf className="h-6 w-6" />
          <span className="text-base sm:text-lg">{APP_NAME}</span>
        </Link>

        <HeaderSearch className="ml-2 hidden flex-1 md:block" />

        <nav className="hidden items-center gap-1 lg:flex">
          {AI_LINKS.map((link) => {
            const active = isActive(link.href);
            return (
              <Button
                key={link.href}
                asChild
                variant="ghost"
                size="sm"
                className={cn(
                  active && 'bg-primary/10 font-semibold text-primary hover:bg-primary/15 hover:text-primary',
                )}
              >
                <Link href={link.href} aria-current={active ? 'page' : undefined}>
                  <link.icon className="mr-1 h-4 w-4" />
                  {link.label}
                </Link>
              </Button>
            );
          })}
        </nav>

        <div className="ml-auto flex items-center gap-1">
          <ThemeToggle />
          <Tooltip label="Cart">
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
          </Tooltip>

          {isAuthenticated ? (
            <>
              {isStaff(user) && (
                <Button asChild variant="ghost" size="sm" className="hidden lg:inline-flex">
                  <Link href={ROUTES.admin.dashboard}>
                    <LayoutDashboard className="mr-1 h-4 w-4" />
                    Admin
                  </Link>
                </Button>
              )}
              <Button asChild variant="ghost" size="sm" className="hidden lg:inline-flex">
                <Link href={ROUTES.account}>
                  <User className="mr-1 h-4 w-4" />
                  {user?.fullName?.split(' ')[0] ?? 'Account'}
                </Link>
              </Button>
              <Tooltip label="Log out">
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={() => logout()}
                  aria-label="Log out"
                  className="hidden lg:inline-flex"
                >
                  <LogOut className="h-5 w-5" />
                </Button>
              </Tooltip>
            </>
          ) : (
            <Button asChild size="sm" className="hidden lg:inline-flex">
              <Link href={ROUTES.login}>Sign in</Link>
            </Button>
          )}
        </div>
      </div>
    </header>
  );
}

/* ------------------------------------------------------------ Mobile drawer */

interface MobileNavProps {
  isAuthenticated: boolean;
  user: ReturnType<typeof useAuth>['user'];
  logout: () => void;
  isActive: (href: string) => boolean;
}

/** Hamburger drawer shown below `lg` — holds search, AI links, and account actions
 *  that are otherwise hidden on small screens (this is how mobile users reach their profile). */
function MobileNav({ isAuthenticated, user, logout, isActive }: MobileNavProps) {
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);

  const accountLinks = isAuthenticated
    ? [
        { href: ROUTES.account, label: 'My account', icon: User },
        { href: ROUTES.orders, label: 'My orders', icon: Package },
        ...(isStaff(user)
          ? [{ href: ROUTES.admin.dashboard, label: 'Admin', icon: LayoutDashboard }]
          : []),
      ]
    : [];

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Open menu">
          <Menu className="h-5 w-5" />
        </Button>
      </SheetTrigger>
      <SheetContent side="left" className="w-72 gap-0 overflow-y-auto">
        <SheetHeader className="mb-4">
          <SheetTitle className="flex items-center gap-2 text-primary">
            <Leaf className="h-5 w-5" /> {APP_NAME}
          </SheetTitle>
        </SheetHeader>

        <div className="mb-4">
          <HeaderSearch onNavigate={close} />
        </div>

        <nav className="flex flex-col gap-1">
          {AI_LINKS.map((link) => (
            <MobileLink
              key={link.href}
              href={link.href}
              label={link.label}
              icon={link.icon}
              active={isActive(link.href)}
              onClick={close}
            />
          ))}
        </nav>

        <div className="my-4 border-t" />

        {isAuthenticated ? (
          <nav className="flex flex-col gap-1">
            {accountLinks.map((link) => (
              <MobileLink
                key={link.href}
                href={link.href}
                label={link.label}
                icon={link.icon}
                active={isActive(link.href)}
                onClick={close}
              />
            ))}
            <button
              onClick={() => {
                close();
                logout();
              }}
              className="mt-1 flex items-center gap-3 rounded-md px-3 py-2 text-sm text-destructive hover:bg-muted"
            >
              <LogOut className="h-4 w-4" /> Log out
            </button>
          </nav>
        ) : (
          <Button asChild className="w-full" onClick={close}>
            <Link href={ROUTES.login}>Sign in</Link>
          </Button>
        )}
      </SheetContent>
    </Sheet>
  );
}

function MobileLink({
  href,
  label,
  icon: Icon,
  active,
  onClick,
}: {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <Link
      href={href}
      onClick={onClick}
      aria-current={active ? 'page' : undefined}
      className={cn(
        'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium hover:bg-muted',
        active && 'bg-primary/10 text-primary',
      )}
    >
      <Icon className="h-4 w-4" /> {label}
    </Link>
  );
}
