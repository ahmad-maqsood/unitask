import test from 'node:test';
import assert from 'node:assert/strict';
import { getRouteHref, resolvePageRoute } from './routes';

test('resolvePageRoute detects home, privacy, and terms across root and subpath deployments', () => {
  assert.equal(resolvePageRoute('/'), 'home');
  assert.equal(resolvePageRoute('/unitask/'), 'home');
  assert.equal(resolvePageRoute('/privacy.html'), 'privacy');
  assert.equal(resolvePageRoute('/unitask/privacy.html'), 'privacy');
  assert.equal(resolvePageRoute('/privacy'), 'privacy');
  assert.equal(resolvePageRoute('/terms.html'), 'terms');
  assert.equal(resolvePageRoute('/unitask/terms.html'), 'terms');
  assert.equal(resolvePageRoute('/', '#privacy-policy'), 'privacy');
  assert.equal(resolvePageRoute('/', '#terms-of-use'), 'terms');
});

test('getRouteHref respects base URL for root and GitHub Pages subpaths', () => {
  assert.equal(getRouteHref('/', 'home'), '/');
  assert.equal(getRouteHref('/', 'privacy'), '/privacy.html');
  assert.equal(getRouteHref('/', 'terms'), '/terms.html');
  assert.equal(getRouteHref('/unitask/', 'home'), '/unitask/');
  assert.equal(getRouteHref('/unitask/', 'privacy'), '/unitask/privacy.html');
  assert.equal(getRouteHref('/unitask/', 'terms'), '/unitask/terms.html');
});
