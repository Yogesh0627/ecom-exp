'use client';

import { Suspense, useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Leaf, Loader2 } from 'lucide-react';
import { ROUTES } from '@/constants';
import { useAuth } from '@/hooks';
import { useToast } from '@/hooks/use-toast';

function CallbackInner() {
  const router = useRouter();
  const params = useSearchParams();
  const { completeOAuthLogin } = useAuth();
  const { toast } = useToast();
  const [failed, setFailed] = useState(false);
  // Exchange the code exactly once, even under React strict-mode double effects.
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    const code = params.get('code');
    const error = params.get('error');
    if (error || !code) {
      toast({ variant: 'destructive', title: 'Google sign-in failed', description: 'Please try again.' });
      router.replace(ROUTES.login);
      return;
    }

    completeOAuthLogin(code)
      .then(() => router.replace(ROUTES.home))
      .catch(() => {
        setFailed(true);
        toast({
          variant: 'destructive',
          title: 'Sign-in link expired',
          description: 'Please sign in again.',
        });
        setTimeout(() => router.replace(ROUTES.login), 1500);
      });
  }, [params, completeOAuthLogin, router, toast]);

  return (
    <div className="container flex min-h-[70vh] flex-col items-center justify-center gap-3 py-8 text-center">
      <Leaf className="h-8 w-8 text-primary" />
      {failed ? (
        <p className="text-muted-foreground">Redirecting you back to sign in…</p>
      ) : (
        <p className="flex items-center gap-2 text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Completing your sign-in…
        </p>
      )}
    </div>
  );
}

export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={<div className="container py-20 text-center text-muted-foreground">Loading…</div>}>
      <CallbackInner />
    </Suspense>
  );
}
