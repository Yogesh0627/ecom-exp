/** Emoji per category slug — gives the storefront recognizable, reliable category art. */
const CATEGORY_EMOJI: Record<string, string> = {
  fruits: '🍎',
  'fresh-vegetables': '🥬',
  'leafy-greens': '🥗',
  vegetables: '🥕',
  'grains-pulses': '🌾',
  grains: '🍚',
  flours: '🌾',
  'nuts-seeds': '🥜',
  dairy: '🥛',
  'healthy-foods': '🥑',
};

export function categoryEmoji(slug?: string | null): string {
  if (!slug) return '🛒';
  return CATEGORY_EMOJI[slug] ?? '🌿';
}
