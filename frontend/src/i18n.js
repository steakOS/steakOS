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

import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import enCommon from './locales/en/common.json'
import enSales from './locales/en/sales.json'
import enDomain from './locales/en/domain.json'
import enHelp from './locales/en/help.json'
import enDesks from './locales/en/desks.json'
import enContent from './locales/en/content.json'
import enSetup from './locales/en/setup.json'
import enProcesses from './locales/en/processes.json'
import deCommon from './locales/de/common.json'
import deSales from './locales/de/sales.json'
import deDomain from './locales/de/domain.json'
import deHelp from './locales/de/help.json'
import deDesks from './locales/de/desks.json'
import deContent from './locales/de/content.json'
import deSetup from './locales/de/setup.json'
import deProcesses from './locales/de/processes.json'
import esCommon from './locales/es/common.json'
import esSales from './locales/es/sales.json'
import esDomain from './locales/es/domain.json'
import esHelp from './locales/es/help.json'
import esDesks from './locales/es/desks.json'
import esContent from './locales/es/content.json'
import esSetup from './locales/es/setup.json'
import esProcesses from './locales/es/processes.json'

export const LOCALE_STORAGE_KEY = 'steakos.ui.locale'
/** Must match I18nWebConfig.COOKIE_NAME — GSP login/logout locale. */
export const SERVER_LANG_COOKIE = 'STEAKOS_LANG'
/** Full locale (e.g. de_DE) — shared across :6060 login and :9001 SPA (localStorage is not). */
export const UI_LOCALE_COOKIE = 'STEAKOS_UI_LOCALE'

export function languageFromLocale(localeCode) {
  if (!localeCode) return 'en'
  const c = String(localeCode).replace('-', '_')
  if (c.startsWith('de')) return 'de'
  if (c.startsWith('es')) return 'es'
  return 'en'
}

/** Map short lang or full code → steakOS locale id. */
export function normalizeLocaleCode(code) {
  if (!code) return null
  const c = String(code).trim().replace('-', '_')
  if (!c) return null
  if (c === 'en' || c.startsWith('en_')) {
    if (c === 'en_SG') return 'en_SG'
    return c.startsWith('en_') ? c : 'en_US'
  }
  if (c === 'de' || c.startsWith('de_')) {
    if (c === 'de_AT') return 'de_AT'
    if (c === 'de_CH') return 'de_CH'
    return c === 'de' ? 'de_DE' : c
  }
  if (c === 'es' || c.startsWith('es_')) {
    return c === 'es' ? 'es_ES' : c
  }
  return c
}

function readCookie(name) {
  try {
    const parts = document.cookie ? document.cookie.split(';') : []
    for (const part of parts) {
      const idx = part.indexOf('=')
      if (idx < 0) continue
      const k = part.slice(0, idx).trim()
      if (k !== name) continue
      return decodeURIComponent(part.slice(idx + 1).trim())
    }
  } catch {
    /* ignore */
  }
  return null
}

/** Locale chosen on login GSP (cookie survives port change; localStorage does not). */
export function readLoginLocaleCookie() {
  return (
    normalizeLocaleCode(readCookie(UI_LOCALE_COOKIE)) ||
    normalizeLocaleCode(readCookie(SERVER_LANG_COOKIE))
  )
}

/** Keep CookieLocaleResolver + SPA handoff cookies in sync. */
export function syncServerLangCookie(localeCode) {
  try {
    const full = normalizeLocaleCode(localeCode) || 'en_US'
    const lang = languageFromLocale(full)
    const maxAge = 'max-age=31536000'
    document.cookie = `${SERVER_LANG_COOKIE}=${encodeURIComponent(lang)}; path=/; SameSite=Lax; ${maxAge}`
    document.cookie = `${UI_LOCALE_COOKIE}=${encodeURIComponent(full)}; path=/; SameSite=Lax; ${maxAge}`
  } catch {
    /* ignore */
  }
}

export function logoutHref() {
  const lang = languageFromLocale(readStoredLocale() || readLoginLocaleCookie())
  return `/logout/index?lang=${encodeURIComponent(lang)}`
}

/** Optional ?lang= / ?locale= on SPA entry (AppBridge after login). */
export function readLocaleFromUrl() {
  try {
    const q = new URLSearchParams(window.location.search)
    return normalizeLocaleCode(q.get('locale') || q.get('lang'))
  } catch {
    return null
  }
}

export function intlLocale(localeCode) {
  if (!localeCode) return 'en-US'
  return String(localeCode).replace('_', '-')
}

export function readStoredLocale() {
  try {
    return localStorage.getItem(LOCALE_STORAGE_KEY)
  } catch {
    return null
  }
}

export function writeStoredLocale(code) {
  try {
    if (code) localStorage.setItem(LOCALE_STORAGE_KEY, code)
  } catch {
    /* ignore */
  }
  syncServerLangCookie(code)
}

void i18n.use(initReactI18next).init({
  resources: {
    en: {
      common: enCommon,
      sales: enSales,
      domain: enDomain,
      help: enHelp,
      desks: enDesks,
      content: enContent,
      setup: enSetup,
      processes: enProcesses,
    },
    de: {
      common: deCommon,
      sales: deSales,
      domain: deDomain,
      help: deHelp,
      desks: deDesks,
      content: deContent,
      setup: deSetup,
      processes: deProcesses,
    },
    es: {
      common: esCommon,
      sales: esSales,
      domain: esDomain,
      help: esHelp,
      desks: esDesks,
      content: esContent,
      setup: esSetup,
      processes: esProcesses,
    },
  },
  lng: languageFromLocale(readStoredLocale() || 'en_US'),
  fallbackLng: 'en',
  defaultNS: 'common',
  interpolation: { escapeValue: false },
})

export default i18n
