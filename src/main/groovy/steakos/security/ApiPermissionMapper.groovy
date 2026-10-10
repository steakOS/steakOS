// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import groovy.transform.CompileStatic

/**
 * Maps /api/** paths + HTTP method → permission requirement (PDR-025 PR3).
 */
@CompileStatic
final class ApiPermissionMapper {

    static final String VERB_CREATE = 'create'
    static final String VERB_CHANGE = 'change'
    static final String VERB_DELETE = 'delete'
    static final String VERB_SETTLE = 'settle'
    static final String KIND_DESK = 'desk'
    static final String KIND_SYSTEM = 'system'
    static final String KIND_ALLOW = 'allow'
    static final String KIND_DENY = 'deny'

    private ApiPermissionMapper() {}

    /**
     * @return map with keys: kind (desk|system|allow|deny), family or systemKey, verb
     */
    static Map requireFor(String method, String path) {
        String m = (method ?: 'GET').toUpperCase()
        String p = path ?: ''
        if (!p.startsWith('/api/')) {
            return [kind: KIND_ALLOW]
        }
        // Always allow these mutating auth/self paths
        if (p.startsWith('/api/auth/totp/begin') ||
                p.startsWith('/api/auth/totp/confirm') ||
                p.startsWith('/api/auth/totp/verify') ||
                p.startsWith('/api/auth/nostr/') ||
                p == '/api/auth/totp/status' ||
                p.startsWith('/api/me')) {
            return [kind: KIND_ALLOW]
        }
        if (p.startsWith('/api/locales') || p.startsWith('/api/version') || p.startsWith('/api/help') ||
                p.startsWith('/api/fx/') || p.startsWith('/api/dashboard') || p.startsWith('/api/currencies') ||
                p.startsWith('/api/tax-codes')) {
            return [kind: KIND_ALLOW]
        }
        // BTCPay webhook — often unauthenticated; filter only runs when authenticated
        if (p.contains('/webhook/')) {
            return [kind: KIND_ALLOW]
        }

        if (!(m in ['POST', 'PUT', 'PATCH', 'DELETE'])) {
            return [kind: KIND_ALLOW]
        }

        String verb = verbForMethod(m, p)

        // System surfaces
        if (p.startsWith('/api/auth/users') || p.startsWith('/api/auth/role-profiles') ||
                p.startsWith('/api/auth/totp/clear') || p.startsWith('/api/auth/totp/begin-for') ||
                p.startsWith('/api/auth/totp/confirm-for')) {
            return [kind: KIND_SYSTEM, systemKey: 'users', verb: verb]
        }
        if (p.startsWith('/api/setup')) {
            return [kind: KIND_SYSTEM, systemKey: 'setup', verb: verb]
        }
        if (p.startsWith('/api/import')) {
            return [kind: KIND_SYSTEM, systemKey: 'import', verb: verb]
        }
        if (p.startsWith('/api/agent/tokens') || p.startsWith('/api/agent/users') ||
                p.startsWith('/api/agents')) {
            return [kind: KIND_SYSTEM, systemKey: 'agents', verb: verb]
        }

        // Money specials
        if (isSettlePath(p)) {
            return [kind: KIND_SYSTEM, systemKey: 'settle', verb: VERB_SETTLE]
        }

        // Domain CRUD: /api/domains/{package}/...
        if (p.startsWith('/api/domains/')) {
            String pkg = segment(p, 2)
            String fam = packageToFamily(pkg)
            if (!fam) return [kind: KIND_ALLOW]
            return [kind: KIND_DESK, family: fam, verb: verb]
        }

        // Family API prefixes
        String fam = pathToFamily(p)
        if (fam) {
            return [kind: KIND_DESK, family: fam, verb: verb]
        }

        // Content companion — allow if authenticated (any desk); not a separate matrix row yet
        if (p.startsWith('/api/content')) {
            return [kind: KIND_ALLOW]
        }

        // Unknown mutating API — deny by default for least privilege
        return [kind: KIND_DENY, reason: 'unmapped_mutating_api']
    }

    static String verbForMethod(String method, String path) {
        if (method == 'DELETE') return VERB_DELETE
        if (method == 'PUT' || method == 'PATCH') return VERB_CHANGE
        // POST: action suffixes often mean change/settle rather than create
        if (isSettlePath(path)) return VERB_SETTLE
        if (path ==~ /.*\/(start|complete|resolve|close|cancel|void|issue|activate|retire|reverse|post|commit|preview|ack).*/) {
            return VERB_CHANGE
        }
        return VERB_CREATE
    }

    static boolean isSettlePath(String p) {
        p.contains('/settle') ||
                p.endsWith('/pay') ||
                p.contains('/reconcile') ||
                p.contains('/payments/inbound') ||
                p.contains('/treasury/seed') ||
                (p.contains('/periods/') && p.endsWith('/close')) ||
                p.contains('/payroll/post')
    }

    static String pathToFamily(String p) {
        if (p.startsWith('/api/sales')) return 'sales'
        if (p.startsWith('/api/scm') || p.startsWith('/api/procurement') || p.startsWith('/api/production') ||
                p.startsWith('/api/catalogue')) return 'scm'
        if (p.startsWith('/api/delivery')) return 'delivery'
        if (p.startsWith('/api/aftersales') || p.startsWith('/api/service')) return 'aftersales'
        if (p.startsWith('/api/finance')) return 'finance'
        if (p.startsWith('/api/hr') || p.startsWith('/api/human')) return 'human'
        if (p.startsWith('/api/digital')) return 'digital'
        if (p.startsWith('/api/capability') || p.startsWith('/api/cap')) return 'capability'
        if (p.startsWith('/api/strategy')) return 'strategy'
        if (p.startsWith('/api/development') || p.startsWith('/api/dev')) return 'development'
        if (p.startsWith('/api/assets') || p.startsWith('/api/asset')) return 'asset'
        if (p.startsWith('/api/core') || p.startsWith('/api/tasks') || p.startsWith('/api/work')) return 'core'
        null
    }

    static String packageToFamily(String pkg) {
        if (!pkg) return null
        switch (pkg) {
            case 'sales': return 'sales'
            case 'procurement':
            case 'production':
            case 'catalogue': return 'scm'
            case 'delivery': return 'delivery'
            case 'service': return 'aftersales'
            case 'finance': return 'finance'
            case 'human': return 'human'
            case 'digital': return 'digital'
            case 'capability': return 'capability'
            case 'agents': return 'agents'
            case 'strategy': return 'strategy'
            case 'development': return 'development'
            case 'asset': return 'asset'
            case 'core': return 'core'
            default: return null
        }
    }

    private static String segment(String path, int index) {
        // /api/domains/sales/Customer → index 0=api 1=domains 2=sales
        String[] parts = path.split('/')
        // parts[0] empty, [1]=api, [2]=domains, [3]=sales
        int i = index + 1
        (i < parts.length) ? parts[i] : null
    }
}
