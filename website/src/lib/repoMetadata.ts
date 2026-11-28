export interface BuildVersions {
  kotlin: string;
  composeBom: string;
  room: string;
  supabase: string;
  ktor: string;
  glance: string;
  workManager: string;
}

export interface RepoMetadata {
  stars: number | null;
  licenseName: string;
  licenseSpdx: string;
  language: string;
  minSdk: number | null;
  targetSdk: number | null;
  signingCertSha256: string;
  versions: BuildVersions;
}

export interface GitHubRepoApiResponse {
  stargazers_count?: number;
  language?: string;
  license?: {
    name?: string;
    spdx_id?: string;
  } | null;
}

export interface TechStackRow {
  layer: string;
  technology: string;
}

const API_TO_ANDROID_VERSION: Record<number, string> = {
  21: '5.0',
  22: '5.1',
  23: '6.0',
  24: '7.0',
  25: '7.1',
  26: '8.0',
  27: '8.1',
  28: '9',
  29: '10',
  30: '11',
  31: '12',
  32: '12L',
  33: '13',
  34: '14',
  35: '15',
  36: '16',
};

export function androidVersionLabel(apiLevel: number | null): string {
  if (!apiLevel || !Number.isFinite(apiLevel)) {
    return 'Android';
  }
  const version = API_TO_ANDROID_VERSION[apiLevel];
  return version ? `Android ${version}+` : `Android API ${apiLevel}+`;
}

export function parseVersionsToml(toml: string): Partial<BuildVersions> {
  if (!toml) return {};
  const readKey = (key: string): string | undefined => {
    const match = toml.match(new RegExp(`^\\s*${key}\\s*=\\s*"([^"]+)"`, 'm'));
    return match?.[1]?.trim() || undefined;
  };

  const result: Partial<BuildVersions> = {};
  const kotlin = readKey('kotlin');
  const composeBom = readKey('composeBom');
  const room = readKey('roomRuntime') ?? readKey('roomKtx') ?? readKey('room');
  const supabase = readKey('supabase');
  const ktor = readKey('ktor');
  const glance = readKey('glance');
  const workManager = readKey('workRuntimeKtx') ?? readKey('work');

  if (kotlin) result.kotlin = kotlin;
  if (composeBom) result.composeBom = composeBom;
  if (room) result.room = room;
  if (supabase) result.supabase = supabase;
  if (ktor) result.ktor = ktor;
  if (glance) result.glance = glance;
  if (workManager) result.workManager = workManager;

  return result;
}

export function parseBuildGradleSdk(gradle: string): {
  minSdk?: number;
  targetSdk?: number;
} {
  if (!gradle) return {};
  const minMatch = gradle.match(/\bminSdk\s*=\s*(\d+)/);
  const targetMatch = gradle.match(/\btargetSdk\s*=\s*(\d+)/);
  const result: { minSdk?: number; targetSdk?: number } = {};
  if (minMatch) result.minSdk = Number(minMatch[1]);
  if (targetMatch) result.targetSdk = Number(targetMatch[1]);
  return result;
}

export function parseSigningCertSha256(markdown: string): string | null {
  if (!markdown) return null;
  const match = markdown.match(/\b([a-f0-9]{64})\b/i);
  return match ? match[1].toLowerCase() : null;
}

export function parseGitHubRepoApi(raw: GitHubRepoApiResponse): Partial<RepoMetadata> {
  if (!raw || typeof raw !== 'object') return {};
  const result: Partial<RepoMetadata> = {};
  if (typeof raw.stargazers_count === 'number' && Number.isFinite(raw.stargazers_count)) {
    result.stars = raw.stargazers_count;
  }
  if (typeof raw.language === 'string' && raw.language.trim()) {
    result.language = raw.language.trim();
  }
  if (raw.license?.name && raw.license.name.trim()) {
    result.licenseName = raw.license.name.trim();
  }
  if (raw.license?.spdx_id && raw.license.spdx_id.trim()) {
    result.licenseSpdx = raw.license.spdx_id.trim();
  }
  return result;
}

export function buildTechStackRows(meta: RepoMetadata): TechStackRow[] {
  const { versions, minSdk, targetSdk } = meta;

  const uiParts = [
    versions.kotlin ? `Kotlin ${versions.kotlin}` : 'Kotlin',
    versions.composeBom ? `Jetpack Compose (${versions.composeBom})` : 'Jetpack Compose',
    'Material 3',
  ];

  const widgetText = versions.glance
    ? `Jetpack Glance ${versions.glance} (UniTask Today)`
    : 'Jetpack Glance (UniTask Today)';

  const roomText = versions.room
    ? `Room ${versions.room} (SQLite) with versioned schema migrations`
    : 'Room (SQLite) with versioned schema migrations';

  const supabaseText = versions.supabase
    ? `Supabase (supabase-kt ${versions.supabase}): Google Auth, PostgREST, Realtime & RLS`
    : 'Supabase: Google Auth, PostgREST, Realtime & RLS';

  const ktorText = versions.ktor
    ? `Ktor ${versions.ktor} (OkHttp engine)`
    : 'Ktor (OkHttp engine)';

  const workText = versions.workManager
    ? `AlarmManager for exact alerts, WorkManager ${versions.workManager} for periodic sync & backup`
    : 'AlarmManager for exact alerts, WorkManager for periodic sync & backup';

  const minLabel = minSdk ? `${androidVersionLabel(minSdk).replace(/\+$/, '')} (API ${minSdk})` : 'Android';
  const compatText = targetSdk
    ? `${minLabel} to Target SDK ${targetSdk}`
    : `${androidVersionLabel(minSdk)}`;

  return [
    { layer: 'UI & Design', technology: uiParts.join(', ') },
    { layer: 'Home-screen widget', technology: widgetText },
    { layer: 'Local storage', technology: roomText },
    { layer: 'Optional cloud (Groups)', technology: supabaseText },
    { layer: 'Networking', technology: ktorText },
    { layer: 'Reminders & sync', technology: workText },
    { layer: 'Android compatibility', technology: compatText },
  ];
}
