import type { MetadataRoute } from 'next';
import { APP_NAME, APP_TAGLINE, APP_DESCRIPTION } from '@/constants';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: `${APP_NAME} — ${APP_TAGLINE}`,
    short_name: APP_NAME,
    description: APP_DESCRIPTION,
    start_url: '/',
    display: 'standalone',
    background_color: '#ffffff',
    theme_color: '#2f8f4e',
    categories: ['shopping', 'food'],
    icons: [{ src: '/icon', sizes: '32x32', type: 'image/png' }],
  };
}
