import { useEffect, useState } from 'react';
import repoSnapshotJson from '../data/repoSnapshot.json';
import { REPO_NAME, REPO_OWNER } from '../data/content';
import { isCacheFresh } from '../lib/release';
import {
  type GitHubRepoApiResponse,
  type RepoMetadata,
  parseBuildGradleSdk,
  parseGitHubRepoApi,
  parseSigningCertSha256,
  parseVersionsToml,
} from '../lib/repoMetadata';

const REPO_API_URL = `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}`;
const RAW_BASE_URL = `https://raw.githubusercontent.com/${REPO_OWNER}/${REPO_NAME}/main`;
const CACHE_KEY = 'unitask_repo_metadata_v1';
const CACHE_TTL_MS = 10 * 60 * 1000;

interface CachedRepoMetadata {
  cachedAt: number;
  data: RepoMetadata;
}

const FALLBACK_METADATA: RepoMetadata = repoSnapshotJson as RepoMetadata;

function readCachedMetadata(): RepoMetadata | null {
  try {
    const raw = window.sessionStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CachedRepoMetadata;
    if (
      parsed &&
      typeof parsed.cachedAt === 'number' &&
      parsed.data &&
      isCacheFresh(parsed.cachedAt, Date.now(), CACHE_TTL_MS)
    ) {
      return parsed.data;
    }
  } catch {
    // Ignore storage errors (private browsing, blocked storage)
  }
  return null;
}

function writeCachedMetadata(data: RepoMetadata): void {
  try {
    const payload: CachedRepoMetadata = { cachedAt: Date.now(), data };
    window.sessionStorage.setItem(CACHE_KEY, JSON.stringify(payload));
  } catch {
    // Ignore storage quota or security errors
  }
}

async function fetchTextSafe(url: string, signal: AbortSignal): Promise<string | null> {
  try {
    const res = await fetch(url, { signal });
    if (!res.ok) return null;
    return await res.text();
  } catch {
    return null;
  }
}

export function useRepoMetadata(): {
  metadata: RepoMetadata;
  source: 'snapshot' | 'cache' | 'live';
} {
  const [state, setState] = useState<{
    metadata: RepoMetadata;
    source: 'snapshot' | 'cache' | 'live';
  }>(() => {
    if (typeof window !== 'undefined') {
      const cached = readCachedMetadata();
      if (cached) {
        return { metadata: cached, source: 'cache' };
      }
    }
    return { metadata: FALLBACK_METADATA, source: 'snapshot' };
  });

  useEffect(() => {
    if (state.source === 'cache') {
      return;
    }

    const controller = new AbortController();

    async function loadLiveMetadata() {
      try {
        const [repoRes, tomlText, gradleText, verifyDocText] = await Promise.all([
          fetch(REPO_API_URL, {
            headers: { Accept: 'application/vnd.github+json' },
            signal: controller.signal,
          })
            .then((r) => (r.ok ? (r.json() as Promise<GitHubRepoApiResponse>) : null))
            .catch(() => null),
          fetchTextSafe(`${RAW_BASE_URL}/gradle/libs.versions.toml`, controller.signal),
          fetchTextSafe(`${RAW_BASE_URL}/app/build.gradle.kts`, controller.signal),
          fetchTextSafe(`${RAW_BASE_URL}/docs/RELEASE-VERIFICATION.md`, controller.signal),
        ]);

        if (controller.signal.aborted) return;

        const repoPatch = repoRes ? parseGitHubRepoApi(repoRes) : {};
        const versionPatch = tomlText ? parseVersionsToml(tomlText) : {};
        const sdkPatch = gradleText ? parseBuildGradleSdk(gradleText) : {};
        const certSha = verifyDocText ? parseSigningCertSha256(verifyDocText) : null;

        const hasAnyLiveUpdate =
          Object.keys(repoPatch).length > 0 ||
          Object.keys(versionPatch).length > 0 ||
          Object.keys(sdkPatch).length > 0 ||
          Boolean(certSha);

        if (!hasAnyLiveUpdate) return;

        const merged: RepoMetadata = {
          ...FALLBACK_METADATA,
          ...repoPatch,
          minSdk: sdkPatch.minSdk ?? FALLBACK_METADATA.minSdk,
          targetSdk: sdkPatch.targetSdk ?? FALLBACK_METADATA.targetSdk,
          signingCertSha256: certSha ?? FALLBACK_METADATA.signingCertSha256,
          versions: {
            ...FALLBACK_METADATA.versions,
            ...versionPatch,
          },
        };

        writeCachedMetadata(merged);
        setState({ metadata: merged, source: 'live' });
      } catch {
        // Offline or rate-limited: keep build-time snapshot seamlessly
      }
    }

    loadLiveMetadata();
    return () => controller.abort();
  }, [state.source]);

  return state;
}
