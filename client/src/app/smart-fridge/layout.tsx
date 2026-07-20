import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Smart Fridge Scanner',
  description:
    'Snap a photo of your fridge and let AI spot your ingredients, then restock what is missing in one tap — no typing.',
  alternates: { canonical: '/smart-fridge' },
  openGraph: {
    title: 'Smart Fridge Scanner · EcoExpress',
    description: 'Photograph your fridge; AI identifies ingredients and helps you restock.',
    url: '/smart-fridge',
  },
};

export default function SmartFridgeLayout({ children }: { children: React.ReactNode }) {
  return children;
}
