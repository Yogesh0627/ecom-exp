'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { ROUTES } from '@/constants';
import { formatCurrency, openRazorpayCheckout } from '@/lib';
import { paymentApi } from '@/api';
import { Plus, Star } from 'lucide-react';
import { useCart, useAddresses, useAddressMutations, useCheckout, useAuth } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import type { Order } from '@/types';
import { AddressFormDialog } from '@/components/account/address-form-dialog';
import { TestPaymentNote } from '@/components/order/test-payment-note';
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, Input, Label, Separator } from '@/components/ui';

const FREE_SHIP_THRESHOLD = 499;
const DELIVERY_FEE = 40;

export default function CheckoutPage() {
  const router = useRouter();
  const { toast } = useToast();
  const { isAuthenticated, isReady, user } = useAuth();
  const { data: cart } = useCart();
  const [paying, setPaying] = useState(false);
  const { data: addresses } = useAddresses();
  const { setDefault } = useAddressMutations();
  const checkout = useCheckout();

  const [selectedAddress, setSelectedAddress] = useState<string | null>(null);
  const [coupon, setCoupon] = useState('');
  const [addrDialogOpen, setAddrDialogOpen] = useState(false);

  useEffect(() => {
    if (isReady && !isAuthenticated) router.push(ROUTES.login);
  }, [isReady, isAuthenticated, router]);

  useEffect(() => {
    if (addresses && addresses.length > 0 && !selectedAddress) {
      setSelectedAddress(addresses.find((a) => a.isDefault)?.id ?? addresses[0].id);
    }
  }, [addresses, selectedAddress]);

  const subtotal = cart?.subtotal ?? 0;
  const shippingFee = subtotal >= FREE_SHIP_THRESHOLD ? 0 : DELIVERY_FEE;

  /**
   * Opens Razorpay for a freshly-placed order. The order is settled to PAID by the server-side
   * webhook, not here — a successful modal only means "payment submitted". If payments aren't
   * configured yet, the order is still placed and the shopper lands on it as PENDING_PAYMENT.
   */
  const payForOrder = async (order: Order) => {
    try {
      const intent = await paymentApi.createIntent(order.id);
      const resp = await openRazorpayCheckout(intent, {
        name: user?.fullName,
        email: user?.email,
      });
      await paymentApi.verifyCallback(resp).catch(() => undefined);
      toast({
        variant: 'success',
        title: 'Payment received',
        description: "We're confirming your order.",
      });
      router.push(ROUTES.order(order.id));
    } catch (e: unknown) {
      if (e instanceof Error && e.message === 'dismissed') {
        toast({ title: 'Payment cancelled', description: 'You can pay from the order page anytime.' });
        router.push(ROUTES.order(order.id));
        return;
      }
      const status = (e as { response?: { status?: number } })?.response?.status;
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      // Payments not configured (400) → the order still stands; just take them to it.
      if (status === 400) {
        toast({ title: 'Order placed', description: msg ?? 'Complete payment from the order page.' });
      } else {
        toast({ variant: 'destructive', title: 'Payment could not start', description: msg ?? 'Please try again.' });
      }
      router.push(ROUTES.order(order.id));
    }
  };

  const onPlaceOrder = async () => {
    if (!selectedAddress) {
      toast({ variant: 'destructive', title: 'Select a delivery address' });
      return;
    }
    setPaying(true);
    try {
      const order = await checkout.mutateAsync({
        addressId: selectedAddress,
        shippingFee,
        couponCode: coupon.trim() || undefined,
      });
      await payForOrder(order);
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Checkout failed.';
      toast({ variant: 'destructive', title: 'Checkout failed', description: msg });
    } finally {
      setPaying(false);
    }
  };

  if (!cart) return <div className="container py-20 text-center text-muted-foreground">Loading…</div>;

  return (
    <div className="container grid gap-6 py-8 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        {/* Address */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Delivery address</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {addresses?.map((a) => (
              <div
                key={a.id}
                role="button"
                tabIndex={0}
                onClick={() => setSelectedAddress(a.id)}
                onKeyDown={(e) => e.key === 'Enter' && setSelectedAddress(a.id)}
                className={`w-full cursor-pointer rounded-md border p-3 text-left text-sm transition-colors ${
                  selectedAddress === a.id ? 'border-primary bg-accent' : 'hover:border-primary'
                }`}
              >
                <div className="flex items-center gap-2">
                  <span className="font-medium">{a.recipientName}</span>
                  <span className="text-muted-foreground">· {a.phone}</span>
                  {a.isDefault ? (
                    <Badge variant="success" className="ml-auto gap-1">
                      <Star className="h-3 w-3" /> Default
                    </Badge>
                  ) : (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        setDefault.mutate(a.id, {
                          onSuccess: () => toast({ title: 'Default address updated' }),
                        });
                      }}
                      disabled={setDefault.isPending}
                      className="ml-auto text-xs text-primary hover:underline"
                    >
                      Set as default
                    </button>
                  )}
                </div>
                <p className="text-muted-foreground">
                  {a.line1}, {a.city}, {a.state} {a.pincode}
                </p>
              </div>
            ))}

            {addresses && addresses.length === 0 && (
              <p className="text-sm text-muted-foreground">
                No saved addresses — add one to continue.
              </p>
            )}
            <Button variant="outline" size="sm" onClick={() => setAddrDialogOpen(true)}>
              <Plus className="mr-1 h-4 w-4" /> Add a new address
            </Button>
          </CardContent>
        </Card>

        {/* Shared add-address dialog; select the new address on save. */}
        <AddressFormDialog
          open={addrDialogOpen}
          onOpenChange={setAddrDialogOpen}
          onSaved={(addr) => setSelectedAddress(addr.id)}
        />

        {/* Items recap */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Items ({cart.totalUnits})</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {cart.items.map((i) => (
              <div key={i.id} className="flex justify-between text-sm">
                <span className="text-muted-foreground">
                  {i.productName} × {i.qty}
                </span>
                <span>{formatCurrency(i.lineTotal)}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>

      {/* Summary */}
      <div>
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Order summary</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div>
              <Label htmlFor="coupon" className="text-xs">Coupon code</Label>
              <Input
                id="coupon"
                value={coupon}
                onChange={(e) => setCoupon(e.target.value)}
                placeholder="e.g. SAVE20"
                className="mt-1"
              />
            </div>
            <Separator />
            <Row label="Subtotal" value={formatCurrency(subtotal)} />
            <Row
              label="Delivery"
              value={shippingFee === 0 ? 'FREE' : formatCurrency(shippingFee)}
            />
            <p className="text-xs text-muted-foreground">
              Taxes (GST) and any coupon are applied on the confirmed order.
            </p>
            <Separator />
            <Button size="lg" className="w-full" onClick={onPlaceOrder} disabled={checkout.isPending || paying}>
              {checkout.isPending ? 'Placing order…' : paying ? 'Opening payment…' : 'Place order & pay'}
            </Button>
            <TestPaymentNote />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}
