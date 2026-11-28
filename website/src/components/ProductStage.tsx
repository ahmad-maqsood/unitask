import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { ChevronLeft, ChevronRight, Expand, X } from 'lucide-react';
import { PRODUCT_SCREENS } from '../data/content';
import { resolveAssetUrl, stepIndex } from '../lib/release';

export function ProductStage() {
  const [activeIndex, setActiveIndex] = useState(0);
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [brokenImages, setBrokenImages] = useState<Record<string, boolean>>({});
  const closeButtonRef = useRef<HTMLButtonElement | null>(null);
  const triggerButtonRef = useRef<HTMLButtonElement | null>(null);

  const activeScreen = PRODUCT_SCREENS[activeIndex];
  const activeSrc = resolveAssetUrl(import.meta.env.BASE_URL, activeScreen.file);

  const goPrev = useCallback(() => {
    setActiveIndex((prev) => stepIndex(prev, -1, PRODUCT_SCREENS.length));
  }, []);

  const goNext = useCallback(() => {
    setActiveIndex((prev) => stepIndex(prev, 1, PRODUCT_SCREENS.length));
  }, []);

  const openLightbox = useCallback(() => {
    setLightboxOpen(true);
  }, []);

  const closeLightbox = useCallback(() => {
    setLightboxOpen(false);
    triggerButtonRef.current?.focus();
  }, []);

  useEffect(() => {
    if (!lightboxOpen) return;

    closeButtonRef.current?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeLightbox();
      } else if (event.key === 'ArrowRight') {
        event.preventDefault();
        goNext();
      } else if (event.key === 'ArrowLeft') {
        event.preventDefault();
        goPrev();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [lightboxOpen, closeLightbox, goNext, goPrev]);

  return (
    <section id="screens" className="py-8 sm:py-14">
      <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
        <motion.div
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.18, ease: [0.16, 1, 0.3, 1] }}
        >
          <div className="glass-panel relative overflow-hidden rounded-3xl border border-slate-900/[0.08] bg-white/90 p-4 backdrop-blur-xl sm:p-8 lg:p-11 dark:border-white/[0.08] dark:bg-[#10131D]/88">
            {/* Subtle corner specular glow */}
            <div
              aria-hidden="true"
              className="pointer-events-none absolute -top-24 -right-24 h-72 w-72 rounded-full opacity-[0.12] blur-3xl transition-colors duration-500 dark:opacity-[0.18]"
              style={{
                background: 'radial-gradient(circle, var(--accent-swatch), transparent 70%)',
              }}
            />

            <div className="relative grid items-center gap-8 lg:grid-cols-12 lg:gap-12">
              {/* Left: Screen selector tabs and details */}
              <div className="min-w-0 lg:col-span-6">
                <p className="font-mono text-xs font-medium accent-text">
                  Inside UniTask
                </p>
                <h2 className="mt-2 text-balance text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl lg:text-[2.15rem] lg:leading-[1.15] dark:text-white">
                  Built for real semesters, screen by screen.
                </h2>
                <p className="mt-3 text-sm leading-relaxed text-slate-600 sm:text-base dark:text-slate-400">
                  Every screenshot is captured directly from the Android app. Explore
                  each workflow or tap the phone to inspect full-size.
                </p>

                <div
                  role="tablist"
                  aria-label="UniTask app screens"
                  className="mt-6 grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-2"
                >
                  {PRODUCT_SCREENS.map((screen, idx) => {
                    const isActive = idx === activeIndex;
                    return (
                      <button
                        key={screen.id}
                        type="button"
                        role="tab"
                        aria-selected={isActive}
                        aria-controls={`screen-panel-${screen.id}`}
                        id={`screen-tab-${screen.id}`}
                        onClick={() => setActiveIndex(idx)}
                        className={`relative flex min-h-11 min-w-0 items-center justify-between gap-2 rounded-xl border px-3 py-2.5 text-left text-sm font-medium transition-colors ${
                          isActive
                            ? 'border-transparent text-slate-950 dark:text-white'
                            : 'border-slate-900/[0.07] bg-slate-900/[0.02] text-slate-600 hover:border-slate-900/15 hover:text-slate-950 dark:border-white/[0.06] dark:bg-white/[0.02] dark:text-slate-400 dark:hover:border-white/15 dark:hover:text-white'
                        }`}
                      >
                        {isActive && (
                          <motion.span
                            layoutId="product-stage-active-tab"
                            transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                            aria-hidden="true"
                            className="accent-tab-active absolute inset-0 rounded-xl border"
                          />
                        )}
                        <span className="relative z-[1] truncate">{screen.label}</span>
                        <span className="relative z-[1] shrink-0 font-mono text-[11px] opacity-60 tabular-nums">
                          {String(idx + 1).padStart(2, '0')}
                        </span>
                      </button>
                    );
                  })}
                </div>

                {/* Active screen caption */}
                <div
                  id={`screen-panel-${activeScreen.id}`}
                  role="tabpanel"
                  aria-labelledby={`screen-tab-${activeScreen.id}`}
                  className="mt-6 border-t border-slate-900/[0.07] pt-5 dark:border-white/[0.08]"
                >
                  <div className="flex items-baseline justify-between gap-4">
                    <h3 className="text-base font-semibold text-slate-950 dark:text-white">
                      {activeScreen.title}
                    </h3>
                    <span className="shrink-0 font-mono text-xs text-slate-500 tabular-nums dark:text-slate-400">
                      {activeIndex + 1} / {PRODUCT_SCREENS.length}
                    </span>
                  </div>
                  <p className="mt-1.5 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                    {activeScreen.caption}
                  </p>
                </div>
              </div>

              {/* Right: Sculpted Android device stage */}
              <div className="flex min-w-0 flex-col items-center lg:col-span-6">
                <div className="relative flex w-full items-center justify-center gap-4 sm:gap-6">
                  {/* Side prev button: shown on sm+ so compact 320px phones never squeeze */}
                  <button
                    type="button"
                    onClick={goPrev}
                    aria-label="Previous screenshot"
                    className="hidden h-11 w-11 shrink-0 items-center justify-center rounded-full border border-slate-900/10 bg-white text-slate-700 shadow-sm transition-all hover:scale-105 hover:border-slate-900/20 hover:text-slate-950 sm:inline-flex dark:border-white/10 dark:bg-[#161A27] dark:text-slate-300 dark:hover:border-white/25 dark:hover:text-white"
                  >
                    <ChevronLeft className="h-5 w-5" aria-hidden="true" />
                  </button>

                  {/* Sculpted anodized Android hardware frame */}
                  <div className="relative w-[224px] max-w-full sm:w-[274px]">
                    <div
                      aria-hidden="true"
                      className="pointer-events-none absolute -inset-6 rounded-[48px] opacity-22 blur-2xl transition-colors duration-300 dark:opacity-28"
                      style={{
                        background:
                          'radial-gradient(circle at center, var(--accent-swatch), transparent 70%)',
                      }}
                    />

                    {/* Subtle physical side keys (volume + power) */}
                    <div
                      aria-hidden="true"
                      className="pointer-events-none absolute top-24 -right-[3px] h-14 w-[3px] rounded-r-sm bg-slate-600/60 dark:bg-white/20"
                    />
                    <div
                      aria-hidden="true"
                      className="pointer-events-none absolute top-42 -right-[3px] h-9 w-[3px] rounded-r-sm bg-slate-600/50 dark:bg-white/15"
                    />

                    <div className="phone-chassis relative rounded-[38px] p-2.5">
                      {/* Top earpiece slit + camera punch hole */}
                      <div
                        aria-hidden="true"
                        className="pointer-events-none absolute top-[15px] left-1/2 z-10 h-2.5 w-2.5 -translate-x-1/2 rounded-full bg-black/90 ring-1 ring-white/15"
                      />

                      <button
                        ref={triggerButtonRef}
                        type="button"
                        onClick={openLightbox}
                        aria-label={`Enlarge ${activeScreen.title} screenshot`}
                        className="group relative block aspect-[9/16] w-full overflow-hidden rounded-[28px] bg-slate-950 text-left"
                      >
                        {PRODUCT_SCREENS.map((screen, idx) => {
                          const isCurrent = idx === activeIndex;
                          const src = resolveAssetUrl(import.meta.env.BASE_URL, screen.file);
                          return (
                            <motion.div
                              key={screen.id}
                              initial={false}
                              animate={{
                                opacity: isCurrent ? 1 : 0,
                                scale: isCurrent ? 1 : 0.99,
                              }}
                              transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
                              aria-hidden={!isCurrent}
                              className={`absolute inset-0 h-full w-full ${
                                isCurrent ? 'z-[1]' : 'pointer-events-none z-0'
                              }`}
                            >
                              {brokenImages[screen.id] ? (
                                <div className="flex h-full w-full flex-col items-center justify-center bg-slate-900 px-6 text-center text-slate-300">
                                  <span className="text-sm font-medium">{screen.title}</span>
                                  <span className="mt-1 text-xs text-slate-400">
                                    Screenshot preview unavailable
                                  </span>
                                </div>
                              ) : (
                                <img
                                  src={src}
                                  alt={screen.alt}
                                  onError={() =>
                                    setBrokenImages((prev) => ({ ...prev, [screen.id]: true }))
                                  }
                                  className="h-full w-full object-cover object-top"
                                />
                              )}
                            </motion.div>
                          );
                        })}

                        <span className="pointer-events-none absolute right-3 bottom-3 z-10 inline-flex items-center gap-1.5 rounded-lg bg-slate-950/82 px-2.5 py-1.5 text-xs font-medium text-white opacity-90 backdrop-blur-md transition-all group-hover:scale-105 group-hover:opacity-100">
                          <Expand className="h-3.5 w-3.5" aria-hidden="true" />
                          <span>Zoom</span>
                        </span>
                      </button>
                    </div>
                  </div>

                  {/* Side next button: shown on sm+ */}
                  <button
                    type="button"
                    onClick={goNext}
                    aria-label="Next screenshot"
                    className="hidden h-11 w-11 shrink-0 items-center justify-center rounded-full border border-slate-900/10 bg-white text-slate-700 shadow-sm transition-all hover:scale-105 hover:border-slate-900/20 hover:text-slate-950 sm:inline-flex dark:border-white/10 dark:bg-[#161A27] dark:text-slate-300 dark:hover:border-white/25 dark:hover:text-white"
                  >
                    <ChevronRight className="h-5 w-5" aria-hidden="true" />
                  </button>
                </div>

                {/* Bottom navigation controls: mobile prev/next + dots indicator */}
                <div className="mt-5 flex items-center justify-center gap-3">
                  <button
                    type="button"
                    onClick={goPrev}
                    aria-label="Previous screenshot"
                    className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-slate-900/10 bg-white text-slate-700 shadow-sm transition-colors hover:text-slate-950 sm:hidden dark:border-white/10 dark:bg-[#161A27] dark:text-slate-300 dark:hover:text-white"
                  >
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                  </button>

                  <div
                    className="flex items-center justify-center gap-1.5"
                    aria-label="Screenshot position"
                  >
                    {PRODUCT_SCREENS.map((screen, idx) => {
                      const isCurrent = idx === activeIndex;
                      return (
                        <button
                          key={screen.id}
                          type="button"
                          onClick={() => setActiveIndex(idx)}
                          aria-label={`Show ${screen.label} screenshot`}
                          aria-current={isCurrent ? 'true' : undefined}
                          className={`h-2 rounded-full transition-all ${
                            isCurrent
                              ? 'w-6'
                              : 'w-2 bg-slate-300 hover:bg-slate-400 dark:bg-white/20 dark:hover:bg-white/40'
                          }`}
                          style={isCurrent ? { backgroundColor: 'var(--accent-text)' } : undefined}
                        />
                      );
                    })}
                  </div>

                  <button
                    type="button"
                    onClick={goNext}
                    aria-label="Next screenshot"
                    className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-slate-900/10 bg-white text-slate-700 shadow-sm transition-colors hover:text-slate-950 sm:hidden dark:border-white/10 dark:bg-[#161A27] dark:text-slate-300 dark:hover:text-white"
                  >
                    <ChevronRight className="h-4 w-4" aria-hidden="true" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </motion.div>
      </div>

      {/* Accessible Lightbox Modal (responsive across 320px portrait, 667x375 landscape, and desktop) */}
      <AnimatePresence>
        {lightboxOpen && (
          <motion.div
            role="dialog"
            aria-modal="true"
            aria-label={`${activeScreen.title} enlarged screenshot`}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            onClick={(event) => {
              if (event.target === event.currentTarget) {
                closeLightbox();
              }
            }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/92 p-3 backdrop-blur-md sm:p-6"
          >
            <div className="relative flex max-h-[94vh] w-full max-w-3xl flex-col items-center">
              <div className="mb-3 flex w-full items-center justify-between gap-3 text-white">
                <div className="min-w-0">
                  <p className="truncate font-[family-name:var(--font-display)] text-sm font-semibold sm:text-base">
                    {activeScreen.title}
                  </p>
                  <p className="font-mono text-[11px] text-slate-400 tabular-nums sm:text-xs">
                    {activeIndex + 1} / {PRODUCT_SCREENS.length} · ← → or Esc
                  </p>
                </div>

                <button
                  ref={closeButtonRef}
                  type="button"
                  onClick={closeLightbox}
                  aria-label="Close lightbox"
                  className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-white/15 bg-white/10 text-white transition-colors hover:bg-white/20"
                >
                  <X className="h-5 w-5" aria-hidden="true" />
                </button>
              </div>

              <div className="flex min-h-0 w-full items-center justify-center gap-3 sm:gap-5">
                <button
                  type="button"
                  onClick={goPrev}
                  aria-label="Previous screenshot in lightbox"
                  className="hidden h-11 w-11 shrink-0 items-center justify-center rounded-full border border-white/15 bg-white/10 text-white transition-colors hover:bg-white/20 sm:inline-flex"
                >
                  <ChevronLeft className="h-5 w-5" aria-hidden="true" />
                </button>

                <motion.img
                  key={activeScreen.id}
                  initial={{ opacity: 0, scale: 0.97 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ duration: 0.2 }}
                  src={activeSrc}
                  alt={activeScreen.alt}
                  className="max-h-[68vh] w-auto max-w-full rounded-2xl border border-white/15 object-contain shadow-2xl sm:max-h-[74vh]"
                />

                <button
                  type="button"
                  onClick={goNext}
                  aria-label="Next screenshot in lightbox"
                  className="hidden h-11 w-11 shrink-0 items-center justify-center rounded-full border border-white/15 bg-white/10 text-white transition-colors hover:bg-white/20 sm:inline-flex"
                >
                  <ChevronRight className="h-5 w-5" aria-hidden="true" />
                </button>
              </div>

              <div className="mt-3 flex w-full items-center justify-between gap-3 sm:justify-center">
                <button
                  type="button"
                  onClick={goPrev}
                  aria-label="Previous screenshot in lightbox"
                  className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-white/15 bg-white/10 text-white transition-colors hover:bg-white/20 sm:hidden"
                >
                  <ChevronLeft className="h-5 w-5" aria-hidden="true" />
                </button>

                <p className="max-w-xl text-center text-xs leading-relaxed text-slate-300 sm:text-sm">
                  {activeScreen.caption}
                </p>

                <button
                  type="button"
                  onClick={goNext}
                  aria-label="Next screenshot in lightbox"
                  className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-white/15 bg-white/10 text-white transition-colors hover:bg-white/20 sm:hidden"
                >
                  <ChevronRight className="h-5 w-5" aria-hidden="true" />
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </section>
  );
}
