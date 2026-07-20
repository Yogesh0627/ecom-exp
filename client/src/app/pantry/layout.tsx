import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'My pantry',
  robots: { index: false, follow: false },
};

export default function PantryLayout({ children }: { children: React.ReactNode }) {
  return children;
}
