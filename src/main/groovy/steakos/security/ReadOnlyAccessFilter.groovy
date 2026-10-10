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
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Blocks mutating HTTP methods for principals with {@code ROLE_READ_ONLY}
 * (demo / visitor accounts). Session logout still allowed.
 */
class ReadOnlyAccessFilter extends OncePerRequestFilter {

    static final String ROLE_READ_ONLY = 'ROLE_READ_ONLY'

    private static final Set<String> MUTATING = ['POST', 'PUT', 'PATCH', 'DELETE'] as Set

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.context?.authentication
        if (auth?.authenticated && isReadOnly(auth) && isMutating(request)) {
            String path = request.requestURI ?: ''
            // Allow logout / login; session company switch (PDR-026) is not a data write
            if (path.contains('/logout') || path.contains('/login') ||
                    path == '/api/me/company' || path.startsWith('/api/me/company?')) {
                filterChain.doFilter(request, response)
                return
            }
            response.status = 403
            response.contentType = 'application/json;charset=UTF-8'
            response.writer.write(
                    '{"error":"Read-only account cannot modify data","code":"READ_ONLY_FORBIDDEN"}'
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    private static boolean isReadOnly(Authentication auth) {
        for (GrantedAuthority a : auth.authorities ?: []) {
            if (ROLE_READ_ONLY == a.authority) {
                return true
            }
        }
        return false
    }

    private static boolean isMutating(HttpServletRequest request) {
        String m = request.method?.toUpperCase()
        return m != null && MUTATING.contains(m)
    }
}
