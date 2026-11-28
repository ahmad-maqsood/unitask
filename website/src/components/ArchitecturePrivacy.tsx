import {
  ArrowRight,
  Cloud,
  Database,
  HardDrive,
  Lock,
  Shield,
  Smartphone,
  Users,
  WifiOff,
} from 'lucide-react';
import { ANDROID_PERMISSIONS } from '../data/content';
import { type RepoMetadata, buildTechStackRows } from '../lib/repoMetadata';
import { getRouteHref, type PageRoute } from '../lib/routes';
import { FadeRise } from './MotionPrimitives';

interface ArchitecturePrivacyProps {
  metadata: RepoMetadata;
  onNavigate: (route: PageRoute) => void;
}

export function ArchitecturePrivacy({ metadata, onNavigate }: ArchitecturePrivacyProps) {
  const privacyHref = getRouteHref(import.meta.env.BASE_URL, 'privacy');
  const termsHref = getRouteHref(import.meta.env.BASE_URL, 'terms');
  const techStack = buildTechStackRows(metadata);
  const roomVersionLabel = metadata.versions.room
    ? `Room ${metadata.versions.room}`
    : 'Room';

  const storagePillars = [
    {
      icon: WifiOff,
      title: 'Works completely offline',
      detail:
        'Deadlines, courses, timetable, My Tasks, Markdown notes, attachments, and alarms run locally on your phone with no account required.',
    },
    {
      icon: Database,
      title: `Stored locally in ${roomVersionLabel} (SQLite)`,
      detail: `Personal data lives in ${roomVersionLabel} with explicit versioned migrations. It is never uploaded to Supabase.`,
    },
    {
      icon: Cloud,
      title: 'Optional Google Drive & Supabase',
      detail:
        'Google Drive backup uses only the hidden appdata scope—UniTask cannot see your other Drive files. Supabase is used only for Study Groups and announcements, protected by Row Level Security.',
    },
    {
      icon: Lock,
      title: 'Zero ads, zero tracking, local crash logs',
      detail:
        'There are no analytics or crash-reporting SDKs. The newest 5 crash logs stay on your device unless you explicitly choose to share them.',
    },
  ];

  const boundaryZones = [
    {
      icon: Smartphone,
      zone: 'Default · Always local',
      title: 'Your Android device',
      detail:
        'Courses, assignments, timetables, Markdown notes, attachments, and alarms stay inside private Room SQLite storage.',
    },
    {
      icon: Cloud,
      zone: 'Opt-in · Your Drive only',
      title: 'Hidden Google Drive appdata',
      detail:
        'Direct device-to-Drive backup using drive.appdata. UniTask never sees your other Drive files, and the developer cannot read backups.',
    },
    {
      icon: Users,
      zone: 'Opt-in · Shared spaces',
      title: 'Supabase Study Groups',
      detail:
        'Only used when you sign in for collaborative groups or announcements, isolated by Postgres Row Level Security.',
    },
  ];

  return (
    <section id="privacy" className="py-16 sm:py-24">
      <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
        <FadeRise className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div className="max-w-2xl">
            <p className="font-mono text-xs font-medium accent-text">
              Architecture &amp; privacy
            </p>
            <h2 className="mt-2 text-balance text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl dark:text-white">
              Local-first by architecture, transparent by default.
            </h2>
            <p className="mt-3 text-base leading-relaxed text-slate-600 dark:text-slate-400">
              Every storage boundary, dependency version, and Android permission below
              is synced directly from the open-source repository.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-4 text-sm font-medium">
            <a
              href={privacyHref}
              onClick={(event) => {
                event.preventDefault();
                onNavigate('privacy');
              }}
              className="inline-flex items-center gap-1.5 accent-text hover:underline"
            >
              <span>Read privacy policy</span>
              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </a>
            <a
              href={termsHref}
              onClick={(event) => {
                event.preventDefault();
                onNavigate('terms');
              }}
              className="inline-flex items-center gap-1.5 text-slate-600 transition-colors hover:text-slate-950 dark:text-slate-400 dark:hover:text-white"
            >
              <span>Terms of use</span>
              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </a>
          </div>
        </FadeRise>

        {/* Visual 3-zone Data Boundary Strip */}
        <FadeRise delay={0.04} className="mt-10">
          <div className="glass-panel rounded-3xl border border-slate-900/[0.08] bg-white/90 p-5 backdrop-blur-xl sm:p-7 dark:border-white/[0.08] dark:bg-[#10131D]/88">
            <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-slate-900/[0.07] pb-4 dark:border-white/[0.08]">
              <h3 className="text-sm font-semibold text-slate-950 dark:text-white">
                Where your data actually lives
              </h3>
              <span className="font-mono text-[11px] text-slate-500 dark:text-slate-400">
                Strict separation between personal and optional cloud data
              </span>
            </div>

            <div className="mt-5 grid gap-5 md:grid-cols-3 md:divide-x md:divide-slate-900/[0.07] dark:md:divide-white/[0.08]">
              {boundaryZones.map((item, index) => {
                const Icon = item.icon;
                return (
                  <div
                    key={item.title}
                    className={`min-w-0 ${index > 0 ? 'border-t border-slate-900/[0.07] pt-5 md:border-t-0 md:pt-0 md:pl-6 dark:border-white/[0.08]' : ''}`}
                  >
                    <div className="flex items-center gap-2.5">
                      <div className="accent-icon-box flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border">
                        <Icon className="h-4 w-4" aria-hidden="true" />
                      </div>
                      <span className="font-mono text-[11px] text-slate-500 dark:text-slate-400">
                        {item.zone}
                      </span>
                    </div>
                    <h4 className="mt-3 text-base font-semibold text-slate-950 dark:text-white">
                      {item.title}
                    </h4>
                    <p className="mt-1.5 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                      {item.detail}
                    </p>
                  </div>
                );
              })}
            </div>
          </div>
        </FadeRise>

        {/* Four core privacy & storage truths */}
        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          {storagePillars.map((pillar, idx) => {
            const Icon = pillar.icon;
            return (
              <FadeRise key={pillar.title} delay={idx * 0.05}>
                <div className="glass-card h-full rounded-2xl border border-slate-900/[0.08] bg-white/90 p-5 sm:p-6 dark:border-white/[0.08] dark:bg-[#10131D]/88">
                  <div className="flex items-center gap-3">
                    <div className="accent-icon-box flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border transition-colors">
                      <Icon className="h-4 w-4" aria-hidden="true" />
                    </div>
                    <h3 className="text-base font-semibold text-slate-950 dark:text-white">
                      {pillar.title}
                    </h3>
                  </div>
                  <p className="mt-3 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                    {pillar.detail}
                  </p>
                </div>
              </FadeRise>
            );
          })}
        </div>

        {/* Live-synced Tech Stack & Android Permissions side-by-side */}
        <div className="mt-6 grid gap-6 lg:grid-cols-12">
          <FadeRise className="min-w-0 lg:col-span-5">
            <div className="glass-card h-full rounded-2xl border border-slate-900/[0.08] bg-white/90 p-5 sm:p-6 dark:border-white/[0.08] dark:bg-[#10131D]/88">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2.5">
                  <HardDrive className="h-4 w-4 shrink-0 accent-text" aria-hidden="true" />
                  <h3 className="text-base font-semibold text-slate-950 dark:text-white">
                    Technology stack
                  </h3>
                </div>
                <span className="font-mono text-[11px] text-slate-400 dark:text-slate-500">
                  libs.versions.toml
                </span>
              </div>

              <dl className="mt-5 divide-y divide-slate-900/[0.07] dark:divide-white/[0.07]">
                {techStack.map((row) => (
                  <div key={row.layer} className="py-3 first:pt-0 last:pb-0">
                    <dt className="font-mono text-xs text-slate-500 dark:text-slate-400">
                      {row.layer}
                    </dt>
                    <dd className="mt-1 text-sm text-slate-800 dark:text-slate-200">
                      {row.technology}
                    </dd>
                  </div>
                ))}
              </dl>
            </div>
          </FadeRise>

          <FadeRise delay={0.08} className="min-w-0 lg:col-span-7">
            <div className="glass-card h-full rounded-2xl border border-slate-900/[0.08] bg-white/90 p-5 sm:p-6 dark:border-white/[0.08] dark:bg-[#10131D]/88">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2.5">
                  <Shield className="h-4 w-4 shrink-0 accent-text" aria-hidden="true" />
                  <h3 className="text-base font-semibold text-slate-950 dark:text-white">
                    Android permissions declared in manifest
                  </h3>
                </div>
                <span className="hidden font-mono text-[11px] text-slate-400 sm:inline dark:text-slate-500">
                  AndroidManifest.xml
                </span>
              </div>

              <ul className="mt-5 divide-y divide-slate-900/[0.07] dark:divide-white/[0.07]">
                {ANDROID_PERMISSIONS.map((perm) => (
                  <li key={perm.name} className="py-3 first:pt-0 last:pb-0">
                    <div className="flex flex-wrap items-baseline justify-between gap-2">
                      <code className="break-all text-xs font-medium text-slate-900 dark:text-slate-100">
                        {perm.name}
                      </code>
                      <span className="shrink-0 font-mono text-[11px] text-slate-500 dark:text-slate-400">
                        {perm.scope}
                      </span>
                    </div>
                    <p className="mt-1 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                      {perm.purpose}
                    </p>
                  </li>
                ))}
              </ul>
            </div>
          </FadeRise>
        </div>
      </div>
    </section>
  );
}
