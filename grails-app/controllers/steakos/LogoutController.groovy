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
        // Apply SPA language before session wipe so thank-you GSP is localized.
        applyUiLocale()

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

    /**
     * Resolve language from ?lang=, STEAKOS_LANG cookie, or Accept-Language.
     * LocaleChangeInterceptor alone is unreliable after session invalidate.
     */
    private void applyUiLocale() {
        String lang = params.lang?.toString()?.trim()
        if (!lang) {
            request.cookies?.each { Cookie c ->
                if (c.name == I18nWebConfig.COOKIE_NAME && c.value) {
                    lang = c.value
                }
            }
        }
        if (!lang) {
            return
        }
        lang = lang.split(/[_-]/)[0].toLowerCase()
        Locale locale
        if (lang == 'de') {
            locale = Locale.GERMAN
        } else if (lang == 'es') {
            locale = new Locale('es', 'ES')
        } else {
            locale = Locale.ENGLISH
        }
        LocaleContextHolder.setLocale(locale)
        // GSP href uses this — request.locale can lag after session wipe.
        request.setAttribute('steakosUiLang', lang)
        try {
            LocaleResolver resolver = RequestContextUtils.getLocaleResolver(request)
            resolver?.setLocale(request, response, locale)
        } catch (Exception ignored) {
        }
        // Ensure cookie survives logout for the next login page.
        Cookie langCookie = new Cookie(I18nWebConfig.COOKIE_NAME, lang)
        langCookie.path = '/'
        langCookie.maxAge = 31536000
        langCookie.httpOnly = false
        response.addCookie(langCookie)
    }

    private void expireCookie(String name) {
        Cookie c = new Cookie(name, '')
        c.path = '/'
        c.maxAge = 0
        c.httpOnly = true
        response.addCookie(c)
    }
}
