'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import {
  User as UserIcon,
  Mail,
  Phone,
  MapPin,
  Plus,
  Pencil,
  Trash2,
  Star,
  CheckCircle2,
  ShieldAlert,
  Package,
} from 'lucide-react';
import { ROUTES } from '@/constants';
import {
  useAuth,
  useProfile,
  useUpdateProfile,
  useChangeEmail,
  useCancelEmailChange,
  useResendVerification,
  useAddresses,
  useAddressMutations,
} from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import type { Address } from '@/types';
import { AddressFormDialog } from '@/components/account/address-form-dialog';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Input,
  Label,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  Skeleton,
} from '@/components/ui';

export default function AccountPage() {
  const { isAuthenticated, isReady } = useAuth();

  if (isReady && !isAuthenticated) {
    return (
      <div className="container py-20 text-center">
        <UserIcon className="mx-auto mb-4 h-12 w-12 text-muted-foreground" />
        <p className="mb-4 text-muted-foreground">Sign in to manage your account.</p>
        <Button asChild>
          <Link href={ROUTES.login}>Sign in</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="container max-w-3xl space-y-8 py-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">My account</h1>
        <Button asChild variant="outline">
          <Link href={ROUTES.orders}>
            <Package className="mr-2 h-4 w-4" /> My orders
          </Link>
        </Button>
      </div>
      <ProfileSection />
      <AddressesSection />
    </div>
  );
}

/* ------------------------------------------------------------------ Profile */

function ProfileSection() {
  const { data: profile, isLoading } = useProfile();
  const updateProfile = useUpdateProfile();
  const changeEmail = useChangeEmail();
  const cancelEmailChange = useCancelEmailChange();
  const resend = useResendVerification();
  const { toast } = useToast();

  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [emailOpen, setEmailOpen] = useState(false);
  const [newEmail, setNewEmail] = useState('');

  useEffect(() => {
    if (profile) {
      setFullName(profile.fullName ?? '');
      setPhone(profile.phone ?? '');
    }
  }, [profile]);

  if (isLoading || !profile) {
    return <Skeleton className="h-64 rounded-lg" />;
  }

  const dirty = fullName !== (profile.fullName ?? '') || phone !== (profile.phone ?? '');

  const saveProfile = async () => {
    try {
      await updateProfile.mutateAsync({ fullName, phone: phone || undefined });
      toast({ variant: 'success', title: 'Profile updated' });
    } catch (e: unknown) {
      toast({ variant: 'destructive', title: 'Could not save', description: errMsg(e) });
    }
  };

  const submitEmailChange = async () => {
    try {
      await changeEmail.mutateAsync({ newEmail: newEmail.trim() });
      setEmailOpen(false);
      setNewEmail('');
      toast({
        variant: 'success',
        title: 'Confirmation sent',
        description: `Check ${newEmail} for a link to confirm the change.`,
      });
    } catch (e: unknown) {
      toast({ variant: 'destructive', title: 'Could not change email', description: errMsg(e) });
    }
  };

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">Profile</CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        {/* Email + verification */}
        <div className="rounded-lg border bg-muted/30 p-4">
          <div className="flex flex-wrap items-center gap-2">
            <Mail className="h-4 w-4 text-muted-foreground" />
            <span className="font-medium">{profile.email}</span>
            {profile.emailVerified ? (
              <Badge variant="success" className="gap-1">
                <CheckCircle2 className="h-3 w-3" /> Verified
              </Badge>
            ) : (
              <Badge variant="warning" className="gap-1">
                <ShieldAlert className="h-3 w-3" /> Unverified
              </Badge>
            )}
            {!profile.oauthOnly && (
              <Button
                size="sm"
                variant="outline"
                className="ml-auto"
                onClick={() => setEmailOpen(true)}
              >
                Change email
              </Button>
            )}
          </div>
          {profile.pendingEmail && (
            <p className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              <span>
                Pending change to <strong>{profile.pendingEmail}</strong> — confirm via the emailed
                link to activate it.
              </span>
              <button
                onClick={() =>
                  cancelEmailChange.mutate(undefined, {
                    onSuccess: () => toast({ title: 'Email change cancelled' }),
                  })
                }
                disabled={cancelEmailChange.isPending}
                className="text-destructive hover:underline"
              >
                Cancel
              </button>
            </p>
          )}
          {!profile.emailVerified && !profile.oauthOnly && (
            <button
              onClick={() =>
                resend.mutate(undefined, {
                  onSuccess: () => toast({ title: 'Verification email sent' }),
                })
              }
              disabled={resend.isPending}
              className="mt-2 block text-xs text-primary hover:underline"
            >
              Resend verification email
            </button>
          )}
          {profile.oauthOnly && (
            <p className="mt-2 text-xs text-muted-foreground">
              You sign in with Google — your email is managed there.
            </p>
          )}
        </div>

        {/* Name */}
        <div>
          <Label htmlFor="fullName">Full name</Label>
          <div className="relative mt-1">
            <UserIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input id="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} className="pl-9" />
          </div>
        </div>

        {/* Phone */}
        <div>
          <Label htmlFor="phone">Mobile number</Label>
          <div className="relative mt-1">
            <Phone className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="phone"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="10-digit Indian mobile"
              className="pl-9"
            />
          </div>
        </div>

        <Button onClick={saveProfile} disabled={!dirty || updateProfile.isPending}>
          {updateProfile.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </CardContent>

      {/* Change email dialog */}
      <Dialog open={emailOpen} onOpenChange={setEmailOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Change email</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            We’ll email a confirmation link to the new address. Your current email stays active until
            you click it.
          </p>
          <div>
            <Label htmlFor="newEmail">New email</Label>
            <Input
              id="newEmail"
              type="email"
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
              className="mt-1"
            />
          </div>
          <DialogFooter>
            <Button onClick={submitEmailChange} disabled={!newEmail.trim() || changeEmail.isPending}>
              {changeEmail.isPending ? 'Sending…' : 'Send confirmation'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

/* ---------------------------------------------------------------- Addresses */

function AddressesSection() {
  const { data: addresses, isLoading } = useAddresses();
  const { remove, setDefault } = useAddressMutations();
  const { toast } = useToast();

  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Address | null>(null);

  const openNew = () => {
    setEditing(null);
    setOpen(true);
  };
  const openEdit = (a: Address) => {
    setEditing(a);
    setOpen(true);
  };

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between pb-3">
        <CardTitle className="text-base">Saved addresses</CardTitle>
        <Button size="sm" onClick={openNew}>
          <Plus className="mr-1 h-4 w-4" /> Add address
        </Button>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-24 rounded-lg" />
        ) : !addresses || addresses.length === 0 ? (
          <p className="text-sm text-muted-foreground">No saved addresses yet.</p>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {addresses.map((a) => (
              <div key={a.id} className="relative rounded-lg border p-4">
                <div className="mb-1 flex items-center gap-2">
                  <MapPin className="h-4 w-4 text-muted-foreground" />
                  <span className="font-medium">{a.label || a.type}</span>
                  {a.isDefault && (
                    <Badge variant="success" className="gap-1">
                      <Star className="h-3 w-3" /> Default
                    </Badge>
                  )}
                </div>
                <p className="text-sm">
                  {a.recipientName} · {a.phone}
                </p>
                <p className="text-sm text-muted-foreground">
                  {a.line1}
                  {a.line2 ? `, ${a.line2}` : ''}, {a.city}, {a.state} {a.pincode}
                </p>
                <div className="mt-3 flex flex-wrap gap-2">
                  {!a.isDefault && (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() =>
                        setDefault.mutate(a.id, {
                          onSuccess: () => toast({ title: 'Default address updated' }),
                        })
                      }
                      disabled={setDefault.isPending}
                    >
                      <Star className="mr-1 h-3 w-3" /> Set default
                    </Button>
                  )}
                  <Button size="sm" variant="outline" onClick={() => openEdit(a)}>
                    <Pencil className="mr-1 h-3 w-3" /> Edit
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    className="text-muted-foreground"
                    onClick={() =>
                      remove.mutate(a.id, {
                        onSuccess: () => toast({ title: 'Address removed' }),
                      })
                    }
                    disabled={remove.isPending}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>

      <AddressFormDialog open={open} onOpenChange={setOpen} initial={editing} />
    </Card>
  );
}

function errMsg(e: unknown, fallback = 'Something went wrong.'): string {
  return (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback;
}
