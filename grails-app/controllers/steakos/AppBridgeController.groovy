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

package steakos

import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import grails.util.Holders
import jakarta.servlet.http.Cookie
import java.net.URLEncoder
import steakos.configuration.I18nWebConfig
import steakos.configuration.LocaleService

/**
 * After GSP login, users hit /app and are sent to the Vite SPA dashboard (FE-1 / F2 / F4).
 * Redirect host follows the request (LAN IP or localhost), not a hard-coded localhost URL.
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class AppBridgeController {

    SpringSecurityService springSecurityService
    LocaleService localeService

    def index() {
        String localeCode = persistLoginLocale()
        // Relative Location keeps the browser on the public host (asterix.local / LAN IP).
        // Absolute http://127.0.0.1:9001/... breaks remote clients after Vite→Grails proxy.
        redirect(uri: spaDashboardPath(localeCode))
    }

    /**
     * Carry login-screen language into UserPreference + SPA query
     * (localStorage on :6060 does not apply on :9001).
     */
    private String persistLoginLocale() {
        String raw = params.lang?.toString()?.trim()
        if (!raw) {
            raw = params.locale?.toString()?.trim()
        }
        if (!raw) {
            request.cookies?.each { Cookie c ->
                if (c.name == 'STEAKOS_UI_LOCALE' && c.value) {
                    raw = c.value
                } else if (!raw && c.name == I18nWebConfig.COOKIE_NAME && c.value) {
                    raw = c.value
                }
            }
        }
        if (!raw && request.locale) {
            raw = request.locale.language
        }
        String normalized = localeService?.normalize(raw)
        if (!normalized) {
            return null
        }
        try {
            User u = springSecurityService?.currentUser as User
            if (u?.username) {
                localeService.setUserLocale(u.username, normalized)
            }
        } catch (Exception ignored) {
        }
        normalized
    }

    /**
     * SPA dashboard path (relative). Prefer this over absolute URLs so login via
     * http://asterix.local:9001 (or any LAN host) does not bounce to 127.0.0.1.
     */
    private String spaDashboardPath(String localeCode = null) {
        if (!localeCode) {
            return '/dashboard'
        }
        String lang = localeService?.languageOf(localeCode) ?: localeCode.split('_')[0]
        return "/dashboard?lang=${URLEncoder.encode(lang, 'UTF-8')}&locale=${URLEncoder.encode(localeCode, 'UTF-8')}"
    }

    /**
     * Absolute SPA dashboard URL when a caller still needs one (emails, rare deep links).
     * Prefer request.serverName/port (honours Spring ForwardedHeaderFilter) over raw Host,
     * which Vite changeOrigin sets to 127.0.0.1:6060.
     */
    private String spaDashboardUrl(String localeCode = null) {
        String path = spaDashboardPath(localeCode)
        String xfHost = request.getHeader('X-Forwarded-Host')?.trim()
        String host = xfHost
        if (host?.contains(',')) {
            host = host.split(',')[0].trim()
        }
        if (!host) {
            String name = request.serverName
            int port = request.serverPort
            if (name) {
                boolean defaultPort = (request.secure && port == 443) || (!request.secure && port == 80)
                host = defaultPort ? name : "${name}:${port}"
            }
        }
        if (!host) {
            host = request.getHeader('Host')?.trim()
        }
        String scheme = request.getHeader('X-Forwarded-Proto')?.trim()
        if (!scheme) {
            scheme = request.secure ? 'https' : 'http'
        }
        if (host) {
            if (host.endsWith(':6060')) {
                host = host[0..-6] + ':9001'
            } else if (host.endsWith(':7070')) {
                host = host[0..-6] + ':9001'
            } else if (host.endsWith(':9090')) {
                host = host[0..-6] + ':9001'
            } else if (!host.contains(':') && (request.serverPort in [6060, 7070, 9090])) {
                host = "${host}:9001"
            }
            // Loopback public host → prefer configured frontend URL when not loopback
            if (host.startsWith('127.0.0.1') || host.startsWith('localhost') || host.startsWith('[::1]')) {
                String configured = Holders.config.getProperty('steakos.frontend.url', String, null)?.trim()
                if (configured && !(configured.contains('127.0.0.1') || configured.contains('localhost'))) {
                    return configured.replaceAll(/\/$/, '') + path
                }
            }
            return "${scheme}://${host}${path}"
        }
        String configured = Holders.config.getProperty('steakos.frontend.url', String, null)?.trim()
        if (configured) {
            return configured.replaceAll(/\/$/, '') + path
        }
        return "http://localhost:9001${path}"
    }
}
