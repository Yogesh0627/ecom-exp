'use client';

import { usePathname } from 'next/navigation';
import { SiteHeader } from './site-header';
import { SiteFooter } from './site-footer';
import { VerifyEmailBanner } from './verify-email-banner';

/**
 * Storefront chrome (header + footer) wraps every page EXCEPT the admin console, which brings its
 * own sidebar layout. Kept here as a client component so the root layout can stay a server
 * component while still branching on the current path.
 */
export function AppFrame({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isAdmin = pathname?.startsWith('/admin');

  if (isAdmin) {
    return <>{children}</>;
  }

  return (
    <>
      <SiteHeader />
      <VerifyEmailBanner />
      <main className="min-h-[calc(100vh-4rem)]">{children}</main>
      <SiteFooter />
    </>
  );
}
