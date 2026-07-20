import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Search',
  // Search-result pages are thin/duplicative — keep them out of the index.
  robots: { index: false, follow: true },
};

export default function SearchLayout({ children }: { children: React.ReactNode }) {
  return children;
}
