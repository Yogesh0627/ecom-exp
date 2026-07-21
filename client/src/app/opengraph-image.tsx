import { ImageResponse } from 'next/og';
import { APP_NAME, APP_TAGLINE } from '@/constants';

// Edge runtime: @vercel/og loads its default font via fetch here, avoiding the Node
// `fileURLToPath(Invalid URL)` crash that breaks `next build` prerendering on some setups.
export const runtime = 'edge';

// The default social share card (1200x630). Text/CSS only for maximum renderer compatibility.
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';
export const alt = `${APP_NAME} — ${APP_TAGLINE}`;

export default function OgImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          padding: '80px',
          background: 'linear-gradient(135deg, #1f6b3b 0%, #2f8f4e 60%, #3fae62 100%)',
          color: '#ffffff',
          fontFamily: 'sans-serif',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 84,
            height: 84,
            borderRadius: 20,
            background: 'rgba(255,255,255,0.18)',
            fontSize: 48,
            fontWeight: 800,
            marginBottom: 28,
          }}
        >
          E
        </div>
        <div style={{ fontSize: 68, fontWeight: 800 }}>{APP_NAME}</div>
        <div style={{ fontSize: 40, fontWeight: 600, maxWidth: 900, marginTop: 12, lineHeight: 1.2 }}>
          {APP_TAGLINE}
        </div>
        <div style={{ fontSize: 26, opacity: 0.9, marginTop: 24 }}>
          Certified organic · AI nutrition · Meal planning · Fresh delivery
        </div>
      </div>
    ),
    { ...size },
  );
}
