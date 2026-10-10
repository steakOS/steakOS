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

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { localesApi, meApi } from '../api/client'
import {
  languageFromLocale,
  normalizeLocaleCode,
  readLocaleFromUrl,
  readLoginLocaleCookie,
  readStoredLocale,
  syncServerLangCookie,
  writeStoredLocale,
} from '../i18n'

/**
 * Resolve locale from login handoff / prefs / localStorage, sync to UserPreference, activate i18n.
 *
 * Login GSP is often on :6060 while the SPA is on :9001 — localStorage does not cross ports.
 * STEAKOS_UI_LOCALE / STEAKOS_LANG cookies do, and win over a stale server pref after login.
 */
export function useLocaleBootstrap() {
  const { i18n } = useTranslation()
  const [locale, setLocale] = useState(
    () => readLocaleFromUrl() || readLoginLocaleCookie() || readStoredLocale() || 'en_US',
  )
  const [locales, setLocales] = useState([])
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const cat = await localesApi.list().catch(() => ({ items: [] }))
        if (!cancelled) setLocales(cat.items || [])
      } catch {
        /* ignore */
      }

      const fromUrl = readLocaleFromUrl()
      const fromLoginCookie = readLoginLocaleCookie()
      const stored = readStoredLocale()
      try {
        const prefs = await meApi.preferences().catch(() => ({}))
        const me = await meApi.get().catch(() => null)
        const isReadOnly = !!me?.readOnly
        const prefCode = normalizeLocaleCode(prefs['ui.locale'] || prefs.locale)

        // Login screen choice (URL / cookie) beats stored prefs — ports differ for localStorage.
        let code =
          fromUrl ||
          fromLoginCookie ||
          prefCode ||
          normalizeLocaleCode(stored) ||
          normalizeLocaleCode(me?.locale?.locale) ||
          'en_US'

        const handoff = !!(fromUrl || fromLoginCookie)
        if (!isReadOnly && handoff && code !== prefCode) {
          await meApi.updatePreferences({ 'ui.locale': code }).catch(() => null)
        } else if (!isReadOnly && !prefCode && code) {
          await meApi.updatePreferences({ 'ui.locale': code }).catch(() => null)
        }

        if (!cancelled && code) {
          writeStoredLocale(code)
          syncServerLangCookie(code)
          setLocale(code)
          await i18n.changeLanguage(languageFromLocale(code))
          document.documentElement.lang = languageFromLocale(code)
          // Drop one-shot ?lang= from the address bar after applying.
          if (fromUrl && window.history?.replaceState) {
            try {
              const u = new URL(window.location.href)
              u.searchParams.delete('lang')
              u.searchParams.delete('locale')
              window.history.replaceState({}, '', u.pathname + u.search + u.hash)
            } catch {
              /* ignore */
            }
          }
        }
      } catch {
        const fallback = fromUrl || fromLoginCookie || stored
        if (fallback) {
          await i18n.changeLanguage(languageFromLocale(fallback))
        }
      } finally {
        if (!cancelled) setReady(true)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [i18n])

  const changeLocale = async (code) => {
    const normalized = normalizeLocaleCode(code) || code
    writeStoredLocale(normalized)
    syncServerLangCookie(normalized)
    setLocale(normalized)
    await i18n.changeLanguage(languageFromLocale(normalized))
    document.documentElement.lang = languageFromLocale(normalized)
    try {
      await meApi.updatePreferences({ 'ui.locale': normalized })
    } catch {
      /* offline / not logged in */
    }
  }

  return { locale, locales, ready, changeLocale }
}
