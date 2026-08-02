import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Vite 5+ rejects any request whose Host header isn't on this list (e.g.
  // a Tailscale MagicDNS name) — comma-separated in VITE_ALLOWED_HOSTS
  // (web-ui/.env), editable from the admin panel's Settings page ("Dev
  // server allowed hosts") instead of hand-editing this file each time.
  // loadEnv (not `import.meta.env`) is required here since this file runs
  // in Node before Vite's own env injection exists yet.
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const allowedHosts = (env.VITE_ALLOWED_HOSTS ?? '')
    .split(',')
    .map((host) => host.trim())
    .filter((host) => host.length > 0)

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 8080,
      strictPort: true,
      host: true,
      allowedHosts: allowedHosts.length > 0 ? allowedHosts : undefined,
      // Proxy API/WS calls to chat-gateway so the browser only ever talks to
      // :8080 — keeps CORS simple locally and lets a single Tailscale Funnel
      // port (which only forwards one port) expose the whole app.
      proxy: {
        '/api': 'http://localhost:8081',
        '/health': 'http://localhost:8081',
        '/ws': { target: 'ws://localhost:8081', ws: true },
      },
    },
  }
})
