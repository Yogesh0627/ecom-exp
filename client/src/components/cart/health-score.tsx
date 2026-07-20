import { AlertTriangle, Info, HeartPulse } from 'lucide-react';
import { cn, healthScoreBand } from '@/lib';
import { Card, CardContent, CardHeader, CardTitle, Badge, Separator } from '@/components/ui';
import type { NutritionSummary } from '@/types';

/**
 * Smart Cart Nutrition panel (PRD §5.2). Faithfully reflects the backend's honesty rule: when the
 * basket has items with no nutrition data, there is NO score — we show why, not a fabricated
 * number. Warnings and the per-serving-ish totals come straight from the API.
 */
export function HealthScore({ nutrition }: { nutrition: NutritionSummary }) {
  const band = healthScoreBand(nutrition.healthScore);

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <HeartPulse className="h-5 w-5 text-primary" />
          Smart Cart Nutrition
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {nutrition.healthScore !== null ? (
          <div className="flex items-center gap-4">
            <div
              className={cn(
                'flex h-16 w-16 shrink-0 items-center justify-center rounded-full border-4 text-xl font-bold',
                band.className,
              )}
              style={{ borderColor: 'currentColor' }}
            >
              {nutrition.healthScore}
            </div>
            <div>
              <p className={cn('font-semibold', band.className)}>{band.label}</p>
              <p className="text-xs text-muted-foreground">Basket health score (0–100)</p>
            </div>
          </div>
        ) : (
          <div className="flex items-start gap-3 rounded-md bg-muted p-3">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">
              Not scored yet — {nutrition.linesMissingData.length} item(s) have no nutrition data,
              so totals are a lower bound.
            </p>
          </div>
        )}

        {nutrition.warnings.length > 0 && (
          <>
            <Separator />
            <div className="space-y-2">
              {nutrition.warnings.map((w, i) => (
                <div key={i} className="flex items-start gap-2 text-sm">
                  <AlertTriangle
                    className={cn(
                      'mt-0.5 h-4 w-4 shrink-0',
                      w.level === 'HIGH' ? 'text-destructive' : 'text-warning',
                    )}
                  />
                  <span>{w.message}</span>
                </div>
              ))}
            </div>
          </>
        )}

        {nutrition.totals.caloriesKcal !== null && (
          <>
            <Separator />
            <div className="grid grid-cols-2 gap-2 text-sm">
              <Macro label="Calories" value={nutrition.totals.caloriesKcal} unit="kcal" />
              <Macro label="Protein" value={nutrition.totals.proteinG} unit="g" />
              <Macro label="Carbs" value={nutrition.totals.carbohydratesG} unit="g" />
              <Macro label="Fat" value={nutrition.totals.fatG} unit="g" />
              <Macro label="Fibre" value={nutrition.totals.fiberG} unit="g" />
              <Macro label="Sugar" value={nutrition.totals.sugarG} unit="g" />
            </div>
          </>
        )}

        {!nutrition.complete && (
          <Badge variant="warning" className="w-full justify-center">
            Partial data — some items are missing nutrition
          </Badge>
        )}
      </CardContent>
    </Card>
  );
}

function Macro({ label, value, unit }: { label: string; value: number | null; unit: string }) {
  return (
    <div className="flex justify-between rounded bg-muted/50 px-2 py-1">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value === null ? '—' : `${value} ${unit}`}</span>
    </div>
  );
}
