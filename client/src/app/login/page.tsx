'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Leaf } from 'lucide-react';
import { ROUTES, APP_NAME, DEMO_LOGIN_ENABLED } from '@/constants';
import { useAuth } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { Button, Card, CardContent, CardHeader, CardTitle, Input, Label } from '@/components/ui';
import { GoogleSignInButton } from '@/components/auth/google-sign-in-button';
import { DemoLoginButtons } from '@/components/auth/demo-login-buttons';

export default function LoginPage() {
  const router = useRouter();
  const { toast } = useToast();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await login({ email, password });
      router.push(ROUTES.home);
    } catch {
      // The backend returns the same message for wrong password vs unknown user (no enumeration).
      toast({ variant: 'destructive', title: 'Sign in failed', description: 'Invalid email or password.' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="container flex min-h-[70vh] items-center justify-center py-8">
      <Card className="w-full max-w-sm">
        <CardHeader className="items-center text-center">
          <Leaf className="h-8 w-8 text-primary" />
          <CardTitle>Sign in to {APP_NAME}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="space-y-4">
            <div>
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required className="mt-1" />
            </div>
            <div>
              <Label htmlFor="password">Password</Label>
              <Input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required className="mt-1" />
            </div>
            <Button type="submit" className="w-full" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>

          <div className="my-4 flex items-center gap-3 text-xs text-muted-foreground">
            <span className="h-px flex-1 bg-border" />
            or
            <span className="h-px flex-1 bg-border" />
          </div>
          <GoogleSignInButton />

          {DEMO_LOGIN_ENABLED && (
            <>
              <div className="my-4 flex items-center gap-3 text-xs text-muted-foreground">
                <span className="h-px flex-1 bg-border" />
                or
                <span className="h-px flex-1 bg-border" />
              </div>
              <DemoLoginButtons />
            </>
          )}

          <p className="mt-4 text-center text-sm text-muted-foreground">
            New here?{' '}
            <Link href={ROUTES.register} className="text-primary hover:underline">Create an account</Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
