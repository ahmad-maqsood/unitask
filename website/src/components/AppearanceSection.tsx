import { useState } from 'react';
import { motion } from 'motion/react';
import { Check, Clock, Globe, Palette, RotateCcw, Sparkles } from 'lucide-react';
import { ACCENT_SWATCHES, APPEARANCE_VIEWS, type AccentSwatch } from '../data/content';
import type { SiteTheme } from '../hooks/useTheme';
import { resolveAssetUrl } from '../lib/release';
import { FadeRise } from './MotionPrimitives';

interface AppearanceSectionProps {
  accent: AccentSwatch;
  theme: SiteTheme;
  onSelectAccent: (id: string) => void;
  onResetAccent: () => void;
}

export function AppearanceSection({
  accent,
  theme,
  onSelectAccent,
  onResetAccent,
}: AppearanceSectionProps) {
  const [selectedViewId, setSelectedViewId] = useState(APPEARANCE_VIEWS[0].id);
  const [sampleDone, setSampleDone] = useState(false);

  const activeView =
    APPEARANCE_VIEWS.find((view) => view.id === selectedViewId) ?? APPEARANCE_VIEWS[0];
  const palette = theme === 'dark' ? accent.dark : accent.light;

  function handleSelectView(viewId: string) {
    setSelectedViewId(viewId);
    if (viewId === 'accent') {
      onSelectAccent('rose');
    } else if (viewId === 'dark' || viewId === 'light') {
      onSelectAccent('indigo');
    }
  }

  return (
    <section id="themes" className="py-16 sm:py-24">
      <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
        <FadeRise>
          <div className="glass-panel relative overflow-hidden rounded-3xl border border-slate-900/[0.08] bg-white/90 p-4 backdrop-blur-xl sm:p-8 lg:p-11 dark:border-white/[0.08] dark:bg-[#10131D]/88">
            <div
              aria-hidden="true"
              className="pointer-events-none absolute -bottom-28 -left-28 h-80 w-80 rounded-full opacity-[0.14] blur-3xl transition-colors duration-500 dark:opacity-[0.2]"
              style={{
                background: `radial-gradient(circle, ${accent.hex}, transparent 70%)`,
              }}
            />

            <div className="relative grid items-center gap-9 lg:grid-cols-12 lg:gap-12">
              {/* Sculpted hardware frame showing the 4 real captured appearance screenshots */}
              <div className="order-2 flex min-w-0 flex-col items-center lg:order-1 lg:col-span-5">
                <div className="relative w-[224px] max-w-full sm:w-[268px]">
                  <div
                    aria-hidden="true"
                    className="pointer-events-none absolute -inset-6 rounded-[48px] blur-2xl transition-colors duration-300"
                    style={{
                      background: `radial-gradient(circle at center, ${accent.hex}42, transparent 70%)`,
                    }}
                  />
                  {/* Physical side keys */}
                  <div
                    aria-hidden="true"
                    className="pointer-events-none absolute top-24 -right-[3px] h-14 w-[3px] rounded-r-sm bg-slate-600/60 dark:bg-white/20"
                  />
                  <div
                    aria-hidden="true"
                    className="pointer-events-none absolute top-42 -right-[3px] h-9 w-[3px] rounded-r-sm bg-slate-600/50 dark:bg-white/15"
                  />

                  <div className="phone-chassis relative rounded-[38px] p-2.5">
                    <div
                      aria-hidden="true"
                      className="pointer-events-none absolute top-[15px] left-1/2 z-10 h-2.5 w-2.5 -translate-x-1/2 rounded-full bg-black/90 ring-1 ring-white/15"
                    />
                    <div className="relative aspect-[9/16] w-full overflow-hidden rounded-[28px] bg-slate-950">
                      {APPEARANCE_VIEWS.map((view) => {
                        const isCurrent = view.id === activeView.id;
                        const src = resolveAssetUrl(import.meta.env.BASE_URL, view.file);
                        return (
                          <motion.img
                            key={view.id}
                            src={src}
                            alt={view.alt}
                            initial={false}
                            animate={{ opacity: isCurrent ? 1 : 0 }}
                            transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
                            aria-hidden={!isCurrent}
                            className={`absolute inset-0 h-full w-full object-cover object-top ${
                              isCurrent ? 'z-[1]' : 'pointer-events-none z-0'
                            }`}
                          />
                        );
                      })}
                    </div>
                  </div>
                </div>
                <p className="mt-4 text-center font-mono text-[11px] text-slate-500 dark:text-slate-400">
                  Captured Android screenshot · {activeView.label}
                </p>
              </div>

              {/* Controls: Screenshot switcher + Live Material 3 Accent Studio */}
              <div className="order-1 min-w-0 lg:order-2 lg:col-span-7">
                <p className="font-mono text-xs font-medium accent-text">
                  Personalisation
                </p>
                <h2 className="mt-2 text-balance text-2xl font-bold tracking-tight text-slate-950 sm:text-4xl dark:text-white">
                  Tuned for clarity in light, dark, and {ACCENT_SWATCHES.length} accents.
                </h2>
                <p className="mt-3 text-sm leading-relaxed text-slate-600 sm:text-base dark:text-slate-400">
                  Switch between System, Light, and Dark modes, pick from{' '}
                  {ACCENT_SWATCHES.length} WCAG AA-checked Material 3 accent palettes,
                  and let the UniTask Today widget follow your colour automatically.
                </p>

                {/* Real captured screenshot selector with animated active indicator */}
                <div
                  role="tablist"
                  aria-label="UniTask appearance previews"
                  className="mt-6 grid grid-cols-2 gap-2 sm:flex sm:flex-wrap"
                >
                  {APPEARANCE_VIEWS.map((view) => {
                    const isSelected = view.id === activeView.id;
                    return (
                      <button
                        key={view.id}
                        type="button"
                        role="tab"
                        aria-selected={isSelected}
                        onClick={() => handleSelectView(view.id)}
                        className={`relative min-h-10 truncate whitespace-nowrap rounded-xl border px-3 py-2 text-center text-xs font-medium transition-colors sm:px-3.5 sm:text-sm ${
                          isSelected
                            ? 'border-transparent text-white'
                            : 'border-slate-900/10 bg-slate-900/[0.02] text-slate-700 hover:border-slate-900/20 hover:text-slate-950 dark:border-white/10 dark:bg-white/[0.03] dark:text-slate-300 dark:hover:border-white/20 dark:hover:text-white'
                        }`}
                      >
                        {isSelected && (
                          <motion.span
                            layoutId="appearance-active-tab"
                            transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                            aria-hidden="true"
                            className="accent-btn absolute inset-0 rounded-xl"
                          />
                        )}
                        <span className="relative z-[1]">{view.label}</span>
                      </button>
                    );
                  })}
                </div>

                <div className="mt-4 border-t border-slate-900/[0.07] pt-4 dark:border-white/[0.08]">
                  <h3 className="text-sm font-semibold text-slate-950 dark:text-white">
                    {activeView.title}
                  </h3>
                  <p className="mt-1 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                    {activeView.description}
                  </p>
                </div>

                {/* Live Material 3 Accent Studio */}
                <div className="mt-6 rounded-2xl border border-slate-900/[0.08] bg-slate-900/[0.025] p-4 sm:p-5 dark:border-white/[0.08] dark:bg-[#0B0E17]/90">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="inline-flex items-center gap-2 text-xs font-semibold text-slate-900 dark:text-slate-200">
                      <Palette className="h-4 w-4 shrink-0 accent-text" aria-hidden="true" />
                      <span>Try all {ACCENT_SWATCHES.length} accents live</span>
                    </span>

                    <div className="flex items-center gap-2.5">
                      <span className="font-mono text-xs text-slate-500 dark:text-slate-400">
                        {accent.name} · {palette.primary}
                      </span>
                      {accent.id !== 'indigo' && (
                        <button
                          type="button"
                          onClick={onResetAccent}
                          className="inline-flex items-center gap-1 rounded-lg border border-slate-900/10 bg-white px-2 py-1 text-[11px] font-medium text-slate-700 transition-colors hover:text-slate-950 dark:border-white/10 dark:bg-white/[0.06] dark:text-slate-300 dark:hover:text-white"
                        >
                          <RotateCcw className="h-3 w-3" aria-hidden="true" />
                          <span>Reset</span>
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Balanced 6+5 swatch grid on narrow phones, full studio row on sm+ */}
                  <div
                    role="radiogroup"
                    aria-label="Apply UniTask accent theme live"
                    className="mt-3.5 grid grid-cols-6 justify-items-center gap-2 sm:flex sm:flex-wrap sm:justify-start"
                  >
                    {ACCENT_SWATCHES.map((swatch) => {
                      const isPicked = swatch.id === accent.id;
                      return (
                        <button
                          key={swatch.id}
                          type="button"
                          role="radio"
                          aria-checked={isPicked}
                          aria-label={`Apply ${swatch.name} accent (${swatch.hex})`}
                          onClick={() => {
                            onSelectAccent(swatch.id);
                            if (swatch.id === 'rose') {
                              setSelectedViewId('accent');
                            }
                          }}
                          className={`relative flex h-9 w-9 items-center justify-center rounded-full border-2 transition-transform hover:scale-110 ${
                            isPicked
                              ? 'scale-110 border-slate-950 dark:border-white'
                              : 'border-transparent'
                          }`}
                        >
                          <span
                            className="flex h-6 w-6 items-center justify-center rounded-full shadow-inner"
                            style={{ backgroundColor: swatch.hex }}
                          >
                            {isPicked && (
                              <Check
                                className="h-3.5 w-3.5 text-white drop-shadow"
                                aria-hidden="true"
                              />
                            )}
                          </span>
                        </button>
                      );
                    })}
                  </div>

                  {/* Interactive Material 3 UniTask Today Glance Widget Preview */}
                  <div
                    className="mt-4 rounded-xl border p-3.5 transition-colors duration-200 sm:p-4"
                    style={{
                      backgroundColor: palette.container,
                      borderColor: `${palette.primary}3D`,
                      color: palette.onContainer,
                    }}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex min-w-0 items-center gap-2">
                        <span
                          className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-xs font-bold"
                          style={{
                            backgroundColor: palette.primary,
                            color: palette.onPrimary,
                          }}
                        >
                          U
                        </span>
                        <span className="truncate text-xs font-semibold">
                          UniTask Today · {accent.name} ({theme === 'dark' ? 'Dark' : 'Light'})
                        </span>
                      </div>
                      <span className="inline-flex items-center gap-1 font-mono text-[11px] opacity-80">
                        <Clock className="h-3 w-3 shrink-0" aria-hidden="true" />
                        <span>Live Material 3 preview</span>
                      </span>
                    </div>

                    {/* Interactive sample row matching real Glance widget behavior */}
                    <button
                      type="button"
                      onClick={() => setSampleDone((prev) => !prev)}
                      aria-pressed={sampleDone}
                      className="mt-3 flex w-full items-center justify-between gap-3 rounded-lg px-2.5 py-2 text-left transition-colors"
                      style={{
                        backgroundColor: `${palette.primary}18`,
                      }}
                    >
                      <span className="flex min-w-0 items-center gap-2.5">
                        <span
                          className="flex h-4 w-4 shrink-0 items-center justify-center rounded border transition-colors"
                          style={{
                            borderColor: palette.primary,
                            backgroundColor: sampleDone ? palette.primary : 'transparent',
                            color: palette.onPrimary,
                          }}
                        >
                          {sampleDone && <Check className="h-3 w-3" aria-hidden="true" />}
                        </span>
                        <span
                          className={`truncate text-xs font-medium ${
                            sampleDone ? 'line-through opacity-70' : ''
                          }`}
                        >
                          CS201 · Linked-list implementation
                        </span>
                      </span>
                      <span className="shrink-0 font-mono text-[10px] opacity-80">
                        {sampleDone ? 'Done' : 'Tap to test'}
                      </span>
                    </button>

                    <div className="mt-2.5 flex flex-wrap items-center gap-x-4 gap-y-1 font-mono text-[11px] opacity-85">
                      <span>primary {palette.primary}</span>
                      <span>container {palette.container}</span>
                    </div>
                  </div>

                  <p className="mt-3 text-xs leading-relaxed text-slate-500 dark:text-slate-400">
                    Selecting a swatch updates this website and the widget preview using
                    the exact hex schemes from{' '}
                    <code className="text-[11px]">AccentThemes.kt</code>. The captured
                    Android screenshots show Dark, Light, Rose, and General settings.
                  </p>
                </div>

                {/* Additional real personalisation details */}
                <div className="mt-6 grid gap-4 border-t border-slate-900/[0.07] pt-5 sm:grid-cols-2 dark:border-white/[0.08]">
                  <div className="flex items-start gap-3">
                    <Globe className="mt-0.5 h-4 w-4 shrink-0 accent-text" aria-hidden="true" />
                    <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-400">
                      <strong className="font-semibold text-slate-900 dark:text-slate-200">
                        English &amp; Urdu (RTL):
                      </strong>{' '}
                      Follows your device language or lets you choose in-app on Android 13+.
                    </p>
                  </div>
                  <div className="flex items-start gap-3">
                    <Sparkles className="mt-0.5 h-4 w-4 shrink-0 accent-text" aria-hidden="true" />
                    <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-400">
                      <strong className="font-semibold text-slate-900 dark:text-slate-200">
                        Themed icon &amp; sounds:
                      </strong>{' '}
                      Android 13+ monochrome icon support and per-channel custom reminder tones.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </FadeRise>
      </div>
    </section>
  );
}
