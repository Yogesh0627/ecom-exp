/** Frontmatter shape for a blog post (`.mdx` files in src/content/blog). */
export interface BlogFrontmatter {
  title: string;
  description: string;
  /** ISO date, e.g. "2026-07-10". */
  date: string;
  /** Public cover image path or URL, e.g. "/blog/organic-basics.jpg". Optional. */
  image?: string;
  tags?: string[];
  author?: string;
}

/** A post in listing context: frontmatter plus its URL slug and computed reading time. */
export interface BlogSummary extends BlogFrontmatter {
  slug: string;
  readingTime: number;
}
