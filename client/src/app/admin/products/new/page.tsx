'use client';

import { useRouter } from 'next/navigation';
import { useMemo, useState } from 'react';
import { ArrowLeft, Plus, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { ROUTES } from '@/constants';
import { useCategories, useCreateProduct } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { GST_RATES, type Category, type CreateVariantPayload } from '@/types';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Input,
  Label,
  Textarea,
} from '@/components/ui';
import { AdminPageHeader, ImageUploader } from '@/components/admin';

function slugify(s: string): string {
  return s.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

function flatten(cats: Category[], depth = 0): { cat: Category; depth: number }[] {
  return cats.flatMap((c) => [{ cat: c, depth }, ...flatten(c.children ?? [], depth + 1)]);
}

interface VariantForm {
  sku: string;
  name: string;
  weightGrams: string;
  mrp: string;
  price: string;
  isDefault: boolean;
  imageUrls: string[];
  // optional nutrition (per 100g)
  caloriesKcal: string;
  proteinG: string;
  fatG: string;
  carbohydratesG: string;
  fiberG: string;
  sugarG: string;
  sodiumMg: string;
}

const emptyVariant = (isDefault = false): VariantForm => ({
  sku: '',
  name: '',
  weightGrams: '',
  mrp: '',
  price: '',
  isDefault,
  imageUrls: [],
  caloriesKcal: '',
  proteinG: '',
  fatG: '',
  carbohydratesG: '',
  fiberG: '',
  sugarG: '',
  sodiumMg: '',
});

const num = (s: string): number | undefined => {
  if (s.trim() === '') return undefined;
  const n = Number(s);
  return Number.isNaN(n) ? undefined : n;
};

export default function NewProductPage() {
  const router = useRouter();
  const { data: categories } = useCategories();
  const create = useCreateProduct();
  const { toast } = useToast();

  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [slugTouched, setSlugTouched] = useState(false);
  const [description, setDescription] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [origin, setOrigin] = useState('');
  const [isOrganic, setIsOrganic] = useState(true);
  const [gstRatePct, setGstRatePct] = useState('5');
  const [hsnCode, setHsnCode] = useState('');
  const [variants, setVariants] = useState<VariantForm[]>([emptyVariant(true)]);

  const flat = useMemo(() => flatten(categories ?? []), [categories]);

  const setVariant = (i: number, patch: Partial<VariantForm>) =>
    setVariants((vs) => vs.map((v, idx) => (idx === i ? { ...v, ...patch } : v)));

  const setDefault = (i: number) =>
    setVariants((vs) => vs.map((v, idx) => ({ ...v, isDefault: idx === i })));

  const addVariant = () => setVariants((vs) => [...vs, emptyVariant(vs.length === 0)]);
  const removeVariant = (i: number) =>
    setVariants((vs) => {
      const next = vs.filter((_, idx) => idx !== i);
      // Ensure exactly one default remains.
      if (next.length > 0 && !next.some((v) => v.isDefault)) next[0].isDefault = true;
      return next;
    });

  const slugValid = /^[a-z0-9]+(-[a-z0-9]+)*$/.test(slug);
  const variantsValid = variants.every(
    (v) => v.sku.trim() && v.name.trim() && num(v.weightGrams) && num(v.mrp) !== undefined && num(v.price) !== undefined,
  );
  const valid = name.trim() && slugValid && categoryId && variants.length > 0 && variantsValid;

  const buildNutrition = (v: VariantForm): CreateVariantPayload['nutrition'] => {
    const fields = {
      caloriesKcal: num(v.caloriesKcal),
      proteinG: num(v.proteinG),
      fatG: num(v.fatG),
      carbohydratesG: num(v.carbohydratesG),
      fiberG: num(v.fiberG),
      sugarG: num(v.sugarG),
      sodiumMg: num(v.sodiumMg),
    };
    const anySet = Object.values(fields).some((x) => x !== undefined);
    if (!anySet) return undefined;
    return { ...fields, source: 'LABEL' };
  };

  const onSubmit = async () => {
    try {
      const product = await create.mutateAsync({
        name: name.trim(),
        slug: slug.trim(),
        description: description.trim() || undefined,
        categoryId,
        origin: origin.trim() || undefined,
        isOrganic,
        gstRatePct: num(gstRatePct),
        hsnCode: hsnCode.trim() || undefined,
        variants: variants.map((v) => ({
          sku: v.sku.trim(),
          name: v.name.trim(),
          weightGrams: num(v.weightGrams)!,
          mrp: num(v.mrp)!,
          price: num(v.price)!,
          isDefault: v.isDefault,
          nutrition: buildNutrition(v),
          imageUrls: v.imageUrls,
        })),
      });
      toast({ variant: 'success', title: 'Product created', description: product.name });
      router.push(ROUTES.admin.products);
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not create the product.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <Link
        href={ROUTES.admin.products}
        className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" /> Back to products
      </Link>
      <AdminPageHeader title="New product" description="Add a product and at least one sellable variant." />

      <div className="space-y-6">
        {/* Product details */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Details</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <Label htmlFor="p-name">Name</Label>
              <Input
                id="p-name"
                value={name}
                onChange={(e) => {
                  setName(e.target.value);
                  if (!slugTouched) setSlug(slugify(e.target.value));
                }}
                placeholder="Organic Baby Spinach"
                className="mt-1"
              />
            </div>
            <div>
              <Label htmlFor="p-slug">Slug</Label>
              <Input
                id="p-slug"
                value={slug}
                onChange={(e) => {
                  setSlugTouched(true);
                  setSlug(e.target.value);
                }}
                className="mt-1"
              />
              {slug && !slugValid && (
                <p className="mt-1 text-xs text-destructive">Lowercase words separated by hyphens.</p>
              )}
            </div>
            <div>
              <Label htmlFor="p-category">Category</Label>
              <select
                id="p-category"
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
                className="mt-1 flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              >
                <option value="">— Select —</option>
                {flat.map(({ cat, depth }) => (
                  <option key={cat.id} value={cat.id}>
                    {' '.repeat(depth * 2)}
                    {cat.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="p-origin">Origin (optional)</Label>
              <Input
                id="p-origin"
                value={origin}
                onChange={(e) => setOrigin(e.target.value)}
                placeholder="Nashik, Maharashtra"
                className="mt-1"
              />
            </div>
            <div>
              <Label htmlFor="p-gst">GST rate</Label>
              <select
                id="p-gst"
                value={gstRatePct}
                onChange={(e) => setGstRatePct(e.target.value)}
                className="mt-1 flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              >
                {GST_RATES.map((r) => (
                  <option key={r} value={r}>
                    {r}%
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="p-hsn">HSN code (optional)</Label>
              <Input
                id="p-hsn"
                value={hsnCode}
                onChange={(e) => setHsnCode(e.target.value)}
                placeholder="0709"
                className="mt-1"
              />
            </div>
            <label className="flex items-center gap-2 text-sm sm:col-span-2">
              <input
                type="checkbox"
                checked={isOrganic}
                onChange={(e) => setIsOrganic(e.target.checked)}
                className="h-4 w-4 rounded border-input"
              />
              Certified organic
            </label>
            <div className="sm:col-span-2">
              <Label htmlFor="p-desc">Description (optional)</Label>
              <Textarea
                id="p-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="mt-1"
              />
            </div>
          </CardContent>
        </Card>

        {/* Variants */}
        <Card>
          <CardHeader className="flex-row items-center justify-between pb-3">
            <CardTitle className="text-base">Variants</CardTitle>
            <Button variant="outline" size="sm" onClick={addVariant}>
              <Plus className="mr-1 h-3 w-3" /> Add variant
            </Button>
          </CardHeader>
          <CardContent className="space-y-6">
            {variants.map((v, i) => (
              <div key={i} className="rounded-lg border p-4">
                <div className="mb-3 flex items-center justify-between">
                  <label className="flex items-center gap-2 text-sm font-medium">
                    <input
                      type="radio"
                      name="default-variant"
                      checked={v.isDefault}
                      onChange={() => setDefault(i)}
                      className="h-4 w-4"
                    />
                    Default variant
                  </label>
                  {variants.length > 1 && (
                    <Button variant="ghost" size="icon" onClick={() => removeVariant(i)}>
                      <Trash2 className="h-4 w-4 text-muted-foreground" />
                    </Button>
                  )}
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div>
                    <Label className="text-xs">SKU</Label>
                    <Input value={v.sku} onChange={(e) => setVariant(i, { sku: e.target.value })} className="mt-1" placeholder="SPIN-200" />
                  </div>
                  <div>
                    <Label className="text-xs">Variant name</Label>
                    <Input value={v.name} onChange={(e) => setVariant(i, { name: e.target.value })} className="mt-1" placeholder="200 g pack" />
                  </div>
                  <div>
                    <Label className="text-xs">Weight (g)</Label>
                    <Input type="number" min="0.01" step="0.01" value={v.weightGrams} onChange={(e) => setVariant(i, { weightGrams: e.target.value })} className="mt-1" />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <Label className="text-xs">MRP (₹)</Label>
                      <Input type="number" min="0" step="0.01" value={v.mrp} onChange={(e) => setVariant(i, { mrp: e.target.value })} className="mt-1" />
                    </div>
                    <div>
                      <Label className="text-xs">Price (₹)</Label>
                      <Input type="number" min="0" step="0.01" value={v.price} onChange={(e) => setVariant(i, { price: e.target.value })} className="mt-1" />
                    </div>
                  </div>
                </div>

                <div className="mt-3">
                  <Label className="text-xs">Images</Label>
                  <div className="mt-1">
                    <ImageUploader
                      value={v.imageUrls}
                      onChange={(urls) => setVariant(i, { imageUrls: urls })}
                    />
                  </div>
                </div>

                <details className="mt-3">
                  <summary className="cursor-pointer text-xs font-medium text-muted-foreground">
                    Nutrition per 100g (optional — enables health scoring)
                  </summary>
                  <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
                    {(
                      [
                        ['caloriesKcal', 'Calories'],
                        ['proteinG', 'Protein (g)'],
                        ['fatG', 'Fat (g)'],
                        ['carbohydratesG', 'Carbs (g)'],
                        ['fiberG', 'Fiber (g)'],
                        ['sugarG', 'Sugar (g)'],
                        ['sodiumMg', 'Sodium (mg)'],
                      ] as const
                    ).map(([key, label]) => (
                      <div key={key}>
                        <Label className="text-xs">{label}</Label>
                        <Input
                          type="number"
                          min="0"
                          step="0.1"
                          value={v[key]}
                          onChange={(e) => setVariant(i, { [key]: e.target.value })}
                          className="mt-1"
                        />
                      </div>
                    ))}
                  </div>
                </details>
              </div>
            ))}
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button variant="outline" asChild>
            <Link href={ROUTES.admin.products}>Cancel</Link>
          </Button>
          <Button onClick={onSubmit} disabled={!valid || create.isPending}>
            {create.isPending ? 'Creating…' : 'Create product'}
          </Button>
        </div>
      </div>
    </div>
  );
}
