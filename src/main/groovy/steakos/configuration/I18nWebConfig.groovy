// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.configuration

import groovy.transform.CompileStatic
import java.util.Locale
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.i18n.CookieLocaleResolver
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor

/**
 * Server-side locale for GSP login/logout: cookie + ?lang= change.
 * SPA still uses localStorage steakos.ui.locale independently.
 */
@CompileStatic
@Configuration
class I18nWebConfig implements WebMvcConfigurer {

    public static final String COOKIE_NAME = 'STEAKOS_LANG'

    @Bean
    @Primary
    LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver(COOKIE_NAME)
        resolver.setDefaultLocale(Locale.ENGLISH)
        resolver
    }

    @Override
    void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor()
        interceptor.setParamName('lang')
        registry.addInterceptor(interceptor)
    }
}
