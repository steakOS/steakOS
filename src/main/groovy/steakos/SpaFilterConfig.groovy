// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

package steakos

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/** Runs before the asset pipeline so packaged Vite files win over a 404. */
@Configuration
class SpaFilterConfig {

    @Bean
    FilterRegistrationBean<SpaWebFilter> spaWebFilterRegistration() {
        FilterRegistrationBean<SpaWebFilter> bean = new FilterRegistrationBean<>(new SpaWebFilter())
        bean.order = Ordered.HIGHEST_PRECEDENCE
        bean.addUrlPatterns('/*')
        return bean
    }
}
