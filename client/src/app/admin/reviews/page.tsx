'use client';

import { useState } from 'react';
import { Star, Check, X, ShieldAlert } from 'lucide-react';
import { dayjs, cn } from '@/lib';
import { useModerationQueue, useModerateReview } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import type { ReviewStatus } from '@/types';
import { Button, Card, CardContent, Badge } from '@/components/ui';
import { AdminPageHeader, DataState } from '@/components/admin';

const TABS: { value: Extract<ReviewStatus, 'PENDING' | 'FLAGGED'>; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'FLAGGED', label: 'Flagged' },
];

function Stars({ rating }: { rating: number }) {
  return (
    <span className="inline-flex">
      {Array.from({ length: 5 }).map((_, i) => (
        <Star
          key={i}
          className={cn('h-4 w-4', i < rating ? 'fill-rating text-rating' : 'text-muted-foreground/30')}
        />
      ))}
    </span>
  );
}

export default function AdminReviewsPage() {
  const [tab, setTab] = useState<'PENDING' | 'FLAGGED'>('PENDING');
  const { data, isLoading, error } = useModerationQueue(tab);
  const moderate = useModerateReview();
  const { toast } = useToast();

  const decide = async (id: string, decision: ReviewStatus) => {
    try {
      await moderate.mutateAsync({ id, decision });
      toast({
        variant: 'success',
        title: decision === 'APPROVED' ? 'Review approved' : 'Review rejected',
      });
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not moderate the review.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  const reviews = data?.reviews ?? [];

  return (
    <div>
      <AdminPageHeader
        title="Reviews"
        description="Approve or reject customer reviews before they appear on the storefront."
      />

      <div className="mb-4 flex gap-2">
        {TABS.map((t) => (
          <button
            key={t.value}
            onClick={() => setTab(t.value)}
            className={cn(
              'rounded-md border px-4 py-1.5 text-sm',
              tab === t.value ? 'border-primary bg-primary text-primary-foreground' : 'hover:border-primary',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      <DataState
        isLoading={isLoading}
        error={error}
        isEmpty={reviews.length === 0}
        emptyLabel={tab === 'PENDING' ? 'No reviews waiting for moderation. 🎉' : 'No flagged reviews.'}
      />

      <div className="space-y-3">
        {reviews.map((r) => (
          <Card key={r.id}>
            <CardContent className="p-5">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <Stars rating={r.rating} />
                    <span className="text-sm font-medium">{r.reviewerName}</span>
                    {r.verifiedPurchase && (
                      <Badge variant="success" className="gap-1">
                        <ShieldAlert className="h-3 w-3" /> Verified purchase
                      </Badge>
                    )}
                    {tab === 'FLAGGED' && <Badge variant="warning">Flagged</Badge>}
                    <span className="text-xs text-muted-foreground">
                      {dayjs(r.createdAt).fromNow()}
                    </span>
                  </div>
                  {r.title && <p className="mt-2 font-medium">{r.title}</p>}
                  {r.body && <p className="mt-1 text-sm text-muted-foreground">{r.body}</p>}
                </div>
                <div className="flex shrink-0 gap-2">
                  <Button
                    size="sm"
                    disabled={moderate.isPending}
                    onClick={() => decide(r.id, 'APPROVED')}
                  >
                    <Check className="mr-1 h-4 w-4" /> Approve
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={moderate.isPending}
                    onClick={() => decide(r.id, 'REJECTED')}
                  >
                    <X className="mr-1 h-4 w-4" /> Reject
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
