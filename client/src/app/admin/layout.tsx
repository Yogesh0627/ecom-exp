import type { Metadata } from 'next';
import { AdminGuard, AdminSidebar } from '@/components/admin';

export const metadata: Metadata = {
  title: { default: 'Admin', template: '%s · Admin · EcoExpress' },
  // The whole console is staff-only — never index it.
  robots: { index: false, follow: false },
};

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AdminGuard>
      <div className="flex min-h-screen">
        <AdminSidebar />
        <main className="min-w-0 flex-1 px-4 py-6 md:px-8">{children}</main>
      </div>
    </AdminGuard>
  );
}
