'use client';

import Link from 'next/link';
import { useAuth } from '@/hooks';
import { isStaff } from '@/lib';
import { ROUTES } from '@/constants';
import { Button, Skeleton } from '@/components/ui';

/**
 * Gates the whole /admin area on staff role. This is a UX gate, not a security boundary — every
 * admin API call is independently authorized server-side — so it only spares non-staff a wall of
 * 403s and points them back to the storefront.
 */
export function AdminGuard({ children }: { children: React.ReactNode }) {
  const { user, isReady, isAuthenticated } = useAuth();

  if (!isReady) {
    return (
      <div className="container space-y-4 py-10">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="container py-20 text-center">
        <p className="mb-4 text-muted-foreground">Please sign in with a staff account.</p>
        <Button asChild>
          <Link href={ROUTES.login}>Sign in</Link>
        </Button>
      </div>
    );
  }

  if (!isStaff(user)) {
    return (
      <div className="container py-20 text-center">
        <h1 className="mb-2 text-xl font-semibold">Not authorized</h1>
        <p className="mb-4 text-muted-foreground">
          This area is for EcoExpress staff. Your account doesn&apos;t have access.
        </p>
        <Button asChild variant="outline">
          <Link href={ROUTES.home}>Back to store</Link>
        </Button>
      </div>
    );
  }

  return <>{children}</>;
}
