import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Create your account',
  description:
    'Join EcoExpress — certified-organic groceries with AI nutrition scoring, a weekly meal planner, and fresh delivery across India.',
  alternates: { canonical: '/register' },
};

export default function RegisterLayout({ children }: { children: React.ReactNode }) {
  return children;
}
