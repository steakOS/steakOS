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

import { useEffect, useMemo, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  LayoutDashboard,
  ListTodo,
  BarChart3,
  LogOut,
  ChevronRight,
  ChevronDown,
  Info,
  Database,
  Settings,
  HelpCircle,
  Upload,
  Wrench,
  Building2,
  Users,
  ShieldCheck,
  KeyRound,
  Plug,
} from 'lucide-react'
import { domainGroups } from '../config/domainRegistry'
import { setupApi, versionApi, meApi } from '../api/client'
import HelpDrawer from '../components/HelpDrawer'
import CommandPalette from '../components/CommandPalette'
import { useLocaleBootstrap } from '../hooks/useLocaleBootstrap'
import useNarrowViewport from '../hooks/useNarrowViewport'
import { logoutHref } from '../i18n'
import { domainResourceLabel } from '../utils/domainLabels'
import { allowsSilverSurfaces } from '../utils/silverSurfaces'
import {
  systemLinks,
  helpNavLinks,
  featureLinksForPackage,
} from '../nav/featureLinks'

/**
 * ADR-044 — read HelpContext that DeskShell stamps on `.desk-shell-root`
 * via data-help-* so AppShell HelpDrawer can resolve without prop drilling.
 */
function readDeskShellHelpContext(pathname) {
  if (typeof document === 'undefined') return null
  const el = document.querySelector('.desk-shell-root[data-help-desk]')
  if (!el) return null
  const ctx = {
    path: pathname || el.getAttribute('data-help-path') || '/',
    desk: el.getAttribute('data-help-desk'),
  }
  const tab = el.getAttribute('data-help-tab')
  const resource = el.getAttribute('data-help-resource')
  const objectId = el.getAttribute('data-help-object-id')
  const status = el.getAttribute('data-help-status')
  const processCode = el.getAttribute('data-help-process')
  if (tab) ctx.tab = tab
  if (resource) ctx.resource = resource
  if (objectId) ctx.objectId = objectId
  if (status) ctx.status = status
  if (processCode) ctx.processCode = processCode
  return ctx
}

/** IRON → Iron for chrome labels */
function formatTierLabel(tier) {
  if (!tier) return ''
  const t = String(tier)
  return t.charAt(0).toUpperCase() + t.slice(1).toLowerCase()
}

function groupContainsPath(group, pathname) {
  if (!pathname) return false
  if (group.package === 'finance' && pathname.startsWith('/finance/')) {
    return true
  }
  if (group.package === 'sales' && (pathname === '/sales' || pathname.startsWith('/sales/'))) {
    return true
  }
  if (group.package === 'procurement' && (pathname === '/scm/buyers' || pathname.startsWith('/scm/buyers'))) {
    return true
  }
  if (
    group.package === 'production' &&
    (pathname === '/scm/shopfloor' || pathname.startsWith('/scm/shopfloor'))
  ) {
    return true
  }
  if (
    group.package === 'delivery' &&
    (pathname === '/delivery' || pathname.startsWith('/delivery/'))
  ) {
    return true
  }
  if (
    group.package === 'service' &&
    (pathname === '/aftersales' || pathname.startsWith('/aftersales/'))
  ) {
    return true
  }
  if (group.package === 'human' && (pathname === '/hr' || pathname.startsWith('/hr/'))) {
    return true
  }
  if (
    group.package === 'digital' &&
    (pathname === '/digital' || pathname.startsWith('/digital/'))
  ) {
    return true
  }
  if (
    group.package === 'capability' &&
    (pathname === '/capability' || pathname.startsWith('/capability/'))
  ) {
    return true
  }
  if (
    group.package === 'agents' &&
    (pathname === '/agents' || pathname.startsWith('/agents/'))
  ) {
    return true
  }
  if (
    group.package === 'strategy' &&
    (pathname === '/strategy' || pathname.startsWith('/strategy/'))
  ) {
    return true
  }
  if (
    group.package === 'development' &&
    (pathname === '/development' || pathname.startsWith('/development/'))
  ) {
    return true
  }
  if (
    group.package === 'asset' &&
    (pathname === '/assets' || pathname.startsWith('/assets/'))
  ) {
    return true
  }
  if (
    group.package === 'grrc' &&
    (pathname === '/grrc' || pathname.startsWith('/grrc/'))
  ) {
    return true
  }
  if (
    group.package === 'stakeholder' &&
    (pathname === '/stakeholder' || pathname.startsWith('/stakeholder/'))
  ) {
    return true
  }
  return group.resources.some(
    (r) => pathname === r.path || pathname.startsWith(`${r.path}/`),
  )
}

function isFinanceMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/finance/')
}

function isStrategyMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/strategy/')
}

function isDevelopmentMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/development/')
}

function isAssetMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/asset/')
}

function isSalesMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/sales/')
}

function isScmMasterDataPath(pathname) {
  return (
    pathname?.startsWith('/domains/procurement/') ||
    pathname?.startsWith('/domains/production/') ||
    pathname?.startsWith('/domains/supplychain/')
  )
}

function isDeliveryMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/delivery/')
}

function isAfterSalesMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/service/')
}

function isHrMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/human/')
}

function isDigitalMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/digital/')
}

function isCapabilityMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/capability/')
}

function isAgentsMasterDataPath(pathname) {
  return pathname?.startsWith('/domains/agents/')
}

function NavGroup({ group, open, onToggle, showAdvanced = false }) {
  const location = useLocation()
  const { t } = useTranslation(['common', 'domain', 'sales'])
  const resources = (group.resources || []).map((r) => ({
    ...r,
    name: domainResourceLabel(
      (keys, opts) => t(keys, { ns: 'domain', ...opts }),
      group.package,
      r.name,
    ),
  }))

  const featureLinks = featureLinksForPackage(group.package, t, { showAdvanced })
  const nestMasterData =
    group.package === 'finance' ||
    group.package === 'sales' ||
    group.package === 'procurement' ||
    group.package === 'production' ||
    group.package === 'delivery' ||
    group.package === 'service' ||
    group.package === 'human' ||
    group.package === 'digital' ||
    group.package === 'capability' ||
    group.package === 'agents' ||
    group.package === 'strategy' ||
    group.package === 'development' ||
    group.package === 'asset'
  const isMasterDataPath =
    group.package === 'finance'
      ? isFinanceMasterDataPath
      : group.package === 'sales'
        ? isSalesMasterDataPath
        : group.package === 'procurement' || group.package === 'production'
          ? isScmMasterDataPath
          : group.package === 'delivery'
            ? isDeliveryMasterDataPath
            : group.package === 'service'
              ? isAfterSalesMasterDataPath
              : group.package === 'human'
                ? isHrMasterDataPath
                : group.package === 'digital'
                  ? isDigitalMasterDataPath
                  : group.package === 'capability'
                    ? isCapabilityMasterDataPath
                    : group.package === 'agents'
                      ? isAgentsMasterDataPath
                      : group.package === 'strategy'
                        ? isStrategyMasterDataPath
                        : group.package === 'development'
                          ? isDevelopmentMasterDataPath
                          : group.package === 'asset'
                            ? isAssetMasterDataPath
                            : () => false

  const [masterOpen, setMasterOpen] = useState(() =>
    nestMasterData && isMasterDataPath(location.pathname),
  )

  // Auto-expand Data Products when viewing nested domain CRUD routes
  useEffect(() => {
    if (nestMasterData && isMasterDataPath(location.pathname)) {
      setMasterOpen(true)
    }
  }, [location.pathname, nestMasterData])

  return (
    <div className={`nav-section ${open ? 'is-open' : 'is-collapsed'}`}>
      <button
        type="button"
        className="nav-group-toggle"
        onClick={onToggle}
        aria-expanded={open}
        aria-controls={`nav-group-${group.package}`}
      >
        <span className="nav-group-label">
          {t(`groups.${group.package}`, { ns: 'common', defaultValue: group.label })}
        </span>
        {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
      </button>

      {open && (
        <div className="nav-group-items" id={`nav-group-${group.package}`}>
          {featureLinks.map((r) => {
            const Icon = r.icon
            return (
              <NavLink
                key={r.path}
                to={r.path}
                className={({ isActive }) => `nav-link feature ${isActive ? 'active' : ''}`}
              >
                {Icon ? <Icon size={14} /> : null}
                {r.name}
              </NavLink>
            )
          })}

          {nestMasterData ? (
            <div className={`nav-sub ${masterOpen ? 'is-open' : ''}`}>
              <button
                type="button"
                className="nav-sub-toggle"
                onClick={() => setMasterOpen((v) => !v)}
                aria-expanded={masterOpen}
                aria-controls={`nav-${group.package}-master-data`}
              >
                <span className="nav-sub-toggle-label">
                  <Database size={14} />
                  {t('nav.masterData', { ns: 'common' })}
                </span>
                {masterOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
              </button>
              {masterOpen && (
                <div className="nav-sub-items" id={`nav-${group.package}-master-data`}>
                  {resources.map((r) => {
                    const Icon = r.icon
                    return (
                      <NavLink
                        key={r.name}
                        to={r.path}
                        className={({ isActive }) =>
                          `nav-link nav-link-nested ${isActive ? 'active' : ''}`
                        }
                      >
                        {Icon ? <Icon size={14} /> : null}
                        {r.name}
                      </NavLink>
                    )
                  })}
                </div>
              )}
            </div>
          ) : (
            resources.map((r) => {
              const Icon = r.icon
              return (
                <NavLink
                  key={r.name}
                  to={r.path}
                  className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                >
                  {Icon ? <Icon size={14} /> : null}
                  {r.name}
                </NavLink>
              )
            })
          )}
        </div>
      )}
    </div>
  )
}

export default function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const { t } = useTranslation('common')
  const { locale } = useLocaleBootstrap()
  const narrow = useNarrowViewport()
  const [navOpen, setNavOpen] = useState(false)
  const [openPackages, setOpenPackages] = useState(() => new Set())
  const [versionLabel, setVersionLabel] = useState(null)
  const [templateTier, setTemplateTier] = useState(null)
  const [enabledFamilies, setEnabledFamilies] = useState(null)
  const [systemOpen, setSystemOpen] = useState(() =>
    location.pathname.startsWith('/settings') ||
    location.pathname.startsWith('/system/integrations') ||
      location.pathname.startsWith('/security') ||
      location.pathname.startsWith('/users') ||
      location.pathname.startsWith('/roles') ||
      location.pathname.startsWith('/company') ||
      location.pathname.startsWith('/system'),
  )
  // Help is for every role (Admin, CEO, desk, read-only) — default open so it is not missed
  const [helpNavOpen, setHelpNavOpen] = useState(true)
  const [helpOpen, setHelpOpen] = useState(false)
  const [helpTopic, setHelpTopic] = useState(null)
  // Snapshot DeskShell HelpContext when opening help (ADR-044); refresh on route change
  const deskHelpContext = useMemo(
    () => (helpOpen ? readDeskShellHelpContext(location.pathname) : null),
    [helpOpen, location.pathname, location.search],
  )
  const [cmdOpen, setCmdOpen] = useState(false)
  const [readOnly, setReadOnly] = useState(false)
  const [meUsername, setMeUsername] = useState(null)
  const [activeCompany, setActiveCompany] = useState(null)
  const [meCompanyCurrency, setMeCompanyCurrency] = useState(null)
  const [isAdmin, setIsAdmin] = useState(false)
  const [isCeo, setIsCeo] = useState(false)

  const elevated = isAdmin || isCeo
  const showAdvanced = allowsSilverSurfaces(templateTier)

  // D6: always start closed when entering narrow; leave closed when leaving narrow
  useEffect(() => {
    if (narrow) setNavOpen(false)
    else setNavOpen(false)
  }, [narrow])

  // D5: close drawer on route change (any navigation)
  useEffect(() => {
    setNavOpen(false)
  }, [location.pathname])

  // Deep-link Help: /path?help=<helpKey> opens the drawer on that topic
  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const help = params.get('help')
    if (help) {
      setHelpTopic(help)
      setHelpOpen(true)
    }
  }, [location.pathname, location.search])

  // Lock body scroll while mobile drawer open
  useEffect(() => {
    if (!narrow || !navOpen) return undefined
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = prev
    }
  }, [narrow, navOpen])

  useEffect(() => {
    const onKey = (e) => {
      const mod = e.metaKey || e.ctrlKey
      if (mod && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setCmdOpen((v) => !v)
      }
      if (mod && e.key === '/') {
        e.preventDefault()
        setHelpTopic(null)
        setHelpOpen(true)
      }
      if (e.key === 'Escape') {
        setCmdOpen(false)
        setHelpOpen(false)
        setHelpTopic(null)
        setNavOpen(false)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  // Primary work/intelligence only — admin catalogs moved to System hub (OI-U10).
  // core/My work is a top-level link (not a dropdown); Task list lives under System.
  const primaryGroups = useMemo(
    () =>
      [...domainGroups]
        .filter(
          (g) =>
            g.primary !== false &&
            g.navRole !== 'admin' &&
            g.package !== 'core',
        )
        .sort((a, b) => a.order - b.order),
    [],
  )

  const [permissions, setPermissions] = useState(null)

  const visibleGroups = useMemo(() => {
    let groups = primaryGroups
    // Pack intersection (R5): prefer navFamilies from /api/me (Admin/CEO = full metal pack)
    const pack =
      (Array.isArray(permissions?.navFamilies) && permissions.navFamilies.length
        ? permissions.navFamilies
        : enabledFamilies) || null
    if (pack && pack.length) {
      groups = groups.filter((g) => {
        if (!g.family) return true
        return pack.includes(g.family)
      })
    }
    // PDR-025: desk See from effective permissions when present
    if (permissions?.desks) {
      groups = groups.filter((g) => {
        const fam = g.family
        if (!fam) return true
        const cell = permissions.desks[fam]
        return cell?.see !== false
      })
    } else if (!elevated) {
      // Legacy fallback before permissions load
      groups = groups.filter((g) => g.package !== 'agents')
    }
    return groups
  }, [primaryGroups, enabledFamilies, permissions, elevated])

  const sysLinks = useMemo(() => {
    const sys = permissions?.system
    if (sys) {
      const links = [
        { name: t('nav.settings'), path: '/settings', icon: Settings },
        {
          name: t('nav.integrations', { defaultValue: 'Integrations' }),
          path: '/system/integrations',
          icon: Plug,
        },
      ]
      if (sys.securitySelf !== false) {
        links.push({
          name: t('nav.security', { defaultValue: 'Security' }),
          path: '/security',
          icon: ShieldCheck,
        })
      }
      links.push({ name: t('nav.company'), path: '/company', icon: Building2 })
      if (sys.users) {
        links.push(
          {
            name: t('nav.users', { defaultValue: 'Users' }),
            path: '/users',
            icon: Users,
          },
          {
            name: t('nav.roles', { defaultValue: 'Roles' }),
            path: '/roles',
            icon: KeyRound,
          },
        )
      }
      if (sys.import) {
        links.push({ name: t('nav.import'), path: '/system/import', icon: Upload })
      }
      if (sys.catalogs) {
        links.push({ name: t('nav.catalogs'), path: '/system/catalogs', icon: Wrench })
      }
      return links
    }
    return systemLinks(t, { isAdmin: elevated, isCeo: elevated && isCeo })
  }, [t, elevated, isCeo, permissions])
  const helpLinks = useMemo(() => helpNavLinks(t), [t])

  // Auto-expand the group that owns the current route
  useEffect(() => {
    const match = visibleGroups.find((g) => groupContainsPath(g, location.pathname))
    if (match) {
      setOpenPackages((prev) => {
        if (prev.has(match.package)) return prev
        const next = new Set(prev)
        next.add(match.package)
        return next
      })
    }
    if (
      location.pathname.startsWith('/settings') ||
      location.pathname.startsWith('/security') ||
      location.pathname.startsWith('/users') ||
      location.pathname.startsWith('/roles') ||
      location.pathname.startsWith('/company') ||
      location.pathname.startsWith('/system')
    ) {
      setSystemOpen(true)
    }
    if (location.pathname === '/help' || location.pathname.startsWith('/help/')) {
      setHelpNavOpen(true)
    }
  }, [location.pathname, visibleGroups])

  useEffect(() => {
    let cancelled = false
    versionApi
      .get()
      .then((v) => {
        if (!cancelled) setVersionLabel(v.displayLabel)
      })
      .catch(() => {
        /* non-fatal for shell */
      })
    setupApi
      .status()
      .then((s) => {
        if (!cancelled) {
          setTemplateTier(s.templateTier)
          // Prefer /api/me nav pack (Admin/CEO = full metal); do not clobber if already set
          setEnabledFamilies((prev) =>
            Array.isArray(prev) && prev.length ? prev : s.enabledFamilies || null,
          )
        }
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  // Refresh identity on route changes (e.g. after /totp → dashboard) so Admin/CEO nav unlocks
  useEffect(() => {
    let cancelled = false
    const applyMe = (me) => {
      if (!me || typeof me !== 'object') return
      const auths = Array.isArray(me.authorities) ? me.authorities : []
      const admin = !!(me.isAdmin || auths.includes('ROLE_ADMIN'))
      const ceo = !!(me.isCeo || auths.includes('ROLE_CEO'))
      setReadOnly(!!(me.readOnly || auths.includes('ROLE_READ_ONLY')))
      setMeUsername(me.username || null)
      setIsAdmin(admin)
      setIsCeo(ceo)
      if (me.permissions) setPermissions(me.permissions)
      if (me.templateTier) setTemplateTier(me.templateTier)
      if (Array.isArray(me.enabledFamilies) && me.enabledFamilies.length) {
        setEnabledFamilies(me.enabledFamilies)
      }
      if (me.activeCompany) setActiveCompany(me.activeCompany)
      else setActiveCompany(null)
      setMeCompanyCurrency(
        me.companyCurrency || me.activeCompany?.companyCurrency || null,
      )
      if (me.totpRequired && !window.location.pathname.startsWith('/totp')) {
        window.location.href = '/totp'
      }
    }
    meApi
      .get()
      .then((me) => {
        if (!cancelled) applyMe(me)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [location.pathname])

  const toggle = (pkg) => {
    setOpenPackages((prev) => {
      const next = new Set(prev)
      if (next.has(pkg)) next.delete(pkg)
      else next.add(pkg)
      return next
    })
  }

  const shellClass = [
    'app-shell',
    narrow ? 'is-narrow' : 'is-wide',
    narrow && navOpen ? 'nav-open' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={shellClass}>
      {narrow && navOpen && (
        <button
          type="button"
          className="nav-backdrop"
          aria-label={t('nav.closeMenu', { defaultValue: 'Close menu' })}
          onClick={() => setNavOpen(false)}
        />
      )}

      <aside className="sidebar" id="app-nav" aria-hidden={narrow && !navOpen ? true : undefined}>
        <NavLink to="/dashboard" className="sidebar-brand" onClick={() => setNavOpen(false)}>
          <img
            src="/favicon-32.png"
            alt=""
            width={28}
            height={28}
            className="sidebar-brand-steak"
            aria-hidden="true"
          />
          <span className="sidebar-brand-text">steakOS</span>
        </NavLink>

        <div className="sidebar-scroll">
          <div className="nav-section">
            <NavLink
              to="/dashboard"
              className={({ isActive }) => `nav-link top ${isActive ? 'active' : ''}`}
            >
              <LayoutDashboard size={16} /> {t('nav.dashboard')}
            </NavLink>
            <NavLink
              to="/work"
              className={({ isActive }) => `nav-link top ${isActive ? 'active' : ''}`}
              end
            >
              <ListTodo size={16} /> {t('pages.work.nav', { defaultValue: 'My work' })}
            </NavLink>
            {showAdvanced && (
              <NavLink
                to="/analytics"
                className={({ isActive }) => `nav-link top ${isActive ? 'active' : ''}`}
              >
                <BarChart3 size={16} /> {t('nav.analytics', { defaultValue: 'Analytics' })}
              </NavLink>
            )}
          </div>

          {visibleGroups.map((group) => (
            <NavGroup
              key={group.package}
              group={group}
              open={openPackages.has(group.package)}
              onToggle={() => toggle(group.package)}
              showAdvanced={showAdvanced}
            />
          ))}

          <div className={`nav-section ${systemOpen ? 'is-open' : 'is-collapsed'}`}>
            <button
              type="button"
              className="nav-group-toggle"
              onClick={() => setSystemOpen((v) => !v)}
              aria-expanded={systemOpen}
            >
              <span className="nav-group-label">{t('nav.system')}</span>
              {systemOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
            </button>
            {systemOpen && (
              <div className="nav-group-items">
                {sysLinks.map((r) => {
                  const Icon = r.icon
                  return (
                    <NavLink
                      key={r.path}
                      to={r.path}
                      className={({ isActive }) =>
                        `nav-link feature ${isActive ? 'active' : ''}`
                      }
                    >
                      {Icon ? <Icon size={14} /> : null}
                      {r.name}
                    </NavLink>
                  )
                })}
              </div>
            )}
          </div>
        </div>

        {/* Pinned footer: Help visible to all roles (Admin, CEO, desk, read-only) */}
        <div className="sidebar-footer">
          <div className={`nav-section nav-section-help ${helpNavOpen ? 'is-open' : 'is-collapsed'}`}>
            <button
              type="button"
              className="nav-group-toggle"
              onClick={() => setHelpNavOpen((v) => !v)}
              aria-expanded={helpNavOpen}
            >
              <span className="nav-group-label">
                {t('nav.helpGroup', { defaultValue: 'Help' })}
              </span>
              {helpNavOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
            </button>
            {helpNavOpen && (
              <div className="nav-group-items">
                {helpLinks.map((r) => {
                  const Icon = r.icon
                  if (r.external) {
                    return (
                      <a key={r.path} href={r.path} className="nav-link feature">
                        {Icon ? <Icon size={14} /> : null}
                        {r.name}
                      </a>
                    )
                  }
                  return (
                    <NavLink
                      key={r.path}
                      to={r.path}
                      end={r.path === '/help'}
                      className={({ isActive }) =>
                        `nav-link feature ${isActive ? 'active' : ''}`
                      }
                    >
                      {Icon ? <Icon size={14} /> : null}
                      {r.name}
                    </NavLink>
                  )
                })}
              </div>
            )}
          </div>

          <div
            className="sidebar-version"
            title={t('shell.versionTitle', {
              defaultValue: 'Product version + template tier (VERSIONS.md / setup)',
            })}
          >
            <div className="sidebar-version-body">
              <div className="sidebar-version-line">{versionLabel || 'steakOS'}</div>
              {templateTier && (
                <div className="sidebar-tier-line">
                  {t('shell.templatePrefix', {
                    defaultValue: 'Template · {{tier}}',
                    tier: formatTierLabel(templateTier),
                  })}
                </div>
              )}
            </div>
            <NavLink
              to="/about"
              className={({ isActive }) =>
                `sidebar-about-icon${isActive ? ' is-active' : ''}`
              }
              title={t('nav.about')}
              aria-label={t('nav.about')}
            >
              <Info size={16} />
            </NavLink>
          </div>
        </div>
      </aside>

      <div className="main-col">
        <header className="topbar">
          <div className="topbar-leading">
            {narrow && (
              <button
                type="button"
                className={`nav-steak-toggle${navOpen ? ' is-open' : ''}`}
                aria-expanded={navOpen}
                aria-controls="app-nav"
                title={
                  navOpen
                    ? t('nav.closeMenu', { defaultValue: 'Close menu' })
                    : t('nav.openMenu', { defaultValue: 'Open menu' })
                }
                aria-label={
                  navOpen
                    ? t('nav.closeMenu', { defaultValue: 'Close menu' })
                    : t('nav.openMenu', { defaultValue: 'Open menu' })
                }
                onClick={() => setNavOpen((v) => !v)}
              >
                <img
                  src="/favicon-32.png"
                  alt=""
                  width={28}
                  height={28}
                  aria-hidden="true"
                />
              </button>
            )}
            <div className="topbar-meta">
              <span className="topbar-meta-full" style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                {!narrow && (
                  <img
                    src="/favicon-32.png"
                    alt=""
                    width={22}
                    height={22}
                    aria-hidden="true"
                  />
                )}
                steakOS
                {templateTier ? ` · ${formatTierLabel(templateTier)}` : ''}
                {locale ? ` · ${locale}` : ''}
                {versionLabel ? ` · ${versionLabel}` : ''}
              </span>
              <span className="topbar-meta-compact">
                {templateTier ? formatTierLabel(templateTier) : 'steakOS'}
                {locale ? ` · ${locale}` : ''}
              </span>
            </div>
          </div>
          <div className="topbar-actions">
            <button
              type="button"
              className="btn btn-ghost"
              title="Ctrl+K"
              onClick={() => setCmdOpen(true)}
            >
              ⌘K
            </button>
            {(activeCompany?.companyCurrency || meCompanyCurrency) && (
              <span
                className="mono"
                title={t('shell.companyCurrency', { defaultValue: 'Company currency' })}
                style={{
                  fontSize: 13,
                  fontWeight: 600,
                  padding: '4px 8px',
                  borderRadius: 6,
                  background: 'var(--surface-2, #f3f4f6)',
                  letterSpacing: '0.02em',
                }}
              >
                {activeCompany?.companyCurrency || meCompanyCurrency}
              </span>
            )}
            <button
              type="button"
              className="btn btn-ghost"
              title={t('shell.help', { defaultValue: 'Help' })}
              onClick={() => {
                setHelpTopic(null)
                setHelpOpen(true)
              }}
            >
              <HelpCircle size={16} />
            </button>
            <a
              className="btn btn-ghost"
              href={logoutHref()}
              title={t('shell.signOut', { defaultValue: 'Sign out' })}
              onClick={() => {
                try {
                  sessionStorage.clear()
                } catch {
                  /* ignore */
                }
              }}
            >
              <LogOut size={16} /> <span className="topbar-logout-label">{t('nav.logout')}</span>
            </a>
          </div>
        </header>
        <main className="content">
          <nav className="app-breadcrumb" aria-label="Breadcrumb">
            {(() => {
              const parts = location.pathname.split('/').filter(Boolean)
              if (parts.length === 0) return null
              const crumbs = []
              let acc = ''
              for (let i = 0; i < parts.length; i++) {
                acc += `/${parts[i]}`
                const label = parts[i]
                  .replace(/-/g, ' ')
                  .replace(/\b\w/g, (c) => c.toUpperCase())
                const isLast = i === parts.length - 1
                crumbs.push(
                  <span key={acc} className="app-breadcrumb-item">
                    {i > 0 ? <span className="app-breadcrumb-sep"> / </span> : null}
                    {isLast ? (
                      <span aria-current="page">{label}</span>
                    ) : (
                      <Link to={acc}>{label}</Link>
                    )}
                  </span>,
                )
              }
              const tab = new URLSearchParams(location.search).get('tab')
              if (tab) {
                crumbs.push(
                  <span key="tab" className="app-breadcrumb-item">
                    <span className="app-breadcrumb-sep"> / </span>
                    <span aria-current="page">{tab}</span>
                  </span>,
                )
              }
              return crumbs
            })()}
          </nav>
          {activeCompany?.demo || activeCompany?.kind === 'DEMO' ? (
            <div
              className="banner-info"
              role="status"
              style={{ marginBottom: '0.75rem' }}
            >
              Demo company: <strong>{activeCompany.name}</strong> — showcase data; live company is
              separate.
              {isAdmin ? (
                <>
                  {' '}
                  Admin: <Link to="/settings">Reset demo</Link> in Settings.
                </>
              ) : null}
            </div>
          ) : null}
          {readOnly && (
            <div
              className="banner-info"
              role="status"
              style={{ marginBottom: '0.75rem' }}
            >
              {t('auth.readOnlyBanner', {
                user: meUsername || 'demo',
                defaultValue:
                  'Read-only session ({{user}}): all features are visible; create, edit, delete, and admin actions are blocked.',
              })}
            </div>
          )}
          <Outlet />
        </main>
      </div>
      <HelpDrawer
        open={helpOpen}
        initialTopic={helpTopic}
        helpContext={deskHelpContext}
        onClose={() => {
          setHelpOpen(false)
          setHelpTopic(null)
          // Drop ?help= from URL without remounting the page
          if (location.search.includes('help=')) {
            const params = new URLSearchParams(location.search)
            params.delete('help')
            const qs = params.toString()
            navigate({ pathname: location.pathname, search: qs ? `?${qs}` : '' }, { replace: true })
          }
        }}
      />
      <CommandPalette
        open={cmdOpen}
        onClose={() => setCmdOpen(false)}
        showAdvanced={showAdvanced}
        elevated={elevated}
      />
    </div>
  )
}

