'use client';

import { Sparkles, IndianRupee, Activity } from 'lucide-react';
import { useAiSpend } from '@/hooks';
import { formatCurrency, formatNumber } from '@/lib';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '@/components/ui';
import { AdminPageHeader, StatCard, DataState, statusLabel } from '@/components/admin';

export default function AdminAiSpendPage() {
  const { data, isLoading, error } = useAiSpend();

  const budget = data?.budgetInr ?? null;
  const spend = data?.monthSpendInr ?? 0;
  const pct = budget && budget > 0 ? Math.min(100, Math.round((spend / budget) * 100)) : null;

  return (
    <div>
      <AdminPageHeader
        title="AI spend"
        description="Month-to-date Gemini usage and cost, metered on every call."
      />

      <DataState isLoading={isLoading} error={error} />

      {data && (
        <div className="space-y-8">
          <div className="grid gap-4 sm:grid-cols-3">
            <StatCard
              label="This month"
              value={formatCurrency(spend)}
              icon={IndianRupee}
              hint={budget != null ? `of ${formatCurrency(budget)} budget` : 'no budget set'}
              tone={pct != null && pct >= 80 ? 'warning' : 'default'}
            />
            <StatCard label="Total AI calls" value={formatNumber(data.totalCalls)} icon={Activity} />
            <StatCard
              label="Budget used"
              value={pct != null ? `${pct}%` : '—'}
              icon={Sparkles}
              tone={pct != null && pct >= 80 ? 'destructive' : 'success'}
            />
          </div>

          {pct != null && (
            <div>
              <div className="mb-1 flex justify-between text-xs text-muted-foreground">
                <span>Budget usage</span>
                <span>{pct}%</span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                <div
                  className={pct >= 80 ? 'h-full bg-destructive' : 'h-full bg-primary'}
                  style={{ width: `${pct}%` }}
                />
              </div>
            </div>
          )}

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">By feature</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              {data.byFeature.length === 0 ? (
                <p className="p-5 text-sm text-muted-foreground">No AI calls yet this month.</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Feature</TableHead>
                      <TableHead className="text-right">Calls</TableHead>
                      <TableHead className="text-right">Tokens in</TableHead>
                      <TableHead className="text-right">Tokens out</TableHead>
                      <TableHead className="text-right">Cost</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.byFeature.map((f) => (
                      <TableRow key={f.feature}>
                        <TableCell className="capitalize">{statusLabel(f.feature)}</TableCell>
                        <TableCell className="text-right tabular-nums">{formatNumber(f.calls)}</TableCell>
                        <TableCell className="text-right tabular-nums">{formatNumber(f.tokensIn)}</TableCell>
                        <TableCell className="text-right tabular-nums">{formatNumber(f.tokensOut)}</TableCell>
                        <TableCell className="text-right tabular-nums">{formatCurrency(f.costInr)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
