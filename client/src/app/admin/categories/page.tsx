'use client';

import { useMemo, useState } from 'react';
import { Plus, FolderTree } from 'lucide-react';
import { useCategories, useCreateCategory } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import type { Category } from '@/types';
import {
  Button,
  Card,
  CardContent,
  Input,
  Label,
  Textarea,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogTrigger,
} from '@/components/ui';
import { AdminPageHeader, DataState } from '@/components/admin';

/** kebab-case slug from a display name. */
function slugify(s: string): string {
  return s
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/** Depth-first flatten so the parent picker can show the whole tree with indentation. */
function flatten(cats: Category[], depth = 0): { cat: Category; depth: number }[] {
  return cats.flatMap((c) => [{ cat: c, depth }, ...flatten(c.children ?? [], depth + 1)]);
}

export default function AdminCategoriesPage() {
  const { data: categories, isLoading, error } = useCategories();
  const create = useCreateCategory();
  const { toast } = useToast();

  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [slugTouched, setSlugTouched] = useState(false);
  const [description, setDescription] = useState('');
  const [parentId, setParentId] = useState('');

  const flat = useMemo(() => flatten(categories ?? []), [categories]);

  const reset = () => {
    setName('');
    setSlug('');
    setSlugTouched(false);
    setDescription('');
    setParentId('');
  };

  const onNameChange = (v: string) => {
    setName(v);
    if (!slugTouched) setSlug(slugify(v));
  };

  const onSubmit = async () => {
    try {
      await create.mutateAsync({
        name: name.trim(),
        slug: slug.trim(),
        description: description.trim() || undefined,
        parentId: parentId || undefined,
      });
      toast({ variant: 'success', title: 'Category created', description: name });
      setOpen(false);
      reset();
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not create the category.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  const valid = name.trim() && /^[a-z0-9]+(-[a-z0-9]+)*$/.test(slug);

  return (
    <div>
      <AdminPageHeader
        title="Categories"
        description="The storefront navigation tree."
        action={
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="mr-1 h-4 w-4" /> New category
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>New category</DialogTitle>
              </DialogHeader>
              <div className="space-y-3">
                <div>
                  <Label htmlFor="cat-name">Name</Label>
                  <Input
                    id="cat-name"
                    value={name}
                    onChange={(e) => onNameChange(e.target.value)}
                    placeholder="Fresh Vegetables"
                    className="mt-1"
                  />
                </div>
                <div>
                  <Label htmlFor="cat-slug">Slug</Label>
                  <Input
                    id="cat-slug"
                    value={slug}
                    onChange={(e) => {
                      setSlugTouched(true);
                      setSlug(e.target.value);
                    }}
                    placeholder="fresh-vegetables"
                    className="mt-1"
                  />
                  {slug && !valid && (
                    <p className="mt-1 text-xs text-destructive">
                      Lowercase words separated by hyphens.
                    </p>
                  )}
                </div>
                <div>
                  <Label htmlFor="cat-parent">Parent (optional)</Label>
                  <select
                    id="cat-parent"
                    value={parentId}
                    onChange={(e) => setParentId(e.target.value)}
                    className="mt-1 flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  >
                    <option value="">— None (top level) —</option>
                    {flat.map(({ cat, depth }) => (
                      <option key={cat.id} value={cat.id}>
                        {' '.repeat(depth * 2)}
                        {cat.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <Label htmlFor="cat-desc">Description (optional)</Label>
                  <Textarea
                    id="cat-desc"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    className="mt-1"
                  />
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setOpen(false)}>
                  Cancel
                </Button>
                <Button onClick={onSubmit} disabled={!valid || create.isPending}>
                  {create.isPending ? 'Creating…' : 'Create'}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        }
      />

      <DataState
        isLoading={isLoading}
        error={error}
        isEmpty={!categories || categories.length === 0}
        emptyLabel="No categories yet. Create the first one to start building the catalog."
      />

      {categories && categories.length > 0 && (
        <Card>
          <CardContent className="p-2">
            <ul className="divide-y">
              {flat.map(({ cat, depth }) => (
                <li key={cat.id} className="flex items-center gap-2 px-3 py-2.5 text-sm">
                  <span style={{ paddingLeft: `${depth * 20}px` }} className="flex items-center gap-2">
                    <FolderTree className="h-4 w-4 text-muted-foreground" />
                    <span className="font-medium">{cat.name}</span>
                  </span>
                  <span className="text-muted-foreground">/{cat.slug}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
