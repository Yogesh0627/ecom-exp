'use client';

import Marquee from 'react-fast-marquee';
import { Star, Quote } from 'lucide-react';
import { Card, CardContent } from '@/components/ui';

interface Testimonial {
  name: string;
  city: string;
  avatar: string;
  rating: number;
  quote: string;
}

const TESTIMONIALS: Testimonial[] = [
  {
    name: 'Priya Sharma',
    city: 'Bengaluru',
    avatar: '👩🏽',
    rating: 5,
    quote:
      'The produce genuinely tastes fresher, and I love that every organic claim has a certificate I can actually open. The nutrition scores changed how I shop.',
  },
  {
    name: 'Rahul Verma',
    city: 'Pune',
    avatar: '🧑🏻',
    rating: 5,
    quote:
      'The “turn my cart into a meal” feature is brilliant — I added paneer and spinach and it planned palak paneer with everything I still needed. Ordered in one tap.',
  },
  {
    name: 'Ananya Iyer',
    city: 'Hyderabad',
    avatar: '👩🏾',
    rating: 4,
    quote:
      'Deliveries are quick and the pantry tracker means I stop over-buying. The weekly meal planner keeps our family eating balanced without any effort.',
  },
  {
    name: 'Kabir Singh',
    city: 'Delhi',
    avatar: '🧔🏽',
    rating: 5,
    quote:
      'Finally an organic store that proves it. The smart fridge scan added everything to my list in seconds — this feels years ahead of other grocery apps.',
  },
  {
    name: 'Meera Nair',
    city: 'Kochi',
    avatar: '👩🏻',
    rating: 5,
    quote:
      'Fresh, well-packed, and on time every single week. The health score on my cart nudged me toward better choices without any lecturing.',
  },
  {
    name: 'Arjun Reddy',
    city: 'Chennai',
    avatar: '🧑🏾',
    rating: 4,
    quote:
      'Love the transparency — real certificates, clear nutrition, honest recommendations. My family eats more veggies now thanks to the meal planner.',
  },
];

function TestimonialCard({ name, city, avatar, rating, quote }: Testimonial) {
  return (
    <Card className="mx-3 h-full w-80 shrink-0">
      <CardContent className="flex h-full flex-col gap-3 p-5">
        <Quote className="h-6 w-6 text-primary/30" />
        <p className="flex-1 text-sm leading-relaxed text-muted-foreground">“{quote}”</p>
        <div className="flex">
          {Array.from({ length: 5 }).map((_, i) => (
            <Star
              key={i}
              className={`h-4 w-4 ${i < rating ? 'fill-rating text-rating' : 'text-muted'}`}
            />
          ))}
        </div>
        <div className="flex items-center gap-3 border-t pt-3">
          <span className="flex h-9 w-9 items-center justify-center rounded-full bg-muted text-lg">
            {avatar}
          </span>
          <div>
            <p className="text-sm font-medium">{name}</p>
            <p className="text-xs text-muted-foreground">{city}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

export function Testimonials() {
  return (
    <section aria-labelledby="testimonials-heading">
      <div className="mb-4 flex items-end justify-between">
        <h2 id="testimonials-heading" className="text-xl font-semibold">
          Loved by home cooks across India
        </h2>
        <span className="hidden text-sm text-muted-foreground sm:inline">
          4.8 ★ average · 2,300+ happy customers
        </span>
      </div>

      {/* Edge-masked marquee (react-fast-marquee), matching the portfolio pattern. */}
      <div className="flex [mask-image:linear-gradient(to_right,transparent,white_8%,white_92%,transparent)]">
        <Marquee speed={28} pauseOnHover gradient={false} className="py-2">
          {TESTIMONIALS.map((t, i) => (
            <TestimonialCard key={`t-${i}`} {...t} />
          ))}
        </Marquee>
      </div>
    </section>
  );
}
