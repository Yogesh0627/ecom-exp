import Link from 'next/link';
import { Leaf, Linkedin, Globe } from 'lucide-react';
import { APP_NAME, APP_TAGLINE, DEVELOPER, ROUTES } from '@/constants';

const COLUMNS: { heading: string; links: { label: string; href: string }[] }[] = [
  {
    heading: 'Shop',
    links: [
      { label: 'All products', href: ROUTES.home },
      { label: 'Search', href: ROUTES.search('') },
      { label: 'Your cart', href: ROUTES.cart },
    ],
  },
  {
    heading: 'Smart features',
    links: [
      { label: 'Meal planner', href: ROUTES.mealPlanner },
      { label: 'Smart Fridge', href: ROUTES.smartFridge },
      { label: 'My pantry', href: ROUTES.pantry },
      { label: 'Organic journal', href: ROUTES.blog },
    ],
  },
  {
    heading: 'Account',
    links: [
      { label: 'Sign in', href: ROUTES.login },
      { label: 'Create account', href: ROUTES.register },
      { label: 'My orders', href: ROUTES.orders },
    ],
  },
];

export function SiteFooter() {
  const year = new Date().getFullYear();

  return (
    <footer className="mt-12 border-t bg-muted/30">
      <div className="container py-10">
        <div className="grid gap-8 md:grid-cols-[1.5fr_repeat(3,1fr)]">
          <div>
            <Link href={ROUTES.home} className="flex items-center gap-2 font-bold text-primary">
              <Leaf className="h-5 w-5" />
              <span className="text-lg">{APP_NAME}</span>
            </Link>
            <p className="mt-3 max-w-xs text-sm text-muted-foreground">
              {APP_TAGLINE}. Certified organic, scored for nutrition, delivered fresh across India.
            </p>
          </div>

          {COLUMNS.map((col) => (
            <div key={col.heading}>
              <h3 className="mb-3 text-sm font-semibold">{col.heading}</h3>
              <ul className="space-y-2">
                {col.links.map((l) => (
                  <li key={l.label}>
                    <Link href={l.href} className="text-sm text-muted-foreground hover:text-primary">
                      {l.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="mt-8 flex flex-col items-center justify-between gap-3 border-t pt-6 text-sm text-muted-foreground sm:flex-row">
          <p>
            © {year} {APP_NAME} · Organic groceries for India
          </p>
          <p className="flex items-center gap-3">
            <span>
              Built by{' '}
              <a
                href={DEVELOPER.url}
                target="_blank"
                rel="noopener noreferrer author"
                className="font-medium text-foreground hover:text-primary"
              >
                {DEVELOPER.name}
              </a>
            </span>
            <a
              href={DEVELOPER.url}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`${DEVELOPER.name} website`}
              className="hover:text-primary"
            >
              <Globe className="h-4 w-4" />
            </a>
            <a
              href={DEVELOPER.linkedin}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`${DEVELOPER.name} on LinkedIn`}
              className="hover:text-primary"
            >
              <Linkedin className="h-4 w-4" />
            </a>
          </p>
        </div>
      </div>
    </footer>
  );
}
