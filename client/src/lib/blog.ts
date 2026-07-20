import 'server-only';
import { promises as fs } from 'fs';
import path from 'path';
import { compileMDX } from 'next-mdx-remote/rsc';
import type { BlogFrontmatter, BlogSummary } from '@/types';

/**
 * Filesystem-backed blog. MDX posts live in src/content/blog/*.mdx with YAML frontmatter
 * (title, description, date, image?, tags?). Everything here is server-only — it touches `fs`.
 */
const BLOG_DIR = path.join(process.cwd(), 'src', 'content', 'blog');

/** ~200 words/min, floored at 1, computed from the body with frontmatter stripped. */
function readingTimeOf(raw: string): number {
  const body = raw.replace(/^---[\s\S]*?---/, '');
  const words = body.trim().split(/\s+/).filter(Boolean).length;
  return Math.max(1, Math.round(words / 200));
}

async function readPost(slug: string): Promise<string | null> {
  try {
    return await fs.readFile(path.join(BLOG_DIR, `${slug}.mdx`), 'utf-8');
  } catch {
    return null;
  }
}

/** All post slugs, used for generateStaticParams. */
export async function getBlogSlugs(): Promise<string[]> {
  try {
    const files = await fs.readdir(BLOG_DIR);
    return files.filter((f) => f.endsWith('.mdx')).map((f) => f.replace(/\.mdx$/, ''));
  } catch {
    return [];
  }
}

/** Just the frontmatter for one post (cheap — no full MDX render of the body into React). */
export async function getBlogFrontmatter(slug: string): Promise<BlogFrontmatter | null> {
  const raw = await readPost(slug);
  if (!raw) return null;
  const { frontmatter } = await compileMDX<BlogFrontmatter>({
    source: raw,
    options: { parseFrontmatter: true },
  });
  return frontmatter;
}

/** All posts as listing summaries, newest first. Missing/invalid dates sink to the bottom. */
export async function getBlogSummaries(): Promise<BlogSummary[]> {
  const slugs = await getBlogSlugs();
  const posts = await Promise.all(
    slugs.map(async (slug) => {
      const raw = await readPost(slug);
      if (!raw) return null;
      const { frontmatter } = await compileMDX<BlogFrontmatter>({
        source: raw,
        options: { parseFrontmatter: true },
      });
      return { slug, readingTime: readingTimeOf(raw), ...frontmatter } satisfies BlogSummary;
    }),
  );
  return posts
    .filter((p): p is BlogSummary => p !== null)
    .sort((a, b) => {
      const ta = a.date ? new Date(a.date).getTime() : 0;
      const tb = b.date ? new Date(b.date).getTime() : 0;
      return tb - ta;
    });
}

/** The fully-rendered post: React content, frontmatter, and reading time. Null if missing. */
export async function getBlogPost(slug: string): Promise<{
  content: React.ReactNode;
  frontmatter: BlogFrontmatter;
  readingTime: number;
} | null> {
  const raw = await readPost(slug);
  if (!raw) return null;
  const { content, frontmatter } = await compileMDX<BlogFrontmatter>({
    source: raw,
    options: { parseFrontmatter: true },
  });
  return { content, frontmatter, readingTime: readingTimeOf(raw) };
}
