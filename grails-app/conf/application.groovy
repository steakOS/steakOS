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

// Spring Security Core plugin — domain wiring (s2-quickstart)
grails.plugin.springsecurity.userLookup.userDomainClassName = 'steakos.User'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'steakos.UserRole'
grails.plugin.springsecurity.authority.className = 'steakos.Role'

// After successful login → SPA dashboard (same origin as login form).
// Use a *relative* URL so LAN clients (http://192.168.x.x:9001) are not sent to localhost.
// Prefer logging in via Vite: http://<host>:9001/login/auth (session cookie on :9001).
// /app → AppBridge persists login-screen locale into prefs and redirects to Vite /dashboard?lang=
grails.plugin.springsecurity.successHandler.defaultTargetUrl = '/app'
grails.plugin.springsecurity.successHandler.alwaysUseDefault = true
// After logout, show thank-you page at /logout/index
grails.plugin.springsecurity.logout.afterLogoutUrl = '/logout/index'
// Allow GET /logout (filter) as well as our LogoutController — clear remember-me
grails.plugin.springsecurity.logout.postOnly = false
grails.plugin.springsecurity.logout.handlerNames = [
		'rememberMeServices',
		'securityContextLogoutHandler'
]


// Plugin defaults: form login at /login/auth, POST /login/authenticate
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
	[pattern: '/error',          access: ['permitAll']],
	[pattern: '/shutdown',       access: ['permitAll']],
	[pattern: '/assets/**',      access: ['permitAll']],
	// A4 — public vulnerability contact (RFC 9116)
	[pattern: '/.well-known/**', access: ['permitAll']],
	// Onboarding tour (static under src/main/resources/static/tour)
	[pattern: '/tour/**',        access: ['permitAll']],
	[pattern: '/**/js/**',       access: ['permitAll']],
	[pattern: '/**/css/**',      access: ['permitAll']],
	[pattern: '/**/images/**',   access: ['permitAll']],
	[pattern: '/**/favicon.ico', access: ['permitAll']],
	[pattern: '/login/**',       access: ['permitAll']],
	[pattern: '/logout/**',      access: ['permitAll']],
	[pattern: '/app',            access: ['IS_AUTHENTICATED_REMEMBERED']],
	[pattern: '/app/**',         access: ['IS_AUTHENTICATED_REMEMBERED']],
	[pattern: '/api/locales',    access: ['permitAll']],
	[pattern: '/api/locales/**', access: ['permitAll']],
	// OBS-01/03 — public liveness/ready/metrics for Uptime Kuma + Prometheus scrape
	[pattern: '/api/health',      access: ['permitAll']],
	[pattern: '/api/health/**',   access: ['permitAll']],
	[pattern: '/api/ops/metrics', access: ['permitAll']],
	// AUTH NIP-07 login (public challenge/verify)
	[pattern: '/api/auth/nostr/**', access: ['permitAll']],
	[pattern: '/api/**',         access: ['IS_AUTHENTICATED_REMEMBERED']],
	// Homepage requires login
	[pattern: '/',               access: ['IS_AUTHENTICATED_REMEMBERED']],
	[pattern: '/index',          access: ['IS_AUTHENTICATED_REMEMBERED']],
	[pattern: '/index.gsp',      access: ['IS_AUTHENTICATED_REMEMBERED']],
	// Everything else authenticated by default
	[pattern: '/**',             access: ['IS_AUTHENTICATED_REMEMBERED']]
]

grails.plugin.springsecurity.filterChain.chainMap = [
	[pattern: '/assets/**',      filters: 'none'],
	[pattern: '/.well-known/**', filters: 'none'],
	[pattern: '/tour/**',        filters: 'none'],
	[pattern: '/**/js/**',       filters: 'none'],
	[pattern: '/**/css/**',      filters: 'none'],
	[pattern: '/**/images/**',   filters: 'none'],
	[pattern: '/**/favicon.ico', filters: 'none'],
	// Public locale catalog (pre-login picker)
	[pattern: '/api/locales',    filters: 'none'],
	[pattern: '/api/locales/**', filters: 'none'],
	// Public health/metrics (synthetics) — no session filters
	[pattern: '/api/health',     filters: 'none'],
	[pattern: '/api/health/**',  filters: 'none'],
	[pattern: '/api/ops/metrics', filters: 'none'],
	// JSON SPA API: session auth via Vite proxy; CSRF off for API mutations (dev skeleton)
	[pattern: '/api/**',         filters: 'JOINED_FILTERS,-csrfFilter'],
	[pattern: '/**',             filters: 'JOINED_FILTERS']
]
