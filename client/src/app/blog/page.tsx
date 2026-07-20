import type { Metadata } from 'next';
import Link from 'next/link';
import Image from 'next/image';
import { Leaf } from 'lucide-react';
import { getBlogSummaries } from '@/lib/blog';
import { ROUTES, SITE_URL, APP_NAME } from '@/constants';
import dayjs from '@/lib/dayjs';

export const metadata: Metadata = {
  title: 'Organic Living Journal',
  description:
    'Practical, no-hype guides to organic food, seasonal produce, nutrition labels, and eating well in India — from the EcoExpress kitchen.',
  alternates: { canonical: `${SITE_URL}${ROUTES.blog}` },
  openGraph: {
    title: `Organic Living Journal · ${APP_NAME}`,
    description:
      'Practical, no-hype guides to organic food, seasonal produce, nutrition, and eating well in India.',
    type: 'website',
    url: `${SITE_URL}${ROUTES.blog}`,
  },
};

// Content is filesystem-backed and only changes on deploy — render statically.
export const dynamic = 'force-static';

export default async function BlogIndexPage() {
  const posts = await getBlogSummaries();

  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Blog',
    name: `Organic Living Journal · ${APP_NAME}`,
    url: `${SITE_URL}${ROUTES.blog}`,
    blogPost: posts.map((p) => ({
      '@type': 'BlogPosting',
      headline: p.title,
      description: p.description,
      datePublished: p.date,
      url: `${SITE_URL}${ROUTES.blogPost(p.slug)}`,
    })),
  };

  return (
    <div className="container py-10 md:py-14">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      <header className="mx-auto max-w-2xl text-center">
        <div className="mb-3 inline-flex items-center gap-2 rounded-full border bg-muted/40 px-3 py-1 text-sm text-muted-foreground">
          <Leaf className="h-4 w-4 text-primary" />
          Organic Living Journal
        </div>
        <h1 className="text-3xl font-bold tracking-tight md:text-4xl">
          Eat well, waste less, shop smarter
        </h1>
        <p className="mt-3 text-muted-foreground">
          Practical, no-hype guides to organic food, seasonal produce, and nutrition — written to
          help you get more out of every basket.
        </p>
      </header>

      {posts.length === 0 ? (
        <p className="mt-12 text-center text-muted-foreground">No posts yet — check back soon.</p>
      ) : (
        <div className="mx-auto mt-10 grid max-w-5xl gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {posts.map((post) => (
            <Link
              key={post.slug}
              href={ROUTES.blogPost(post.slug)}
              className="group flex flex-col overflow-hidden rounded-xl border bg-card transition-shadow hover:shadow-md"
            >
              <div className="relative aspect-[16/9] overflow-hidden bg-muted">
                {post.image ? (
                  <Image
                    src={post.image}
                    alt={post.title}
                    fill
                    unoptimized
                    sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw"
                    className="object-cover transition-transform duration-300 group-hover:scale-105"
                  />
                ) : (
                  <div className="flex h-full items-center justify-center">
                    <Leaf className="h-10 w-10 text-primary/40" />
                  </div>
                )}
              </div>
              <div className="flex flex-1 flex-col p-5">
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <time dateTime={post.date}>{dayjs(post.date).format('LL')}</time>
                  <span aria-hidden="true">·</span>
                  <span>{post.readingTime} min read</span>
                </div>
                <h2 className="mt-2 line-clamp-2 font-semibold leading-snug group-hover:text-primary">
                  {post.title}
                </h2>
                <p className="mt-2 line-clamp-3 flex-1 text-sm text-muted-foreground">
                  {post.description}
                </p>
                {post.tags && post.tags.length > 0 && (
                  <div className="mt-4 flex flex-wrap gap-1.5">
                    {post.tags.slice(0, 3).map((tag) => (
                      <span
                        key={tag}
                        className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground"
                      >
                        #{tag}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
