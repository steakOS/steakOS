// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment

/**
 * Fails production startup when database or encryption secrets are missing or known-weak.
 * Runs before the application context is refreshed.
 */
class SecretsStartupGuard implements EnvironmentPostProcessor, Ordered {

    @Override
    int getOrder() {
        return Ordered.LOWEST_PRECEDENCE
    }

    @Override
    void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!ProductionSecrets.isProduction(environment)) {
            return
        }
        String remember = environment.getProperty('steakos.security.rememberMeKey')
        if (!remember) {
            remember = System.getenv('STEAKOS_REMEMBER_ME_KEY')
        }
        List<String> problems = ProductionSecrets.problems(
                environment.getProperty('steakos.encryption.password'),
                environment.getProperty('steakos.encryption.salt'),
                environment.getProperty('dataSource.password'),
                remember
        )
        if (problems) {
            throw new IllegalStateException('steakOS refused to start. ' + problems.join(' '))
        }
    }
}
