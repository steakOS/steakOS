// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos

import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import jakarta.servlet.http.Cookie
import java.util.Locale
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.support.RequestContextUtils
import steakos.configuration.I18nWebConfig

/**
 * Logout entry at /logout/index (SPA navbar + thank-you page).
 * Must clear both HTTP session and remember-me cookie — otherwise /login/auth
 * silently re-authenticates and redirects to /dashboard.
 */
@Secured('permitAll')
class LogoutController {

    SpringSecurityService springSecurityService

    static allowedMethods = [index: ['GET', 'POST']]

    /** Default Grails Spring Security remember-me cookie name. */
    static final String REMEMBER_ME_COOKIE = 'grails_remember_me'

    def index() {
        // Apply SPA language so the thank-you GSP is localized.
        applyUiLocale()

        // GET only shows the page. Ending the session is POST.
        if (request.method != 'POST') {
            response.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate')
            response.setHeader('Pragma', 'no-cache')
            render(view: '/logout/index')
            return
        }

        def authentication = SecurityContextHolder.context?.authentication
        boolean wasLoggedIn = springSecurityService?.isLoggedIn() ||
                (authentication != null && authentication.authenticated && authentication.principal != 'anonymousUser')

        if (wasLoggedIn || authentication != null) {
            try {
                SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler()
                sessionLogout.invalidateHttpSession = true
                sessionLogout.clearAuthentication = true
                sessionLogout.logout(request, response, authentication)
            } catch (Exception ignored) {
                // still clear cookies / context below
            }
            try {
                new CookieClearingLogoutHandler('JSESSIONID', REMEMBER_ME_COOKIE)
                        .logout(request, response, authentication)
            } catch (Exception ignored) {
            }
            SecurityContextHolder.clearContext()
        }

        // Belt-and-braces: expire cookies even if handlers no-op (proxy / already cleared)
        expireCookie('JSESSIONID')
        expireCookie(REMEMBER_ME_COOKIE)

        // Drop any leftover TOTP / Nostr session flags if session still alive
        try {
            request.getSession(false)?.invalidate()
        } catch (Exception ignored) {
        }

        // Session invalidation can drop locale — re-apply for the thank-you render.
        applyUiLocale()

        response.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate')
        response.setHeader('Pragma', 'no-cache')
        render(view: '/logout/index')
    }

    /** Full locale cookie shared with SPA (e.g. es_ES). */
    static final String UI_LOCALE_COOKIE = 'STEAKOS_UI_LOCALE'

    /**
     * Resolve language from ?lang= / ?locale=, STEAKOS_UI_LOCALE / STEAKOS_LANG cookies.
     * LocaleChangeInterceptor alone is unreliable after session invalidate.
     * Always re-persist both cookies so the login screen does not fall back to en_US.
     */
    private void applyUiLocale() {
        String raw = params.locale?.toString()?.trim() ?: params.lang?.toString()?.trim()
        String fullFromCookie = null
        String shortFromCookie = null
        request.cookies?.each { Cookie c ->
            if (c.name == UI_LOCALE_COOKIE && c.value) {
                fullFromCookie = c.value
            } else if (c.name == I18nWebConfig.COOKIE_NAME && c.value) {
                shortFromCookie = c.value
            }
        }
        if (!raw) {
            raw = fullFromCookie ?: shortFromCookie
        }
        if (!raw) {
            return
        }

        String full = normalizeFullLocale(raw, fullFromCookie)
        String lang = full.split(/[_-]/)[0].toLowerCase()

        Locale locale
        if (lang == 'de') {
            locale = Locale.GERMAN
        } else if (lang == 'es') {
            locale = new Locale('es', 'ES')
        } else {
            locale = Locale.ENGLISH
        }
        LocaleContextHolder.setLocale(locale)
        // GSP href uses these — request.locale can lag after session wipe.
        request.setAttribute('steakosUiLang', lang)
        request.setAttribute('steakosUiLocale', full)
        try {
            LocaleResolver resolver = RequestContextUtils.getLocaleResolver(request)
            resolver?.setLocale(request, response, locale)
        } catch (Exception ignored) {
        }
        // Ensure cookies survive logout for the next login page (picker + SPA handoff).
        Cookie langCookie = new Cookie(I18nWebConfig.COOKIE_NAME, lang)
        langCookie.path = '/'
        langCookie.maxAge = 31536000
        langCookie.httpOnly = false
        response.addCookie(langCookie)

        Cookie fullCookie = new Cookie(UI_LOCALE_COOKIE, full)
        fullCookie.path = '/'
        fullCookie.maxAge = 31536000
        fullCookie.httpOnly = false
        response.addCookie(fullCookie)
    }

    /** Map short or full codes to a stable full locale id. */
    private static String normalizeFullLocale(String raw, String preferredFull) {
        String c = (raw ?: '').trim().replace('-', '_')
        if (!c) {
            return 'en_US'
        }
        if (c.contains('_')) {
            return c
        }
        String pref = (preferredFull ?: '').trim().replace('-', '_')
        if (pref.startsWith(c + '_')) {
            return pref
        }
        if (c.equalsIgnoreCase('de')) return 'de_DE'
        if (c.equalsIgnoreCase('es')) return 'es_ES'
        if (c.equalsIgnoreCase('en')) return 'en_US'
        return c
    }

    private void expireCookie(String name) {
        Cookie c = new Cookie(name, '')
        c.path = '/'
        c.maxAge = 0
        c.httpOnly = true
        response.addCookie(c)
    }
}
