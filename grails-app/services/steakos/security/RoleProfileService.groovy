// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import steakos.Role
import steakos.User
import steakos.UserRole
import steakos.configuration.TemplateTiers

/**
 * PDR-025 role profiles — seed defaults, resolve effective permissions for nav/API.
 */
@Slf4j
@Transactional
class RoleProfileService {

    static final List<String> ALL_DESK_FAMILIES = [
            'core', 'sales', 'scm', 'delivery', 'aftersales', 'finance',
            'human', 'digital', 'capability', 'agents', 'strategy', 'development', 'asset'
    ].asImmutable()

    /** Profiles that always See the full metal pack (CEO system exceptions stay in matrix.system). */
    static final Set<String> ELEVATED_SEE_PROFILES = ['admin', 'ceo'].asImmutable() as Set

    static final int MATRIX_VERSION = 1

    AuthAccountService authAccountService

    void ensureSeeded() {
        defaults().each { Map d ->
            RoleProfile existing = RoleProfile.findByProfileId(d.profileId as String)
            if (!existing) {
                new RoleProfile(
                        profileId       : d.profileId,
                        label           : d.label,
                        defaultHome     : d.defaultHome,
                        springRolesJson : JsonOutput.toJson(d.springRoles),
                        matrixJson      : JsonOutput.toJson(d.matrix),
                        matrixVersion   : MATRIX_VERSION,
                        systemPreset    : true
                ).save(failOnError: true)
            } else if (existing.systemPreset) {
                // Keep system preset home in sync (admin was stuck on /settings after an old edit)
                String wantHome = d.defaultHome as String
                if (wantHome && existing.defaultHome != wantHome) {
                    existing.defaultHome = wantHome
                    existing.save(failOnError: true)
                }
            }
        }
        // Backfill users missing profile id from authorities
        User.list().each { User u ->
            if (!u.roleProfileId) {
                u.roleProfileId = inferProfileId(u)
                u.save(failOnError: true)
            }
        }
    }

    List<Map> listProfiles() {
        ensureSeeded()
        RoleProfile.list(sort: 'profileId').collect { RoleProfile p ->
            Map m = toMap(p)
            m.userCount = countUsers(p.profileId)
            m
        }
    }

    RoleProfile findByProfileId(String profileId) {
        ensureSeeded()
        RoleProfile.findByProfileId(profileId?.trim())
    }

    Map toMap(RoleProfile p) {
        if (!p) return null
        [
                profileId    : p.profileId,
                label        : p.label,
                defaultHome  : p.defaultHome,
                springRoles  : parseJsonList(p.springRolesJson),
                matrix       : parseJsonMap(p.matrixJson),
                matrixVersion: p.matrixVersion,
                systemPreset : p.systemPreset
        ]
    }

    /**
     * Effective permissions for nav / me API.
     * @param enabledFamilies template families (null/empty = all known desks)
     * @param templateTier metal code — Admin/CEO See floor = that metal\'s default pack (e.g. full Bronze)
     */
    Map effectivePermissions(User user, Collection<String> enabledFamilies = null, String templateTier = null) {
        ensureSeeded()
        String pid = user?.roleProfileId ?: inferProfileId(user)
        RoleProfile profile = findByProfileId(pid) ?: findByProfileId('user')
        Map matrix = parseJsonMap(profile.matrixJson)
        Map override = user?.permissionOverrideJson ? parseJsonMap(user.permissionOverrideJson) : [:]

        Set<String> configured = (enabledFamilies && !enabledFamilies.isEmpty())
                ? enabledFamilies.collect { it.toString() } as Set
                : ALL_DESK_FAMILIES as Set

        boolean elevatedSee = ELEVATED_SEE_PROFILES.contains(profile.profileId)
        String tier = TemplateTiers.normalize(templateTier ?: TemplateTiers.DEFAULT_OPERATOR_TIER)
        Set<String> metalPack = new LinkedHashSet<>(
                TemplateTiers.defaultFamilies(tier).collect { it?.toString() }.findAll { it }
        )
        // Admin/CEO always See (+ work) the full metal pack so Bronze owners get every desk/data product.
        // CEO system exceptions (no setup / no catalogs by default) stay in matrix.system.
        Set<String> seeFamilies = elevatedSee && metalPack ? metalPack : configured
        Set<String> writeFamilies = elevatedSee && metalPack ? metalPack : configured

        boolean readOnly = user?.authorities*.authority?.contains('ROLE_READ_ONLY')

        Map desks = [:]
        ALL_DESK_FAMILIES.each { String fam ->
            boolean inSeePack = seeFamilies.contains(fam) || fam == 'core'
            boolean inWritePack = writeFamilies.contains(fam) || fam == 'core'
            if (!inSeePack) {
                desks[fam] = [see: false, work: false, create: false, change: false, delete: false]
                return
            }
            Map cell = deskCell(matrix, override, fam)
            boolean see = (cell.see as boolean) || elevatedSee
            boolean create = inWritePack && !readOnly && (cell.create as boolean)
            boolean change = inWritePack && !readOnly && (cell.change as boolean)
            boolean delete = inWritePack && !readOnly && (cell.delete as boolean)
            if (fam == 'core') {
                see = true
            }
            if (elevatedSee) {
                see = true
                // Elevated presets seed work=true on all desks — keep writes when metal pack includes fam
                if (inWritePack && !readOnly) {
                    create = create || (cell.create as boolean) || (cell.work as boolean)
                    change = change || (cell.change as boolean) || (cell.work as boolean)
                }
            }
            desks[fam] = [
                    see   : see,
                    work  : create || change,
                    create: create,
                    change: change,
                    delete: delete
            ]
        }

        Map sys = (matrix.system instanceof Map) ? new LinkedHashMap(matrix.system as Map) : [:]
        Map sysOv = (override.system instanceof Map) ? (override.system as Map) : [:]
        sysOv.each { k, v -> if (v != null && v != 'inherit') sys[k] = v }
        if (readOnly) {
            ['users', 'import', 'catalogs', 'agents', 'setup', 'securityOthers', 'settle'].each { sys[it] = false }
        }

        Map specials = (matrix.specials instanceof Map) ? new LinkedHashMap(matrix.specials as Map) : [:]
        Map spOv = (override.specials instanceof Map) ? (override.specials as Map) : [:]
        spOv.each { k, v -> if (v != null && v != 'inherit') specials[k] = v }

        [
                roleProfileId: profile.profileId,
                label        : profile.label,
                defaultHome  : profile.defaultHome,
                desks        : desks,
                navFamilies  : seeFamilies as List,
                system       : [
                        users          : !!sys.users,
                        securitySelf   : sys.securitySelf != null ? !!sys.securitySelf : true,
                        securityOthers : !!sys.securityOthers,
                        import         : !!sys.import,
                        catalogs       : !!sys.catalogs,
                        agents         : !!sys.agents,
                        setup          : !!sys.setup,
                        settle         : !!sys.settle || !!specials.settle
                ],
                specials     : specials
        ]
    }

    boolean canSeeDesk(User user, String family, Collection<String> enabledFamilies = null, String templateTier = null) {
        Map eff = effectivePermissions(user, enabledFamilies, templateTier)
        Map desk = (eff.desks as Map)?.get(family)
        desk && desk.see
    }

    /**
     * Assign profile to user: set roleProfileId + Spring roles from profile.
     */
    User assignProfile(User user, String profileId, boolean allowAdminAssign) {
        RoleProfile profile = findByProfileId(profileId)
        if (!profile) {
            throw new IllegalArgumentException("unknown role profile: ${profileId}")
        }
        if (profile.profileId == 'admin' && !allowAdminAssign) {
            throw new SecurityException('Only Admin can assign the Admin profile')
        }
        List roles = parseJsonList(profile.springRolesJson)
        user.roleProfileId = profile.profileId
        user.springSecurityService = authAccountService?.springSecurityService ?: user.springSecurityService
        user.save(failOnError: true, flush: true)
        authAccountService.setUserRoles(user, roles.collect { it.toString() })
        user
    }

    String inferProfileId(User user) {
        Collection auths = user?.authorities*.authority ?: []
        if (auths.contains('ROLE_ADMIN') && auths.contains('ROLE_CEO')) return 'ceo'
        if (auths.contains('ROLE_ADMIN')) return 'admin'
        if (auths.contains('ROLE_CEO')) return 'ceo'
        if (auths.contains('ROLE_READ_ONLY')) return 'read_only'
        'user'
    }

    RoleProfile resetToDefault(String profileId) {
        Map d = defaults().find { it.profileId == profileId }
        if (!d) throw new IllegalArgumentException("unknown profile: ${profileId}")
        RoleProfile p = findByProfileId(profileId)
        if (!p) throw new IllegalArgumentException("profile not found: ${profileId}")
        if (!p.systemPreset) throw new IllegalStateException('only system presets can reset')
        p.label = d.label as String
        p.defaultHome = d.defaultHome as String
        p.springRolesJson = JsonOutput.toJson(d.springRoles)
        p.matrixJson = JsonOutput.toJson(d.matrix)
        p.matrixVersion = MATRIX_VERSION
        p.save(failOnError: true, flush: true)
        p
    }

    /** Re-assign Spring roles from profile to every user on this profileId. */
    int applyProfileToUsers(String profileId, boolean allowAdminAssign) {
        RoleProfile profile = findByProfileId(profileId)
        if (!profile) throw new IllegalArgumentException("unknown profile: ${profileId}")
        if (profile.profileId == 'admin' && !allowAdminAssign) {
            throw new SecurityException('Only Admin can apply the Admin profile')
        }
        List roles = parseJsonList(profile.springRolesJson)
        int n = 0
        User.findAllByRoleProfileId(profileId).each { User u ->
            authAccountService.setUserRoles(u, roles.collect { it.toString() })
            n++
        }
        n
    }

    int countUsers(String profileId) {
        User.countByRoleProfileId(profileId) as int
    }

    private Map deskCell(Map matrix, Map override, String fam) {
        Map desks = (matrix.desks instanceof Map) ? (matrix.desks as Map) : [:]
        Map base = (desks[fam] instanceof Map) ? new LinkedHashMap(desks[fam] as Map) : [see: false, create: false, change: false, delete: false]
        Map ovDesks = (override.desks instanceof Map) ? (override.desks as Map) : [:]
        if (ovDesks[fam] instanceof Map) {
            (ovDesks[fam] as Map).each { k, v ->
                if (v != null && v != 'inherit') base[k] = v
            }
        }
        // Expand shorthand: work true → create+change
        if (base.work == true) {
            base.create = true
            base.change = true
        }
        if (base.see == null) base.see = false
        if (base.create == null) base.create = false
        if (base.change == null) base.change = false
        if (base.delete == null) base.delete = false
        base
    }

    private static Map parseJsonMap(String json) {
        if (!json) return [:]
        try {
            def v = new JsonSlurper().parseText(json)
            return (v instanceof Map) ? (v as Map) : [:]
        } catch (ignored) {
            return [:]
        }
    }

    private static List parseJsonList(String json) {
        if (!json) return []
        try {
            def v = new JsonSlurper().parseText(json)
            return (v instanceof List) ? (v as List) : []
        } catch (ignored) {
            return []
        }
    }

    /** Default preset definitions (concept §5.2). */
    static List<Map> defaults() {
        Map allWork = deskMap(ALL_DESK_FAMILIES, true, true, true, false)
        [
                [
                        profileId  : 'admin',
                        label      : 'Admin',
                        defaultHome: '/dashboard',
                        springRoles: ['ROLE_USER', 'ROLE_ADMIN'],
                        matrix     : [
                                version : MATRIX_VERSION,
                                desks   : allWork,
                                system  : [users: true, securitySelf: true, securityOthers: true, import: true, catalogs: true, agents: true, setup: true, settle: true],
                                specials: [settle: true, hrSee: true]
                        ]
                ],
                [
                        profileId  : 'ceo',
                        label      : 'CEO',
                        defaultHome: '/dashboard',
                        springRoles: ['ROLE_USER', 'ROLE_CEO', 'ROLE_ADMIN'],
                        matrix     : [
                                version : MATRIX_VERSION,
                                desks   : allWork,
                                system  : [users: true, securitySelf: true, securityOthers: true, import: true, catalogs: false, agents: true, setup: false, settle: true],
                                specials: [settle: true, hrSee: true]
                        ]
                ],
                [
                        profileId  : 'office',
                        label      : 'Office',
                        defaultHome: '/work',
                        springRoles: ['ROLE_USER'],
                        matrix     : [
                                version : MATRIX_VERSION,
                                desks   : deskMap(['core', 'sales', 'delivery', 'aftersales', 'capability'], true, true, true, false),
                                system  : [users: false, securitySelf: true, securityOthers: false, import: false, catalogs: false, agents: false, setup: false, settle: false],
                                specials: [settle: false, hrSee: false]
                        ]
                ],
                [
                        profileId  : 'ops',
                        label      : 'Ops',
                        defaultHome: '/work',
                        springRoles: ['ROLE_USER'],
                        matrix     : [
                                version : MATRIX_VERSION,
                                desks   : deskMap(['core', 'scm', 'delivery', 'asset', 'capability'], true, true, true, false) +
                                        deskMap(['sales'], true, false, false, false),
                                system  : [users: false, securitySelf: true, securityOthers: false, import: false, catalogs: false, agents: false, setup: false, settle: false],
                                specials: [settle: false, hrSee: false]
                        ]
                ],
                [
                        profileId  : 'finance',
                        label      : 'Finance',
                        defaultHome: '/finance',
                        springRoles: ['ROLE_USER'],
                        matrix     : [
                                version : MATRIX_VERSION,
                                desks   : deskMap(['core', 'finance'], true, true, true, false) +
                                        deskMap(['sales'], true, false, false, false),
                                system  : [users: false, securitySelf: true, securityOthers: false, import: false, catalogs: false, agents: false, setup: false, settle: true],
                                specials: [settle: true, hrSee: false]
                        ]
                ],
                [
                        profileId  : 'user',
                        label      : 'User',
                        defaultHome: '/work',
                        springRoles: ['ROLE_USER'],
                        matrix     : [
                                version : MATRIX_VERSION,
                                desks   : deskMap(['core', 'sales', 'delivery'], true, true, true, false) +
                                        deskMap(['aftersales'], true, false, false, false),
                                system  : [users: false, securitySelf: true, securityOthers: false, import: false, catalogs: false, agents: false, setup: false, settle: false],
                                specials: [settle: false, hrSee: false]
                        ]
                ],
                [
                        profileId  : 'read_only',
                        label      : 'Read only',
                        defaultHome: '/work',
                        springRoles: ['ROLE_USER', 'ROLE_READ_ONLY'],
                        matrix     : [
                                version : MATRIX_VERSION,
                                desks   : deskMap(ALL_DESK_FAMILIES, true, false, false, false),
                                system  : [users: false, securitySelf: true, securityOthers: false, import: false, catalogs: false, agents: false, setup: false, settle: false],
                                specials: [settle: false, hrSee: false]
                        ]
                ]
        ]
    }

    private static Map deskMap(Collection<String> fams, boolean see, boolean create, boolean change, boolean delete) {
        Map m = [:]
        fams.each { String f ->
            m[f] = [see: see, create: create, change: change, delete: delete, work: create || change]
        }
        m
    }
}
