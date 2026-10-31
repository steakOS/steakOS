// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import steakos.User
import steakos.configuration.CompanyConfigService

/**
 * PDR-025 PR3 — enforce role matrix write verbs on mutating /api/** requests.
 */
@Slf4j
@CompileStatic
class MatrixAccessFilter extends OncePerRequestFilter {

    RoleProfileService roleProfileService
    AuthAccountService authAccountService
    CompanyConfigService companyConfigService

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.context?.authentication
        if (auth == null || !auth.authenticated || auth.principal == null || auth.principal == 'anonymousUser') {
            filterChain.doFilter(request, response)
            return
        }

        String path = request.requestURI ?: ''
        String method = request.method ?: 'GET'
        Map req = ApiPermissionMapper.requireFor(method, path)
        String kind = req.kind as String

        if (kind == ApiPermissionMapper.KIND_ALLOW) {
            filterChain.doFilter(request, response)
            return
        }
        if (kind == ApiPermissionMapper.KIND_DENY) {
            forbid(response, 'Permission denied for this API', 'MATRIX_UNMAPPED')
            return
        }

        User user = resolveUser(auth)
        if (!user) {
            filterChain.doFilter(request, response)
            return
        }

        List enabledFamilies = null
        String templateTier = null
        try {
            String npub = user.npub ?: 'user:admin'
            enabledFamilies = companyConfigService?.enabledFamilies(npub) as List
            templateTier = companyConfigService?.getString(npub, 'template.tier', null)
        } catch (ignored) {
        }

        Map eff
        try {
            eff = roleProfileService.effectivePermissions(user, enabledFamilies, templateTier)
        } catch (Exception e) {
            log.warn("matrix resolve failed: ${e.message}")
            filterChain.doFilter(request, response)
            return
        }

        boolean ok = false
        if (kind == ApiPermissionMapper.KIND_DESK) {
            String fam = req.family as String
            String verb = req.verb as String
            Map desks = eff.desks as Map ?: [:]
            Map cell = desks[fam] as Map ?: [:]
            if (verb == ApiPermissionMapper.VERB_CREATE) {
                ok = !!cell.create
            } else if (verb == ApiPermissionMapper.VERB_CHANGE) {
                ok = !!cell.change
            } else if (verb == ApiPermissionMapper.VERB_DELETE) {
                ok = !!cell.delete
                // Presets keep desk delete=false; Task: Admin/CEO, assignee, or owner may delete
                if (!ok && SelfAssignedTaskDelete.allows(method, path, request.contextPath, user)) {
                    ok = true
                }
            } else {
                ok = !!cell.work || !!cell.change || !!cell.create
            }
            if (!ok) {
                forbid(response, "Role cannot ${verb} on desk ${fam}", 'MATRIX_DESK_FORBIDDEN')
                return
            }
        } else if (kind == ApiPermissionMapper.KIND_SYSTEM) {
            String key = req.systemKey as String
            Map sys = eff.system as Map ?: [:]
            ok = !!sys[key]
            // settle also in specials
            if (!ok && key == 'settle') {
                Map specials = eff.specials as Map ?: [:]
                ok = !!specials.settle
            }
            if (!ok) {
                forbid(response, "Role cannot perform system action: ${key}", 'MATRIX_SYSTEM_FORBIDDEN')
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    private User resolveUser(Authentication auth) {
        try {
            String name = auth.name
            return authAccountService?.findByUsername(name)
        } catch (ignored) {
            return null
        }
    }

    private static void forbid(HttpServletResponse response, String message, String code) throws IOException {
        response.status = 403
        response.contentType = 'application/json;charset=UTF-8'
        String safe = (message ?: 'Forbidden').replace('"', "'")
        response.writer.write("{\"error\":\"${safe}\",\"code\":\"${code}\"}")
    }
}
