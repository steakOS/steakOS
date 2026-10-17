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

import { lazy, Suspense, useEffect, useState } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import AppShell from './layout/AppShell'
import { meApi, setupApi } from './api/client'

// Lazy pages: one bad/corrupt module transform must not blank the entire SPA boot.
const DashboardCeo = lazy(() => import('./pages/DashboardCeo'))
const MyWork = lazy(() => import('./pages/MyWork'))
const DomainList = lazy(() => import('./pages/DomainList'))
const DomainForm = lazy(() => import('./pages/DomainForm'))
const About = lazy(() => import('./pages/About'))
const FinanceTransactions = lazy(() => import('./pages/FinanceTransactions'))
const FinanceInvoices = lazy(() => import('./pages/FinanceInvoices'))
const FinancePayments = lazy(() => import('./pages/FinancePayments'))
const FinanceHub = lazy(() => import('./pages/FinanceHub'))
const PostingRulesPage = lazy(() => import('./pages/PostingRulesPage'))
const SalesDesk = lazy(() => import('./pages/SalesDesk'))
const SalesPricingPage = lazy(() => import('./pages/SalesPricingPage'))
const SalesCrmPage = lazy(() => import('./pages/SalesCrmPage'))
const ScmDesk = lazy(() => import('./pages/ScmDesk'))
const DeliveryDesk = lazy(() => import('./pages/DeliveryDesk'))
const AfterSalesDesk = lazy(() => import('./pages/AfterSalesDesk'))
const HrDesk = lazy(() => import('./pages/HrDesk'))
const DigitalDesk = lazy(() => import('./pages/DigitalDesk'))
const StrategyDesk = lazy(() => import('./pages/StrategyDesk'))
const DevelopmentDesk = lazy(() => import('./pages/DevelopmentDesk'))
const AssetDesk = lazy(() => import('./pages/AssetDesk'))
const GrrcDesk = lazy(() => import('./pages/GrrcDesk'))
const StakeholderDesk = lazy(() => import('./pages/StakeholderDesk'))
const AnalyticsDesk = lazy(() => import('./pages/AnalyticsDesk'))
const FinanceCollectionsPage = lazy(() => import('./pages/FinanceCollectionsPage'))
const FinanceMatchPage = lazy(() => import('./pages/FinanceMatchPage'))
const CapabilityHub = lazy(() => import('./pages/CapabilityHub'))
const AgentHub = lazy(() => import('./pages/AgentHub'))
const SetupWizard = lazy(() => import('./pages/SetupWizard'))
const SettingsPage = lazy(() => import('./pages/SettingsPage'))
const IntegrationsPage = lazy(() => import('./pages/IntegrationsPage'))
const CompanyProfile = lazy(() => import('./pages/CompanyProfile'))
const SystemCatalogs = lazy(() => import('./pages/SystemCatalogs'))
const HelpCenter = lazy(() => import('./pages/HelpCenter'))
const ItSupportPage = lazy(() => import('./pages/ItSupportPage'))
const ImportHub = lazy(() => import('./pages/ImportHub'))
const TotpGatePage = lazy(() => import('./pages/TotpGatePage'))
const SecuritySettingsPage = lazy(() => import('./pages/SecuritySettingsPage'))
const UsersAdminPage = lazy(() => import('./pages/UsersAdminPage'))
const RolesPage = lazy(() => import('./pages/RolesPage'))
const SelectCompanyPage = lazy(() => import('./pages/SelectCompanyPage'))

function RouteFallback() {
  return (
    <div className="setup-loading-screen">
      <p className="muted">Loading…</p>
    </div>
  )
}

/**
 * Gate: force /setup until setupComplete (OI-W01).
 * About allowed; legacy tenants bootstrap as complete Iron.
 *
 * Must only treat a real JSON status as incomplete — never HTML/login noise.
 * Status is re-fetched on navigation so finish → app does not loop on a stale flag.
 * Completed tenants may still open /setup (wizard shows celebration/finale).
 */
function SetupGate({ children }) {
  const location = useLocation()
  const [ready, setReady] = useState(false)
  const [complete, setComplete] = useState(true)
  const [gateError, setGateError] = useState(null)

  // Re-check on every navigation so finish → dashboard does not loop on a stale
  // "incomplete" flag from the initial mount (the post-setup flicker bug).
  useEffect(() => {
    let cancelled = false

    const timer = window.setTimeout(() => {
      if (cancelled) return
      setComplete(true)
      setReady(true)
      setGateError((g) => g || 'Setup status timed out — continuing.')
    }, 8000)

    setupApi
      .status()
      .then((s) => {
        if (cancelled) return
        if (s && typeof s === 'object' && 'setupComplete' in s) {
          setComplete(!!s.setupComplete)
        } else {
          setComplete(true)
        }
        setReady(true)
      })
      .catch((e) => {
        if (cancelled) return
        if (e?.code === 'UNAUTHORIZED') {
          setGateError('Redirecting to login…')
          setComplete(true)
          setReady(true)
          return
        }
        setComplete(true)
        setReady(true)
        setGateError(e?.message || null)
      })
      .finally(() => {
        window.clearTimeout(timer)
      })

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [location.pathname])

  if (!ready) {
    return (
      <div className="setup-loading-screen">
        <p className="muted">{gateError || 'Loading setup…'}</p>
        <p className="muted" style={{ fontSize: '0.85rem' }}>
          If this never finishes, open{' '}
          <a href="/login/auth">/login/auth</a> and sign in again.
        </p>
      </div>
    )
  }

  const onSetup = location.pathname.startsWith('/setup')
  const onAbout = location.pathname === '/about'
  const onCompany = location.pathname === '/company'
  const onTour = location.pathname === '/tour' || location.pathname.startsWith('/tour/')
  const onTotp = location.pathname === '/totp' || location.pathname.startsWith('/totp/')

  // Tour is static HTML; allow even when setup incomplete (TourEscape leaves SPA).
  // Company profile shows partial tenant data + banner while setup is open.
  // /totp is AUTH01 gate — always reachable after password login.
  if (!complete && !onSetup && !onAbout && !onCompany && !onTour && !onTotp) {
    return <Navigate to="/setup" replace />
  }
  // Allow /setup even when setupComplete. SetupWizard shows the celebration/finale
  // itself. A gate redirect to /dashboard raced the wizard (celebration flag not set
  // yet on cold open) and broke Help / Settings / direct /setup links.

  return children
}

/** AUTH03 — land on role default home (after company pick if needed). */
function HomeRedirect() {
  const [to, setTo] = useState(null)
  useEffect(() => {
    let cancelled = false
    meApi
      .get()
      .then((me) => {
        if (cancelled) return
        if (me?.needCompanyPick) setTo('/select-company')
        else setTo('/dashboard')
      })
      .catch(() => {
        if (!cancelled) setTo('/dashboard')
      })
    return () => {
      cancelled = true
    }
  }, [])
  if (!to) return <RouteFallback />
  return <Navigate to={to} replace />
}

/** PDR-026 — force company picker when 2+ memberships and none active. */
function CompanyPickGate({ children }) {
  const location = useLocation()
  const [ready, setReady] = useState(false)
  const [needPick, setNeedPick] = useState(false)

  useEffect(() => {
    let cancelled = false
    const onSelect = location.pathname.startsWith('/select-company')
    const onTotp = location.pathname.startsWith('/totp')
    const onSetup = location.pathname.startsWith('/setup')
    if (onSelect || onTotp || onSetup) {
      setNeedPick(false)
      setReady(true)
      return undefined
    }
    meApi
      .get()
      .then((me) => {
        if (cancelled) return
        setNeedPick(!!me?.needCompanyPick)
        setReady(true)
      })
      .catch(() => {
        if (!cancelled) {
          setNeedPick(false)
          setReady(true)
        }
      })
    return () => {
      cancelled = true
    }
  }, [location.pathname])

  if (!ready) {
    return (
      <div className="setup-loading-screen">
        <p className="muted">Loading…</p>
      </div>
    )
  }
  if (needPick && !location.pathname.startsWith('/select-company')) {
    return <Navigate to="/select-company" replace />
  }
  return children
}

/** Safety: if SPA ever mounts on /tour, leave React and load static tour. */
function TourEscape() {
  useEffect(() => {
    window.location.replace('/tour/index.html')
  }, [])
  return (
    <div className="setup-loading-screen">
      <p className="muted">Opening onboarding tour…</p>
    </div>
  )
}

export default function App() {
  return (
    <SetupGate>
      <CompanyPickGate>
        <Suspense fallback={<RouteFallback />}>
          <Routes>
          <Route path="setup/*" element={<SetupWizard />} />
          <Route path="tour" element={<TourEscape />} />
          <Route path="tour/*" element={<TourEscape />} />
          {/* AUTH01 — no AppShell chrome until TOTP ok */}
          <Route path="totp" element={<TotpGatePage />} />
          <Route path="select-company" element={<SelectCompanyPage />} />
          <Route element={<AppShell />}>
            <Route index element={<HomeRedirect />} />
            <Route path="dashboard" element={<DashboardCeo />} />
            <Route path="work" element={<MyWork />} />
            <Route path="domains/core/Activity/*" element={<Navigate to="/work" replace />} />
            <Route path="about" element={<About />} />
            <Route path="company" element={<CompanyProfile />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="system/integrations" element={<IntegrationsPage />} />
            <Route path="security" element={<SecuritySettingsPage />} />
            <Route path="users" element={<UsersAdminPage />} />
            <Route path="roles" element={<RolesPage />} />
            <Route path="help" element={<HelpCenter />} />
            <Route path="help/it-support" element={<ItSupportPage />} />
            <Route path="system/import" element={<ImportHub />} />
            <Route path="system/catalogs" element={<SystemCatalogs />} />
            <Route path="sales" element={<SalesDesk />} />
            <Route path="sales/pricing" element={<SalesPricingPage />} />
            <Route path="sales/crm" element={<SalesCrmPage />} />
            <Route path="scm" element={<Navigate to="/scm/buyers" replace />} />
            <Route path="scm/buyers" element={<ScmDesk desk="buyers" />} />
            <Route path="scm/shopfloor" element={<ScmDesk desk="shopfloor" />} />
            <Route path="delivery" element={<DeliveryDesk />} />
            <Route path="aftersales" element={<AfterSalesDesk />} />
            <Route path="hr" element={<HrDesk />} />
            <Route path="digital" element={<DigitalDesk />} />
            <Route path="strategy" element={<StrategyDesk />} />
            <Route path="development" element={<DevelopmentDesk />} />
            <Route path="assets" element={<AssetDesk />} />
            <Route path="grrc" element={<GrrcDesk />} />
            <Route path="stakeholder" element={<StakeholderDesk />} />
            {/* Analytics desk UI (PR-14); AppShell nav link is PR-15 */}
            <Route path="analytics" element={<AnalyticsDesk />} />
            <Route path="analytics/:kpiId" element={<AnalyticsDesk />} />
            <Route path="capability" element={<Navigate to="/capability/projects" replace />} />
            <Route path="capability/:section" element={<CapabilityHub />} />
            <Route path="agents" element={<Navigate to="/agents/agents" replace />} />
            <Route path="agents/:section" element={<AgentHub />} />
            <Route path="finance/transactions" element={<FinanceTransactions />} />
            <Route path="finance/invoices" element={<FinanceInvoices />} />
            <Route path="finance/payments" element={<FinancePayments />} />
            <Route path="finance/posting-rules" element={<PostingRulesPage />} />
            <Route path="finance/collections" element={<FinanceCollectionsPage />} />
            <Route path="finance/match" element={<FinanceMatchPage />} />
            <Route path="finance" element={<Navigate to="/finance/treasury" replace />} />
            <Route path="finance/:section" element={<FinanceHub />} />
            <Route path="domains/:package/:resource" element={<DomainList />} />
            <Route path="domains/:package/:resource/new" element={<DomainForm />} />
            <Route path="domains/:package/:resource/:id" element={<DomainForm />} />
          </Route>
          <Route path="*" element={<HomeRedirect />} />
        </Routes>
        </Suspense>
      </CompanyPickGate>
    </SetupGate>
  )
}
