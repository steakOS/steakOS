// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// FE-1: port 9001, proxy Grails API + login (session cookie on :9001)
export default defineConfig({
  plugins: [
    react(),
    {
      // /tour and /tour/ must hit public/tour/index.html — not the React SPA (catch-all → dashboard)
      name: 'steakos-tour-index',
      configureServer(server) {
        server.middlewares.use((req, _res, next) => {
          const url = req.url?.split('?')[0]
          if (url === '/tour' || url === '/tour/') {
            req.url = '/tour/index.html'
          }
          next()
        })
      },
    },
  ],
  server: {
    port: 9001,
    // LAN clients: host 0.0.0.0 so 192.168.x.x:9001 and asterix.local work
    host: true,
    // Vite blocks unknown Host headers by default (403). Allow mDNS / LAN names.
    allowedHosts: ['asterix.local', '.local', 'localhost'],
    proxy: {
      '/api': grailsProxy(),
      '/login': grailsProxy(),
      '/logout': grailsProxy(),
      '/logoff': grailsProxy(),
      '/assets': grailsProxy(),
      '/app': grailsProxy(),
    },
  },
})

/**
 * Proxy to Grails on :6060.
 * - Forwards X-Forwarded-Host so Spring can build correct absolute URLs
 * - Rewrites Location redirects from 127.0.0.1:6060 / localhost:6060 back to the
 *   browser-facing host:port (fixes LAN login redirect to unreachable 127.0.0.1:6060)
 */
function grailsProxy() {
  return {
    target: 'http://127.0.0.1:6060',
    changeOrigin: true,
    configure(proxy) {
      proxy.on('proxyReq', (proxyReq, req) => {
        const host = req.headers.host
        if (host) {
          proxyReq.setHeader('X-Forwarded-Host', host)
          proxyReq.setHeader('X-Forwarded-Proto', 'http')
          const port = host.includes(':') ? host.split(':').pop() : '80'
          proxyReq.setHeader('X-Forwarded-Port', port)
          proxyReq.setHeader('X-Forwarded-For', req.socket?.remoteAddress || '127.0.0.1')
        }
      })
      proxy.on('proxyRes', (proxyRes, req) => {
        const loc = proxyRes.headers['location']
        if (!loc || typeof loc !== 'string') return
        const publicHost = req.headers.host
        if (!publicHost) return
        const publicOrigin = `http://${publicHost}`
        // Rewrite loopback absolute Locations (API :6060 or mistaken SPA :9001) to the
        // browser-facing host (asterix.local / LAN IP). Relative Locations are left alone.
        const rewritten = loc
          .replace(/^https?:\/\/127\.0\.0\.1:6060\b/, publicOrigin)
          .replace(/^https?:\/\/localhost:6060\b/, publicOrigin)
          .replace(/^https?:\/\/\[::1\]:6060\b/, publicOrigin)
          .replace(/^https?:\/\/0\.0\.0\.0:6060\b/, publicOrigin)
          .replace(/^https?:\/\/127\.0\.0\.1:9001\b/, publicOrigin)
          .replace(/^https?:\/\/localhost:9001\b/, publicOrigin)
          .replace(/^https?:\/\/\[::1\]:9001\b/, publicOrigin)
          .replace(/^https?:\/\/0\.0\.0\.0:9001\b/, publicOrigin)
        if (rewritten !== loc) {
          proxyRes.headers['location'] = rewritten
        }
      })
    },
  }
}
