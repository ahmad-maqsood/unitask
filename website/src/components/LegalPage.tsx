import { useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  ChevronDown,
  Cloud,
  Code2,
  HardDrive,
  Mail,
  Scale,
  ShieldCheck,
  Smartphone,
  Users,
} from 'lucide-react';
import { motion } from 'motion/react';
import type { LegalDocument } from '../data/legal';
import { getRouteHref, type PageRoute } from '../lib/routes';

interface LegalPageProps {
  document: LegalDocument;
  onNavigate: (route: PageRoute) => void;
}

const PRIVACY_PILLARS = [
  {
    icon: Smartphone,
    label: 'On your phone',
    detail: 'Courses, tasks, timetables, notes, and attachments stay in private local app storage.',
  },
  {
    icon: Cloud,
    label: 'Hidden Drive backup',
    detail: 'Optional Google Drive backup uses drive.appdata only — UniTask cannot see other files.',
  },
  {
    icon: Users,
    label: 'Optional study groups',
    detail: 'Supabase is only used if you sign in for groups or announcements; personal notes stay local.',
  },
];

const TERMS_PILLARS = [
  {
    icon: Code2,
    label: 'Free & MIT open source',
    detail: 'No ads, no paid tiers. The source code is published openly on GitHub under the MIT License.',
  },
  {
    icon: HardDrive,
    label: 'Your content, your backups',
    detail: 'You keep ownership of everything you add and remain responsible for keeping important backups.',
  },
  {
    icon: Scale,
    label: 'Fair & respectful use',
    detail: 'Use study groups respectfully, follow your university rules, and keep backup reminders for critical deadlines.',
  },
];

function estimateReadMinutes(doc: LegalDocument): number {
  const parts: string[] = [doc.summary, doc.intro];
  for (const s of doc.sections) {
    parts.push(s.heading);
    if (s.paragraphs) parts.push(...s.paragraphs);
    if (s.afterParagraphs) parts.push(...s.afterParagraphs);
    if (s.bullets) {
      for (const b of s.bullets) {
        if (b.lead) parts.push(b.lead);
        parts.push(b.text);
      }
    }
  }
  const words = parts.join(' ').trim().split(/\s+/).filter(Boolean).length;
  return Math.max(2, Math.round(words / 210));
}

export function LegalPage({ document, onNavigate }: LegalPageProps) {
  const isPrivacy = document.route === 'privacy';
  const siblingRoute: PageRoute = isPrivacy ? 'terms' : 'privacy';
  const siblingLabel = isPrivacy ? 'Terms of Use' : 'Privacy Policy';
  const homeHref = getRouteHref(import.meta.env.BASE_URL, 'home');
  const siblingHref = getRouteHref(import.meta.env.BASE_URL, siblingRoute);

  const [activeSectionId, setActiveSectionId] = useState(document.sections[0]?.id ?? '');
  const [mobileTocOpen, setMobileTocOpen] = useState(false);

  const readMinutes = useMemo(() => estimateReadMinutes(document), [document]);
  const pillars = isPrivacy ? PRIVACY_PILLARS : TERMS_PILLARS;

  useEffect(() => {
    setActiveSectionId(document.sections[0]?.id ?? '');
    setMobileTocOpen(false);
  }, [document]);

  useEffect(() => {
    if (typeof window === 'undefined' || !('IntersectionObserver' in window)) return;
    const elements = document.sections
      .map((s) => window.document.getElementById(s.id))
      .filter((el): el is HTMLElement => Boolean(el));

    if (elements.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]?.target.id) {
          setActiveSectionId(visible[0].target.id);
        }
      },
      { rootMargin: '-18% 0px -65% 0px', threshold: 0 },
    );

    for (const el of elements) observer.observe(el);
    return () => observer.disconnect();
  }, [document]);

  return (
    <div className="relative overflow-hidden py-10 sm:py-16">
      {/* Editorial dotted backdrop unique to legal pages */}
      <div
        aria-hidden="true"
        className="editorial-dot-bg pointer-events-none absolute inset-x-0 top-0 h-[420px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 -top-28 mx-auto h-[360px] max-w-4xl rounded-full opacity-[0.18] blur-3xl transition-colors duration-300 dark:opacity-[0.24]"
        style={{
          background: 'radial-gradient(ellipse at center, var(--accent-swatch), transparent 70%)',
        }}
      />

      <div className="relative mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
        {/* Top editorial navigation row */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <a
            href={homeHref}
            onClick={(event) => {
              event.preventDefault();
              onNavigate('home');
            }}
            className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-slate-900/10 bg-white/85 px-3.5 text-sm font-medium text-slate-700 backdrop-blur-md transition-colors hover:border-slate-900/20 hover:text-slate-950 dark:border-white/10 dark:bg-white/[0.04] dark:text-slate-300 dark:hover:border-white/20 dark:hover:text-white"
          >
            <ArrowLeft className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>Back to UniTask</span>
          </a>

          <a
            href={siblingHref}
            onClick={(event) => {
              event.preventDefault();
              onNavigate(siblingRoute);
            }}
            className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-slate-900/10 bg-white/85 px-3.5 text-sm font-medium text-slate-700 backdrop-blur-md transition-colors hover:border-slate-900/20 hover:text-slate-950 dark:border-white/10 dark:bg-white/[0.04] dark:text-slate-300 dark:hover:border-white/20 dark:hover:text-white"
          >
            {isPrivacy ? (
              <Scale className="h-4 w-4 shrink-0 accent-text" aria-hidden="true" />
            ) : (
              <ShieldCheck className="h-4 w-4 shrink-0 accent-text" aria-hidden="true" />
            )}
            <span>Read {siblingLabel}</span>
          </a>
        </div>

        {/* Distinct Editorial Hero Banner */}
        <motion.div
          key={document.route}
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.42, ease: [0.16, 1, 0.3, 1] }}
          className="mt-8 border-b border-slate-900/[0.08] pb-8 sm:mt-10 sm:pb-10 dark:border-white/[0.08]"
        >
          <div className="flex items-center gap-3">
            <div className="accent-icon-box flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border">
              {isPrivacy ? (
                <ShieldCheck className="h-5 w-5" aria-hidden="true" />
              ) : (
                <Scale className="h-5 w-5" aria-hidden="true" />
              )}
            </div>
            <p className="font-mono text-xs font-medium accent-text">
              {document.eyebrow}
            </p>
          </div>

          <h1 className="mt-4 max-w-3xl text-balance text-3xl font-bold tracking-[-0.03em] text-slate-950 sm:text-5xl dark:text-white">
            {document.title}
          </h1>

          <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-xs text-slate-500 tabular-nums dark:text-slate-400">
            <span>Last updated {document.lastUpdated}</span>
            <span aria-hidden="true" className="opacity-45">
              ·
            </span>
            <span>{document.sections.length} sections</span>
            <span aria-hidden="true" className="opacity-45">
              ·
            </span>
            <span>~{readMinutes} min read</span>
            <span aria-hidden="true" className="opacity-45">
              ·
            </span>
            <span>UniTask Android &amp; website</span>
          </div>

          {/* Page-specific 3-pillar visual overview strip */}
          <div className="mt-8 grid gap-4 md:grid-cols-3">
            {pillars.map((pillar) => {
              const Icon = pillar.icon;
              return (
                <div
                  key={pillar.label}
                  className="glass-card rounded-2xl border border-slate-900/[0.08] bg-white/90 p-5 backdrop-blur-md dark:border-white/[0.08] dark:bg-[#10131D]/88"
                >
                  <div className="flex items-center gap-2.5">
                    <div className="accent-icon-box flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border">
                      <Icon className="h-4 w-4" aria-hidden="true" />
                    </div>
                    <h2 className="text-sm font-semibold text-slate-950 dark:text-white">
                      {pillar.label}
                    </h2>
                  </div>
                  <p className="mt-2.5 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                    {pillar.detail}
                  </p>
                </div>
              );
            })}
          </div>
        </motion.div>

        {/* Main 2-column reading layout: Sticky Scroll-Spy Sidebar + Editorial Document Flow */}
        <div className="mt-8 grid items-start gap-8 sm:mt-10 lg:grid-cols-12 lg:gap-12">
          {/* Mobile & Tablet compact collapsible Contents drawer */}
          <div className="lg:hidden">
            <button
              type="button"
              onClick={() => setMobileTocOpen((open) => !open)}
              aria-expanded={mobileTocOpen}
              aria-controls="legal-mobile-toc"
              className="glass-card flex min-h-12 w-full items-center justify-between gap-3 rounded-2xl border border-slate-900/[0.08] bg-white/90 px-4 py-3 text-left text-sm font-semibold text-slate-900 dark:border-white/[0.08] dark:bg-[#10131D]/88 dark:text-white"
            >
              <span>Jump to section ({document.sections.length})</span>
              <ChevronDown
                className={`h-4 w-4 shrink-0 text-slate-500 transition-transform duration-200 ${
                  mobileTocOpen ? 'rotate-180' : ''
                }`}
                aria-hidden="true"
              />
            </button>

            {mobileTocOpen && (
              <nav
                id="legal-mobile-toc"
                aria-label="On this page"
                className="glass-card mt-2 rounded-2xl border border-slate-900/[0.08] bg-white/95 p-3 dark:border-white/[0.08] dark:bg-[#10131D]/95"
              >
                <ol className="grid grid-cols-1 gap-1 sm:grid-cols-2">
                  {document.sections.map((section) => (
                    <li key={section.id}>
                      <a
                        href={`#${section.id}`}
                        onClick={() => setMobileTocOpen(false)}
                        className="flex min-h-10 items-baseline gap-2.5 rounded-lg px-3 py-2 text-sm text-slate-700 transition-colors hover:bg-slate-900/[0.04] hover:text-slate-950 dark:text-slate-300 dark:hover:bg-white/[0.06] dark:hover:text-white"
                      >
                        <span className="shrink-0 font-mono text-[11px] accent-text tabular-nums">
                          {section.number}
                        </span>
                        <span className="leading-snug">{section.heading}</span>
                      </a>
                    </li>
                  ))}
                </ol>
              </nav>
            )}
          </div>

          {/* Sticky Desktop Scroll-Spy Contents Sidebar */}
          <aside className="hidden lg:sticky lg:top-24 lg:col-span-4 lg:block">
            <nav
              aria-label="On this page"
              className="glass-card rounded-2xl border border-slate-900/[0.08] bg-white/90 p-5 backdrop-blur-xl dark:border-white/[0.08] dark:bg-[#10131D]/88"
            >
              <p className="font-mono text-xs font-medium text-slate-500 dark:text-slate-400">
                Contents
              </p>
              <ol className="mt-3 space-y-0.5">
                {document.sections.map((section) => {
                  const isActive = section.id === activeSectionId;
                  return (
                    <li key={section.id}>
                      <a
                        href={`#${section.id}`}
                        onClick={() => setActiveSectionId(section.id)}
                        aria-current={isActive ? 'location' : undefined}
                        className={`flex min-h-9 items-baseline gap-2.5 rounded-xl px-3 py-1.5 text-sm transition-colors ${
                          isActive
                            ? 'accent-tab-active border font-medium'
                            : 'border border-transparent text-slate-600 hover:bg-slate-900/[0.04] hover:text-slate-950 dark:text-slate-400 dark:hover:bg-white/[0.05] dark:hover:text-white'
                        }`}
                      >
                        <span className="shrink-0 font-mono text-[11px] opacity-70 tabular-nums">
                          {section.number}
                        </span>
                        <span className="leading-snug">{section.heading}</span>
                      </a>
                    </li>
                  );
                })}
              </ol>

              <div className="mt-5 border-t border-slate-900/[0.07] pt-4 dark:border-white/[0.08]">
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Questions or data requests?
                </p>
                <a
                  href="mailto:am9566222@gmail.com"
                  className="mt-1 inline-flex items-center gap-1.5 font-mono text-xs font-medium accent-text hover:underline"
                >
                  <Mail className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  <span>am9566222@gmail.com</span>
                </a>
              </div>
            </nav>
          </aside>

          {/* Editorial Document Body */}
          <div className="min-w-0 lg:col-span-8">
            {/* In Short highlighted executive summary */}
            <div
              className="glass-panel rounded-2xl border p-5 backdrop-blur-xl sm:p-7"
              style={{
                backgroundColor: 'color-mix(in srgb, var(--accent-container) 58%, transparent)',
                borderColor: 'color-mix(in srgb, var(--accent) 28%, transparent)',
              }}
            >
              <p className="font-mono text-xs font-medium accent-text">In short</p>
              <p className="mt-2.5 text-base leading-relaxed font-medium text-slate-900 sm:text-[17px] dark:text-slate-100">
                {document.summary}
              </p>
            </div>

            <p className="mt-7 text-base leading-relaxed text-slate-600 dark:text-slate-300">
              {document.intro}
            </p>

            {/* Clean editorial numbered sections separated by hairlines */}
            <div className="mt-8 divide-y divide-slate-900/[0.08] rounded-3xl border border-slate-900/[0.08] bg-white/90 px-5 backdrop-blur-md sm:px-8 dark:divide-white/[0.08] dark:border-white/[0.08] dark:bg-[#10131D]/88">
              {document.sections.map((section) => (
                <section
                  key={section.id}
                  id={section.id}
                  className="scroll-mt-24 py-7 sm:grid sm:grid-cols-12 sm:gap-6 sm:py-8"
                >
                  <div className="mb-2 sm:col-span-2 sm:mb-0">
                    <span className="font-mono text-xs font-semibold accent-text tabular-nums sm:text-sm">
                      {section.number}
                    </span>
                  </div>

                  <div className="min-w-0 sm:col-span-10">
                    <h2 className="text-xl font-semibold tracking-tight text-slate-950 dark:text-white">
                      {section.heading}
                    </h2>

                    {section.paragraphs && (
                      <div className="mt-3.5 space-y-3 text-sm leading-7 text-slate-600 sm:text-[15px] dark:text-slate-300">
                        {section.paragraphs.map((paragraph) => (
                          <p key={paragraph}>{paragraph}</p>
                        ))}
                      </div>
                    )}

                    {section.bullets && (
                      <ul className="mt-3.5 space-y-2.5 text-sm leading-7 text-slate-600 sm:text-[15px] dark:text-slate-300">
                        {section.bullets.map((bullet) => (
                          <li key={bullet.text} className="flex items-start gap-3">
                            <span
                              aria-hidden="true"
                              className="mt-2.5 h-1.5 w-1.5 shrink-0 rounded-full"
                              style={{ backgroundColor: 'var(--accent-text)' }}
                            />
                            <span className="min-w-0">
                              {bullet.lead && (
                                <strong className="font-semibold text-slate-900 dark:text-white">
                                  {bullet.lead}{' '}
                                </strong>
                              )}
                              {bullet.text}
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}

                    {section.afterParagraphs && (
                      <div className="mt-3.5 space-y-3 text-sm leading-7 text-slate-600 sm:text-[15px] dark:text-slate-300">
                        {section.afterParagraphs.map((paragraph) => (
                          <p key={paragraph}>{paragraph}</p>
                        ))}
                      </div>
                    )}

                    {section.externalLink && (
                      <p className="mt-3.5 text-sm leading-7 text-slate-600 sm:text-[15px] dark:text-slate-300">
                        {section.externalLink.prefix}
                        <a
                          href={section.externalLink.href}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-0.5 font-medium underline underline-offset-4 accent-text hover:opacity-80"
                        >
                          <span>{section.externalLink.label}</span>
                          <ArrowUpRight className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                        </a>
                        {section.externalLink.suffix}
                      </p>
                    )}

                    {section.internalLink && (
                      <p className="mt-3.5 text-sm leading-7 text-slate-600 sm:text-[15px] dark:text-slate-300">
                        {section.internalLink.prefix}
                        <a
                          href={getRouteHref(import.meta.env.BASE_URL, section.internalLink.route)}
                          onClick={(event) => {
                            event.preventDefault();
                            onNavigate(section.internalLink!.route);
                          }}
                          className="font-medium underline underline-offset-4 accent-text hover:opacity-80"
                        >
                          {section.internalLink.label}
                        </a>
                        {section.internalLink.suffix}
                      </p>
                    )}

                    {section.emailContact && (
                      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-900/[0.08] bg-slate-900/[0.025] px-4 py-3.5 dark:border-white/[0.08] dark:bg-white/[0.03]">
                        <span className="text-sm text-slate-600 dark:text-slate-300">
                          {section.emailContact.prefix}
                        </span>
                        <a
                          href={`mailto:${section.emailContact.email}`}
                          className="inline-flex items-center gap-2 break-all font-mono text-sm font-medium accent-text hover:underline"
                        >
                          <Mail className="h-4 w-4 shrink-0" aria-hidden="true" />
                          <span>{section.emailContact.email}</span>
                        </a>
                      </div>
                    )}
                  </div>
                </section>
              ))}
            </div>

            {/* Bottom sibling document switch card */}
            <div className="glass-card mt-8 flex flex-col items-start justify-between gap-4 rounded-2xl border border-slate-900/[0.08] bg-white/90 p-5 sm:flex-row sm:items-center sm:p-6 dark:border-white/[0.08] dark:bg-[#10131D]/88">
              <div>
                <p className="font-mono text-xs text-slate-500 dark:text-slate-400">
                  Continue reading
                </p>
                <p className="mt-1 text-base font-semibold text-slate-950 dark:text-white">
                  UniTask {siblingLabel}
                </p>
              </div>
              <a
                href={siblingHref}
                onClick={(event) => {
                  event.preventDefault();
                  onNavigate(siblingRoute);
                }}
                className="accent-btn inline-flex min-h-11 items-center gap-2 whitespace-nowrap rounded-xl px-5 text-sm font-semibold transition-all hover:-translate-y-0.5"
              >
                <span>Open {siblingLabel}</span>
                <ArrowRight className="h-4 w-4 shrink-0" aria-hidden="true" />
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
