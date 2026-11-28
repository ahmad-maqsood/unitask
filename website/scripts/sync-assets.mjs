import fs from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const websiteDir = path.resolve(__dirname, '..');
const repoRoot = path.resolve(websiteDir, '..');

const REPO_OWNER = 'ahmad-maqsood';
const REPO_NAME = 'unitask';
const REPO_BRANCH = process.env.SYNC_REF || 'main';
const RAW_BASE = `https://raw.githubusercontent.com/${REPO_OWNER}/${REPO_NAME}/${REPO_BRANCH}`;
const REPO_API = `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}`;
const RELEASE_API = `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest`;
const STRICT = process.env.SYNC_STRICT === 'true';

const SCREENSHOT_KEYS = [
  'deadlines',
  'timetable',
  'note_preview',
  'timer_pomodoro',
  'courses',
  'groups',
  'progress',
  'global_search',
  'theme_dark',
  'theme_accent',
  'settings_general',
];

const PUBLIC_DIR = path.join(websiteDir, 'public');
const SCREENSHOTS_OUT_DIR = path.join(PUBLIC_DIR, 'screenshots');
const SNAPSHOT_PATH = path.join(websiteDir, 'src', 'data', 'releaseSnapshot.json');
const REPO_SNAPSHOT_PATH = path.join(websiteDir, 'src', 'data', 'repoSnapshot.json');
const ICON_OUT_PATH = path.join(PUBLIC_DIR, 'app-icon.png');
const FAVICON_OUT_PATH = path.join(PUBLIC_DIR, 'favicon.svg');

const errors = [];

function recordError(message, err) {
  const detail = err instanceof Error ? err.message : String(err ?? '');
  const full = detail ? `${message}: ${detail}` : message;
  errors.push(full);
  console.warn(`[sync-assets] ${full}`);
}

function githubHeaders(accept) {
  const headers = {
    'User-Agent': 'unitask-website-sync',
    ...(accept ? { Accept: accept } : {}),
  };
  if (process.env.GITHUB_TOKEN) {
    headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  }
  return headers;
}

async function fetchBuffer(url) {
  const res = await fetch(url, { headers: githubHeaders() });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} for ${url}`);
  }
  return Buffer.from(await res.arrayBuffer());
}

async function readLocalOrRaw(relativeCandidates) {
  for (const rel of relativeCandidates) {
    const localPath = path.join(repoRoot, rel);
    if (existsSync(localPath)) {
      return { buffer: await fs.readFile(localPath), source: `local:${rel}` };
    }
  }
  let lastErr;
  for (const rel of relativeCandidates) {
    try {
      const buffer = await fetchBuffer(`${RAW_BASE}/${rel}`);
      return { buffer, source: `remote:${rel}` };
    } catch (err) {
      lastErr = err;
    }
  }
  throw lastErr ?? new Error(`Could not resolve any of: ${relativeCandidates.join(', ')}`);
}

async function syncScreenshots() {
  await fs.mkdir(SCREENSHOTS_OUT_DIR, { recursive: true });
  let totalBytes = 0;

  for (const key of SCREENSHOT_KEYS) {
    const outPath = path.join(SCREENSHOTS_OUT_DIR, `${key}.webp`);
    try {
      const { buffer, source } = await readLocalOrRaw([
        `docs/screenshots/${key}.png`,
        `docs/screenshots/${key}.jpg`,
      ]);
      const webpBuffer = await sharp(buffer)
        .webp({ quality: 80, effort: 5 })
        .toBuffer();
      await fs.writeFile(outPath, webpBuffer);
      totalBytes += webpBuffer.length;
      console.log(
        `[sync-assets] ${key}.webp (${(webpBuffer.length / 1024).toFixed(1)} KB) <- ${source}`,
      );
    } catch (err) {
      if (existsSync(outPath)) {
        const stat = await fs.stat(outPath);
        totalBytes += stat.size;
        recordError(`Kept existing ${key}.webp after sync failure`, err);
      } else {
        recordError(`Failed to sync screenshot "${key}"`, err);
      }
    }
  }

  const totalMb = (totalBytes / (1024 * 1024)).toFixed(2);
  console.log(`[sync-assets] Total screenshot payload: ${totalMb} MB (${SCREENSHOT_KEYS.length} files)`);
}

async function syncIcon() {
  await fs.mkdir(PUBLIC_DIR, { recursive: true });

  try {
    const { buffer, source } = await readLocalOrRaw([
      'app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png',
      'app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',
    ]);
    await fs.writeFile(ICON_OUT_PATH, buffer);
    console.log(`[sync-assets] app-icon.png (${(buffer.length / 1024).toFixed(1)} KB) <- ${source}`);
  } catch (err) {
    if (!existsSync(ICON_OUT_PATH)) {
      recordError('Failed to sync app-icon.png', err);
    } else {
      recordError('Kept existing app-icon.png after sync failure', err);
    }
  }

  try {
    const { buffer, source } = await readLocalOrRaw(['unitask-site/public/favicon.svg']);
    await fs.writeFile(FAVICON_OUT_PATH, buffer);
    console.log(`[sync-assets] favicon.svg <- ${source}`);
  } catch (err) {
    if (!existsSync(FAVICON_OUT_PATH)) {
      recordError('Failed to sync favicon.svg', err);
    }
  }
}

async function syncReleaseSnapshot() {
  await fs.mkdir(path.dirname(SNAPSHOT_PATH), { recursive: true });

  try {
    const res = await fetch(RELEASE_API, {
      headers: githubHeaders('application/vnd.github+json'),
    });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status} from GitHub Releases API`);
    }
    const raw = await res.json();
    const apk = (raw.assets || []).find((a) => typeof a.name === 'string' && a.name.endsWith('.apk'));
    const attestation = (raw.assets || []).find(
      (a) => typeof a.name === 'string' && a.name.endsWith('.intoto.jsonl'),
    );

    const snapshot = {
      tagName: raw.tag_name,
      name: raw.name || raw.tag_name,
      publishedAt: raw.published_at,
      htmlUrl: raw.html_url,
      body: raw.body || '',
      apk: apk
        ? {
            name: apk.name,
            size: apk.size,
            downloadUrl: apk.browser_download_url,
          }
        : null,
      attestation: attestation
        ? {
            name: attestation.name,
            size: attestation.size,
            downloadUrl: attestation.browser_download_url,
          }
        : null,
    };

    await fs.writeFile(SNAPSHOT_PATH, `${JSON.stringify(snapshot, null, 2)}\n`, 'utf8');
    console.log(`[sync-assets] releaseSnapshot.json updated to ${snapshot.tagName}`);
  } catch (err) {
    if (existsSync(SNAPSHOT_PATH)) {
      recordError('Kept existing releaseSnapshot.json after API failure', err);
    } else {
      recordError('Failed to fetch latest release snapshot', err);
    }
  }
}

function readTomlKey(toml, key) {
  const match = toml.match(new RegExp(`^\\s*${key}\\s*=\\s*"([^"]+)"`, 'm'));
  return match?.[1]?.trim() || '';
}

async function syncRepoMetadata() {
  await fs.mkdir(path.dirname(REPO_SNAPSHOT_PATH), { recursive: true });

  let existing = {
    stars: null,
    licenseName: 'MIT License',
    licenseSpdx: 'MIT',
    language: 'Kotlin',
    minSdk: null,
    targetSdk: null,
    signingCertSha256: '',
    versions: {
      kotlin: '',
      composeBom: '',
      room: '',
      supabase: '',
      ktor: '',
      glance: '',
      workManager: '',
    },
  };

  if (existsSync(REPO_SNAPSHOT_PATH)) {
    try {
      existing = { ...existing, ...JSON.parse(await fs.readFile(REPO_SNAPSHOT_PATH, 'utf8')) };
    } catch {
      // Ignore malformed existing snapshot
    }
  }

  try {
    const repoRes = await fetch(REPO_API, {
      headers: githubHeaders('application/vnd.github+json'),
    });
    if (repoRes.ok) {
      const repoJson = await repoRes.json();
      if (typeof repoJson.stargazers_count === 'number') {
        existing.stars = repoJson.stargazers_count;
      }
      if (repoJson.license?.name) {
        existing.licenseName = repoJson.license.name;
      }
      if (repoJson.license?.spdx_id) {
        existing.licenseSpdx = repoJson.license.spdx_id;
      }
      if (repoJson.language) {
        existing.language = repoJson.language;
      }
    }
  } catch (err) {
    recordError('Failed to fetch GitHub repo metadata', err);
  }

  try {
    const { buffer } = await readLocalOrRaw(['gradle/libs.versions.toml']);
    const toml = buffer.toString('utf8');
    existing.versions = {
      kotlin: readTomlKey(toml, 'kotlin') || existing.versions.kotlin,
      composeBom: readTomlKey(toml, 'composeBom') || existing.versions.composeBom,
      room: readTomlKey(toml, 'roomRuntime') || readTomlKey(toml, 'roomKtx') || existing.versions.room,
      supabase: readTomlKey(toml, 'supabase') || existing.versions.supabase,
      ktor: readTomlKey(toml, 'ktor') || existing.versions.ktor,
      glance: readTomlKey(toml, 'glance') || existing.versions.glance,
      workManager: readTomlKey(toml, 'workRuntimeKtx') || existing.versions.workManager,
    };
  } catch (err) {
    recordError('Failed to sync gradle/libs.versions.toml', err);
  }

  try {
    const { buffer } = await readLocalOrRaw(['app/build.gradle.kts']);
    const gradle = buffer.toString('utf8');
    const minMatch = gradle.match(/\bminSdk\s*=\s*(\d+)/);
    const targetMatch = gradle.match(/\btargetSdk\s*=\s*(\d+)/);
    if (minMatch) existing.minSdk = Number(minMatch[1]);
    if (targetMatch) existing.targetSdk = Number(targetMatch[1]);
  } catch (err) {
    recordError('Failed to sync app/build.gradle.kts', err);
  }

  try {
    const { buffer } = await readLocalOrRaw(['docs/RELEASE-VERIFICATION.md']);
    const verifyDoc = buffer.toString('utf8');
    const shaMatch = verifyDoc.match(/\b([a-f0-9]{64})\b/i);
    if (shaMatch) existing.signingCertSha256 = shaMatch[1].toLowerCase();
  } catch (err) {
    recordError('Failed to sync docs/RELEASE-VERIFICATION.md', err);
  }

  await fs.writeFile(REPO_SNAPSHOT_PATH, `${JSON.stringify(existing, null, 2)}\n`, 'utf8');
  console.log(
    `[sync-assets] repoSnapshot.json updated (Kotlin ${existing.versions.kotlin}, Room ${existing.versions.room}, API ${existing.minSdk}->${existing.targetSdk})`,
  );
}

async function main() {
  await syncScreenshots();
  await syncIcon();
  await syncReleaseSnapshot();
  await syncRepoMetadata();

  if (errors.length > 0 && STRICT) {
    console.error(`[sync-assets] Strict mode enabled; aborting due to ${errors.length} error(s).`);
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('[sync-assets] Unexpected error:', err);
  if (STRICT) {
    process.exit(1);
  }
});
