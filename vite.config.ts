import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig} from 'vite';

export default defineConfig(() => {
  const base = process.env.VITE_BASE_PATH || '/';
  return {
    root: path.resolve(__dirname, 'website'),
    publicDir: path.resolve(__dirname, 'website/public'),
    base,
    plugins: [
      react(),
      tailwindcss(),
    ],
    build: {
      outDir: path.resolve(__dirname, 'dist'),
      emptyOutDir: true,
      rollupOptions: {
        input: {
          main: path.resolve(__dirname, 'website/index.html'),
          privacy: path.resolve(__dirname, 'website/privacy.html'),
          terms: path.resolve(__dirname, 'website/terms.html'),
        },
      },
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'website'),
      },
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
    },
  };
});
