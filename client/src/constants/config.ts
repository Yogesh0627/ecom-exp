/** Runtime config sourced from the environment.
 *  Use `||` (not `??`) so an EMPTY env var (e.g. an unset Docker build ARG passes "") falls back to
 *  the default instead of becoming "" — an empty URL would crash `new URL(...)` at build time. */
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8081/api/v1';

/** Backend origin (without the /api/v1 prefix) — the OAuth entry point lives at the root. */
export const API_ORIGIN = API_BASE_URL.replace(/\/api\/v1\/?$/, '');

/** Full-page navigation target that starts the Google sign-in flow on the backend. */
export const GOOGLE_LOGIN_URL = `${API_ORIGIN}/oauth2/authorization/google`;

export const APP_NAME = 'EcoExpress';
export const APP_TAGLINE = 'AI-powered organic groceries for India';
export const APP_DESCRIPTION =
  'Shop certified-organic groceries with AI nutrition scoring, a weekly meal planner, and a smart fridge scanner — fresh produce delivered across India.';

/** Public site URL, used for canonical/OG/sitemap. Override per environment.
 *  `||` (not `??`) so an empty env var falls back rather than yielding "" (which breaks new URL()). */
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000').replace(
  /\/$/,
  '',
);

/** The developer, surfaced in the footer and as the metadata author/creator. */
export const DEVELOPER = {
  name: 'Yogesh Chauhan',
  url: 'https://yogeshchauhan.dev',
  linkedin: 'https://www.linkedin.com/in/yogeshchauhan-dev/',
} as const;

/**
 * One-click "sign in as admin/user" buttons on the login page. This is a public showcase build, so
 * they are ON everywhere by default (local AND the deployed demo) — recruiters and curious users
 * can explore both roles without credentials. Set NEXT_PUBLIC_ENABLE_DEMO_LOGIN=false to hide them.
 * The credentials mirror the backend's bootstrap admin and a self-provisioned demo customer.
 */
export const DEMO_LOGIN_ENABLED = process.env.NEXT_PUBLIC_ENABLE_DEMO_LOGIN !== 'false';

export const DEMO_ACCOUNTS = {
  admin: {
    email: process.env.NEXT_PUBLIC_DEMO_ADMIN_EMAIL ?? 'admin@ecoexpress.in',
    password: process.env.NEXT_PUBLIC_DEMO_ADMIN_PASSWORD ?? 'ChangeMe-EcoAdmin-2026',
  },
  user: {
    email: process.env.NEXT_PUBLIC_DEMO_USER_EMAIL ?? 'demo@ecoexpress.in',
    password: process.env.NEXT_PUBLIC_DEMO_USER_PASSWORD ?? 'Demo-User-2026',
    fullName: 'Demo Customer',
  },
} as const;

/**
 * Whether payments run against Razorpay TEST keys. When true, the checkout/pay screens show the
 * test-card details so anyone (recruiters, you) can complete a transaction and see the order settle.
 * Set NEXT_PUBLIC_PAYMENTS_TEST_MODE=false once you switch to live Razorpay keys.
 */
export const PAYMENTS_TEST_MODE = process.env.NEXT_PUBLIC_PAYMENTS_TEST_MODE !== 'false';

export const STORAGE_KEYS = {
  accessToken: 'eco_access_token',
  refreshToken: 'eco_refresh_token',
} as const;

/** Currency formatting — the storefront is India-only, so INR. */
export const CURRENCY = 'INR';
export const LOCALE = 'en-IN';
