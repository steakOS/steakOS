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

package steakos.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.web.filter.OncePerRequestFilter
import steakos.User
import steakos.agent.AgentTokenService

/**
 * ADR-037: if {@code Authorization: Bearer …} is present, authenticate as agent User.
 * Session cookie auth still works when no Bearer header is sent.
 */
class AgentBearerTokenFilter extends OncePerRequestFilter {

    AgentTokenService agentTokenService
    UserDetailsService userDetailsService

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            def existing = SecurityContextHolder.context?.authentication
            if (existing == null || !existing.authenticated) {
                String header = request.getHeader('Authorization')
                if (header != null && header.regionMatches(true, 0, 'Bearer ', 0, 7)) {
                    String raw = header.substring(7).trim()
                    User agent = agentTokenService?.authenticate(raw)
                    if (agent != null) {
                        UserDetails details = loadDetails(agent)
                        def auth = new UsernamePasswordAuthenticationToken(details, 'N/A', details.authorities)
                        auth.details = request
                        SecurityContextHolder.context.authentication = auth
                        String tenant = agentTokenService.linkedTenantForToken(raw)
                        if (tenant) {
                            request.setAttribute('steakos.linkedTenantKey', tenant)
                            request.setAttribute('steakos.agentUsername', agent.username)
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // leave context empty → 401/403 as usual
        }
        filterChain.doFilter(request, response)
    }

    private UserDetails loadDetails(User agent) {
        try {
            if (userDetailsService != null) {
                return userDetailsService.loadUserByUsername(agent.username)
            }
        } catch (Exception ignored) {
        }
        def auths = [new SimpleGrantedAuthority('ROLE_AI'), new SimpleGrantedAuthority('ROLE_USER')]
        // Spring Security UserDetails without GrailsUser id
        return new org.springframework.security.core.userdetails.User(
                agent.username, 'N/A', agent.enabled, true, true, true, auths)
    }
}
