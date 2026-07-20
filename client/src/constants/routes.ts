/** Centralised route paths — no hardcoded URLs scattered across components. */
export const ROUTES = {
  home: '/',
  login: '/login',
  register: '/register',
  category: (slug: string) => `/category/${slug}`,
  product: (slug: string) => `/products/${slug}`,
  cart: '/cart',
  checkout: '/checkout',
  orders: '/orders',
  order: (id: string) => `/orders/${id}`,
  account: '/account',
  search: (q: string) => `/search?q=${encodeURIComponent(q)}`,
  // AI hero features (PRD §5)
  mealPlanner: '/meal-planner',
  pantry: '/pantry',
  smartFridge: '/smart-fridge',
  // Editorial — organic living & nutrition blog
  blog: '/blog',
  blogPost: (slug: string) => `/blog/${slug}`,
  // Admin console (PRD §6) — staff only
  admin: {
    dashboard: '/admin',
    orders: '/admin/orders',
    order: (id: string) => `/admin/orders/${id}`,
    products: '/admin/products',
    categories: '/admin/categories',
    inventory: '/admin/inventory',
    purchaseOrders: '/admin/purchase-orders',
    purchaseOrder: (id: string) => `/admin/purchase-orders/${id}`,
    banners: '/admin/banners',
    reviews: '/admin/reviews',
    coupons: '/admin/coupons',
    settings: '/admin/settings',
    aiSpend: '/admin/ai-spend',
  },
} as const;
