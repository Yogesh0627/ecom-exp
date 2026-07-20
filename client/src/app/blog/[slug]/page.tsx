import type { Metadata } from 'next';
import Link from 'next/link';
import Image from 'next/image';
import { notFound } from 'next/navigation';
import { ArrowLeft } from 'lucide-react';
import { getBlogPost, getBlogFrontmatter, getBlogSlugs } from '@/lib/blog';
import { ROUTES, SITE_URL, APP_NAME, DEVELOPER } from '@/constants';
import dayjs from '@/lib/dayjs';

interface PageProps {
  params: { slug: string };
}

export async function generateStaticParams() {
  const slugs = await getBlogSlugs();
  return slugs.map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const fm = await getBlogFrontmatter(params.slug);
  if (!fm) return { title: 'Post not found' };

  const url = `${SITE_URL}${ROUTES.blogPost(params.slug)}`;
  const image = fm.image ? `${SITE_URL}${fm.image}` : undefined;

  return {
    title: fm.title,
    description: fm.description,
    keywords: fm.tags,
    alternates: { canonical: url },
    openGraph: {
      title: fm.title,
      description: fm.description,
      type: 'article',
      url,
      publishedTime: fm.date,
      authors: [fm.author ?? APP_NAME],
      tags: fm.tags,
      images: image ? [{ url: image }] : undefined,
    },
    twitter: {
      card: 'summary_large_image',
      title: fm.title,
      description: fm.description,
      images: image ? [image] : undefined,
    },
  };
}

export default async function BlogPostPage({ params }: PageProps) {
  const post = await getBlogPost(params.slug);
  if (!post) notFound();

  const { content, frontmatter: fm, readingTime } = post;
  const url = `${SITE_URL}${ROUTES.blogPost(params.slug)}`;

  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BlogPosting',
    mainEntityOfPage: { '@type': 'WebPage', '@id': url },
    headline: fm.title,
    description: fm.description,
    image: fm.image ? `${SITE_URL}${fm.image}` : undefined,
    datePublished: fm.date,
    author: { '@type': 'Organization', name: fm.author ?? APP_NAME, url: SITE_URL },
    publisher: {
      '@type': 'Organization',
      name: APP_NAME,
      url: SITE_URL,
    },
  };

  return (
    <article className="container py-10 md:py-14">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      <div className="mx-auto max-w-3xl">
        <Link
          href={ROUTES.blog}
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-primary"
        >
          <ArrowLeft className="h-4 w-4" />
          All articles
        </Link>

        <header className="mt-6">
          <h1 className="text-3xl font-bold tracking-tight md:text-4xl">{fm.title}</h1>
          <p className="mt-3 text-lg text-muted-foreground">{fm.description}</p>
          <div className="mt-4 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <time dateTime={fm.date}>{dayjs(fm.date).format('LL')}</time>
            <span aria-hidden="true">·</span>
            <span>{readingTime} min read</span>
            {fm.author && (
              <>
                <span aria-hidden="true">·</span>
                <span>{fm.author}</span>
              </>
            )}
          </div>
        </header>

        {fm.image && (
          <Image
            src={fm.image}
            alt={fm.title}
            width={1200}
            height={630}
            priority
            unoptimized
            className="mt-8 aspect-[16/9] w-full rounded-2xl border object-cover"
          />
        )}

        <div className="prose prose-neutral mt-10 max-w-none dark:prose-invert prose-headings:scroll-mt-20 prose-a:text-primary">
          {content}
        </div>

        {fm.tags && fm.tags.length > 0 && (
          <div className="mt-10 flex flex-wrap gap-2 border-t pt-6">
            {fm.tags.map((tag) => (
              <span
                key={tag}
                className="rounded-full border bg-muted/40 px-3 py-1 text-xs text-muted-foreground"
              >
                #{tag}
              </span>
            ))}
          </div>
        )}

        <footer className="mt-10 rounded-xl border bg-muted/30 p-6 text-sm text-muted-foreground">
          Written by the {APP_NAME} team. Platform built by{' '}
          <a
            href={DEVELOPER.url}
            target="_blank"
            rel="noopener noreferrer author"
            className="font-medium text-foreground hover:text-primary"
          >
            {DEVELOPER.name}
          </a>
          .
        </footer>
      </div>
    </article>
  );
}
