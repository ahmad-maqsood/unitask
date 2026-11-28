import { useEffect, useState } from 'react';
import snapshotJson from '../data/releaseSnapshot.json';
import {
  type GitHubReleaseApiResponse,
  type ReleaseInfo,
  isCacheFresh,
  parseGitHubRelease,
} from '../lib/release';

const RELEASE_API_URL = 'https://api.github.com/repos/ahmad-maqsood/unitask/releases/latest';
const CACHE_KEY = 'unitask_latest_release_v1';
const CACHE_TTL_MS = 10 * 60 * 1000;

interface CachedRelease {
  cachedAt: number;
  data: ReleaseInfo;
}

const FALLBACK_RELEASE: ReleaseInfo = snapshotJson as ReleaseInfo;

function readCachedRelease(): ReleaseInfo | null {
  try {
    const raw = window.sessionStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CachedRelease;
    if (
      parsed &&
      typeof parsed.cachedAt === 'number' &&
      parsed.data &&
      isCacheFresh(parsed.cachedAt, Date.now(), CACHE_TTL_MS)
    ) {
      return parsed.data;
    }
  } catch {
    // Ignore storage errors (private mode, disabled storage, malformed cache)
  }
  return null;
}

function writeCachedRelease(data: ReleaseInfo): void {
  try {
    const payload: CachedRelease = { cachedAt: Date.now(), data };
    window.sessionStorage.setItem(CACHE_KEY, JSON.stringify(payload));
  } catch {
    // Ignore storage quota or security errors
  }
}

export function useLatestRelease(): {
  release: ReleaseInfo;
  source: 'snapshot' | 'cache' | 'live';
} {
  const [state, setState] = useState<{
    release: ReleaseInfo;
    source: 'snapshot' | 'cache' | 'live';
  }>(() => {
    if (typeof window !== 'undefined') {
      const cached = readCachedRelease();
      if (cached) {
        return { release: cached, source: 'cache' };
      }
    }
    return { release: FALLBACK_RELEASE, source: 'snapshot' };
  });

  useEffect(() => {
    if (state.source === 'cache') {
      return;
    }

    const controller = new AbortController();

    async function loadLatest() {
      try {
        const res = await fetch(RELEASE_API_URL, {
          headers: { Accept: 'application/vnd.github+json' },
          signal: controller.signal,
        });
        if (!res.ok) return;
        const json = (await res.json()) as GitHubReleaseApiResponse;
        const parsed = parseGitHubRelease(json);
        if (!parsed) return;
        writeCachedRelease(parsed);
        setState({ release: parsed, source: 'live' });
      } catch {
        // Offline, aborted, or rate-limited: keep build-time snapshot seamlessly
      }
    }

    loadLatest();
    return () => controller.abort();
  }, [state.source]);

  return state;
}
