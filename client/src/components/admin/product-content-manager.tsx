'use client';

import { useEffect, useState } from 'react';
import { Sparkles, Save, Eye, EyeOff, Loader2, Wand2 } from 'lucide-react';
import { useAdminContent, useAiStatus } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { AiStatusBanner } from '@/components/system/ai-status-banner';
import type { ContentDraft } from '@/api';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Label,
  Textarea,
} from '@/components/ui';

const FIELDS: Array<{ key: keyof ContentDraft; label: string; hint: string }> = [
  { key: 'overview', label: 'Overview', hint: 'A richer description than the short one.' },
  { key: 'advantages', label: 'Advantages', hint: 'One point per line.' },
  { key: 'healthBenefits', label: 'Health benefits', hint: 'Cautiously framed; one point per line.' },
  { key: 'nutrientSupport', label: 'Nutrients that support health', hint: 'No disease claims.' },
  { key: 'whyChoose', label: 'Why choose this', hint: 'Organic / quality points.' },
  { key: 'storageTips', label: 'Storage tips', hint: 'How to keep it fresh.' },
];

const EMPTY: ContentDraft = {
  overview: '',
  advantages: '',
  healthBenefits: '',
  nutrientSupport: '',
  whyChoose: '',
  storageTips: '',
};

/**
 * Admin: AI-drafted, human-approved product content. Generate a draft with AI, edit it, then
 * publish — nothing reaches shoppers until it's published.
 */
export function ProductContentManager({ slug }: { slug: string }) {
  const { content, generate, update, publish, unpublish } = useAdminContent(slug);
  const { data: aiStatus } = useAiStatus();
  const { toast } = useToast();
  const aiDown = aiStatus && !aiStatus.available;
  const [draft, setDraft] = useState<ContentDraft>(EMPTY);

  // Load server content into the editable form whenever it changes.
  const loaded = content.data;
  useEffect(() => {
    if (loaded) {
      setDraft({
        overview: loaded.overview ?? '',
        advantages: loaded.advantages ?? '',
        healthBenefits: loaded.healthBenefits ?? '',
        nutrientSupport: loaded.nutrientSupport ?? '',
        whyChoose: loaded.whyChoose ?? '',
        storageTips: loaded.storageTips ?? '',
      });
    }
  }, [loaded]);

  const status = loaded?.status;
  const isPublished = status === 'PUBLISHED';

  const onGenerate = async () => {
    try {
      await generate.mutateAsync();
      toast({ variant: 'success', title: 'AI draft generated', description: 'Review and publish it.' });
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'AI generation failed. Try again.';
      toast({ variant: 'destructive', title: 'Generation failed', description: msg });
    }
  };

  const onSave = async () => {
    try {
      await update.mutateAsync(draft);
      toast({ variant: 'success', title: 'Saved' });
    } catch {
      toast({ variant: 'destructive', title: 'Could not save' });
    }
  };

  const onPublish = async () => {
    try {
      await publish.mutateAsync();
      toast({ variant: 'success', title: 'Published', description: 'Shoppers can now see this.' });
    } catch {
      toast({ variant: 'destructive', title: 'Could not publish' });
    }
  };

  const onUnpublish = async () => {
    try {
      await unpublish.mutateAsync();
      toast({ title: 'Unpublished', description: 'Back to draft — hidden from shoppers.' });
    } catch {
      toast({ variant: 'destructive', title: 'Could not unpublish' });
    }
  };

  return (
    <Card className="mt-6">
      <CardHeader className="flex-row items-center justify-between pb-3">
        <div className="flex items-center gap-2">
          <CardTitle className="text-base">Rich content</CardTitle>
          {status && (
            <Badge variant={isPublished ? 'success' : 'outline'}>
              {isPublished ? 'Published' : 'Draft'}
            </Badge>
          )}
          {loaded?.generatedByAi && (
            <Badge variant="outline" className="gap-1">
              <Wand2 className="h-3 w-3" /> AI-drafted
            </Badge>
          )}
        </div>
        <Button size="sm" variant="outline" onClick={onGenerate} disabled={generate.isPending || aiDown}>
          {generate.isPending ? (
            <Loader2 className="mr-1 h-4 w-4 animate-spin" />
          ) : (
            <Sparkles className="mr-1 h-4 w-4" />
          )}
          {loaded ? 'Regenerate with AI' : 'Generate with AI'}
        </Button>
      </CardHeader>
      <CardContent className="space-y-4">
        <AiStatusBanner />
        <p className="text-xs text-muted-foreground">
          AI drafts are grounded in this product’s real facts, but review before publishing — nothing
          shows to shoppers until it’s published. Avoid disease-cure claims.
        </p>

        {FIELDS.map((f) => (
          <div key={f.key}>
            <Label htmlFor={`content-${f.key}`}>{f.label}</Label>
            <Textarea
              id={`content-${f.key}`}
              value={draft[f.key] ?? ''}
              onChange={(e) => setDraft((d) => ({ ...d, [f.key]: e.target.value }))}
              placeholder={f.hint}
              rows={3}
              className="mt-1"
            />
          </div>
        ))}

        <div className="flex flex-wrap gap-2 pt-1">
          <Button onClick={onSave} disabled={update.isPending}>
            <Save className="mr-1 h-4 w-4" /> {update.isPending ? 'Saving…' : 'Save'}
          </Button>
          {isPublished ? (
            <Button variant="outline" onClick={onUnpublish} disabled={unpublish.isPending}>
              <EyeOff className="mr-1 h-4 w-4" /> Unpublish
            </Button>
          ) : (
            <Button variant="outline" onClick={onPublish} disabled={publish.isPending || !loaded}>
              <Eye className="mr-1 h-4 w-4" /> Publish
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
