import { useCallback, useEffect, useState } from 'react';
import { ACCENT_SWATCHES, type AccentSwatch } from '../data/content';

const STORAGE_KEY = 'unitask-site-accent';

function readInitialAccentId(): string {
  if (typeof window === 'undefined') return 'indigo';
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (stored && ACCENT_SWATCHES.some((s) => s.id === stored)) {
      return stored;
    }
  } catch {
    // Ignore storage access errors
  }
  return 'indigo';
}

export function useAccentTheme(): {
  accent: AccentSwatch;
  accentId: string;
  setAccentId: (id: string) => void;
  resetAccent: () => void;
} {
  const [accentId, setAccentIdState] = useState<string>(readInitialAccentId);

  const accent =
    ACCENT_SWATCHES.find((swatch) => swatch.id === accentId) ?? ACCENT_SWATCHES[0];

  useEffect(() => {
    const root = document.documentElement;
    root.style.setProperty('--accent-light', accent.light.primary);
    root.style.setProperty('--accent-light-hover', accent.light.hover);
    root.style.setProperty('--accent-light-container', accent.light.container);
    root.style.setProperty('--accent-light-on-container', accent.light.onContainer);

    root.style.setProperty('--accent-dark', accent.dark.primary);
    root.style.setProperty('--accent-dark-text', accent.dark.text);
    root.style.setProperty('--accent-dark-hover', accent.dark.hover);
    root.style.setProperty('--accent-dark-container', accent.dark.container);
    root.style.setProperty('--accent-dark-on-container', accent.dark.onContainer);

    root.style.setProperty('--accent-swatch', accent.hex);

    try {
      window.localStorage.setItem(STORAGE_KEY, accent.id);
    } catch {
      // Ignore storage access errors
    }
  }, [accent]);

  const setAccentId = useCallback((id: string) => {
    if (ACCENT_SWATCHES.some((s) => s.id === id)) {
      setAccentIdState(id);
    }
  }, []);

  const resetAccent = useCallback(() => {
    setAccentIdState('indigo');
  }, []);

  return { accent, accentId, setAccentId, resetAccent };
}
