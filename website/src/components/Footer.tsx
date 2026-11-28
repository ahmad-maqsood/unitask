import { ISSUES_URL, LICENSE_URL, RELEASES_URL, REPO_URL } from '../data/content';
import { resolveAssetUrl } from '../lib/release';
import { getRouteHref, type PageRoute } from '../lib/routes';

interface FooterProps {
  currentRoute: PageRoute;
  licenseName: string;
  onNavigate: (route: PageRoute) => void;
}

export function Footer({ currentRoute, licenseName, onNavigate }: FooterProps) {
  const iconSrc = resolveAssetUrl(import.meta.env.BASE_URL, 'favicon.svg');
  const homeHref = getRouteHref(import.meta.env.BASE_URL, 'home');
  const privacyHref = getRouteHref(import.meta.env.BASE_URL, 'privacy');
  const termsHref = getRouteHref(import.meta.env.BASE_URL, 'terms');

  return (
    <footer className="border-t border-slate-900/[0.08] py-12 dark:border-white/[0.08]">
      <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-8 px-4 sm:flex-row sm:items-center sm:px-6 lg:px-8">
        <div>
          <a
            href={homeHref}
            onClick={(event) => {
              if (currentRoute !== 'home') {
                event.preventDefault();
                onNavigate('home');
              }
            }}
            className="inline-flex items-center gap-2.5 text-base font-semibold tracking-tight text-slate-950 dark:text-white"
          >
            <img
              src={iconSrc}
              alt=""
              width={24}
              height={24}
              className="h-6 w-6 rounded-md"
            />
            <span className="font-[family-name:var(--font-display)]">UniTask</span>
          </a>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
            Made by Ahmad Maqsood
          </p>
        </div>

        <nav
          aria-label="Footer"
          className="flex flex-wrap items-center gap-x-6 gap-y-2.5 text-sm text-slate-600 dark:text-slate-400"
        >
          <a
            href={REPO_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="transition-colors hover:text-slate-950 dark:hover:text-white"
          >
            GitHub
          </a>
          <a
            href={ISSUES_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="transition-colors hover:text-slate-950 dark:hover:text-white"
          >
            Issues
          </a>
          <a
            href={RELEASES_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="transition-colors hover:text-slate-950 dark:hover:text-white"
          >
            Releases
          </a>
          <a
            href={LICENSE_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="transition-colors hover:text-slate-950 dark:hover:text-white"
          >
            {licenseName || 'MIT License'}
          </a>
          <a
            href={privacyHref}
            onClick={(event) => {
              event.preventDefault();
              onNavigate('privacy');
            }}
            aria-current={currentRoute === 'privacy' ? 'page' : undefined}
            className={`transition-colors hover:text-slate-950 dark:hover:text-white ${
              currentRoute === 'privacy' ? 'font-semibold accent-text' : ''
            }`}
          >
            Privacy policy
          </a>
          <a
            href={termsHref}
            onClick={(event) => {
              event.preventDefault();
              onNavigate('terms');
            }}
            aria-current={currentRoute === 'terms' ? 'page' : undefined}
            className={`transition-colors hover:text-slate-950 dark:hover:text-white ${
              currentRoute === 'terms' ? 'font-semibold accent-text' : ''
            }`}
          >
            Terms of use
          </a>
        </nav>
      </div>
    </footer>
  );
}
