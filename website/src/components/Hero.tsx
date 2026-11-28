import { useState } from 'react';
import { motion } from 'motion/react';
import { ArrowUpRight, Download, Github, Star } from 'lucide-react';
import { REPO_URL } from '../data/content';
import { type ReleaseInfo, formatBytes, resolveAssetUrl } from '../lib/release';
import { type RepoMetadata, androidVersionLabel } from '../lib/repoMetadata';

interface HeroProps {
  release: ReleaseInfo;
  metadata: RepoMetadata;
}

export function Hero({ release, metadata }: HeroProps) {
  const [iconFailed, setIconFailed] = useState(false);
  const appIconUrl = resolveAssetUrl(import.meta.env.BASE_URL, 'app-icon.png');
  const fallbackIconUrl = resolveAssetUrl(import.meta.env.BASE_URL, 'favicon.svg');
  const downloadHref = release.apk?.downloadUrl || '#download';
  const apkSizeLabel = release.apk ? formatBytes(release.apk.size) : 'APK';
  const androidLabel = androidVersionLabel(metadata.minSdk);
  const licenseLabel = metadata.licenseName || 'MIT License';

  return (
    <section
      id="top"
      className="relative overflow-hidden pt-12 pb-10 sm:pt-20 sm:pb-14"
    >
      {/* Subtle architectural hairline grid fading cleanly to canvas */}
      <div
        aria-hidden="true"
        className="hero-grid-bg pointer-events-none absolute inset-x-0 top-0 h-[520px]"
      />

      {/* Sculpted dual-layer ambient aura synced live to active Material 3 accent */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 -top-28 mx-auto h-[460px] max-w-5xl rounded-full opacity-[0.18] blur-3xl transition-colors duration-500 dark:opacity-[0.26]"
        style={{
          background:
            'radial-gradient(ellipse 65% 55% at 50% 40%, var(--accent-swatch), transparent 72%)',
        }}
      />

      <div className="relative mx-auto max-w-4xl px-4 text-center sm:px-6 lg:px-8">
        {/* Sculptural app icon medallion */}
        <motion.div
          initial={{ opacity: 0, y: 12, scale: 0.96 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.45, ease: [0.16, 1, 0.3, 1] }}
          className="relative mx-auto mb-7 flex h-20 w-20 items-center justify-center sm:h-24 sm:w-24"
        >
          <div
            aria-hidden="true"
            className="absolute -inset-2.5 rounded-full border border-slate-900/[0.06] dark:border-white/[0.07]"
          />
          <div
            aria-hidden="true"
            className="absolute -inset-1 rounded-full opacity-35 blur-md transition-colors duration-300 dark:opacity-50"
            style={{
              background: 'radial-gradient(circle, var(--accent-swatch), transparent 70%)',
            }}
          />
          <div className="relative flex h-full w-full items-center justify-center rounded-full border border-slate-900/12 bg-white/95 p-1.5 shadow-xl shadow-slate-950/[0.1] dark:border-white/15 dark:bg-[#141826]/95 dark:shadow-black/60">
            <img
              src={iconFailed ? fallbackIconUrl : appIconUrl}
              onError={() => setIconFailed(true)}
              alt="UniTask app icon"
              width={84}
              height={84}
              className="h-full w-full rounded-full object-cover"
            />
          </div>
        </motion.div>

        <motion.p
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.03, ease: [0.16, 1, 0.3, 1] }}
          className="font-mono text-xs font-medium tracking-wide accent-text"
        >
          Open-source student utility for Android
        </motion.p>

        <motion.h1
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.48, delay: 0.06, ease: [0.16, 1, 0.3, 1] }}
          className="mx-auto mt-3 max-w-3xl text-balance text-[2.2rem] leading-[1.08] font-bold tracking-[-0.035em] text-slate-950 sm:text-5xl lg:text-[4rem] lg:leading-[1.04] dark:text-white"
        >
          Your entire semester,{' '}
          <span className="accent-headline-grad">calm and offline-first.</span>
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.48, delay: 0.1, ease: [0.16, 1, 0.3, 1] }}
          className="mx-auto mt-5 max-w-2xl text-pretty text-[15px] leading-relaxed text-slate-600 sm:text-lg dark:text-slate-400"
        >
          UniTask is a free, open-source Android app for university students. Keep
          courses, deadlines, Week A / Week B timetables, Markdown notes, and a
          Pomodoro study timer on your device—with optional study groups when you
          need them. No ads, no tracking.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.48, delay: 0.14, ease: [0.16, 1, 0.3, 1] }}
          className="mt-8 flex flex-col items-stretch justify-center gap-3 sm:flex-row sm:items-center"
        >
          <a
            href={downloadHref}
            className="accent-btn inline-flex h-12 items-center justify-center gap-2.5 whitespace-nowrap rounded-xl px-6 text-sm font-semibold transition-all hover:-translate-y-0.5"
          >
            <Download className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>Download for Android</span>
            <span className="font-mono text-xs text-white/80 tabular-nums">
              {release.tagName}
            </span>
          </a>

          <a
            href={REPO_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex h-12 items-center justify-center gap-2 whitespace-nowrap rounded-xl border border-slate-900/12 bg-white/90 px-6 text-sm font-semibold text-slate-800 backdrop-blur-md transition-all hover:-translate-y-0.5 hover:border-slate-900/25 hover:text-slate-950 dark:border-white/12 dark:bg-white/[0.04] dark:text-slate-200 dark:hover:border-white/25 dark:hover:text-white"
          >
            <Github className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>View on GitHub</span>
            <ArrowUpRight className="h-3.5 w-3.5 shrink-0 opacity-70" aria-hidden="true" />
          </a>
        </motion.div>

        {/* Live GitHub metadata spec line (unboxed, clean typographic separators) */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.45, delay: 0.2 }}
          className="mx-auto mt-7 inline-flex max-w-full flex-wrap items-center justify-center gap-x-3 gap-y-1.5 border-t border-slate-900/[0.07] pt-5 font-mono text-xs text-slate-500 tabular-nums dark:border-white/[0.08] dark:text-slate-400"
        >
          <span className="whitespace-nowrap">
            Latest {release.tagName}
          </span>
          <span aria-hidden="true" className="opacity-45">
            ·
          </span>
          <span className="whitespace-nowrap">{apkSizeLabel}</span>
          <span aria-hidden="true" className="opacity-45">
            ·
          </span>
          <span className="whitespace-nowrap">
            {androidLabel}
            {metadata.minSdk ? ` (API ${metadata.minSdk})` : ''}
          </span>
          <span aria-hidden="true" className="opacity-45">
            ·
          </span>
          <span className="whitespace-nowrap">{licenseLabel}</span>
          {typeof metadata.stars === 'number' && metadata.stars > 0 && (
            <>
              <span aria-hidden="true" className="opacity-45">
                ·
              </span>
              <span className="inline-flex items-center gap-1 whitespace-nowrap">
                <Star className="h-3 w-3 accent-text" aria-hidden="true" />
                <span>{metadata.stars} on GitHub</span>
              </span>
            </>
          )}
        </motion.div>
      </div>
    </section>
  );
}
