import test from 'node:test';
import assert from 'node:assert/strict';
import {
  extractReleaseHighlights,
  formatBytes,
  formatReleaseDate,
  isCacheFresh,
  parseGitHubRelease,
  resolveAssetUrl,
  stepIndex,
} from './release';

test('formatBytes formats exact byte sizes clearly', () => {
  assert.equal(formatBytes(0), '0 B');
  assert.equal(formatBytes(-4), '0 B');
  assert.equal(formatBytes(512), '512 B');
  assert.equal(formatBytes(11269), '11.0 KB');
  assert.equal(formatBytes(7375833), '7.03 MB');
});

test('parseGitHubRelease selects apk and attestation bundle while ignoring mapping file', () => {
  const parsed = parseGitHubRelease({
    tag_name: 'v4.8.0',
    name: 'UniTask 4.8.0',
    published_at: '2026-11-27T07:41:23Z',
    html_url: 'https://github.com/ahmad-maqsood/unitask/releases/tag/v4.8.0',
    body: '## What\'s New\n- Interactive Markdown guide\n- Plum accent theme',
    assets: [
      {
        name: 'UniTask-4.8.0-mapping.txt',
        size: 128108325,
        browser_download_url: 'https://example.com/mapping.txt',
      },
      {
        name: 'UniTask-4.8.0.apk',
        size: 7375833,
        browser_download_url: 'https://example.com/UniTask-4.8.0.apk',
      },
      {
        name: 'UniTask-4.8.0.apk.intoto.jsonl',
        size: 11269,
        browser_download_url: 'https://example.com/UniTask-4.8.0.apk.intoto.jsonl',
      },
    ],
  });

  assert.ok(parsed);
  assert.equal(parsed.tagName, 'v4.8.0');
  assert.equal(parsed.apk?.name, 'UniTask-4.8.0.apk');
  assert.equal(parsed.apk?.size, 7375833);
  assert.equal(parsed.attestation?.name, 'UniTask-4.8.0.apk.intoto.jsonl');
});

test('parseGitHubRelease returns null for invalid payloads', () => {
  assert.equal(parseGitHubRelease({}), null);
  assert.equal(parseGitHubRelease({ tag_name: '   ' }), null);
});

test('extractReleaseHighlights extracts bullet items cleanly', () => {
  const items = extractReleaseHighlights(
    '## Notes\r\n- First change\r\n- Second change\r\n* Third change\r\n- Fourth change',
    3,
  );
  assert.deepEqual(items, ['First change', 'Second change', 'Third change']);
});

test('isCacheFresh enforces 10-minute TTL accurately', () => {
  const ttl = 10 * 60 * 1000;
  const start = 1_700_000_000_000;
  assert.equal(isCacheFresh(start, start + ttl - 1, ttl), true);
  assert.equal(isCacheFresh(start, start + ttl, ttl), false);
  assert.equal(isCacheFresh(start, start - 100, ttl), false);
});

test('resolveAssetUrl respects default and subpath Vite base URLs', () => {
  assert.equal(resolveAssetUrl('/', 'screenshots/deadlines.webp'), '/screenshots/deadlines.webp');
  assert.equal(resolveAssetUrl('/unitask/', '/screenshots/deadlines.webp'), '/unitask/screenshots/deadlines.webp');
  assert.equal(resolveAssetUrl('/unitask', 'app-icon.png'), '/unitask/app-icon.png');
});

test('stepIndex wraps forward and backward across carousel bounds', () => {
  assert.equal(stepIndex(0, -1, 8), 7);
  assert.equal(stepIndex(7, 1, 8), 0);
  assert.equal(stepIndex(2, 1, 8), 3);
  assert.equal(stepIndex(0, 1, 0), 0);
});

test('formatReleaseDate handles valid and invalid dates safely', () => {
  assert.match(formatReleaseDate('2026-11-27T07:41:23Z'), /Nov 27, 2026/);
  assert.equal(formatReleaseDate('not-a-date'), '');
  assert.equal(formatReleaseDate(''), '');
});
