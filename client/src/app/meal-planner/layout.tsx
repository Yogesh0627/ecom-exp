import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'AI Meal Planner',
  description:
    'Get a personalised 7-day meal plan built around your goals and what you already have in your pantry — powered by AI, shoppable in one tap.',
  alternates: { canonical: '/meal-planner' },
  openGraph: {
    title: 'AI Meal Planner · EcoExpress',
    description: 'A personalised weekly meal plan, built around your goals and your pantry.',
    url: '/meal-planner',
  },
};

export default function MealPlannerLayout({ children }: { children: React.ReactNode }) {
  return children;
}
