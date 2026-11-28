import {
  BellRing,
  CalendarCheck2,
  Clock,
  FileText,
  ShieldCheck,
  Timer,
  TrendingUp,
  Users,
} from 'lucide-react';
import { CAPABILITIES, type CapabilityIconName } from '../data/content';
import { FadeRise } from './MotionPrimitives';

function CapabilityIcon({ name }: { name: CapabilityIconName }) {
  const iconClass = 'h-5 w-5';
  switch (name) {
    case 'calendar-check':
      return <CalendarCheck2 className={iconClass} aria-hidden="true" />;
    case 'file-text':
      return <FileText className={iconClass} aria-hidden="true" />;
    case 'clock':
      return <Clock className={iconClass} aria-hidden="true" />;
    case 'timer':
      return <Timer className={iconClass} aria-hidden="true" />;
    case 'bell-ring':
      return <BellRing className={iconClass} aria-hidden="true" />;
    case 'shield-check':
      return <ShieldCheck className={iconClass} aria-hidden="true" />;
    case 'users':
      return <Users className={iconClass} aria-hidden="true" />;
    case 'trending-up':
      return <TrendingUp className={iconClass} aria-hidden="true" />;
  }
}

const MARQUEE_FOOTNOTES: Record<string, string[]> = {
  'deadlines-coursework': ['Priority levels', 'Subtask checklists', 'Up to 25 MB offline files'],
  'markdown-notes': ['Live preview', '[[ Backlinks', 'PDF & ZIP export'],
  'reminders-widget': ['Exact AlarmManager', 'Snooze & Mark Done', 'Glance Today widget'],
  'private-backups': ['Hidden Drive appdata', '2 rotating copies', 'Encrypted local export'],
};

export function CapabilitiesBento() {
  return (
    <section id="features" className="py-16 sm:py-24">
      <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
        <FadeRise className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div className="max-w-2xl">
            <p className="font-mono text-xs font-medium accent-text">
              Capabilities
            </p>
            <h2 className="mt-2 text-balance text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl dark:text-white">
              Everything coursework demands, without subscription clutter.
            </h2>
            <p className="mt-3 text-base leading-relaxed text-slate-600 dark:text-slate-400">
              Designed around how university students actually plan classes, assignments,
              notes, and group deadlines.
            </p>
          </div>
          <p className="shrink-0 font-mono text-xs text-slate-500 tabular-nums dark:text-slate-400">
            {CAPABILITIES.length} core modules · 100% free
          </p>
        </FadeRise>

        <div className="mt-10 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          {CAPABILITIES.map((item, idx) => {
            const footnotes = MARQUEE_FOOTNOTES[item.id];
            const isWide = item.span === 'wide';

            return (
              <FadeRise
                key={item.id}
                delay={Math.min(idx * 0.04, 0.2)}
                className={
                  isWide
                    ? 'md:col-span-2 lg:col-span-2'
                    : 'md:col-span-1 lg:col-span-1'
                }
              >
                <article className="glass-card group relative flex h-full flex-col justify-between overflow-hidden rounded-2xl border border-slate-900/[0.08] bg-white/90 p-5 backdrop-blur-md transition-all duration-200 hover:-translate-y-0.5 hover:border-slate-900/20 sm:p-6 dark:border-white/[0.08] dark:bg-[#10131D]/88 dark:hover:border-white/20 dark:hover:bg-[#141927]">
                  {/* Subtle top-right hover aura */}
                  <div
                    aria-hidden="true"
                    className="pointer-events-none absolute -top-12 -right-12 h-28 w-28 rounded-full opacity-0 blur-2xl transition-opacity duration-300 group-hover:opacity-25 dark:group-hover:opacity-35"
                    style={{
                      background: 'radial-gradient(circle, var(--accent-swatch), transparent 70%)',
                    }}
                  />

                  <div className="relative">
                    <div className="flex items-start justify-between gap-4">
                      <div className="accent-icon-box flex h-10 w-10 items-center justify-center rounded-xl border transition-transform duration-200 group-hover:scale-105">
                        <CapabilityIcon name={item.icon} />
                      </div>
                      <span className="font-mono text-[11px] text-slate-400 tabular-nums dark:text-slate-500">
                        {String(idx + 1).padStart(2, '0')}
                      </span>
                    </div>
                    <h3 className="mt-5 text-lg font-semibold tracking-tight text-slate-950 dark:text-white">
                      {item.title}
                    </h3>
                    <p className="mt-2 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                      {item.description}
                    </p>
                  </div>

                  {footnotes && (
                    <div className="relative mt-5 flex flex-wrap items-center gap-x-2.5 gap-y-1 border-t border-slate-900/[0.06] pt-3.5 font-mono text-[11px] text-slate-500 dark:border-white/[0.07] dark:text-slate-400">
                      {footnotes.map((label, footIdx) => (
                        <span key={label} className="inline-flex items-center gap-2.5">
                          {footIdx > 0 && (
                            <span aria-hidden="true" className="opacity-45">
                              ·
                            </span>
                          )}
                          <span>{label}</span>
                        </span>
                      ))}
                    </div>
                  )}
                </article>
              </FadeRise>
            );
          })}
        </div>
      </div>
    </section>
  );
}
