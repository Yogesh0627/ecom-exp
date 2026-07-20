'use client';

import { useState } from 'react';
import { Plus, Trash2, ImageIcon } from 'lucide-react';
import { dayjs } from '@/lib';
import { useAdminBanners, useCreateBanner, useDeleteBanner } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { BANNER_PLACEMENTS, type BannerPlacement } from '@/types';
import {
  Button,
  Card,
  CardContent,
  Input,
  Label,
  Badge,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogTrigger,
} from '@/components/ui';
import { AdminPageHeader, DataState, statusLabel } from '@/components/admin';

const emptyForm = {
  title: '',
  subtitle: '',
  imageUrl: '',
  linkUrl: '',
  placement: 'HOME_HERO' as BannerPlacement,
  position: '0',
  activeFrom: '',
  activeUntil: '',
};

export default function AdminBannersPage() {
  const { data: banners, isLoading, error } = useAdminBanners();
  const create = useCreateBanner();
  const del = useDeleteBanner();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);

  const set = (patch: Partial<typeof form>) => setForm((f) => ({ ...f, ...patch }));

  const onCreate = async () => {
    try {
      await create.mutateAsync({
        title: form.title.trim(),
        subtitle: form.subtitle.trim() || undefined,
        imageUrl: form.imageUrl.trim(),
        linkUrl: form.linkUrl.trim() || undefined,
        placement: form.placement,
        position: Number(form.position) || 0,
        activeFrom: form.activeFrom ? new Date(`${form.activeFrom}T00:00:00`).toISOString() : undefined,
        activeUntil: form.activeUntil ? new Date(`${form.activeUntil}T23:59:59`).toISOString() : undefined,
      });
      toast({ variant: 'success', title: 'Banner created', description: form.title });
      setOpen(false);
      setForm(emptyForm);
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not create the banner.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  const onDelete = async (id: string, title: string) => {
    if (!window.confirm(`Delete banner "${title}"?`)) return;
    try {
      await del.mutateAsync(id);
      toast({ variant: 'success', title: 'Banner deleted', description: title });
    } catch {
      toast({ variant: 'destructive', title: 'Failed', description: 'Could not delete.' });
    }
  };

  const rows = banners ?? [];
  const valid = form.title.trim() && form.imageUrl.trim();

  return (
    <div>
      <AdminPageHeader
        title="Banners"
        description="Promotional banners shown across the storefront."
        action={
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="mr-1 h-4 w-4" /> New banner
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>New banner</DialogTitle>
              </DialogHeader>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="sm:col-span-2">
                  <Label className="text-xs">Title</Label>
                  <Input value={form.title} onChange={(e) => set({ title: e.target.value })} className="mt-1" />
                </div>
                <div className="sm:col-span-2">
                  <Label className="text-xs">Subtitle (optional)</Label>
                  <Input value={form.subtitle} onChange={(e) => set({ subtitle: e.target.value })} className="mt-1" />
                </div>
                <div className="sm:col-span-2">
                  <Label className="text-xs">Image URL</Label>
                  <Input value={form.imageUrl} onChange={(e) => set({ imageUrl: e.target.value })} placeholder="https://…" className="mt-1" />
                </div>
                <div className="sm:col-span-2">
                  <Label className="text-xs">Link URL (optional)</Label>
                  <Input value={form.linkUrl} onChange={(e) => set({ linkUrl: e.target.value })} placeholder="/category/fruits" className="mt-1" />
                </div>
                <div>
                  <Label className="text-xs">Placement</Label>
                  <select
                    value={form.placement}
                    onChange={(e) => set({ placement: e.target.value as BannerPlacement })}
                    className="mt-1 flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  >
                    {BANNER_PLACEMENTS.map((p) => (
                      <option key={p} value={p}>
                        {statusLabel(p)}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <Label className="text-xs">Position</Label>
                  <Input type="number" min="0" value={form.position} onChange={(e) => set({ position: e.target.value })} className="mt-1" />
                </div>
                <div>
                  <Label className="text-xs">Active from (optional)</Label>
                  <Input type="date" value={form.activeFrom} onChange={(e) => set({ activeFrom: e.target.value })} className="mt-1" />
                </div>
                <div>
                  <Label className="text-xs">Active until (optional)</Label>
                  <Input type="date" value={form.activeUntil} onChange={(e) => set({ activeUntil: e.target.value })} className="mt-1" />
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setOpen(false)}>
                  Cancel
                </Button>
                <Button onClick={onCreate} disabled={!valid || create.isPending}>
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
        isEmpty={rows.length === 0}
        emptyLabel="No banners yet. Create one to feature on the storefront."
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {rows.map((b) => (
          <Card key={b.id} className="overflow-hidden">
            <div className="relative aspect-[16/7] bg-muted">
              {b.imageUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={b.imageUrl} alt={b.title} className="h-full w-full object-cover" />
              ) : (
                <div className="flex h-full items-center justify-center">
                  <ImageIcon className="h-8 w-8 text-muted-foreground/40" />
                </div>
              )}
              <div className="absolute right-2 top-2 flex gap-1">
                {b.live ? (
                  <Badge variant="success">live</Badge>
                ) : b.isActive ? (
                  <Badge variant="warning">scheduled</Badge>
                ) : (
                  <Badge variant="secondary">inactive</Badge>
                )}
              </div>
            </div>
            <CardContent className="p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate font-medium">{b.title}</p>
                  {b.subtitle && <p className="truncate text-xs text-muted-foreground">{b.subtitle}</p>}
                </div>
                <Button variant="ghost" size="icon" onClick={() => onDelete(b.id, b.title)}>
                  <Trash2 className="h-4 w-4 text-muted-foreground" />
                </Button>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <Badge variant="outline">{statusLabel(b.placement)}</Badge>
                <span>pos {b.position}</span>
                {b.activeUntil && <span>· until {dayjs(b.activeUntil).format('ll')}</span>}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
