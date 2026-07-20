import type { Metadata, Viewport } from 'next';
import './globals.css';
import { APP_NAME, APP_TAGLINE, APP_DESCRIPTION, SITE_URL, DEVELOPER } from '@/constants';
import { Providers } from './providers';
import { AppFrame } from '@/components/layout/app-frame';
import { ServerWakeBanner } from '@/components/system/server-wake-banner';

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: `${APP_NAME} — ${APP_TAGLINE}`,
    template: `%s · ${APP_NAME}`,
  },
  description: APP_DESCRIPTION,
  applicationName: APP_NAME,
  category: 'shopping',
  keywords: [
    'organic groceries',
    'online grocery India',
    'organic food delivery',
    'certified organic',
    'AI meal planner',
    'nutrition score',
    'smart fridge',
    'fresh produce',
    'EcoExpress',
  ],
  authors: [{ name: DEVELOPER.name, url: DEVELOPER.url }],
  creator: DEVELOPER.name,
  publisher: APP_NAME,
  alternates: { canonical: '/' },
  openGraph: {
    type: 'website',
    siteName: APP_NAME,
    title: `${APP_NAME} — ${APP_TAGLINE}`,
    description: APP_DESCRIPTION,
    url: SITE_URL,
    locale: 'en_IN',
  },
  twitter: {
    card: 'summary_large_image',
    title: `${APP_NAME} — ${APP_TAGLINE}`,
    description: APP_DESCRIPTION,
    creator: '@yogeshchauhan',
  },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true, 'max-image-preview': 'large', 'max-snippet': -1 },
  },
};

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#ffffff' },
    { media: '(prefers-color-scheme: dark)', color: '#0b0f0d' },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <Providers>
          <ServerWakeBanner />
          <AppFrame>{children}</AppFrame>
        </Providers>
      </body>
    </html>
  );
}
