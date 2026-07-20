'use client';

import Link from 'next/link';
import { Suspense, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { CheckCircle2, XCircle, Loader2, Leaf } from 'lucide-react';
import { ROUTES } from '@/constants';
import { authApi } from '@/api';
import { Button, Card, CardContent } from '@/components/ui';

type State = 'verifying' | 'ok' | 'error';

function VerifyInner() {
  const params = useSearchParams();
  const [state, setState] = useState<State>('verifying');
  const [message, setMessage] = useState('');
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    const token = params.get('token');
    if (!token) {
      setState('error');
      setMessage('This link is missing its verification token.');
      return;
    }
    authApi
      .verifyEmail(token)
      .then(() => setState('ok'))
      .catch((e: unknown) => {
        setState('error');
        setMessage(
          (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
            'This verification link is invalid or has expired.',
        );
      });
  }, [params]);

  return (
    <div className="container flex min-h-[70vh] items-center justify-center py-8">
      <Card className="w-full max-w-sm">
        <CardContent className="flex flex-col items-center gap-3 p-8 text-center">
          {state === 'verifying' && (
            <>
              <Loader2 className="h-8 w-8 animate-spin text-primary" />
              <p className="text-muted-foreground">Verifying your email…</p>
            </>
          )}
          {state === 'ok' && (
            <>
              <CheckCircle2 className="h-10 w-10 text-success" />
              <h1 className="text-lg font-semibold">Email verified</h1>
              <p className="text-sm text-muted-foreground">Your account is all set.</p>
              <Button asChild className="mt-2">
                <Link href={ROUTES.home}>Start shopping</Link>
              </Button>
            </>
          )}
          {state === 'error' && (
            <>
              <XCircle className="h-10 w-10 text-destructive" />
              <h1 className="text-lg font-semibold">Couldn&apos;t verify</h1>
              <p className="text-sm text-muted-foreground">{message}</p>
              <Button asChild variant="outline" className="mt-2">
                <Link href={ROUTES.home}>Back to store</Link>
              </Button>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense
      fallback={
        <div className="container flex min-h-[70vh] items-center justify-center">
          <Leaf className="h-8 w-8 text-primary" />
        </div>
      }
    >
      <VerifyInner />
    </Suspense>
  );
}
