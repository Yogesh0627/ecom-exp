'use client';

import { useEffect, useState } from 'react';
import { useTheme } from 'next-themes';
import { Sun, Moon, Monitor } from 'lucide-react';
import { Button, Tooltip } from '@/components/ui';

// Cycle order: system → light → dark → system. One click advances one step.
const ORDER = ['system', 'light', 'dark'] as const;
type ThemeChoice = (typeof ORDER)[number];

const META: Record<ThemeChoice, { icon: typeof Sun; label: string }> = {
  system: { icon: Monitor, label: 'System theme' },
  light: { icon: Sun, label: 'Light theme' },
  dark: { icon: Moon, label: 'Dark theme' },
};

export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  // Theme is only known on the client — render a stable placeholder until mounted to avoid a
  // hydration mismatch and an icon flash.
  useEffect(() => setMounted(true), []);

  if (!mounted) {
    return (
      <Button variant="ghost" size="icon" aria-label="Toggle theme" disabled>
        <Monitor className="h-5 w-5" />
      </Button>
    );
  }

  const current = (ORDER.includes(theme as ThemeChoice) ? theme : 'system') as ThemeChoice;
  const next = ORDER[(ORDER.indexOf(current) + 1) % ORDER.length];
  const Icon = META[current].icon;

  return (

    <Tooltip label={META[current].label}>
    <Button
      variant="ghost"
      size="icon"
      onClick={() => setTheme(next)}
      aria-label={`${META[current].label} — switch to ${META[next].label.toLowerCase()}`}
      // title={META[current].label}
    >
      <Icon className="h-5 w-5" />
    </Button>
    </Tooltip>
  );
}
