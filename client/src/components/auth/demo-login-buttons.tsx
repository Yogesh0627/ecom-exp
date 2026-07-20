'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { ShieldCheck, UserRound } from 'lucide-react';
import { ROUTES, DEMO_ACCOUNTS } from '@/constants';
import { useAuth } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { Button } from '@/components/ui';

/**
 * Dev/demo shortcut: sign in as the bootstrap admin or a demo customer in one click. Only rendered
 * when DEMO_LOGIN_ENABLED (dev, or an explicit prod opt-in). The customer account self-provisions
 * on first use — if the login fails because it doesn't exist yet, we register it (which logs in).
 */
export function DemoLoginButtons() {
  const router = useRouter();
  const { login, register } = useAuth();
  const { toast } = useToast();
  const [busy, setBusy] = useState<null | 'admin' | 'user'>(null);

  const asAdmin = async () => {
    setBusy('admin');
    try {
      await login(DEMO_ACCOUNTS.admin);
      router.push(ROUTES.admin.dashboard);
    } catch {
      toast({
        variant: 'destructive',
        title: 'Admin demo login failed',
        description: 'Is the backend running with the bootstrap admin configured?',
      });
    } finally {
      setBusy(null);
    }
  };

  const asUser = async () => {
    setBusy('user');
    const { email, password, fullName } = DEMO_ACCOUNTS.user;
    try {
      try {
        await login({ email, password });
      } catch {
        // First run — the demo customer doesn't exist yet. Creating it also signs them in.
        await register({ email, password, fullName });
      }
      router.push(ROUTES.home);
    } catch {
      toast({
        variant: 'destructive',
        title: 'User demo login failed',
        description: 'Could not sign in or create the demo account.',
      });
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-2">
      <p className="text-center text-xs font-medium uppercase tracking-wide text-muted-foreground">
        Quick demo login
      </p>
      <div className="grid grid-cols-2 gap-2">
        <Button variant="outline" size="sm" onClick={asAdmin} disabled={busy !== null}>
          <ShieldCheck className="mr-1.5 h-4 w-4" />
          {busy === 'admin' ? 'Signing in…' : 'As Admin'}
        </Button>
        <Button variant="outline" size="sm" onClick={asUser} disabled={busy !== null}>
          <UserRound className="mr-1.5 h-4 w-4" />
          {busy === 'user' ? 'Signing in…' : 'As User'}
        </Button>
      </div>
      <p className="text-center text-xs text-muted-foreground">
        Explore the full app instantly — no signup needed.
      </p>
    </div>
  );
}
