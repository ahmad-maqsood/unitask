export interface ReleaseAsset {
  name: string;
  size: number;
  downloadUrl: string;
}

export interface ReleaseInfo {
  tagName: string;
  name: string;
  publishedAt: string;
  htmlUrl: string;
  body: string;
  apk: ReleaseAsset | null;
  attestation: ReleaseAsset | null;
}

export interface GitHubReleaseApiResponse {
  tag_name?: string;
  name?: string;
  published_at?: string;
  html_url?: string;
  body?: string;
  assets?: Array<{
    name?: string;
    size?: number;
    browser_download_url?: string;
  }>;
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  const precision = unitIndex >= 2 ? 2 : unitIndex === 1 ? 1 : 0;
  return `${value.toFixed(precision)} ${units[unitIndex]}`;
}

export function parseGitHubRelease(raw: GitHubReleaseApiResponse): ReleaseInfo | null {
  if (!raw || typeof raw.tag_name !== 'string' || !raw.tag_name.trim()) {
    return null;
  }

  const assets = Array.isArray(raw.assets) ? raw.assets : [];
  const apkRaw = assets.find(
    (asset) =>
      typeof asset.name === 'string' &&
      asset.name.toLowerCase().endsWith('.apk') &&
      typeof asset.browser_download_url === 'string',
  );
  const attestationRaw = assets.find(
    (asset) =>
      typeof asset.name === 'string' &&
      asset.name.toLowerCase().endsWith('.intoto.jsonl') &&
      typeof asset.browser_download_url === 'string',
  );

  return {
    tagName: raw.tag_name,
    name: raw.name && raw.name.trim() ? raw.name : raw.tag_name,
    publishedAt: raw.published_at || '',
    htmlUrl:
      raw.html_url ||
      `https://github.com/ahmad-maqsood/unitask/releases/tag/${encodeURIComponent(raw.tag_name)}`,
    body: raw.body || '',
    apk: apkRaw
      ? {
          name: apkRaw.name!,
          size: typeof apkRaw.size === 'number' ? apkRaw.size : 0,
          downloadUrl: apkRaw.browser_download_url!,
        }
      : null,
    attestation: attestationRaw
      ? {
          name: attestationRaw.name!,
          size: typeof attestationRaw.size === 'number' ? attestationRaw.size : 0,
          downloadUrl: attestationRaw.browser_download_url!,
        }
      : null,
  };
}

export function extractReleaseHighlights(body: string, maxItems = 3): string[] {
  if (!body) return [];
  const lines = body
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => /^[-*]\s+/.test(line))
    .map((line) => line.replace(/^[-*]\s+/, '').trim())
    .filter(Boolean);
  return lines.slice(0, maxItems);
}

export function isCacheFresh(cachedAt: number, now: number, ttlMs: number): boolean {
  if (!Number.isFinite(cachedAt) || !Number.isFinite(now) || ttlMs <= 0) {
    return false;
  }
  return now >= cachedAt && now - cachedAt < ttlMs;
}

export function resolveAssetUrl(baseUrl: string, relativePath: string): string {
  const cleanBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
  const cleanPath = relativePath.replace(/^\/+/, '');
  return `${cleanBase}${cleanPath}`;
}

export function formatReleaseDate(isoDate: string): string {
  if (!isoDate) return '';
  const parsed = new Date(isoDate);
  if (Number.isNaN(parsed.getTime())) return '';
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(parsed);
}

export function stepIndex(current: number, delta: number, length: number): number {
  if (length <= 0) return 0;
  return (((current + delta) % length) + length) % length;
}
