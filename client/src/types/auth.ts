/** Auth types — mirror the backend AuthDtos. */

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  emailVerified: boolean;
  roles: string[];
  permissions: string[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
}

/** The signed-in user's own profile (GET /auth/me) — richer than UserSummary (has phone + pending email). */
export interface Profile {
  id: string;
  email: string;
  fullName: string;
  phone?: string | null;
  emailVerified: boolean;
  pendingEmail?: string | null;
  oauthOnly: boolean;
  roles: string[];
}

export interface UpdateProfilePayload {
  fullName?: string;
  phone?: string;
}

export interface ChangeEmailPayload {
  newEmail: string;
}

/** Recommendation shown on the product page ("goes well with"). */
export interface Recommendation {
  variantId: string;
  sku: string;
  productName: string;
  productSlug: string;
  price: number;
  ruleType: string;
  reason?: string | null;
  weight: number;
}

/** A public review on a product page. */
export interface Review {
  id: string;
  productId: string;
  reviewerName: string;
  rating: number;
  title?: string | null;
  body?: string | null;
  verifiedPurchase: boolean;
  status: string;
  helpfulCount: number;
  imageUrls: string[];
  createdAt: string;
}
