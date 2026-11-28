import { useCallback, useEffect, useState } from 'react';
import { MotionConfig } from 'motion/react';
import { AppearanceSection } from './components/AppearanceSection';
import { ArchitecturePrivacy } from './components/ArchitecturePrivacy';
import { CapabilitiesBento } from './components/CapabilitiesBento';
import { DownloadSection } from './components/DownloadSection';
import { Footer } from './components/Footer';
import { Hero } from './components/Hero';
import { LegalPage } from './components/LegalPage';
import { Navbar } from './components/Navbar';
import { ProductStage } from './components/ProductStage';
import { PRIVACY_POLICY, TERMS_OF_USE } from './data/legal';
import { useAccentTheme } from './hooks/useAccentTheme';
import { useLatestRelease } from './hooks/useLatestRelease';
import { useRepoMetadata } from './hooks/useRepoMetadata';
import { useTheme } from './hooks/useTheme';
import { getRouteHref, resolvePageRoute, type PageRoute } from './lib/routes';

const ROUTE_TITLES: Record<PageRoute, string> = {
  home: 'UniTask — Local-First Android Task Manager for University Students',
  privacy: 'UniTask Privacy Policy',
  terms: 'UniTask Terms of Use',
};

export default function App() {
  const { release } = useLatestRelease();
  const { metadata } = useRepoMetadata();
  const { theme, toggleTheme } = useTheme();
  const { accent, setAccentId, resetAccent } = useAccentTheme();
  const [route, setRoute] = useState<PageRoute>(() =>
    typeof window !== 'undefined'
      ? resolvePageRoute(window.location.pathname, window.location.hash)
      : 'home',
  );

  useEffect(() => {
    document.title = ROUTE_TITLES[route];
  }, [route]);

  useEffect(() => {
    const syncRoute = () => {
      setRoute(resolvePageRoute(window.location.pathname, window.location.hash));
    };
    window.addEventListener('popstate', syncRoute);
    window.addEventListener('hashchange', syncRoute);
    return () => {
      window.removeEventListener('popstate', syncRoute);
      window.removeEventListener('hashchange', syncRoute);
    };
  }, []);

  const navigate = useCallback((nextRoute: PageRoute, hash?: string) => {
    const baseHref = getRouteHref(import.meta.env.BASE_URL, nextRoute);
    const targetUrl = hash ? `${baseHref}${hash}` : baseHref;

    try {
      window.history.pushState(null, '', targetUrl);
    } catch {
      // Ignore history restrictions in sandboxed contexts
    }

    setRoute(nextRoute);

    if (nextRoute === 'home' && hash) {
      window.requestAnimationFrame(() => {
        const el = document.querySelector(hash);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth' });
        }
      });
    } else {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' as ScrollBehavior });
    }
  }, []);

  return (
    <MotionConfig reducedMotion="user">
      <div className="min-h-screen bg-[#F6F7FA] text-[#111422] transition-colors duration-200 dark:bg-[#080A10] dark:text-[#EEF1F8]">
        <a
          href="#main-content"
          className="accent-btn sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-50 focus:rounded-lg focus:px-4 focus:py-2 focus:text-sm focus:font-medium"
        >
          Skip to main content
        </a>

        <Navbar
          version={release.tagName}
          theme={theme}
          currentRoute={route}
          onToggleTheme={toggleTheme}
          onNavigate={navigate}
        />

        <main id="main-content">
          {route === 'privacy' ? (
            <LegalPage document={PRIVACY_POLICY} onNavigate={navigate} />
          ) : route === 'terms' ? (
            <LegalPage document={TERMS_OF_USE} onNavigate={navigate} />
          ) : (
            <>
              <Hero release={release} metadata={metadata} />
              <ProductStage />
              <CapabilitiesBento />
              <AppearanceSection
                accent={accent}
                theme={theme}
                onSelectAccent={setAccentId}
                onResetAccent={resetAccent}
              />
              <ArchitecturePrivacy metadata={metadata} onNavigate={navigate} />
              <DownloadSection release={release} metadata={metadata} />
            </>
          )}
        </main>

        <Footer
          currentRoute={route}
          licenseName={metadata.licenseName}
          onNavigate={navigate}
        />
      </div>
    </MotionConfig>
  );
}
