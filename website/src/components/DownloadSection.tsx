import { useState } from 'react';
import {
  ArrowUpRight,
  Check,
  Copy,
  Download,
  FileCheck2,
  FileText,
  ShieldCheck,
} from 'lucide-react';
import { INSTALL_STEPS, RELEASES_URL, VERIFICATION_DOC_URL } from '../data/content';
import {
  type ReleaseInfo,
  extractReleaseHighlights,
  formatBytes,
  formatReleaseDate,
} from '../lib/release';
import { type RepoMetadata, androidVersionLabel } from '../lib/repoMetadata';
import { FadeRise } from './MotionPrimitives';

interface DownloadSectionProps {
  release: ReleaseInfo;
  metadata: RepoMetadata;
}

export function DownloadSection({ release, metadata }: DownloadSectionProps) {
  const [copied, setCopied] = useState(false);

  const apkName = release.apk?.name || `UniTask-${release.tagName.replace(/^v/, '')}.apk`;
  const apkUrl = release.apk?.downloadUrl || RELEASES_URL;
  const apkSize = release.apk ? formatBytes(release.apk.size) : 'APK';
  const publishedDate = formatReleaseDate(release.publishedAt);
  const highlights = extractReleaseHighlights(release.body, 3);
  const minAndroidLabel = androidVersionLabel(metadata.minSdk);

  const verifyCommand = `gh attestation verify ${apkName} --repo ahmad-maqsood/unitask --signer-workflow ahmad-maqsood/unitask/.github/workflows/release-apk.yml`;

  async function handleCopyCommand() {
    try {
      await navigator.clipboard.writeText(verifyCommand);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API unavailable in restricted contexts
    }
  }

  return (
    <section id="download" className="py-16 sm:py-24">
      <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
        <FadeRise>
          <div className="glass-panel relative overflow-hidden rounded-3xl border border-slate-900/[0.08] bg-white/90 p-5 backdrop-blur-xl sm:p-9 lg:p-11 dark:border-white/[0.08] dark:bg-[#10131D]/88">
            <div
              aria-hidden="true"
              className="pointer-events-none absolute -top-28 -right-28 h-80 w-80 rounded-full opacity-20 blur-3xl transition-colors duration-300 dark:opacity-25"
              style={{
                background: 'radial-gradient(circle, var(--accent-swatch), transparent 70%)',
              }}
            />

            <div className="relative grid gap-10 lg:grid-cols-12 lg:gap-12">
              {/* Left: Primary APK download + live release links */}
              <div className="min-w-0 lg:col-span-7">
                <p className="font-mono text-xs font-medium accent-text">
                  Download
                </p>
                <h2 className="mt-2 text-balance text-2xl font-bold tracking-tight text-slate-950 sm:text-4xl dark:text-white">
                  Get UniTask {release.tagName} for Android.
                </h2>

                <p className="mt-3 flex flex-wrap items-center gap-x-2.5 gap-y-1 font-mono text-xs text-slate-500 tabular-nums dark:text-slate-400">
                  <span>{apkName}</span>
                  <span aria-hidden="true">·</span>
                  <span>{apkSize}</span>
                  {publishedDate && (
                    <>
                      <span aria-hidden="true">·</span>
                      <span>Released {publishedDate}</span>
                    </>
                  )}
                  <span aria-hidden="true">·</span>
                  <span>
                    {minAndroidLabel}
                    {metadata.minSdk ? ` (API ${metadata.minSdk})` : ''}
                  </span>
                </p>

                <div className="mt-7 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
                  <a
                    href={apkUrl}
                    className="accent-btn inline-flex h-12 items-center justify-center gap-2.5 whitespace-nowrap rounded-xl px-6 text-sm font-semibold transition-all hover:-translate-y-0.5"
                  >
                    <Download className="h-4 w-4 shrink-0" aria-hidden="true" />
                    <span>Download APK ({apkSize})</span>
                  </a>

                  <a
                    href={release.htmlUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex h-12 items-center justify-center gap-2 whitespace-nowrap rounded-xl border border-slate-900/12 bg-white px-5 text-sm font-medium text-slate-800 transition-colors hover:border-slate-900/25 hover:text-slate-950 dark:border-white/12 dark:bg-white/[0.04] dark:text-slate-200 dark:hover:border-white/25 dark:hover:text-white"
                  >
                    <FileText className="h-4 w-4 shrink-0" aria-hidden="true" />
                    <span>Release notes</span>
                    <ArrowUpRight className="h-3.5 w-3.5 shrink-0 opacity-70" aria-hidden="true" />
                  </a>

                  {release.attestation && (
                    <a
                      href={release.attestation.downloadUrl}
                      className="inline-flex h-12 items-center justify-center gap-2 whitespace-nowrap rounded-xl border border-slate-900/12 bg-white px-5 text-sm font-medium text-slate-800 transition-colors hover:border-slate-900/25 hover:text-slate-950 dark:border-white/12 dark:bg-white/[0.04] dark:text-slate-200 dark:hover:border-white/25 dark:hover:text-white"
                    >
                      <FileCheck2 className="h-4 w-4 shrink-0" aria-hidden="true" />
                      <span>Provenance (.intoto.jsonl)</span>
                    </a>
                  )}
                </div>

                {highlights.length > 0 && (
                  <div className="mt-7 border-t border-slate-900/[0.07] pt-5 dark:border-white/[0.08]">
                    <h3 className="text-xs font-semibold text-slate-900 dark:text-slate-200">
                      Highlights in {release.tagName}
                    </h3>
                    <ul className="mt-2.5 space-y-2 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                      {highlights.map((line) => (
                        <li key={line} className="flex items-start gap-2.5">
                          <span
                            aria-hidden="true"
                            className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full"
                            style={{ backgroundColor: 'var(--accent-text)' }}
                          />
                          <span>{line}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* Artifact attestation & signing key verification */}
                <div className="mt-7 rounded-2xl border border-slate-900/[0.08] bg-slate-900/[0.025] p-4 dark:border-white/[0.08] dark:bg-[#0A0D15]">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="inline-flex items-center gap-2 text-xs font-semibold text-slate-900 dark:text-slate-200">
                      <ShieldCheck className="h-4 w-4 shrink-0 accent-text" aria-hidden="true" />
                      <span>Verify build provenance</span>
                    </span>
                    <div className="flex flex-wrap items-center gap-3">
                      <a
                        href={VERIFICATION_DOC_URL}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 text-xs font-medium accent-text hover:underline"
                      >
                        <span>Verification guide</span>
                        <ArrowUpRight className="h-3 w-3" aria-hidden="true" />
                      </a>
                      <button
                        type="button"
                        onClick={handleCopyCommand}
                        aria-label="Copy verification command"
                        className="inline-flex min-h-8 items-center gap-1.5 rounded-lg border border-slate-900/10 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:text-slate-950 dark:border-white/10 dark:bg-white/[0.05] dark:text-slate-300 dark:hover:text-white"
                      >
                        {copied ? (
                          <>
                            <Check className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />
                            <span>Copied</span>
                          </>
                        ) : (
                          <>
                            <Copy className="h-3.5 w-3.5" aria-hidden="true" />
                            <span>Copy</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>

                  <pre className="mt-3 max-w-full overflow-x-auto rounded-xl bg-slate-950 px-3.5 py-3 text-xs leading-relaxed text-slate-200 dark:bg-black/65">
                    <code>{verifyCommand}</code>
                  </pre>

                  {metadata.signingCertSha256 && (
                    <div className="mt-3 font-mono text-[11px] text-slate-500 dark:text-slate-400">
                      <span className="block text-slate-600 dark:text-slate-300">
                        Signing certificate SHA-256
                      </span>
                      <code className="mt-0.5 block break-all leading-relaxed text-slate-500 dark:text-slate-400">
                        {metadata.signingCertSha256}
                      </code>
                    </div>
                  )}
                </div>
              </div>

              {/* Right: Short install steps */}
              <div className="flex min-w-0 flex-col justify-between lg:col-span-5">
                <div>
                  <h3 className="text-lg font-semibold text-slate-950 dark:text-white">
                    Install in three steps
                  </h3>
                  <ol className="mt-5 space-y-5">
                    {INSTALL_STEPS.map((item) => (
                      <li key={item.step} className="flex gap-3.5 sm:gap-4">
                        <span className="accent-icon-box flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border font-mono text-xs font-medium tabular-nums">
                          {item.step}
                        </span>
                        <div className="min-w-0">
                          <h4 className="text-sm font-semibold text-slate-950 dark:text-white">
                            {item.title}
                          </h4>
                          <p className="mt-1 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                            {item.detail}
                          </p>
                        </div>
                      </li>
                    ))}
                  </ol>
                </div>

                <p className="mt-8 border-t border-slate-900/[0.07] pt-5 text-xs leading-relaxed text-slate-500 dark:border-white/[0.08] dark:text-slate-400">
                  Looking for older versions or beta pre-releases? Browse all builds on{' '}
                  <a
                    href={RELEASES_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-medium accent-text hover:underline"
                  >
                    GitHub Releases
                  </a>
                  .
                </p>
              </div>
            </div>
          </div>
        </FadeRise>
      </div>
    </section>
  );
}
