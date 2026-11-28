import { resolveAssetUrl } from './release';

export type PageRoute = 'home' | 'privacy' | 'terms';

export function resolvePageRoute(pathname: string, hash = ''): PageRoute {
  const cleanHash = hash.replace(/^#\/?/, '').toLowerCase();
  if (cleanHash === 'privacy-policy' || cleanHash === 'privacy') {
    return 'privacy';
  }
  if (cleanHash === 'terms-of-use' || cleanHash === 'terms') {
    return 'terms';
  }

  const normalized = pathname.replace(/\/+$/, '').toLowerCase();
  if (normalized.endsWith('/privacy.html') || normalized.endsWith('/privacy')) {
    return 'privacy';
  }
  if (normalized.endsWith('/terms.html') || normalized.endsWith('/terms')) {
    return 'terms';
  }
  return 'home';
}

export function getRouteHref(baseUrl: string, route: PageRoute): string {
  if (route === 'privacy') {
    return resolveAssetUrl(baseUrl, 'privacy.html');
  }
  if (route === 'terms') {
    return resolveAssetUrl(baseUrl, 'terms.html');
  }
  return baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
}
