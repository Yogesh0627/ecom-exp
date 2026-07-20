'use client';

import Link from 'next/link';
import { useRef, useState } from 'react';
import { Camera, Sparkles, ShoppingCart, CheckCircle2, HelpCircle } from 'lucide-react';
import { ROUTES } from '@/constants';
import { formatCurrency } from '@/lib';
import { useFridgeScan, useCartMutations, useAuth } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { Button, Card, CardContent, CardHeader, CardTitle, Badge, Skeleton } from '@/components/ui';
import { AiStatusBanner } from '@/components/system/ai-status-banner';
import type { FridgeScanResult } from '@/types';

export default function SmartFridgePage() {
  const { isAuthenticated, isReady } = useAuth();
  const { toast } = useToast();
  const scan = useFridgeScan();
  const { addItem } = useCartMutations();
  const fileRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [result, setResult] = useState<FridgeScanResult | null>(null);

  const onFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      setPreview(dataUrl);
      setResult(null);
      // Strip the `data:image/...;base64,` prefix — the API wants raw base64.
      const base64 = dataUrl.split(',')[1];
      runScan(base64, file.type || 'image/jpeg');
    };
    reader.readAsDataURL(file);
  };

  const runScan = async (base64: string, mimeType: string) => {
    try {
      const res = await scan.mutateAsync({ imageBase64: base64, mimeType });
      setResult(res);
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'The fridge scanner is unavailable right now.';
      toast({ variant: 'destructive', title: 'Scan failed', description: msg });
    }
  };

  const onAddToCart = async (variantId: string, name: string) => {
    try {
      await addItem.mutateAsync({ variantId, qty: 1 });
      toast({ variant: 'success', title: 'Added to cart', description: name });
    } catch {
      toast({ variant: 'destructive', title: 'Could not add', description: 'Out of stock.' });
    }
  };

  if (isReady && !isAuthenticated) {
    return (
      <div className="container py-20 text-center">
        <p className="mb-4 text-muted-foreground">Sign in to use the Smart Fridge scanner.</p>
        <Button asChild>
          <Link href={ROUTES.login}>Sign in</Link>
        </Button>
      </div>
    );
  }

  const matched = result?.items.filter((i) => i.matchedVariantId) ?? [];
  const unmatched = result?.items.filter((i) => !i.matchedVariantId) ?? [];

  return (
    <div className="container max-w-3xl space-y-6 py-8">
      <AiStatusBanner />
      <div className="space-y-1">
        <div className="inline-flex items-center gap-2 rounded-full bg-accent px-3 py-1 text-xs font-medium text-accent-foreground">
          <Sparkles className="h-3.5 w-3.5" /> Gemini Vision
        </div>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <Camera className="h-6 w-6 text-primary" /> Smart Fridge
        </h1>
        <p className="text-muted-foreground">
          Snap a photo of your fridge. We&apos;ll spot the ingredients and help you restock what&apos;s
          missing.
        </p>
      </div>

      <Card>
        <CardContent className="p-6">
          <input ref={fileRef} type="file" accept="image/*" onChange={onFile} className="hidden" />
          <div className="flex flex-col items-center gap-4">
            {preview ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={preview} alt="Fridge" className="max-h-64 rounded-lg object-contain" />
            ) : (
              <div className="flex h-40 w-full items-center justify-center rounded-lg border-2 border-dashed text-muted-foreground">
                <Camera className="h-10 w-10 opacity-40" />
              </div>
            )}
            <Button onClick={() => fileRef.current?.click()} disabled={scan.isPending}>
              <Camera className="mr-2 h-4 w-4" />
              {scan.isPending ? 'Scanning…' : preview ? 'Scan another photo' : 'Upload a fridge photo'}
            </Button>
          </div>
        </CardContent>
      </Card>

      {scan.isPending && (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-14 rounded-lg" />
          ))}
        </div>
      )}

      {result && !scan.isPending && (
        <>
          {result.detectedCount === 0 ? (
            <Card>
              <CardContent className="py-8 text-center text-muted-foreground">
                No ingredients detected. Try a clearer, well-lit photo of the fridge contents.
              </CardContent>
            </Card>
          ) : (
            <>
              {matched.length > 0 && (
                <Card>
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base">Found in our store — restock these</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2">
                    {matched.map((item, i) => (
                      <div key={i} className="flex items-center gap-3">
                        <div className="flex-1">
                          <p className="font-medium capitalize">{item.detectedName}</p>
                          <p className="text-xs text-muted-foreground">
                            {item.matchedProductName}
                            {item.matchedPrice != null && ` · ${formatCurrency(item.matchedPrice)}`}
                          </p>
                        </div>
                        {item.needsConfirmation && (
                          <Badge variant="warning" className="gap-1">
                            <HelpCircle className="h-3 w-3" /> Low confidence
                          </Badge>
                        )}
                        <Button
                          size="sm"
                          onClick={() => onAddToCart(item.matchedVariantId!, item.matchedProductName!)}
                        >
                          <ShoppingCart className="mr-1 h-3 w-3" /> Add
                        </Button>
                      </div>
                    ))}
                  </CardContent>
                </Card>
              )}

              {unmatched.length > 0 && (
                <Card>
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center gap-2 text-base">
                      <CheckCircle2 className="h-4 w-4 text-success" /> Already have these
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="flex flex-wrap gap-2">
                      {unmatched.map((item, i) => (
                        <Badge key={i} variant="secondary" className="capitalize">
                          {item.detectedName}
                        </Badge>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              )}
            </>
          )}
        </>
      )}
    </div>
  );
}
