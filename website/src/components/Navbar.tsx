import { useEffect, useState } from 'react';
import { AnimatePresence, motion, useScroll, useSpring } from 'motion/react';
import { Download, Menu, Moon, Sun, X } from 'lucide-react';
import type { SiteTheme } from '../hooks/useTheme';
import { resolveAssetUrl } from '../lib/release';
import { getRouteHref, type PageRoute } from '../lib/routes';

interface NavbarProps {
  version: string;
  theme: SiteTheme;
  currentRoute: PageRoute;
  onToggleTheme: () => void;
  onNavigate: (route: PageRoute, hash?: string) => void;
}

const NAV_LINKS = [
  { hash: '#screens', label: 'Screens' },
  { hash: '#features', label: 'Features' },
  { hash: '#themes', label: 'Themes' },
  { hash: '#privacy', label: 'Privacy' },
  { hash: '#download', label: 'Download' },
];

export function Navbar({
  version,
  theme,
  currentRoute,
  onToggleTheme,
  onNavigate,
}: NavbarProps) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { scrollYProgress } = useScroll();
  const scaleX = useSpring(scrollYProgress, {
    stiffness: 220,
    damping: 32,
    restDelta: 0.001,
  });

  const iconSrc = resolveAssetUrl(import.meta.env.BASE_URL, 'favicon.svg');
  const homeHref = getRouteHref(import.meta.env.BASE_URL, 'home');

  useEffect(() => {
    if (!mobileOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMobileOpen(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [mobileOpen]);

  function handleSectionClick(event: React.MouseEvent<HTMLAnchorElement>, hash: string) {
    setMobileOpen(false);
    if (currentRoute !== 'home') {
      event.preventDefault();
      onNavigate('home', hash);
    }
  }

  return (
    <header className="sticky top-0 z-40 border-b border-slate-900/[0.08] bg-[#F6F7FA]/82 backdrop-blur-xl transition-colors dark:border-white/[0.08] dark:bg-[#080A10]/82">
      {/* Subtle scroll progress hairline */}
      <motion.div
        aria-hidden="true"
        className="pointer-events-none absolute right-0 bottom-0 left-0 h-[2px] origin-left opacity-80"
        style={{
          scaleX,
          backgroundColor: 'var(--accent-text)',
        }}
      />

      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-2 px-3 sm:gap-4 sm:px-6 lg:gap-8 lg:px-8">
        {/* Zone 1: Brand */}
        <a
          href={homeHref}
          onClick={(event) => {
            if (currentRoute !== 'home') {
              event.preventDefault();
              onNavigate('home');
            }
            setMobileOpen(false);
          }}
          className="inline-flex shrink-0 items-center gap-2 whitespace-nowrap rounded-lg text-base font-semibold tracking-tight text-slate-950 sm:gap-2.5 sm:text-lg dark:text-white"
        >
          <img
            src={iconSrc}
            alt=""
            width={28}
            height={28}
            className="h-6 w-6 rounded-lg sm:h-7 sm:w-7"
          />
          <span className="font-[family-name:var(--font-display)]">UniTask</span>
        </a>

        {/* Zone 2: Primary links (clean typography, generous spacing at md & lg) */}
        <nav aria-label="Primary" className="hidden items-center gap-4 md:flex lg:gap-7">
          {NAV_LINKS.map((link) => (
            <a
              key={link.hash}
              href={currentRoute === 'home' ? link.hash : `${homeHref}${link.hash}`}
              onClick={(event) => handleSectionClick(event, link.hash)}
              className="group relative shrink-0 whitespace-nowrap py-1 text-sm font-medium text-slate-600 transition-colors hover:text-slate-950 dark:text-slate-400 dark:hover:text-white"
            >
              <span>{link.label}</span>
              <span
                aria-hidden="true"
                className="absolute right-0 bottom-0 left-0 h-[1.5px] origin-left scale-x-0 transition-transform duration-200 group-hover:scale-x-100"
                style={{ backgroundColor: 'var(--accent-text)' }}
              />
            </a>
          ))}
        </nav>

        {/* Zone 3: Theme toggle + Primary Download action + Mobile menu trigger */}
        <div className="flex shrink-0 items-center gap-1.5 sm:gap-2.5">
          <button
            type="button"
            onClick={onToggleTheme}
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-slate-900/10 bg-white/85 text-slate-700 transition-colors hover:border-slate-900/20 hover:text-slate-950 sm:h-10 sm:w-10 dark:border-white/10 dark:bg-white/[0.04] dark:text-slate-300 dark:hover:border-white/20 dark:hover:text-white"
          >
            {theme === 'dark' ? (
              <Sun className="h-4 w-4" aria-hidden="true" />
            ) : (
              <Moon className="h-4 w-4" aria-hidden="true" />
            )}
          </button>

          <a
            href={currentRoute === 'home' ? '#download' : `${homeHref}#download`}
            onClick={(event) => handleSectionClick(event, '#download')}
            className="accent-btn inline-flex h-9 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-xl px-2.5 text-xs font-semibold transition-all hover:-translate-y-0.5 sm:h-10 sm:gap-2 sm:px-4 sm:text-sm"
          >
            <Download className="h-3.5 w-3.5 sm:h-4 sm:w-4" aria-hidden="true" />
            <span>Download</span>
            <span className="hidden font-mono text-xs text-white/85 lg:inline">
              {version}
            </span>
          </a>

          <button
            type="button"
            onClick={() => setMobileOpen((open) => !open)}
            aria-expanded={mobileOpen}
            aria-controls="mobile-nav-drawer"
            aria-label={mobileOpen ? 'Close navigation menu' : 'Open navigation menu'}
            className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-slate-900/10 bg-white/85 text-slate-700 transition-colors hover:text-slate-950 sm:h-10 sm:w-10 dark:border-white/10 dark:bg-white/[0.04] dark:text-slate-300 dark:hover:text-white md:hidden"
          >
            {mobileOpen ? (
              <X className="h-4 w-4 sm:h-5 sm:w-5" aria-hidden="true" />
            ) : (
              <Menu className="h-4 w-4 sm:h-5 sm:w-5" aria-hidden="true" />
            )}
          </button>
        </div>
      </div>

      <AnimatePresence>
        {mobileOpen && (
          <motion.nav
            id="mobile-nav-drawer"
            aria-label="Mobile"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
            className="overflow-hidden border-t border-slate-900/[0.07] bg-[#F6F7FA]/96 backdrop-blur-xl dark:border-white/[0.08] dark:bg-[#080A10]/96 md:hidden"
          >
            <div className="flex flex-col gap-0.5 px-3 py-3 sm:px-6">
              {NAV_LINKS.map((link) => (
                <a
                  key={link.hash}
                  href={currentRoute === 'home' ? link.hash : `${homeHref}${link.hash}`}
                  onClick={(event) => handleSectionClick(event, link.hash)}
                  className="flex min-h-11 items-center justify-between rounded-xl px-3.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-900/[0.05] hover:text-slate-950 dark:text-slate-300 dark:hover:bg-white/[0.06] dark:hover:text-white"
                >
                  <span>{link.label}</span>
                  {link.hash === '#download' && (
                    <span className="font-mono text-xs text-slate-500 dark:text-slate-400">
                      {version}
                    </span>
                  )}
                </a>
              ))}
              <div className="my-1.5 border-t border-slate-900/[0.07] dark:border-white/[0.08]" />
              <a
                href={getRouteHref(import.meta.env.BASE_URL, 'privacy')}
                onClick={(event) => {
                  event.preventDefault();
                  setMobileOpen(false);
                  onNavigate('privacy');
                }}
                aria-current={currentRoute === 'privacy' ? 'page' : undefined}
                className={`flex min-h-11 items-center rounded-xl px-3.5 text-sm font-medium transition-colors hover:bg-slate-900/[0.05] dark:hover:bg-white/[0.06] ${
                  currentRoute === 'privacy'
                    ? 'accent-text font-semibold'
                    : 'text-slate-600 hover:text-slate-950 dark:text-slate-400 dark:hover:text-white'
                }`}
              >
                Privacy policy
              </a>
              <a
                href={getRouteHref(import.meta.env.BASE_URL, 'terms')}
                onClick={(event) => {
                  event.preventDefault();
                  setMobileOpen(false);
                  onNavigate('terms');
                }}
                aria-current={currentRoute === 'terms' ? 'page' : undefined}
                className={`flex min-h-11 items-center rounded-xl px-3.5 text-sm font-medium transition-colors hover:bg-slate-900/[0.05] dark:hover:bg-white/[0.06] ${
                  currentRoute === 'terms'
                    ? 'accent-text font-semibold'
                    : 'text-slate-600 hover:text-slate-950 dark:text-slate-400 dark:hover:text-white'
                }`}
              >
                Terms of use
              </a>
            </div>
          </motion.nav>
        )}
      </AnimatePresence>
    </header>
  );
}
