'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Sparkles, Leaf, Truck, Salad } from 'lucide-react';
import { ROUTES } from '@/constants';
import { Button } from '@/components/ui';

interface Slide {
  eyebrow: string;
  icon: typeof Leaf;
  title: string;
  subtitle: string;
  cta: string;
  href: string;
  /** Tailwind gradient classes for the slide background. */
  gradient: string;
  /** Decorative produce emoji scattered in the background. */
  mascots: string[];
}

const SLIDES: Slide[] = [
  {
    eyebrow: 'AI-assisted organic shopping',
    icon: Sparkles,
    title: 'Fresh organic groceries, intelligently delivered',
    subtitle: 'Smart Cart nutrition scoring, AI meal planning, and farm-fresh produce — all in one place.',
    cta: 'Shop now',
    href: ROUTES.search(''),
    gradient: 'from-primary/90 to-primary',
    mascots: ['🥬', '🥕', '🍅', '🥦', '🌽'],
  },
  {
    eyebrow: 'Certified organic',
    icon: Leaf,
    title: 'Every product, backed by real certificates',
    subtitle: 'NPOP & Jaivik Bharat certified. Tap any product to see the proof behind “100% organic”.',
    cta: 'Browse produce',
    href: ROUTES.category('fresh-vegetables'),
    gradient: 'from-emerald-600 to-green-700',
    mascots: ['🥭', '🍌', '🍎', '🍇', '🥑'],
  },
  {
    eyebrow: 'Turn your cart into a meal',
    icon: Salad,
    title: 'Let AI plan your week of healthy meals',
    subtitle: 'Add what you love — our AI suggests dishes and the exact ingredients to complete them.',
    cta: 'Try the meal planner',
    href: ROUTES.mealPlanner,
    gradient: 'from-teal-600 to-emerald-700',
    mascots: ['🍆', '🫑', '🧄', '🧅', '🌶️'],
  },
  {
    eyebrow: 'Free delivery over ₹499',
    icon: Truck,
    title: 'Farm-fresh, at your door in hours',
    subtitle: 'Cold-chain delivery keeps produce crisp. Free above ₹499 across the city.',
    cta: 'Start shopping',
    href: ROUTES.search(''),
    gradient: 'from-lime-600 to-green-700',
    mascots: ['🥔', '🫘', '🌾', '🥒', '🍚'],
  },
];

const INTERVAL_MS = 5500;

export function HeroCarousel() {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);

  const go = useCallback((next: number) => {
    setIndex((prev) => (next + SLIDES.length) % SLIDES.length);
  }, []);

  useEffect(() => {
    if (paused) return;
    const id = setInterval(() => setIndex((p) => (p + 1) % SLIDES.length), INTERVAL_MS);
    return () => clearInterval(id);
  }, [paused]);

  return (
    <section
      className="relative overflow-hidden rounded-2xl"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      aria-roledescription="carousel"
    >
      <div className="relative h-[280px] sm:h-[320px]">
        {SLIDES.map((slide, i) => (
          <div
            key={slide.title}
            className={`absolute inset-0 bg-gradient-to-br ${slide.gradient} p-8 text-white transition-opacity duration-700 sm:p-12 ${
              i === index ? 'opacity-100' : 'pointer-events-none opacity-0'
            }`}
            aria-hidden={i !== index}
          >
            {/* Mascots — decorative produce scattered in the background */}
            <div aria-hidden className="pointer-events-none absolute inset-0 select-none overflow-hidden">
              {slide.mascots.map((m, j) => (
                <span
                  key={j}
                  className="absolute opacity-15"
                  style={{
                    fontSize: `${[7, 5, 8, 4.5, 6][j % 5]}rem`,
                    top: `${[8, 55, 18, 62, 35][j % 5]}%`,
                    right: `${[6, 20, 40, 4, 30][j % 5]}%`,
                    transform: `rotate(${[-12, 10, -6, 14, 4][j % 5]}deg)`,
                  }}
                >
                  {m}
                </span>
              ))}
            </div>

            <div className="relative max-w-xl space-y-4">
              <div className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-medium backdrop-blur">
                <slide.icon className="h-3.5 w-3.5" /> {slide.eyebrow}
              </div>
              <h1 className="text-3xl font-bold leading-tight sm:text-4xl">{slide.title}</h1>
              <p className="text-white/85">{slide.subtitle}</p>
              <Button asChild variant="secondary" size="lg">
                <Link href={slide.href}>{slide.cta}</Link>
              </Button>
            </div>
          </div>
        ))}
      </div>

      {/* Arrows */}
      <button
        onClick={() => go(index - 1)}
        aria-label="Previous slide"
        className="absolute left-3 top-1/2 hidden -translate-y-1/2 rounded-full bg-white/20 p-2 text-white backdrop-blur transition-colors hover:bg-white/35 sm:block"
      >
        <ChevronLeft className="h-5 w-5" />
      </button>
      <button
        onClick={() => go(index + 1)}
        aria-label="Next slide"
        className="absolute right-3 top-1/2 hidden -translate-y-1/2 rounded-full bg-white/20 p-2 text-white backdrop-blur transition-colors hover:bg-white/35 sm:block"
      >
        <ChevronRight className="h-5 w-5" />
      </button>

      {/* Dots */}
      <div className="absolute bottom-4 left-1/2 flex -translate-x-1/2 gap-2">
        {SLIDES.map((s, i) => (
          <button
            key={s.title}
            onClick={() => setIndex(i)}
            aria-label={`Go to slide ${i + 1}`}
            className={`h-2 rounded-full transition-all ${
              i === index ? 'w-6 bg-white' : 'w-2 bg-white/50 hover:bg-white/80'
            }`}
          />
        ))}
      </div>
    </section>
  );
}
