'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Minus,
  Plus,
  Trash2,
  ShoppingBag,
  AlertTriangle,
  ChefHat,
  Sparkles,
  Plus as PlusIcon,
} from 'lucide-react';
import { ROUTES } from '@/constants';
import { formatCurrency } from '@/lib';
import {
  useCart,
  useCartMutations,
  useAuth,
  useCartRecommendations,
  useRecipeSuggestion,
  useAiStatus,
} from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { HealthScore } from '@/components/cart/health-score';
import { AiStatusBanner } from '@/components/system/ai-status-banner';
import { Button, Card, CardContent, Badge, Separator, Skeleton } from '@/components/ui';
import type { BasketRec, RecipeItem, RecipeSuggestion } from '@/types';

export default function CartPage() {
  const router = useRouter();
  const { isAuthenticated, isReady } = useAuth();
  const { data: cart, isLoading } = useCart();
  const { updateItem, removeItem } = useCartMutations();

  if (isReady && !isAuthenticated) {
    return (
      <div className="container py-20 text-center">
        <ShoppingBag className="mx-auto mb-4 h-12 w-12 text-muted-foreground" />
        <p className="mb-4 text-muted-foreground">Sign in to see your cart.</p>
        <Button asChild>
          <Link href={ROUTES.login}>Sign in</Link>
        </Button>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="container grid gap-6 py-8 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-lg" />
          ))}
        </div>
        <Skeleton className="h-72 rounded-lg" />
      </div>
    );
  }

  if (!cart || cart.items.length === 0) {
    return (
      <div className="container py-20 text-center">
        <ShoppingBag className="mx-auto mb-4 h-12 w-12 text-muted-foreground" />
        <p className="mb-4 text-muted-foreground">Your cart is empty.</p>
        <Button asChild>
          <Link href={ROUTES.home}>Start shopping</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="container space-y-10 py-8">
    <div className="grid gap-6 lg:grid-cols-3">
      {/* Line items */}
      <div className="space-y-4 lg:col-span-2">
        <h1 className="text-xl font-semibold">Your cart ({cart.totalUnits})</h1>

        {cart.items.map((item) => {
          const unavailable = cart.unavailableVariantIds.includes(item.variantId);
          return (
            <Card key={item.id}>
              <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:gap-4">
                <div className="flex min-w-0 flex-1 gap-4">
                  <div className="h-16 w-16 shrink-0 overflow-hidden rounded-md bg-muted">
                    {item.imageUrl && (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={item.imageUrl} alt={item.productName} className="h-full w-full object-cover" />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <Link href={ROUTES.product(item.productSlug)} className="line-clamp-1 font-medium hover:underline">
                      {item.productName}
                    </Link>
                    <p className="text-xs text-muted-foreground">{item.variantName}</p>
                    <p className="mt-1 text-sm font-semibold">{formatCurrency(item.unitPrice)}</p>
                    {item.priceChanged && (
                      <Badge variant="warning" className="mt-1">Price changed since you added it</Badge>
                    )}
                    {unavailable && (
                      <span className="mt-1 flex items-center gap-1 text-xs text-destructive">
                        <AlertTriangle className="h-3 w-3" /> Only {item.availableStock} in stock
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex items-center justify-between gap-3 sm:justify-end">
                  <div className="flex items-center gap-1">
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() => updateItem.mutate({ variantId: item.variantId, qty: item.qty - 1 })}
                      disabled={updateItem.isPending}
                    >
                      <Minus className="h-3 w-3" />
                    </Button>
                    <span className="w-8 text-center text-sm font-medium">{item.qty}</span>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() => updateItem.mutate({ variantId: item.variantId, qty: item.qty + 1 })}
                      disabled={updateItem.isPending}
                    >
                      <Plus className="h-3 w-3" />
                    </Button>
                  </div>
                  <div className="w-20 text-right font-semibold">{formatCurrency(item.lineTotal)}</div>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="text-muted-foreground"
                    onClick={() => removeItem.mutate(item.variantId)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* Summary + nutrition */}
      <div className="space-y-4">
        <HealthScore nutrition={cart.nutrition} />
        <Card>
          <CardContent className="space-y-3 p-5">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Subtotal</span>
              <span className="font-medium">{formatCurrency(cart.subtotal)}</span>
            </div>
            <p className="text-xs text-muted-foreground">Taxes and delivery calculated at checkout.</p>
            <Separator />
            <Button
              size="lg"
              className="w-full"
              onClick={() => router.push(ROUTES.checkout)}
              disabled={cart.unavailableVariantIds.length > 0}
            >
              {cart.unavailableVariantIds.length > 0 ? 'Some items unavailable' : 'Proceed to checkout'}
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>

    {/* Cart Intelligence */}
    <RecipeSuggestionPanel />
    <BasketRecommendations />
    </div>
  );
}

/** "Turn my cart into a meal" — AI suggests a dish and lets you add the real, in-stock ingredients. */
function RecipeSuggestionPanel() {
  const { toast } = useToast();
  const { addItem } = useCartMutations();
  const recipe = useRecipeSuggestion();
  const { data: aiStatus, refetch: refetchAiStatus } = useAiStatus();
  const result: RecipeSuggestion | undefined = recipe.data;
  // Remember dishes already shown so "Suggest another" returns something different.
  const [seenDishes, setSeenDishes] = useState<string[]>([]);

  const aiDown = aiStatus && !aiStatus.available;

  const suggest = () => {
    recipe.mutate(
      { includePantry: true, exclude: seenDishes },
      {
        onSuccess: (r) => setSeenDishes((prev) => [...new Set([...prev, r.dish])]),
        // A failure is often the AI hitting its limit — refresh status so the banner shows.
        onError: () => refetchAiStatus(),
      },
    );
  };

  const addIngredient = async (item: RecipeItem) => {
    try {
      await addItem.mutateAsync({ variantId: item.variantId, qty: 1 });
      toast({ variant: 'success', title: 'Added', description: item.productName });
    } catch {
      toast({ variant: 'destructive', title: 'Could not add', description: item.productName });
    }
  };

  return (
    <section>
      <Card className="border-primary/30 bg-primary/5">
        <CardContent className="p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <ChefHat className="h-5 w-5 text-primary" />
              <div>
                <h2 className="font-semibold">Turn your cart into a meal</h2>
                <p className="text-sm text-muted-foreground">
                  Let AI suggest a dish you can cook with what’s in your cart.
                </p>
              </div>
            </div>
            <Button onClick={suggest} disabled={recipe.isPending || aiDown}>
              <Sparkles className="mr-2 h-4 w-4" />
              {recipe.isPending ? 'Thinking…' : result ? 'Suggest another' : 'Suggest a dish'}
            </Button>
          </div>

          <AiStatusBanner className="mt-4" />

          {recipe.isError && !aiDown && (
            <p className="mt-4 text-sm text-destructive">
              Couldn’t generate a suggestion right now. Please try again in a moment.
            </p>
          )}

          {result && (
            <div className="mt-5 space-y-4">
              <div>
                <h3 className="text-lg font-bold text-primary">{result.dish}</h3>
                {result.description && (
                  <p className="text-sm text-muted-foreground">{result.description}</p>
                )}
                {result.usingFromCart.length > 0 && (
                  <p className="mt-1 text-xs text-muted-foreground">
                    Using from your cart: {result.usingFromCart.join(', ')}
                  </p>
                )}
              </div>

              {result.addToCart.length > 0 && (
                <div>
                  <p className="mb-2 text-sm font-medium">Add these to make it:</p>
                  <div className="grid gap-2 sm:grid-cols-2">
                    {result.addToCart.map((item) => (
                      <div
                        key={item.variantId}
                        className="flex items-center justify-between rounded-lg border bg-background px-3 py-2"
                      >
                        <div className="min-w-0">
                          <Link
                            href={ROUTES.product(item.productSlug)}
                            className="line-clamp-1 text-sm font-medium hover:underline"
                          >
                            {item.productName}
                          </Link>
                          <p className="text-xs text-muted-foreground">
                            {formatCurrency(item.price)}
                          </p>
                        </div>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => addIngredient(item)}
                          disabled={addItem.isPending}
                        >
                          <PlusIcon className="mr-1 h-3 w-3" /> Add
                        </Button>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {result.alsoNeed.length > 0 && (
                <p className="text-xs text-muted-foreground">
                  You’ll also need (not sold here): {result.alsoNeed.join(', ')}.
                </p>
              )}
            </div>
          )}
        </CardContent>
      </Card>
    </section>
  );
}

/** "Complete your basket" — complementary in-stock products. */
function BasketRecommendations() {
  const { toast } = useToast();
  const { addItem } = useCartMutations();
  const { data: recs } = useCartRecommendations(6);

  if (!recs || recs.length === 0) return null;

  const add = async (r: BasketRec) => {
    try {
      await addItem.mutateAsync({ variantId: r.variantId, qty: 1 });
      toast({ variant: 'success', title: 'Added', description: r.productName });
    } catch {
      toast({ variant: 'destructive', title: 'Could not add', description: r.productName });
    }
  };

  return (
    <section>
      <h2 className="mb-4 text-lg font-semibold">Complete your basket</h2>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        {recs.map((r) => (
          <Card key={r.variantId} className="flex h-full flex-col overflow-hidden">
            <Link href={ROUTES.product(r.productSlug)} className="block">
              <div className="relative aspect-square bg-muted">
                {r.imageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={r.imageUrl} alt={r.productName} className="h-full w-full object-cover" />
                ) : (
                  <div className="flex h-full items-center justify-center">
                    <ShoppingBag className="h-7 w-7 text-muted-foreground opacity-30" />
                  </div>
                )}
              </div>
            </Link>
            <CardContent className="flex flex-1 flex-col p-3">
              <Link
                href={ROUTES.product(r.productSlug)}
                className="line-clamp-2 text-sm font-medium hover:underline"
              >
                {r.productName}
              </Link>
              <p className="mt-1 font-semibold">{formatCurrency(r.price)}</p>
              <Button
                size="sm"
                variant="outline"
                className="mt-2"
                onClick={() => add(r)}
                disabled={addItem.isPending}
              >
                <PlusIcon className="mr-1 h-3 w-3" /> Add
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>
    </section>
  );
}
