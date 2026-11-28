import test from 'node:test';
import assert from 'node:assert/strict';
import {
  androidVersionLabel,
  buildTechStackRows,
  parseBuildGradleSdk,
  parseGitHubRepoApi,
  parseSigningCertSha256,
  parseVersionsToml,
} from './repoMetadata';

test('parseVersionsToml extracts real Gradle catalog versions', () => {
  const sample = `
[versions]
kotlin = "2.4.21"
composeBom = "2026.11.01"
roomRuntime = "2.8.5"
supabase = "3.9.0"
ktor = "3.6.1"
glance = "1.1.1"
workRuntimeKtx = "2.12.0"
`;
  assert.deepEqual(parseVersionsToml(sample), {
    kotlin: '2.4.21',
    composeBom: '2026.11.01',
    room: '2.8.5',
    supabase: '3.9.0',
    ktor: '3.6.1',
    glance: '1.1.1',
    workManager: '2.12.0',
  });
});

test('parseBuildGradleSdk extracts minSdk and targetSdk', () => {
  const gradle = `
    defaultConfig {
      minSdk = 24
      targetSdk = 36
    }
  `;
  assert.deepEqual(parseBuildGradleSdk(gradle), { minSdk: 24, targetSdk: 36 });
});

test('parseSigningCertSha256 finds 64-char hex certificate fingerprint', () => {
  const doc = 'The digest line should read:\n```\nF000BCF3C12E6ED4764B30765F6D58E083F163B1C2BA997F35F6A03E3468B818\n```';
  assert.equal(
    parseSigningCertSha256(doc),
    'f000bcf3c12e6ed4764b30765f6d58e083f163b1c2ba997f35f6a03e3468b818',
  );
  assert.equal(parseSigningCertSha256('no hash here'), null);
});

test('androidVersionLabel maps API levels cleanly', () => {
  assert.equal(androidVersionLabel(24), 'Android 7.0+');
  assert.equal(androidVersionLabel(34), 'Android 14+');
  assert.equal(androidVersionLabel(null), 'Android');
});

test('parseGitHubRepoApi and buildTechStackRows build dynamic stack entries', () => {
  const repo = parseGitHubRepoApi({
    stargazers_count: 14,
    language: 'Kotlin',
    license: { name: 'MIT License', spdx_id: 'MIT' },
  });
  assert.equal(repo.stars, 14);
  assert.equal(repo.licenseName, 'MIT License');

  const rows = buildTechStackRows({
    stars: 14,
    licenseName: 'MIT License',
    licenseSpdx: 'MIT',
    language: 'Kotlin',
    minSdk: 24,
    targetSdk: 36,
    signingCertSha256: 'abc',
    versions: {
      kotlin: '2.4.21',
      composeBom: '2026.11.01',
      room: '2.8.5',
      supabase: '3.9.0',
      ktor: '3.6.1',
      glance: '1.1.1',
      workManager: '2.12.0',
    },
  });

  assert.equal(rows.length, 7);
  assert.match(rows[0].technology, /Kotlin 2\.4\.21/);
  assert.match(rows[2].technology, /Room 2\.8\.5/);
  assert.match(rows[6].technology, /Android 7\.0 \(API 24\) to Target SDK 36/);
});
