'use client';

import Link from 'next/link';
import { useState } from 'react';
import { CalendarDays, Sparkles, Utensils } from 'lucide-react';
import { ROUTES } from '@/constants';
import { cn } from '@/lib';
import { useGenerateMealPlan, useAuth } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { Button, Card, CardContent, CardHeader, CardTitle, Badge, Skeleton } from '@/components/ui';
import { AiStatusBanner } from '@/components/system/ai-status-banner';
import type { MealGoal, MealPlan } from '@/types';

const GOALS: { value: MealGoal; label: string }[] = [
  { value: 'BALANCED', label: 'Balanced' },
  { value: 'HIGH_PROTEIN', label: 'High protein' },
  { value: 'WEIGHT_LOSS', label: 'Weight loss' },
  { value: 'MUSCLE_GAIN', label: 'Muscle gain' },
  { value: 'DIABETIC_FRIENDLY', label: 'Diabetic-friendly' },
  { value: 'KIDS', label: 'For kids' },
];

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const MEAL_ORDER = ['BREAKFAST', 'LUNCH', 'SNACK', 'DINNER'] as const;

export default function MealPlannerPage() {
  const { isAuthenticated, isReady } = useAuth();
  const { toast } = useToast();
  const generate = useGenerateMealPlan();
  const [goal, setGoal] = useState<MealGoal>('BALANCED');
  const [plan, setPlan] = useState<MealPlan | null>(null);

  const onGenerate = async () => {
    try {
      const result = await generate.mutateAsync({ goal });
      setPlan(result);
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'The meal planner is unavailable right now.';
      toast({ variant: 'destructive', title: 'Could not generate plan', description: msg });
    }
  };

  if (isReady && !isAuthenticated) {
    return (
      <div className="container py-20 text-center">
        <p className="mb-4 text-muted-foreground">Sign in to use the AI meal planner.</p>
        <Button asChild>
          <Link href={ROUTES.login}>Sign in</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="container space-y-8 py-8">
      <AiStatusBanner />
      <div className="space-y-2">
        <div className="inline-flex items-center gap-2 rounded-full bg-accent px-3 py-1 text-xs font-medium text-accent-foreground">
          <Sparkles className="h-3.5 w-3.5" /> AI-powered
        </div>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <CalendarDays className="h-6 w-6 text-primary" /> Weekly meal planner
        </h1>
        <p className="text-muted-foreground">
          Pick a goal and get a 7-day plan built around what you already have in your pantry.
        </p>
      </div>

      {/* Goal picker */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Your goal for the week</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap gap-2">
            {GOALS.map((g) => (
              <button
                key={g.value}
                onClick={() => setGoal(g.value)}
                className={cn(
                  'rounded-md border px-4 py-2 text-sm transition-colors',
                  goal === g.value
                    ? 'border-primary bg-primary text-primary-foreground'
                    : 'hover:border-primary',
                )}
              >
                {g.label}
              </button>
            ))}
          </div>
          <Button onClick={onGenerate} disabled={generate.isPending} size="lg">
            <Sparkles className="mr-2 h-4 w-4" />
            {generate.isPending ? 'Generating your week…' : 'Generate meal plan'}
          </Button>
        </CardContent>
      </Card>

      {generate.isPending && (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-lg" />
          ))}
        </div>
      )}

      {/* Plan grid */}
      {plan && !generate.isPending && (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold">Your week</h2>
            <Badge variant="secondary">{plan.goal.replace(/_/g, ' ').toLowerCase()}</Badge>
            <Badge variant="outline">{plan.entryCount} meals</Badge>
          </div>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
            {DAYS.map((dayLabel, idx) => {
              const dayNum = idx + 1;
              const meals = plan.meals
                .filter((m) => m.day === dayNum)
                .sort((a, b) => MEAL_ORDER.indexOf(a.mealType) - MEAL_ORDER.indexOf(b.mealType));
              return (
                <Card key={dayNum}>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm">{dayLabel}</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2">
                    {meals.map((m, i) => (
                      <div key={i} className="text-sm">
                        <div className="flex items-center gap-1 text-xs font-medium text-muted-foreground">
                          <Utensils className="h-3 w-3" />
                          {m.mealType.charAt(0) + m.mealType.slice(1).toLowerCase()}
                        </div>
                        <p>{m.title}</p>
                      </div>
                    ))}
                  </CardContent>
                </Card>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
