'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useMemo, useState } from 'react';
import {
  Leaf,
  ShoppingCart,
  Star,
  CheckCircle2,
  ShieldCheck,
  FileText,
  Sparkles,
  HeartPulse,
  Lightbulb,
  Package,
  Info,
} from 'lucide-react';
import { ROUTES } from '@/constants';
import { formatCurrency, dayjs } from '@/lib';
import {
  useProduct,
  useProductCertifications,
  useProductContent,
  useSimilarProducts,
  useRecommendations,
  useReviews,
  useCartMutations,
  useAuth,
} from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { Button, Card, CardContent, Badge, Separator, Skeleton, Breadcrumbs } from '@/components/ui';
import {
  CERT_TYPE_LABEL,
  type Certification,
  type NutritionFacts,
  type ProductContent,
  type ProductSummary,
  type ProductVariant,
} from '@/types';

export default function ProductDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const router = useRouter();
  const { toast } = useToast();
  const { isAuthenticated } = useAuth();
  const { data: product, isLoading } = useProduct(slug);

  const [variantId, setVariantId] = useState<string | null>(null);
  const [activeImage, setActiveImage] = useState(0);
  const variant: ProductVariant | undefined = useMemo(() => {
    if (!product) return undefined;
    return product.variants.find((v) => v.id === variantId) ?? product.variants.find((v) => v.isDefault) ?? product.variants[0];
  }, [product, variantId]);

  const { addItem } = useCartMutations();
  const { data: recommendations } = useRecommendations(variant?.id);
  const { data: similar } = useSimilarProducts(slug);
  const { data: content } = useProductContent(slug);
  const { data: reviews } = useReviews(product?.id);
  const { data: certifications } = useProductCertifications(slug);

  if (isLoading) {
    return (
      <div className="container grid gap-8 py-8 md:grid-cols-2">
        <Skeleton className="aspect-square rounded-xl" />
        <div className="space-y-4">
          <Skeleton className="h-8 w-3/4" />
          <Skeleton className="h-6 w-1/3" />
          <Skeleton className="h-24 w-full" />
        </div>
      </div>
    );
  }

  if (!product || !variant) {
    return <div className="container py-20 text-center text-muted-foreground">Product not found.</div>;
  }

  const onAdd = async () => {
    if (!isAuthenticated) {
      router.push(ROUTES.login);
      return;
    }
    try {
      await addItem.mutateAsync({ variantId: variant.id, qty: 1 });
      toast({ variant: 'success', title: 'Added to cart', description: product.name });
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not add to cart.';
      toast({ variant: 'destructive', title: 'Out of stock', description: msg });
    }
  };

  // Gallery: primary first, then the rest. Reset the selection if it runs past the array.
  const images = [...variant.images].sort(
    (a, b) => Number(b.isPrimary) - Number(a.isPrimary) || a.position - b.position,
  );
  const selected = images[activeImage] ?? images[0];

  return (
    <div className="container space-y-12 py-8">
      <Breadcrumbs
        items={[
          ...(product.category
            ? [{ label: product.category.name, href: ROUTES.category(product.category.slug) }]
            : []),
          { label: product.name },
        ]}
      />

      <div className="grid gap-8 md:grid-cols-2">
        {/* Gallery */}
        <div className="space-y-3">
          <div className="relative aspect-square overflow-hidden rounded-xl bg-muted">
            {selected ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={selected.url} alt={selected.alt ?? product.name} className="h-full w-full object-cover" />
            ) : (
              <div className="flex h-full items-center justify-center">
                <Leaf className="h-16 w-16 text-muted-foreground opacity-30" />
              </div>
            )}
          </div>
          {images.length > 1 && (
            <div className="flex flex-wrap gap-2">
              {images.map((img, i) => (
                <button
                  key={img.id}
                  onClick={() => setActiveImage(i)}
                  aria-label={`View image ${i + 1}`}
                  className={`relative h-16 w-16 overflow-hidden rounded-lg border-2 transition-colors ${
                    i === activeImage ? 'border-primary' : 'border-transparent hover:border-muted-foreground/40'
                  }`}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={img.url} alt={img.alt ?? ''} className="h-full w-full object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Info */}
        <div className="space-y-5">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              {product.isOrganic && (
                <Badge variant="success" className="gap-1">
                  <Leaf className="h-3 w-3" /> Organic
                </Badge>
              )}
              {product.origin && <Badge variant="outline">Origin: {product.origin}</Badge>}
            </div>
            <h1 className="text-2xl font-bold">{product.name}</h1>
            {product.description && <p className="text-muted-foreground">{product.description}</p>}
          </div>

          {/* Variant chips */}
          {product.variants.length > 1 && (
            <div className="flex flex-wrap gap-2">
              {product.variants.map((v) => (
                <button
                  key={v.id}
                  onClick={() => setVariantId(v.id)}
                  className={`rounded-md border px-3 py-1.5 text-sm transition-colors ${
                    v.id === variant.id
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'hover:border-primary'
                  }`}
                >
                  {v.name}
                </button>
              ))}
            </div>
          )}

          <div className="flex items-baseline gap-3">
            <span className="text-2xl font-bold">{formatCurrency(variant.price)}</span>
            {variant.discountPercent > 0 && (
              <>
                <span className="text-muted-foreground line-through">{formatCurrency(variant.mrp)}</span>
                <Badge variant="destructive">{Math.round(variant.discountPercent)}% off</Badge>
              </>
            )}
          </div>

          <Button size="lg" onClick={onAdd} disabled={addItem.isPending} className="w-full sm:w-auto">
            <ShoppingCart className="mr-2 h-5 w-5" />
            {addItem.isPending ? 'Adding…' : 'Add to cart'}
          </Button>

          {certifications && certifications.length > 0 && (
            <CertificationsPanel certs={certifications} />
          )}

          {variant.nutrition && <NutritionPanel n={variant.nutrition} weight={variant.weightGrams} />}
        </div>
      </div>

      {/* AI-assisted rich content (only shown when an admin has published it) */}
      {content && <ProductContentSections content={content} />}

      {/* You may also like — similar in-stock products */}
      {similar && similar.length > 0 && (
        <section>
          <h2 className="mb-4 text-lg font-semibold">You may also like</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {similar.map((p) => (
              <SimilarCard key={p.id} p={p} />
            ))}
          </div>
        </section>
      )}

      {/* Recommendations */}
      {recommendations && recommendations.length > 0 && (
        <section>
          <h2 className="mb-4 text-lg font-semibold">Goes well with</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {recommendations.map((r) => (
              <Link key={r.variantId} href={ROUTES.product(r.productSlug)}>
                <Card className="h-full transition-shadow hover:shadow-md">
                  <CardContent className="p-3">
                    <p className="line-clamp-2 text-sm font-medium">{r.productName}</p>
                    {r.reason && <p className="mt-1 text-xs text-muted-foreground">{r.reason}</p>}
                    <p className="mt-2 font-semibold">{formatCurrency(r.price)}</p>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* Reviews */}
      <section>
        <h2 className="mb-4 text-lg font-semibold">
          Reviews {reviews && reviews.length > 0 && `(${reviews.length})`}
        </h2>
        {reviews && reviews.length > 0 ? (
          <div className="space-y-4">
            {reviews.map((rev) => (
              <Card key={rev.id}>
                <CardContent className="space-y-2 p-4">
                  <div className="flex items-center gap-2">
                    <div className="flex">
                      {Array.from({ length: 5 }).map((_, i) => (
                        <Star
                          key={i}
                          className={`h-4 w-4 ${i < rev.rating ? 'fill-rating text-rating' : 'text-muted'}`}
                        />
                      ))}
                    </div>
                    <span className="text-sm font-medium">{rev.reviewerName}</span>
                    {rev.verifiedPurchase && (
                      <Badge variant="success" className="gap-1">
                        <CheckCircle2 className="h-3 w-3" /> Verified purchase
                      </Badge>
                    )}
                    <span className="ml-auto text-xs text-muted-foreground">
                      {dayjs(rev.createdAt).fromNow()}
                    </span>
                  </div>
                  {rev.title && <p className="text-sm font-semibold">{rev.title}</p>}
                  {rev.body && <p className="text-sm text-muted-foreground">{rev.body}</p>}
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No reviews yet.</p>
        )}
      </section>
    </div>
  );
}

/** Trust panel: the organic claim, backed by viewable certificates. The differentiator. */
function CertificationsPanel({ certs }: { certs: Certification[] }) {
  return (
    <Card className="border-success/40 bg-success-subtle/40">
      <CardContent className="p-4">
        <div className="mb-3 flex items-center gap-2">
          <ShieldCheck className="h-5 w-5 text-success" />
          <p className="text-sm font-semibold">Certified &amp; verified</p>
        </div>
        <div className="space-y-2">
          {certs.map((c) => (
            <a
              key={c.id}
              href={c.documentUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 rounded-md border bg-background px-3 py-2 text-sm transition-colors hover:border-primary"
            >
              <FileText className="h-4 w-4 shrink-0 text-muted-foreground" />
              <span className="min-w-0 flex-1">
                <span className="font-medium">{CERT_TYPE_LABEL[c.certType]}</span>
                {c.issuingBody && (
                  <span className="text-muted-foreground"> · {c.issuingBody}</span>
                )}
              </span>
              {c.verified && !c.expired && (
                <Badge variant="success" className="gap-1">
                  <CheckCircle2 className="h-3 w-3" /> Verified
                </Badge>
              )}
              {c.expired && <Badge variant="destructive">Expired</Badge>}
            </a>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

/** Splits a content section into bullet lines (the model returns newline-separated points). */
function bullets(text?: string | null): string[] {
  if (!text) return [];
  return text
    .split('\n')
    .map((l) => l.replace(/^[-•*\d.)\s]+/, '').trim())
    .filter(Boolean);
}

/** Rich, AI-assisted (admin-approved) product content. */
function ProductContentSections({ content }: { content: ProductContent }) {
  const sections: Array<{ icon: typeof Info; title: string; body?: string | null }> = [
    { icon: Info, title: 'About this product', body: content.overview },
    { icon: Sparkles, title: 'Why you’ll love it', body: content.advantages },
    { icon: HeartPulse, title: 'Health benefits', body: content.healthBenefits },
    { icon: Leaf, title: 'Nutrients that support your health', body: content.nutrientSupport },
    { icon: CheckCircle2, title: 'Why choose this', body: content.whyChoose },
    { icon: Package, title: 'Storage tips', body: content.storageTips },
  ].filter((s) => s.body && s.body.trim());

  if (sections.length === 0) return null;

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">Product details</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        {sections.map((s) => {
          const items = bullets(s.body);
          const asList = items.length > 1;
          return (
            <Card key={s.title}>
              <CardContent className="p-5">
                <div className="mb-3 flex items-center gap-2">
                  <s.icon className="h-5 w-5 text-primary" />
                  <h3 className="font-semibold">{s.title}</h3>
                </div>
                {asList ? (
                  <ul className="space-y-1.5">
                    {items.map((it, i) => (
                      <li key={i} className="flex gap-2 text-sm text-muted-foreground">
                        <Lightbulb className="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary/70" />
                        <span>{it}</span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm leading-relaxed text-muted-foreground">{s.body}</p>
                )}
              </CardContent>
            </Card>
          );
        })}
      </div>
      {(content.healthBenefits || content.nutrientSupport) && (
        <p className="text-xs text-muted-foreground">
          General nutritional information, not medical advice. Consult a professional for dietary
          guidance.
        </p>
      )}
    </section>
  );
}

/** A similar-product card ("you may also like"). */
function SimilarCard({ p }: { p: ProductSummary }) {
  return (
    <Link href={ROUTES.product(p.slug)}>
      <Card className="h-full overflow-hidden transition-shadow hover:shadow-md">
        <div className="relative aspect-square bg-muted">
          {p.imageUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={p.imageUrl} alt={p.name} className="h-full w-full object-cover" />
          ) : (
            <div className="flex h-full items-center justify-center">
              <Leaf className="h-8 w-8 text-muted-foreground opacity-30" />
            </div>
          )}
        </div>
        <CardContent className="p-3">
          <p className="line-clamp-2 text-sm font-medium">{p.name}</p>
          <p className="mt-1 font-semibold">{formatCurrency(p.price)}</p>
        </CardContent>
      </Card>
    </Link>
  );
}

function NutritionPanel({ n, weight }: { n: NutritionFacts; weight: number }) {
  const rows: Array<[string, number | null, string]> = [
    ['Calories', n.caloriesKcal, 'kcal'],
    ['Protein', n.proteinG, 'g'],
    ['Carbs', n.carbohydratesG, 'g'],
    ['Fat', n.fatG, 'g'],
    ['Fibre', n.fiberG, 'g'],
    ['Sugar', n.sugarG, 'g'],
    ['Iron', n.ironMg, 'mg'],
    ['Sodium', n.sodiumMg, 'mg'],
  ];
  return (
    <Card>
      <CardContent className="p-4">
        <div className="mb-2 flex items-center justify-between">
          <p className="text-sm font-semibold">Nutrition · per {n.basisGrams}g</p>
          <Badge variant="outline">{n.source}</Badge>
        </div>
        <Separator className="mb-3" />
        <div className="grid grid-cols-2 gap-x-6 gap-y-1.5 text-sm">
          {rows.map(([label, value, unit]) => (
            <div key={label} className="flex justify-between">
              <span className="text-muted-foreground">{label}</span>
              {/* null = not measured, shown as em dash — never fabricated as 0 */}
              <span className="font-medium">{value === null ? '—' : `${value} ${unit}`}</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
