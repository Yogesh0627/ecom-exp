import { CreditCard } from 'lucide-react';
import { PAYMENTS_TEST_MODE } from '@/constants';

/**
 * Shown on checkout / pay screens while payments are in Razorpay TEST mode, so anyone can complete
 * a transaction and watch the order settle to PAID — no real money moves. Hidden once live keys are
 * configured (NEXT_PUBLIC_PAYMENTS_TEST_MODE=false).
 */
export function TestPaymentNote({ className }: { className?: string }) {
  if (!PAYMENTS_TEST_MODE) return null;
  return (
    <div
      className={`flex items-start gap-2 rounded-lg border border-info/40 bg-info/5 p-3 text-xs ${className ?? ''}`}
    >
      <CreditCard className="mt-0.5 h-4 w-4 shrink-0 text-info" />
      <div className="space-y-1 text-muted-foreground">
        <p className="font-medium text-foreground">Test payment mode — no real money is charged</p>
        <p>
          Card <strong className="font-semibold text-foreground">4111 1111 1111 1111</strong>, any
          future expiry, any CVV — then choose <strong className="text-foreground">Success</strong>{' '}
          on the simulated bank page.
        </p>
        <p>
          Or pay by UPI: <strong className="font-semibold text-foreground">success@razorpay</strong>{' '}
          (use <span className="font-mono">failure@razorpay</span> to test a failed payment).
        </p>
      </div>
    </div>
  );
}
