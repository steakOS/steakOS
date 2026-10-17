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
  BookOpen,
  FileText,
  Wallet,
  Receipt,
  PieChart,
  Landmark,
  Users,
  Shield,
  Database,
  ShoppingCart,
  Package,
  Factory,
  Briefcase,
  Headphones,
  Server,
  FolderKanban,
  GitBranch,
  BadgeCheck,
  RefreshCw,
  Bot,
  Network,
  ShieldCheck,
  Brain,
  Settings,
  HelpCircle,
  Bitcoin,
  Upload,
  Target,
  FlaskConical,
  Warehouse,
  Wrench,
  Building2,
  Ticket,
  LifeBuoy,
  KeyRound,
  Plug,
  ShieldAlert,
  Handshake,
  Tags,
  ContactRound,
  Scale,
  GitCompareArrows,
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

/**
 * AUTH03 — System nav by role.
 * Admin + CEO: full System menu for now (roles matrix later may narrow).
 * Help / tour / IT Support live under the Help group (not System).
 */
function systemLinks(t, { isAdmin, isCeo } = {}) {
  const links = [
    { name: t('nav.settings'), path: '/settings', icon: Settings },
    {
      name: t('nav.integrations', { defaultValue: 'Integrations' }),
      path: '/system/integrations',
      icon: Plug,
    },
    {
      name: t('nav.security', { defaultValue: 'Security' }),
      path: '/security',
      icon: ShieldCheck,
    },
    { name: t('nav.company'), path: '/company', icon: Building2 },
  ]
  if (isAdmin || isCeo) {
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
      { name: t('nav.import'), path: '/system/import', icon: Upload },
      { name: t('nav.catalogs'), path: '/system/catalogs', icon: Wrench },
    )
  }
  return links
}

/**
 * Help hub — tour, help center, IT support (below System).
 * Visible to **all** authenticated roles (Admin, CEO, desk users, read-only).
 */
function helpNavLinks(t) {
  return [
    {
      name: t('nav.helpCenter', { defaultValue: t('nav.help') }),
      path: '/help',
      icon: LifeBuoy,
    },
    {
      name: t('nav.itSupport', { defaultValue: 'IT Support' }),
      path: '/help/it-support',
      icon: Ticket,
    },
    {
      name: t('nav.tour'),
      path: '/tour/index.html',
      icon: BookOpen,
      external: true,
    },
  ]
}

/** IRON → Iron for chrome labels */
function formatTierLabel(tier) {
  if (!tier) return ''
  const t = String(tier)
  return t.charAt(0).toUpperCase() + t.slice(1).toLowerCase()
}

function financeFeatureLinks(t, { showAdvanced = false } = {}) {
  const links = [
    { name: t('features.generalLedger'), path: '/finance/transactions', icon: BookOpen },
    { name: t('features.invoices'), path: '/finance/invoices', icon: FileText },
    { name: t('features.payments', { defaultValue: 'Payments' }), path: '/finance/payments', icon: Bitcoin },
    { name: t('features.treasury'), path: '/finance/treasury', icon: Wallet },
    { name: t('features.payables'), path: '/finance/payables', icon: Receipt },
    { name: t('features.budgets'), path: '/finance/budgets', icon: PieChart },
    { name: t('features.tax'), path: '/finance/tax', icon: Landmark },
    { name: t('features.payroll'), path: '/finance/payroll', icon: Users },
    { name: t('features.control'), path: '/finance/control', icon: Shield },
    {
      name: t('features.postingRules', { defaultValue: 'Posting rules' }),
      path: '/finance/posting-rules',
      icon: BookOpen,
    },
  ]
  if (showAdvanced) {
    links.push(
      {
        name: t('features.collections', { defaultValue: 'Collections' }),
        path: '/finance/collections',
        icon: Scale,
      },
      {
        name: t('features.threeWayMatch', { defaultValue: 'Three-way match' }),
        path: '/finance/match',
        icon: GitCompareArrows,
      },
    )
  }
  return links
}

function salesFeatureLinks(t, { showAdvanced = false } = {}) {
  const links = [{ name: t('desk.title', { ns: 'sales' }), path: '/sales', icon: ShoppingCart }]
  if (showAdvanced) {
    links.push(
      {
        name: t('features.pricing', { defaultValue: 'Price lists' }),
        path: '/sales/pricing',
        icon: Tags,
      },
      {
        name: t('features.crm', { defaultValue: 'CRM' }),
        path: '/sales/crm',
        icon: ContactRound,
      },
    )
  }
  return links
}

function grrcFeatureLinks(t) {
  return [
    {
      name: t('features.grrcDesk', { defaultValue: 'GRRC desk' }),
      path: '/grrc',
      icon: ShieldAlert,
    },
  ]
}

function stakeholderFeatureLinks(t) {
  return [
    {
      name: t('features.stakeholderDesk', { defaultValue: 'Stakeholder desk' }),
      path: '/stakeholder',
      icon: Handshake,
    },
  ]
}

function procurementFeatureLinks(t) {
  return [{ name: t('features.buyersDesk'), path: '/scm/buyers', icon: Package }]
}

function productionFeatureLinks(t) {
  return [{ name: t('features.shopFloor'), path: '/scm/shopfloor', icon: Factory }]
}

function deliveryFeatureLinks(t) {
  return [{ name: t('features.deliveryDesk'), path: '/delivery', icon: Briefcase }]
}

function afterSalesFeatureLinks(t) {
  return [{ name: t('features.afterSalesDesk'), path: '/aftersales', icon: Headphones }]
}

function hrFeatureLinks(t) {
  return [{ name: t('features.hrDesk'), path: '/hr', icon: Users }]
}

function digitalFeatureLinks(t) {
  return [{ name: t('features.digitalDesk'), path: '/digital', icon: Server }]
}

function strategyFeatureLinks(t) {
  return [{ name: t('features.strategyDesk', { defaultValue: 'Strategy desk' }), path: '/strategy', icon: Target }]
}

function developmentFeatureLinks(t) {
  return [
    {
      name: t('features.developmentDesk', { defaultValue: 'Development desk' }),
      path: '/development',
      icon: FlaskConical,
    },
  ]
}

function assetFeatureLinks(t) {
  return [{ name: t('features.assetDesk', { defaultValue: 'Assets desk' }), path: '/assets', icon: Warehouse }]
}

function capabilityFeatureLinks(t) {
  return [
    { name: t('features.projects'), path: '/capability/projects', icon: FolderKanban },
    { name: t('features.processes'), path: '/capability/processes', icon: GitBranch },
    { name: t('features.quality'), path: '/capability/quality', icon: BadgeCheck },
    { name: t('features.changes'), path: '/capability/changes', icon: RefreshCw },
    { name: t('features.knowledge'), path: '/capability/knowledge', icon: BookOpen },
  ]
}

function agentsFeatureLinks(t) {
  return [
    { name: t('features.agents'), path: '/agents/agents', icon: Bot },
    { name: t('features.hives'), path: '/agents/hives', icon: Network },
    { name: t('features.hitl'), path: '/agents/hitl', icon: ShieldCheck },
    { name: t('features.memory'), path: '/agents/memory', icon: Brain },
  ]
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

  const featureLinks =
    group.package === 'finance'
      ? financeFeatureLinks(t, { showAdvanced })
      : group.package === 'sales'
        ? salesFeatureLinks(t, { showAdvanced })
        : group.package === 'procurement'
          ? procurementFeatureLinks(t)
          : group.package === 'production'
            ? productionFeatureLinks(t)
            : group.package === 'delivery'
              ? deliveryFeatureLinks(t)
              : group.package === 'service'
                ? afterSalesFeatureLinks(t)
                : group.package === 'human'
                  ? hrFeatureLinks(t)
                  : group.package === 'digital'
                    ? digitalFeatureLinks(t)
                    : group.package === 'capability'
                      ? capabilityFeatureLinks(t)
                      : group.package === 'agents'
                        ? agentsFeatureLinks(t)
                        : group.package === 'strategy'
                          ? strategyFeatureLinks(t)
                          : group.package === 'development'
                            ? developmentFeatureLinks(t)
                            : group.package === 'asset'
                              ? assetFeatureLinks(t)
                              : group.package === 'grrc'
                                ? grrcFeatureLinks(t)
                                : group.package === 'stakeholder'
                                  ? stakeholderFeatureLinks(t)
                                  : []
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
  const [cmdOpen, setCmdOpen] = useState(false)
  const [readOnly, setReadOnly] = useState(false)
  const [meUsername, setMeUsername] = useState(null)
  const [companies, setCompanies] = useState([])
  const [activeCompany, setActiveCompany] = useState(null)
  const [meCompanyCurrency, setMeCompanyCurrency] = useState(null)
  const [companyBusy, setCompanyBusy] = useState(false)
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
      if (Array.isArray(me.companies)) setCompanies(me.companies)
      if (me.activeCompany) setActiveCompany(me.activeCompany)
      else setActiveCompany(null)
      setMeCompanyCurrency(
        me.companyCurrency || me.activeCompany?.companyCurrency || null,
      )
      if (me.needCompanyPick && !window.location.pathname.startsWith('/select-company')) {
        window.location.href = '/select-company'
        return
      }
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
            {companies.length > 1 && (
              <label
                className="muted topbar-company-switcher"
                style={{ fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}
              >
                <span className="topbar-logout-label">
                  {t('pages.company.nav', { defaultValue: 'Company' })}
                </span>
                <select
                  value={activeCompany?.id ?? ''}
                  disabled={companyBusy}
                  aria-label={t('shell.activeCompany', { defaultValue: 'Active company' })}
                  onChange={async (e) => {
                    const id = Number(e.target.value)
                    if (!id) return
                    setCompanyBusy(true)
                    try {
                      const r = await meApi.selectCompany(id)
                      setActiveCompany(r.activeCompany || companies.find((c) => c.id === id))
                      window.location.reload()
                    } catch {
                      /* keep prior */
                    } finally {
                      setCompanyBusy(false)
                    }
                  }}
                >
                  {companies.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                      {c.demo || c.kind === 'DEMO'
                        ? t('shell.demoSuffix', { defaultValue: ' (Demo)' })
                        : ''}
                    </option>
                  ))}
                </select>
              </label>
            )}
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
      <CommandPalette open={cmdOpen} onClose={() => setCmdOpen(false)} />
    </div>
  )
}

