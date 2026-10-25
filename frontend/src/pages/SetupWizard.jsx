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

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { authApi, meApi, nostrApi, setupApi } from '../api/client'
import AuthAppGuide from '../components/AuthAppGuide'
import { RULES_HINT, validatePassword } from '../util/passwordPolicy'
import { validateBtcReceiveAddress } from '../util/btcAddress'
import { companyNameSpecialCharWarn } from '../util/companyNameWarn'

const STEP_IDS = [
  'tour',
  'company',
  'site',
  'banking',
  'bitcoin',
  'nostr', // OI-NOSTR01 — after Bitcoin (micro-decision: banking → BTC → Nostr)
  'template',
  'families',
  'accounts',
  'summary',
]
const CELEBRATION_KEY = 'steakos.setupCelebration'
/** Pack-fixed on Silver — not individually off (SI-04 / plan-wizard Q2). */
const SILVER_FIXED_FAMILIES = ['grrc', 'stakeholder']

function openMetalCodes(tiers) {
  return (tiers || [])
    .filter(
      (tier) =>
        tier.openForMetalSelection === true ||
        (tier.openForMetalSelection == null && tier.choosable === true),
    )
    .map((tier) => String(tier.code || '').toUpperCase())
}

/** QA only: ?previewSilver=1 or localStorage steakos.previewSilverWizard=1 */
function previewSilverWizard() {
  try {
    if (typeof window !== 'undefined') {
      const q = new URLSearchParams(window.location.search || '')
      if (q.get('previewSilver') === '1') return true
    }
    return localStorage.getItem('steakos.previewSilverWizard') === '1'
  } catch {
    return false
  }
}

function defaultPluginDepthForTier(tier) {
  const t = String(tier || 'BRONZE').toUpperCase()
  if (t === 'SILVER' || t === 'GOLD' || t === 'IRON') {
    return { sales: 'ADVANCED', finance: 'ADVANCED' }
  }
  return { sales: 'BASICS', finance: 'BASICS' }
}

function defaultMessagingFlags() {
  return { emailEnabled: false, telegramEnabled: false }
}

function preferredTemplateFromTour(openSet) {
  // Accept tour preference only when that metal is open (API / openSet). Else Bronze.
  try {
    const t = (localStorage.getItem('steakos.preferredTemplate') || '').toUpperCase()
    const open = openSet?.length ? openSet : ['BRONZE']
    if (t && open.includes(t)) return t
  } catch {
    /* ignore */
  }
  return 'BRONZE'
}

/** PDR-023 D13: en-US → USD, else EUR */
function defaultPrimaryRail() {
  try {
    const loc = (typeof navigator !== 'undefined' ? navigator.language : '') || ''
    const n = loc.toLowerCase().replace('_', '-')
    if (n === 'en-us' || n.startsWith('en-us')) return 'USD'
  } catch {
    /* ignore */
  }
  return 'EUR'
}

const EMPTY = {
  companyName: '',
  legalForm: 'SOLE',
  taxId: '',
  countryCode: 'DE',
  primaryValueRail: defaultPrimaryRail(),
  functionalCurrency: defaultPrimaryRail(),
  bitcoinDisplayUnit: 'SATS',
  displayFiat: 'EUR',
  dualSats: false,
  address: '',
  siteName: 'Main',
  siteType: 'HQ',
  siteAddress: '',
  /** When true, main site address tracks company registered address. */
  siteAddressSameAsCompany: true,
  bankName: '',
  /** SEPA (IBAN/BIC) or ACH (US routing + account). Default from country when unset. */
  paymentScheme: '',
  iban: '',
  bic: '',
  branchName: '',
  accountNumber: '',
  routingNumber: '',
  /** OI-BTC01 — DEFERRED | SINGLE_ADDRESS | BTCPAY */
  btcIntegrationLevel: 'DEFERRED',
  btcReceiveAddress: '',
  btcReceiveAddressLabel: '',
  btcpayBaseUrl: '',
  btcpayStoreId: '',
  btcpayDefaultCurrency: 'EUR',
  btcpayApiKey: '',
  btcpayWebhookSecret: '',
  /** OI-NOSTR01 — DEFERRED | LOGIN_AUDIT | LOGIN_AUDIT_NOTIFY */
  nostrIntegrationLevel: 'DEFERRED',
  nostrRelayUrl: 'ws://127.0.0.1:7777',
  nostrOperatorNpub: '',
  templateTier: preferredTemplateFromTour(),
  enabledFamilies: null,
  /** G5 depth map — Advanced pack-mandatory on Silver; Basics on Bronze. */
  pluginDepth: defaultPluginDepthForTier(preferredTemplateFromTour()),
  /** SI-R7 optional adapters — defaults off until credentials under Integrations. */
  messaging: defaultMessagingFlags(),
  seedDemo: true,
  seedCoa: true,
  locale: 'en_US',
  adminPassword: '',
  ceoUsername: 'ceo',
  ceoPassword: '',
  ceoDisplayName: 'CEO',
}

function markCelebration() {
  try {
    sessionStorage.setItem(CELEBRATION_KEY, '1')
  } catch {
    /* ignore */
  }
}

function clearCelebration() {
  try {
    sessionStorage.removeItem(CELEBRATION_KEY)
  } catch {
    /* ignore */
  }
}

function hasCelebrationFlag() {
  try {
    return sessionStorage.getItem(CELEBRATION_KEY) === '1'
  } catch {
    return false
  }
}

export default function SetupWizard() {
  const { t } = useTranslation(['setup', 'common'])
  const [step, setStep] = useState(0)
  const [draft, setDraft] = useState(EMPTY)
  const [tiers, setTiers] = useState([])
  const [status, setStatus] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  /** Inline BTC address feedback on wizard bitcoin step (Task 7620). */
  const [btcAddressError, setBtcAddressError] = useState(null)
  /** Soft (non-blocking) company-name special-char tip (Task 7622). */
  const [companyNameWarn, setCompanyNameWarn] = useState(null)
  const [check, setCheck] = useState(null)
  /** 'wizard' | 'celebration' — celebration is post-complete, no further apply */
  const [phase, setPhase] = useState(() => (hasCelebrationFlag() ? 'celebration' : 'wizard'))

  // AUTH02 — Admin + CEO passwords + TOTP in wizard
  const [meUser, setMeUser] = useState(null)
  const [adminEnrol, setAdminEnrol] = useState(null)
  const [ceoEnrol, setCeoEnrol] = useState(null)
  const [adminCode, setAdminCode] = useState('')
  const [ceoCode, setCeoCode] = useState('')
  const [adminTotpOk, setAdminTotpOk] = useState(false)
  const [ceoTotpOk, setCeoTotpOk] = useState(false)
  const [adminRecovery, setAdminRecovery] = useState(null)
  const [ceoRecovery, setCeoRecovery] = useState(null)
  const [accountsMsg, setAccountsMsg] = useState(null)
  /** Celebration: user confirms they have auth apps + recovery codes before login. */
  const [authReadyAck, setAuthReadyAck] = useState(false)

  /** OI-NOSTR01 — local relay install/start status on wizard Nostr step */
  const [nostrRelay, setNostrRelay] = useState(null)
  const [nostrRelayBusy, setNostrRelayBusy] = useState(false)
  const [nostrRelayMsg, setNostrRelayMsg] = useState(null)

  // Keep site address in sync while "same as company" is checked
  useEffect(() => {
    if (!draft.siteAddressSameAsCompany) return
    const companyAddr = draft.address || ''
    if (draft.siteAddress === companyAddr) return
    setDraft((prev) =>
      prev.siteAddressSameAsCompany ? { ...prev, siteAddress: companyAddr } : prev,
    )
  }, [draft.siteAddressSameAsCompany, draft.address, draft.siteAddress])

  // Probe local Nostr relay when entering the Nostr step
  useEffect(() => {
    if (phase !== 'wizard' || STEP_IDS[step] !== 'nostr') return
    let cancelled = false
    ;(async () => {
      try {
        const st = await nostrApi.relayStatus()
        if (!cancelled) setNostrRelay(st)
      } catch (e) {
        if (!cancelled) {
          setNostrRelay({ ok: false, reachable: false, detail: e.message || String(e) })
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [phase, step])

  const load = useCallback(async () => {
    setError(null)
    try {
      const st = await setupApi.status()
      if (!st || typeof st !== 'object' || !('setupComplete' in st)) {
        setError(t('errors.loginAgain', { ns: 'common' }))
        return
      }
      setStatus(st)
      setTiers(st.tiers || [])

      if (st.setupComplete) {
        // Already done: show finale (not step 1 + "Setup already complete" errors)
        markCelebration()
        setPhase('celebration')
        return
      }

      clearCelebration()
      setPhase('wizard')
      const open = openMetalCodes(st.tiers || [])
      const d = await setupApi.getDraft()
      if (d?.draft && typeof d.draft === 'object' && Object.keys(d.draft).length) {
        setDraft((prev) => {
          const merged = { ...prev, ...d.draft }
          // Migrate legacy functionalCurrency-only drafts → primaryValueRail
          if (!merged.primaryValueRail && merged.functionalCurrency) {
            const fc = String(merged.functionalCurrency).toUpperCase()
            merged.primaryValueRail =
              fc === 'USD' ? 'USD' : fc === 'BTC' || fc === 'XBT' ? 'BTC' : 'EUR'
          }
          if (!merged.primaryValueRail) merged.primaryValueRail = defaultPrimaryRail()
          merged.functionalCurrency = merged.primaryValueRail
          merged.dualSats = false
          if (merged.siteAddressSameAsCompany == null) {
            const co = (merged.address || '').trim()
            const sa = (merged.siteAddress || '').trim()
            merged.siteAddressSameAsCompany = !sa || sa === co
            if (merged.siteAddressSameAsCompany) merged.siteAddress = merged.address || ''
          }
          // Gate closed metals from tour/stale draft (keep tenant metal if already applied)
          const draftTier = String(merged.templateTier || '').toUpperCase()
          const statusTier = String(st.templateTier || '').toUpperCase()
          const allowClosed =
            previewSilverWizard() || (draftTier && draftTier === statusTier)
          if (draftTier && !open.includes(draftTier) && !allowClosed) {
            merged.templateTier = preferredTemplateFromTour(open)
            merged.enabledFamilies = null
          } else if (!draftTier) {
            merged.templateTier = preferredTemplateFromTour(open)
          }
          const depthBase =
            st.pluginDepth && typeof st.pluginDepth === 'object'
              ? st.pluginDepth
              : defaultPluginDepthForTier(merged.templateTier)
          merged.pluginDepth = {
            ...defaultPluginDepthForTier(merged.templateTier),
            ...depthBase,
            ...(merged.pluginDepth && typeof merged.pluginDepth === 'object'
              ? merged.pluginDepth
              : {}),
          }
          // Silver+ forces Advanced (G5) — UI badges are fixed, not toggles
          if (['SILVER', 'GOLD', 'IRON'].includes(String(merged.templateTier).toUpperCase())) {
            merged.pluginDepth = {
              ...merged.pluginDepth,
              sales: 'ADVANCED',
              finance: 'ADVANCED',
            }
          }
          const msgBase =
            st.messaging && typeof st.messaging === 'object'
              ? st.messaging
              : defaultMessagingFlags()
          merged.messaging = {
            ...defaultMessagingFlags(),
            ...msgBase,
            ...(merged.messaging && typeof merged.messaging === 'object'
              ? merged.messaging
              : {}),
          }
          return merged
        })
      } else {
        setDraft((prev) => {
          const tier = preferredTemplateFromTour(open)
          return {
            ...prev,
            templateTier: tier,
            pluginDepth: st.pluginDepth || defaultPluginDepthForTier(tier),
            messaging: {
              ...defaultMessagingFlags(),
              ...(st.messaging && typeof st.messaging === 'object' ? st.messaging : {}),
            },
          }
        })
      }
      if (typeof d?.draftStep === 'number' && d.draftStep > 0) {
        setStep(Math.min(d.draftStep, STEP_IDS.length - 1))
      }
    } catch (e) {
      if (e?.code === 'UNAUTHORIZED') return
      setError(e.message || String(e))
    }
  }, [t])

  useEffect(() => {
    load()
  }, [load])

  // Family sets aligned to TemplateTiers / PLUGINS.md §8 (operator ladder)
  const defaultFamiliesByTier = useMemo(
    () => ({
      BRONZE: [
        'core', 'sales', 'scm', 'finance', 'delivery', 'aftersales', 'capability', 'agents',
        'human', 'digital',
        'strategy', 'development', 'asset',
      ],
      SILVER: [
        'core', 'sales', 'scm', 'finance', 'delivery', 'aftersales', 'capability', 'agents',
        'human', 'digital',
        'strategy', 'development', 'asset',
        'grrc', 'stakeholder',
      ],
      GOLD: [
        'core', 'sales', 'scm', 'delivery', 'aftersales', 'human', 'digital',
        'finance', 'capability', 'agents',
        'strategy', 'development', 'asset', 'grrc', 'stakeholder',
      ],
    }),
    [],
  )

  const familiesForTier = useMemo(() => {
    return (
      draft.enabledFamilies ||
      defaultFamiliesByTier[draft.templateTier] ||
      defaultFamiliesByTier.BRONZE
    )
  }, [draft.enabledFamilies, draft.templateTier, defaultFamiliesByTier])

  const isSilverPack = useMemo(() => {
    const t = String(draft.templateTier || '').toUpperCase()
    return t === 'SILVER' || t === 'GOLD' || t === 'IRON'
  }, [draft.templateTier])

  /** Full Silver options when metal is Silver (or QA preview while selection closed). */
  const showSilverOptions = isSilverPack || previewSilverWizard()

  const openMetals = useMemo(() => openMetalCodes(tiers), [tiers])

  const pluginDepth = useMemo(
    () => ({
      ...defaultPluginDepthForTier(draft.templateTier),
      ...(draft.pluginDepth && typeof draft.pluginDepth === 'object' ? draft.pluginDepth : {}),
    }),
    [draft.pluginDepth, draft.templateTier],
  )

  const messaging = useMemo(
    () => ({
      ...defaultMessagingFlags(),
      ...(draft.messaging && typeof draft.messaging === 'object' ? draft.messaging : {}),
    }),
    [draft.messaging],
  )

  /** People data (HR) in pack → privacy ack required before finish */
  const needsHrPrivacyAck = useMemo(
    () => familiesForTier.includes('human'),
    [familiesForTier],
  )
  /** Server may already have an ack from a prior run; UI still requires an explicit click each setup. */
  const hrPrivacyAlreadyAcked = !!(status?.privacy?.hrAcknowledged)
  const [hrPrivacyChecked, setHrPrivacyChecked] = useState(false)

  const patch = (fields) => {
    setDraft((d) => {
      const next = { ...d, ...fields }
      if (fields.templateTier != null) {
        const tier = String(fields.templateTier).toUpperCase()
        next.pluginDepth = defaultPluginDepthForTier(tier)
        // Messaging stays operator choice; keep existing or default off
        if (!next.messaging) next.messaging = defaultMessagingFlags()
      }
      return next
    })
  }

  const patchMessaging = (key, value) => {
    setDraft((d) => ({
      ...d,
      messaging: {
        ...defaultMessagingFlags(),
        ...(d.messaging && typeof d.messaging === 'object' ? d.messaging : {}),
        [key]: value,
      },
    }))
  }

  const saveDraft = async (nextStep = step) => {
    await setupApi.saveDraft({
      step: nextStep,
      draft: {
        ...draft,
        enabledFamilies: familiesForTier,
        pluginDepth: isSilverPack
          ? { ...pluginDepth, sales: 'ADVANCED', finance: 'ADVANCED' }
          : pluginDepth,
        messaging,
      },
    })
  }

  const refreshAccountTotpFlags = useCallback(async () => {
    try {
      const me = await meApi.get()
      setMeUser(me)
      if (me?.totpEnabled) setAdminTotpOk(true)
      const list = await authApi.usersList().catch(() => ({ items: [] }))
      const ceoName = (draft.ceoUsername || 'ceo').trim().toLowerCase()
      const ceo = (list.items || []).find((u) => u.username === ceoName)
      if (ceo?.totpEnabled) setCeoTotpOk(true)
    } catch {
      /* non-fatal while loading */
    }
  }, [draft.ceoUsername])

  useEffect(() => {
    if ((STEP_IDS[step] === 'accounts' || STEP_IDS[step] === 'summary') && phase === 'wizard') {
      refreshAccountTotpFlags()
    }
  }, [step, phase, refreshAccountTotpFlags])

  const saveAdminPassword = async () => {
    const err = validatePassword(draft.adminPassword)
    if (err) throw new Error(`Admin ${err}`)
    const username = meUser?.username || 'admin'
    await authApi.usersUpdate({ username, password: draft.adminPassword })
  }

  const ensureCeoAccount = async () => {
    const username = (draft.ceoUsername || 'ceo').trim().toLowerCase()
    if (!username) throw new Error('CEO username required')
    const ceoPwErr = validatePassword(draft.ceoPassword)
    if (ceoPwErr) throw new Error(`CEO ${ceoPwErr}`)
    const list = await authApi.usersList()
    const exists = (list.items || []).some((u) => u.username === username)
    const authorities = ['ROLE_USER', 'ROLE_CEO', 'ROLE_ADMIN']
    if (exists) {
      await authApi.usersUpdate({
        username,
        password: draft.ceoPassword,
        authorities,
      })
    } else {
      await authApi.usersCreate({
        username,
        password: draft.ceoPassword,
        displayName: draft.ceoDisplayName || 'CEO',
        authorities,
      })
    }
    return username
  }

  const beginAdminTotp = async () => {
    setBusy(true)
    setError(null)
    setAccountsMsg(null)
    try {
      // Start TOTP first so a password-save glitch cannot block the QR.
      const r = await authApi.totpBegin()
      if (!r?.secret && !r?.qrDataUrl) {
        throw new Error('Authenticator setup did not return a secret — try again or reload.')
      }
      setAdminEnrol(r)
      if (draft.adminPassword?.trim()) {
        try {
          await saveAdminPassword()
          setAccountsMsg('Admin password updated. Scan the QR with your authenticator app.')
        } catch (pwErr) {
          setAccountsMsg(
            `Authenticator QR ready, but password was not saved: ${pwErr.message || pwErr}. You can retry the password later.`,
          )
        }
      } else {
        setAccountsMsg('Scan the QR (or enter the key) in your authenticator app, then confirm the code.')
      }
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const confirmAdminTotp = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const r = await authApi.totpConfirm(adminCode.trim())
      setAdminRecovery(r.recoveryCodes || [])
      setAdminEnrol(null)
      setAdminTotpOk(true)
      setAccountsMsg('Admin authenticator enabled — save recovery codes offline.')
    } catch (ex) {
      setError(ex.message || String(ex))
    } finally {
      setBusy(false)
    }
  }

  const beginCeoTotp = async () => {
    setBusy(true)
    setError(null)
    setAccountsMsg(null)
    try {
      const username = await ensureCeoAccount()
      const r = await authApi.totpBeginFor(username)
      if (!r?.secret && !r?.qrDataUrl) {
        throw new Error('CEO authenticator setup did not return a secret — try again.')
      }
      setCeoEnrol(r)
      setAccountsMsg('CEO QR ready. Scan with the authenticator app, then confirm the code.')
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const confirmCeoTotp = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const username = (draft.ceoUsername || 'ceo').trim().toLowerCase()
      const r = await authApi.totpConfirmFor(username, ceoCode.trim())
      setCeoRecovery(r.recoveryCodes || [])
      setCeoEnrol(null)
      setCeoTotpOk(true)
      setAccountsMsg('CEO authenticator enabled — save recovery codes offline.')
    } catch (ex) {
      setError(ex.message || String(ex))
    } finally {
      setBusy(false)
    }
  }

  const goNext = async () => {
    if (STEP_IDS[step] === 'accounts' && (!adminTotpOk || !ceoTotpOk)) {
      setError(
        'Set Admin and CEO passwords and enable authenticator apps for both before continuing.',
      )
      return
    }
    if (STEP_IDS[step] === 'bitcoin') {
      const lvl = draft.btcIntegrationLevel || 'DEFERRED'
      if (lvl === 'SINGLE_ADDRESS') {
        const v = validateBtcReceiveAddress(draft.btcReceiveAddress)
        if (!v.ok) {
          const msg =
            v.code === 'empty'
              ? t('bitcoin.addressRequired', {
                  defaultValue: v.message,
                })
              : v.code === 'seed'
                ? t('bitcoin.addressSeedRejected', {
                    defaultValue: v.message,
                  })
                : t('bitcoin.addressInvalid', {
                    defaultValue: v.message,
                  })
          setBtcAddressError(msg)
          setError(msg)
          return
        }
        setBtcAddressError(null)
      }
      if (
        lvl === 'BTCPAY' &&
        (!draft.btcpayBaseUrl?.trim() || !draft.btcpayStoreId?.trim() || !draft.btcpayApiKey?.trim())
      ) {
        setError(
          t('bitcoin.btcpayRequired', {
            defaultValue: 'Level 3 requires BTCPay base URL, store ID, and API key.',
          }),
        )
        return
      }
    }
    if (STEP_IDS[step] === 'nostr') {
      const npub = (draft.nostrOperatorNpub || '').trim()
      if (npub) {
        const lower = npub.toLowerCase()
        if (lower.startsWith('nsec') || lower.includes('nsec1')) {
          setError(
            t('nostr.nsecRejected', {
              defaultValue: 'Never paste a secret key (nsec). Public npub only, or leave blank.',
            }),
          )
          return
        }
        if (!lower.startsWith('npub1') && !/^[0-9a-fA-F]{64}$/.test(npub)) {
          setError(
            t('nostr.npubInvalid', {
              defaultValue: 'Operator public id must be npub1… or 64-char hex (or leave blank).',
            }),
          )
          return
        }
      }
      const relay = (draft.nostrRelayUrl || '').trim()
      if (relay && !relay.startsWith('ws://') && !relay.startsWith('wss://')) {
        setError(
          t('nostr.relayInvalid', {
            defaultValue: 'Relay URL must start with ws:// or wss://.',
          }),
        )
        return
      }
    }
    setBusy(true)
    setError(null)
    try {
      if (STEP_IDS[step] === 'accounts') {
        if (draft.adminPassword) await saveAdminPassword()
        await ensureCeoAccount()
      }
      // Level ≥ 1 → auto-install + start local nostr-rs-relay (project-local)
      if (STEP_IDS[step] === 'nostr') {
        const lvl = draft.nostrIntegrationLevel || 'DEFERRED'
        if (lvl === 'LOGIN_AUDIT' || lvl === 'LOGIN_AUDIT_NOTIFY') {
          setNostrRelayBusy(true)
          setNostrRelayMsg(
            t('nostr.relayEnsuring', {
              defaultValue: 'Installing/starting local Nostr relay (may take a minute if building)…',
            }),
          )
          try {
            const ensured = await nostrApi.relayEnsure()
            setNostrRelay(ensured)
            if (!ensured?.reachable && !ensured?.ok) {
              setNostrRelayMsg(
                ensured?.error ||
                  t('nostr.relayEnsureWarn', {
                    defaultValue:
                      'Relay not reachable yet. You can continue and finish under System → Integrations, or go Back and retry.',
                  }),
              )
              // Soft: allow continue with warning (don’t brick setup)
            } else {
              setNostrRelayMsg(
                t('nostr.relayReady', {
                  defaultValue: 'Local Nostr relay is reachable on loopback.',
                }),
              )
            }
          } catch (ex) {
            setNostrRelayMsg(ex.message || String(ex))
            // Soft continue
          } finally {
            setNostrRelayBusy(false)
          }
        }
      }
      const next = Math.min(step + 1, STEP_IDS.length - 1)
      await saveDraft(next)
      setStep(next)
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const goBack = () => setStep((s) => Math.max(0, s - 1))

  /**
   * Apply + complete, then stay on a celebration screen.
   * Do NOT navigate to /dashboard here — SetupGate still thinks setup is incomplete
   * until a full reload or status re-fetch, which caused the step-1 flicker loop.
   */
  const finish = async () => {
    const showErr = (msg) => {
      setError(msg)
      try {
        window.scrollTo({ top: 0, behavior: 'smooth' })
      } catch {
        /* ignore */
      }
    }
    setError(null)
    if (!draft.companyName?.trim()) {
      showErr('Company name is required before finishing.')
      return
    }
    // Always read live TOTP flags (React state can be stale after HMR / remount)
    try {
      const me = await meApi.get()
      const list = await authApi.usersList().catch(() => ({ items: [] }))
      const ceoName = (draft.ceoUsername || 'ceo').trim().toLowerCase()
      const ceo = (list.items || []).find((u) => u.username === ceoName)
      const adminOk = !!me?.totpEnabled
      const ceoOk = !!ceo?.totpEnabled
      setAdminTotpOk(adminOk)
      setCeoTotpOk(ceoOk)
      setMeUser(me)
      if (!adminOk || !ceoOk) {
        showErr(
          `Authenticator required before finish — Admin 2FA ${adminOk ? 'on' : 'off'}, CEO 2FA ${ceoOk ? 'on' : 'off'}. Go back to Accounts and enable both.`,
        )
        return
      }
    } catch (e) {
      showErr(
        e?.code === 'TOTP_REQUIRED'
          ? 'Session needs an authenticator code. Open /totp, enter a code, then return here and Finish again.'
          : e.message || String(e),
      )
      return
    }
    if (needsHrPrivacyAck && !hrPrivacyChecked) {
      showErr(
        t('privacy.required', {
          defaultValue:
            'Acknowledge privacy duties before enabling people (HR) data on this instance.',
        }),
      )
      return
    }

    setBusy(true)
    try {
      if (needsHrPrivacyAck) {
        const ack = await setupApi.privacyHrAck({})
        setStatus(ack?.status || status)
      }
      // Only apply metals that are open for selection (Gold stays closed; preview cannot escalate).
      const applyTier = openMetals.includes(String(draft.templateTier || '').toUpperCase())
        ? draft.templateTier
        : preferredTemplateFromTour(openMetals)
      const applyDepth = ['SILVER', 'GOLD', 'IRON'].includes(String(applyTier).toUpperCase())
        ? { sales: 'ADVANCED', finance: 'ADVANCED' }
        : defaultPluginDepthForTier(applyTier)
      await setupApi.apply({
        ...draft,
        templateTier: applyTier,
        enabledFamilies:
          draft.enabledFamilies ||
          defaultFamiliesByTier[applyTier] ||
          defaultFamiliesByTier.BRONZE,
        pluginDepth: applyDepth,
        messaging,
        seedCoa: draft.seedCoa,
      })
      const report = await setupApi.check()
      setCheck(report)
      await setupApi.complete({ seedDemo: draft.seedDemo !== false })
      markCelebration()
      setPhase('celebration')
      setStatus((s) => (s ? { ...s, setupComplete: true } : { setupComplete: true }))
    } catch (e) {
      const msg = e.message || String(e)
      // If apply/complete raced and setup is already done, still show the party
      if (/already complete/i.test(msg) || e?.code === 'SETUP_COMPLETE') {
        markCelebration()
        setPhase('celebration')
        setError(null)
      } else {
        showErr(msg)
      }
    } finally {
      setBusy(false)
    }
  }

  /** Leave setup for login → app (full navigation remounts SetupGate with complete=true). */
  const launchSystem = () => {
    clearCelebration()
    // Prefer SPA entry; session cookie usually still valid → lands in shell after login redirect
    window.location.assign('/login/auth')
  }

  if (phase === 'celebration') {
    const company = draft.companyName || status?.companyName || ''
    return (
      <div className="setup-wizard setup-wizard--finale">
        <header className="setup-header">
          <div className="setup-brand">{t('brand')}</div>
          <div className="setup-meta">{t('celebration.meta')}</div>
          <div className="setup-progress">
            <div className="setup-progress-bar" style={{ width: '100%' }} />
          </div>
        </header>

        <main className="setup-main">
          <section className="setup-card setup-finale">
            <div className="setup-finale-art">
              <img
                src="/setup/bbq.jpg"
                alt=""
                className="setup-finale-img"
              />
            </div>
            <p className="setup-finale-eyebrow">{t('celebration.eyebrow')}</p>
            <h1>{t('celebration.title')}</h1>
            <p className="setup-finale-lead">{t('celebration.lead')}</p>
            {company ? (
              <p className="setup-finale-company">
                {t('celebration.forCompany', { name: company })}
              </p>
            ) : null}
            <p>{t('celebration.body')}</p>
            <div className="setup-finale-creds">
              <div className="setup-finale-creds-title">{t('celebration.credsTitle')}</div>
              <dl className="setup-summary">
                <dt>Admin</dt>
                <dd className="mono">{meUser?.username || 'admin'}</dd>
                <dt>CEO</dt>
                <dd className="mono">{draft.ceoUsername || 'ceo'}</dd>
              </dl>
              <p className="setup-tip">
                {t('celebration.credsHintAuth', {
                  defaultValue:
                    'Sign in with the Admin or CEO password from the Accounts step. After the password, steakOS will ask for a 6-digit code from your authenticator app.',
                })}
              </p>
            </div>

            <AuthAppGuide
              title={t('celebration.authGuideTitle', {
                defaultValue: 'Important: authenticator app required at login',
              })}
            />

            <label className="setup-check" style={{ display: 'block', marginTop: 12 }}>
              <input
                type="checkbox"
                checked={authReadyAck}
                onChange={(e) => setAuthReadyAck(e.target.checked)}
              />{' '}
              {t('celebration.authReadyAck', {
                defaultValue:
                  'I have an authenticator app installed (or will install one now), and I saved my recovery codes offline.',
              })}
            </label>

            <p className="setup-finale-cta-copy">{t('celebration.ctaCopy')}</p>
            <button
              type="button"
              className="btn btn-primary setup-finale-btn"
              disabled={!authReadyAck}
              onClick={launchSystem}
              title={
                authReadyAck
                  ? undefined
                  : t('celebration.authReadyRequired', {
                      defaultValue: 'Confirm the authenticator checklist above first.',
                    })
              }
            >
              {t('celebration.cta')}
            </button>
            {!authReadyAck && (
              <p className="muted" style={{ fontSize: '0.85rem', marginTop: 8 }}>
                {t('celebration.authReadyRequired', {
                  defaultValue: 'Confirm the authenticator checklist above first.',
                })}
              </p>
            )}
          </section>
        </main>
      </div>
    )
  }

  const progress = ((step + 1) / STEP_IDS.length) * 100

  return (
    <div className="setup-wizard">
      <header className="setup-header">
        <div className="setup-brand">{t('brand')}</div>
        <div className="setup-meta">
          {step + 1}/{STEP_IDS.length} · {t(`steps.${STEP_IDS[step]}`)}
          {status?.templateTier ? ` · ${status.templateTier}` : ''}
        </div>
        <div className="setup-progress">
          <div className="setup-progress-bar" style={{ width: `${progress}%` }} />
        </div>
      </header>

      <main className="setup-main">
        {error && <div className="setup-error">{error}</div>}

        {step === 0 && (
          <section className="setup-card">
            <h1>{t('tour.title')}</h1>
            <p>{t('tour.body')}</p>
            <p className="setup-tip">{t('tour.tip')}</p>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 12 }}>
              <a className="btn btn-primary" href="/tour/index.html" target="_blank" rel="noreferrer">
                {t('tour.open')}
              </a>
            </div>
          </section>
        )}

        {step === 1 && (
          <section className="setup-card">
            <h1>{t('company.title')}</h1>
            <p>{t('company.blurb')}</p>
            <label>
              {t('company.name')}
              <input
                value={draft.companyName}
                onChange={(e) => {
                  const value = e.target.value
                  patch({ companyName: value })
                  const w = companyNameSpecialCharWarn(value)
                  if (!w.warn) {
                    setCompanyNameWarn(null)
                    return
                  }
                  const msg =
                    w.code === 'html'
                      ? t('company.nameWarnHtml', { defaultValue: w.message })
                      : w.code === 'control'
                        ? t('company.nameWarnControl', { defaultValue: w.message })
                        : t('company.nameWarnUnusual', { defaultValue: w.message })
                  setCompanyNameWarn(msg)
                }}
                placeholder={t('ph.companyExample', { ns: 'common' })}
              />
            </label>
            {companyNameWarn && (
              <p className="setup-tip" style={{ color: 'var(--warning, #9a6700)', marginTop: 6 }}>
                {companyNameWarn}
              </p>
            )}
            <label>
              {t('company.legalForm')}
              <select value={draft.legalForm} onChange={(e) => patch({ legalForm: e.target.value })}>
                <optgroup label={t('company.legalGroupDach', { defaultValue: 'DACH (DE · AT · CH)' })}>
                  <option value="SOLE">{t('company.sole')}</option>
                  <option value="EK">{t('company.ek')}</option>
                  <option value="GBR">{t('company.gbr')}</option>
                  <option value="UG">{t('company.ug')}</option>
                  <option value="GMBH">{t('company.gmbh')}</option>
                  <option value="GMBH_CO_KG">{t('company.gmbhCoKg')}</option>
                  <option value="AG">{t('company.ag')}</option>
                  <option value="KG">{t('company.kg')}</option>
                  <option value="OHG">{t('company.ohg')}</option>
                  <option value="OG">{t('company.og')}</option>
                  <option value="KLG">{t('company.klg')}</option>
                  <option value="EG">{t('company.eg')}</option>
                </optgroup>
                <optgroup label={t('company.legalGroupUs', { defaultValue: 'United States' })}>
                  <option value="SOLE_US">{t('company.soleUs')}</option>
                  <option value="LLC">{t('company.llc')}</option>
                  <option value="PLLC">{t('company.pllc')}</option>
                  <option value="CORP">{t('company.corp')}</option>
                  <option value="SCORP">{t('company.scorp')}</option>
                  <option value="LLP">{t('company.llp')}</option>
                  <option value="LP">{t('company.lp')}</option>
                  <option value="PARTNERSHIP">{t('company.partnership')}</option>
                  <option value="NONPROFIT">{t('company.nonprofit')}</option>
                </optgroup>
                <optgroup label={t('company.legalGroupOther', { defaultValue: 'Other' })}>
                  <option value="SA">{t('company.sa')}</option>
                  <option value="OTHER">{t('company.other')}</option>
                </optgroup>
              </select>
            </label>
            <label>
              {t('company.country')}
              <input
                value={draft.countryCode}
                onChange={(e) => patch({ countryCode: e.target.value })}
                maxLength={8}
              />
            </label>
            <label>
              {t('company.taxId')}
              <input value={draft.taxId} onChange={(e) => patch({ taxId: e.target.value })} />
            </label>
            <label>
              {t('company.taxRegime', { defaultValue: 'Tax regime' })}
              <select
                value={
                  draft.taxRegime ||
                  (String(draft.countryCode || '').toUpperCase() === 'AT'
                    ? 'AT_VAT'
                    : String(draft.countryCode || '').toUpperCase() === 'US'
                      ? 'US_SALES'
                      : String(draft.countryCode || '').toUpperCase() === 'DE'
                        ? 'DE_VAT'
                        : 'NONE')
                }
                onChange={(e) => patch({ taxRegime: e.target.value })}
              >
                <option value="DE_VAT">{t('company.taxRegimeDe', { defaultValue: 'Germany VAT' })}</option>
                <option value="AT_VAT">{t('company.taxRegimeAt', { defaultValue: 'Austria VAT' })}</option>
                <option value="US_SALES">{t('company.taxRegimeUs', { defaultValue: 'US sales tax (stub)' })}</option>
                <option value="DE_SMALL_BUSINESS">{t('company.taxRegimeKlein', { defaultValue: 'Kleinunternehmer / small business (no VAT)' })}</option>
                <option value="NONE">{t('company.taxRegimeNone', { defaultValue: 'None / not registered' })}</option>
              </select>
            </label>
            <p className="setup-tip muted">
              {t('company.taxRegimeHelp', {
                defaultValue: 'Seeded from country. Operator owns rates — not legal advice (OI-F23).',
              })}
            </p>
            <label>
              {t('company.primaryRail')}
              <select
                value={draft.primaryValueRail || 'EUR'}
                onChange={(e) => {
                  const rail = e.target.value
                  patch({
                    primaryValueRail: rail,
                    functionalCurrency: rail,
                    dualSats: false,
                  })
                }}
              >
                <option value="EUR">{t('company.railEur')}</option>
                <option value="USD">{t('company.railUsd')}</option>
                <option value="BTC">{t('company.railBtc')}</option>
              </select>
            </label>
            <p className="setup-tip">{t('company.primaryRailHelp')}</p>
            <p className="setup-tip muted">{t('company.moneyHintNote')}</p>
            {draft.primaryValueRail === 'BTC' && (
              <>
                <label>
                  {t('company.bitcoinUnit')}
                  <select
                    value={draft.bitcoinDisplayUnit || 'SATS'}
                    onChange={(e) => patch({ bitcoinDisplayUnit: e.target.value })}
                  >
                    <option value="SATS">{t('company.unitSats')}</option>
                    <option value="BTC">{t('company.unitBtc')}</option>
                  </select>
                </label>
                <label>
                  {t('company.displayFiat')}
                  <select
                    value={draft.displayFiat || 'EUR'}
                    onChange={(e) => patch({ displayFiat: e.target.value })}
                  >
                    <option value="EUR">EUR</option>
                    <option value="USD">USD</option>
                  </select>
                </label>
                <p className="setup-tip">{t('company.btcRailHelp')}</p>
              </>
            )}
            <label>
              {t('company.address')}
              <textarea
                value={draft.address}
                onChange={(e) => patch({ address: e.target.value })}
                rows={3}
              />
            </label>
          </section>
        )}

        {step === 2 && (
          <section className="setup-card">
            <h1>{t('site.title')}</h1>
            <p>{t('site.blurb')}</p>
            <label>
              {t('site.name')}
              <input value={draft.siteName} onChange={(e) => patch({ siteName: e.target.value })} />
            </label>
            <label>
              {t('site.type')}
              <select value={draft.siteType} onChange={(e) => patch({ siteType: e.target.value })}>
                <option value="HQ">{t('site.hq')}</option>
                <option value="WAREHOUSE">{t('site.warehouse')}</option>
                <option value="SALES">{t('site.sales')}</option>
                <option value="SERVICE">{t('site.service')}</option>
                <option value="OTHER">{t('site.other')}</option>
              </select>
            </label>
            <label className="setup-check" style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
              <input
                type="checkbox"
                checked={!!draft.siteAddressSameAsCompany}
                onChange={(e) => {
                  const on = e.target.checked
                  if (on) {
                    patch({
                      siteAddressSameAsCompany: true,
                      siteAddress: draft.address || '',
                    })
                  } else {
                    patch({ siteAddressSameAsCompany: false })
                  }
                }}
              />
              <span>
                {t('site.sameAsCompany', {
                  defaultValue: 'Use the same address as the company',
                })}
              </span>
            </label>
            <label>
              {t('site.address')}
              <textarea
                value={draft.siteAddress}
                onChange={(e) =>
                  patch({ siteAddressSameAsCompany: false, siteAddress: e.target.value })
                }
                rows={2}
                disabled={!!draft.siteAddressSameAsCompany}
                placeholder={
                  draft.siteAddressSameAsCompany
                    ? t('site.sameAsCompanyHint', {
                        defaultValue: 'Copied from company registered address',
                      })
                    : undefined
                }
              />
            </label>
            {draft.siteAddressSameAsCompany && !draft.address?.trim() && (
              <p className="muted" style={{ fontSize: '0.85rem' }}>
                {t('site.sameAsCompanyEmpty', {
                  defaultValue:
                    'Company address is empty — go back to the company step to fill it, or uncheck and enter a site address here.',
                })}
              </p>
            )}
          </section>
        )}

        {step === 3 && (
          <section className="setup-card">
            <h1>{t('banking.title')}</h1>
            <p>{t('banking.blurb')}</p>
            {(() => {
              const cc = (draft.countryCode || '').toUpperCase()
              const scheme =
                draft.paymentScheme ||
                (cc === 'US' || cc === 'USA' ? 'ACH' : 'SEPA')
              return (
                <>
                  <label>
                    {t('banking.scheme', { defaultValue: 'Payment scheme' })}
                    <select
                      value={scheme}
                      onChange={(e) => patch({ paymentScheme: e.target.value })}
                    >
                      <option value="SEPA">
                        {t('banking.schemeSepa', {
                          defaultValue: 'SEPA (Europe — IBAN / BIC)',
                        })}
                      </option>
                      <option value="ACH">
                        {t('banking.schemeAch', {
                          defaultValue: 'ACH (United States — routing / account)',
                        })}
                      </option>
                    </select>
                  </label>
                  <label>
                    {t('banking.label')}
                    <input
                      value={draft.bankName}
                      onChange={(e) => patch({ bankName: e.target.value })}
                      placeholder={t('ph.primaryBank', { ns: 'common' })}
                    />
                  </label>
                  {scheme === 'ACH' ? (
                    <>
                      <label>
                        {t('banking.branch', { defaultValue: 'Branch' })}
                        <input
                          value={draft.branchName}
                          onChange={(e) => patch({ branchName: e.target.value })}
                          placeholder={t('banking.branchPh', {
                            defaultValue: 'Branch name or number (optional)',
                          })}
                        />
                      </label>
                      <label>
                        {t('banking.accountNumber', { defaultValue: 'Account number' })}
                        <input
                          value={draft.accountNumber}
                          onChange={(e) => patch({ accountNumber: e.target.value })}
                          autoComplete="off"
                        />
                      </label>
                      <label>
                        {t('banking.routingNumber', { defaultValue: 'Routing number' })}
                        <input
                          value={draft.routingNumber}
                          onChange={(e) => patch({ routingNumber: e.target.value })}
                          inputMode="numeric"
                          autoComplete="off"
                          placeholder="9 digits"
                        />
                      </label>
                    </>
                  ) : (
                    <>
                      <label>
                        {t('banking.iban')}
                        <input
                          value={draft.iban}
                          onChange={(e) => patch({ iban: e.target.value })}
                        />
                      </label>
                      <label>
                        {t('banking.bic')}
                        <input
                          value={draft.bic}
                          onChange={(e) => patch({ bic: e.target.value })}
                        />
                      </label>
                    </>
                  )}
                </>
              )
            })()}
          </section>
        )}

        {step === 4 && (
          <section className="setup-card">
            <h1>{t('bitcoin.title', { defaultValue: 'Bitcoin receive' })}</h1>
            <p>
              {t('bitcoin.blurb', {
                defaultValue:
                  'How this company receives Bitcoin in steakOS. Separate from primary value rail (books currency). steakOS does not custody keys.',
              })}
            </p>
            <fieldset className="setup-tier-grid" style={{ border: 'none', padding: 0, margin: 0 }}>
              {[
                {
                  code: 'DEFERRED',
                  title: t('bitcoin.level1Title', { defaultValue: 'Level 1 — Not now' }),
                  body: t('bitcoin.level1Body', {
                    defaultValue:
                      'No live Bitcoin receive yet. Mock settle only. You can still book on a BTC primary rail. Basic literacy nudge.',
                  }),
                },
                {
                  code: 'SINGLE_ADDRESS',
                  title: t('bitcoin.level2Title', { defaultValue: 'Level 2 — Single address' }),
                  body: t('bitcoin.level2Body', {
                    defaultValue:
                      'Occasional on-chain payments to one software-wallet address. Manual mark paid. Not Lightning / merchant-grade.',
                  }),
                },
                {
                  code: 'BTCPAY',
                  title: t('bitcoin.level3Title', { defaultValue: 'Level 3 — BTCPay Server' }),
                  body: t('bitcoin.level3Body', {
                    defaultValue:
                      'Operator-run BTCPay (Lightning + on-chain). Enter connection fields here; probe and enable live under Finance → Payments.',
                  }),
                },
              ].map((opt) => (
                <button
                  key={opt.code}
                  type="button"
                  className={`setup-tier-card${draft.btcIntegrationLevel === opt.code ? ' selected' : ''}`}
                  onClick={() => {
                    patch({ btcIntegrationLevel: opt.code })
                    if (opt.code !== 'SINGLE_ADDRESS') setBtcAddressError(null)
                  }}
                >
                  <strong>{opt.title}</strong>
                  <span>{opt.body}</span>
                </button>
              ))}
            </fieldset>

            {draft.btcIntegrationLevel === 'SINGLE_ADDRESS' && (
              <div style={{ marginTop: '1rem' }}>
                <p className="setup-tip" style={{ color: 'var(--warning, #9a6700)' }}>
                  {t('bitcoin.level2Warn', {
                    defaultValue:
                      'Security notice: single-address receive is for occasional payments only. Never paste a seed or xprv. Plan to upgrade to BTCPay for regular Lightning / on-chain.',
                  })}
                </p>
                <label>
                  {t('bitcoin.receiveAddress', { defaultValue: 'Bitcoin receive address *' })}
                  <input
                    value={draft.btcReceiveAddress}
                    onChange={(e) => {
                      const value = e.target.value
                      patch({ btcReceiveAddress: value })
                      // Validate as soon as the operator enters an address (step 5 / bitcoin).
                      const trimmed = value.trim()
                      if (!trimmed) {
                        setBtcAddressError(null)
                        return
                      }
                      const v = validateBtcReceiveAddress(value)
                      if (v.ok) {
                        setBtcAddressError(null)
                        setError(null)
                      } else {
                        const msg =
                          v.code === 'seed'
                            ? t('bitcoin.addressSeedRejected', { defaultValue: v.message })
                            : t('bitcoin.addressInvalid', { defaultValue: v.message })
                        setBtcAddressError(msg)
                      }
                    }}
                    onBlur={() => {
                      if ((draft.btcIntegrationLevel || 'DEFERRED') !== 'SINGLE_ADDRESS') return
                      const v = validateBtcReceiveAddress(draft.btcReceiveAddress)
                      if (!v.ok) {
                        const msg =
                          v.code === 'empty'
                            ? t('bitcoin.addressRequired', { defaultValue: v.message })
                            : v.code === 'seed'
                              ? t('bitcoin.addressSeedRejected', { defaultValue: v.message })
                              : t('bitcoin.addressInvalid', { defaultValue: v.message })
                        setBtcAddressError(msg)
                      } else {
                        setBtcAddressError(null)
                      }
                    }}
                    placeholder="bc1…"
                    autoComplete="off"
                    spellCheck={false}
                    aria-invalid={btcAddressError ? 'true' : undefined}
                  />
                </label>
                {btcAddressError && (
                  <p className="setup-tip" style={{ color: 'var(--danger, #b42318)', marginTop: 6 }}>
                    {btcAddressError}
                  </p>
                )}
                <label>
                  {t('bitcoin.receiveLabel', { defaultValue: 'Address label (optional)' })}
                  <input
                    value={draft.btcReceiveAddressLabel}
                    onChange={(e) => patch({ btcReceiveAddressLabel: e.target.value })}
                    placeholder={t('bitcoin.receiveLabelPh', {
                      defaultValue: 'e.g. steakOS receive',
                    })}
                  />
                </label>
              </div>
            )}

            {draft.btcIntegrationLevel === 'BTCPAY' && (
              <div className="form-grid" style={{ marginTop: '1rem' }}>
                <label>
                  {t('bitcoin.btcpayBaseUrl', { defaultValue: 'BTCPay base URL *' })}
                  <input
                    value={draft.btcpayBaseUrl}
                    onChange={(e) => patch({ btcpayBaseUrl: e.target.value })}
                    placeholder="https://pay.example.com"
                    autoComplete="off"
                  />
                </label>
                <label>
                  {t('bitcoin.btcpayStoreId', { defaultValue: 'Store ID *' })}
                  <input
                    value={draft.btcpayStoreId}
                    onChange={(e) => patch({ btcpayStoreId: e.target.value })}
                    autoComplete="off"
                  />
                </label>
                <label>
                  {t('bitcoin.btcpayDefaultCurrency', { defaultValue: 'Default currency' })}
                  <input
                    value={draft.btcpayDefaultCurrency || 'EUR'}
                    onChange={(e) => patch({ btcpayDefaultCurrency: e.target.value })}
                  />
                </label>
                <label>
                  {t('bitcoin.btcpayApiKey', { defaultValue: 'API key *' })}
                  <input
                    type="password"
                    value={draft.btcpayApiKey}
                    onChange={(e) => patch({ btcpayApiKey: e.target.value })}
                    autoComplete="new-password"
                  />
                </label>
                <label>
                  {t('bitcoin.btcpayWebhookSecret', { defaultValue: 'Webhook secret' })}
                  <input
                    type="password"
                    value={draft.btcpayWebhookSecret}
                    onChange={(e) => patch({ btcpayWebhookSecret: e.target.value })}
                    autoComplete="new-password"
                  />
                </label>
                <p className="muted" style={{ fontSize: '0.85rem' }}>
                  {t('bitcoin.level3Hint', {
                    defaultValue:
                      'Secrets are stored encrypted. Enable live after Probe under Finance → Payments.',
                  })}
                </p>
              </div>
            )}

            {draft.primaryValueRail === 'BTC' && draft.btcIntegrationLevel === 'DEFERRED' && (
              <p className="muted" style={{ fontSize: '0.85rem', marginTop: '0.75rem' }}>
                {t('bitcoin.railVsReceive', {
                  defaultValue:
                    'Your books use Bitcoin as primary rail; receive stays deferred (mock settle) until you upgrade the integration level.',
                })}
              </p>
            )}
          </section>
        )}

        {STEP_IDS[step] === 'nostr' && (
          <section className="setup-card">
            <h1>{t('nostr.title', { defaultValue: 'Nostr' })}</h1>
            <p>
              {t('nostr.blurb', {
                defaultValue:
                  'How steakOS uses Nostr for this company. Admin configures here. Never paste a secret key (nsec). Link a browser extension under Security anytime.',
              })}
            </p>
            <NostrIdentityPanel
              draft={draft}
              patch={patch}
              t={t}
              onDetectLinked={async () => {
                try {
                  const hint = await nostrApi.identity()
                  if (hint?.linkedNpub) {
                    patch({ nostrOperatorNpub: hint.linkedNpub })
                  }
                  return hint
                } catch {
                  return null
                }
              }}
            />
            <div className="setup-tier-grid" style={{ marginTop: 16 }}>
              {[
                {
                  code: 'DEFERRED',
                  title: t('nostr.level0Title', { defaultValue: 'Level 0 — Not now' }),
                  body: t('nostr.level0Body', {
                    defaultValue:
                      'Skip publish. NIP-07 login stays optional under Security. Local relay may still be installed idle.',
                  }),
                },
                {
                  code: 'LOGIN_AUDIT',
                  title: t('nostr.level1Title', { defaultValue: 'Level 1 — Login + private audit' }),
                  body: t('nostr.level1Body', {
                    defaultValue:
                      'Publish selected audit events to the private local relay (LIVE only). DEMO stays mock-safe.',
                  }),
                },
                {
                  code: 'LOGIN_AUDIT_NOTIFY',
                  title: t('nostr.level2Title', {
                    defaultValue: 'Level 2 — Login + audit + notify',
                  }),
                  body: t('nostr.level2Body', {
                    defaultValue:
                      'Adds in-app Nostr notifications later (giftwrap). Partner DMs are out of wizard v1.',
                  }),
                },
              ].map((opt) => (
                <button
                  key={opt.code}
                  type="button"
                  className={`setup-tier-card${draft.nostrIntegrationLevel === opt.code ? ' selected' : ''}`}
                  onClick={() => patch({ nostrIntegrationLevel: opt.code })}
                >
                  <strong>{opt.title}</strong>
                  <span>{opt.body}</span>
                </button>
              ))}
            </div>
            <div
              className="setup-tip"
              style={{
                marginTop: 16,
                padding: '10px 12px',
                borderRadius: 8,
                border: '1px solid var(--border, #d4c8b8)',
                background: nostrRelay?.reachable ? 'rgba(47,107,79,0.08)' : 'rgba(196,92,38,0.06)',
              }}
            >
              <strong>
                {t('nostr.relayStatusTitle', { defaultValue: 'Local Nostr relay' })}
              </strong>
              <div className="muted" style={{ fontSize: '0.85rem', marginTop: 4 }}>
                {nostrRelayBusy
                  ? t('nostr.relayEnsuring', {
                      defaultValue: 'Installing/starting local relay…',
                    })
                  : nostrRelay?.reachable
                    ? t('nostr.relayUp', {
                        defaultValue: 'Reachable at {{url}}',
                        url: nostrRelay.relayUrl || draft.nostrRelayUrl,
                      })
                    : t('nostr.relayDown', {
                        defaultValue:
                          'Not running yet. Choosing Level 1 or 2 will auto-install and start the project-local relay (loopback). Level 0 skips this.',
                      })}
              </div>
              {nostrRelay && (
                <div className="muted" style={{ fontSize: '0.8rem', marginTop: 4 }}>
                  {t('nostr.relayBinary', {
                    defaultValue: 'Binary: {{state}}',
                    state: nostrRelay.binaryPresent
                      ? t('nostr.binaryPresent', { defaultValue: 'present' })
                      : t('nostr.binaryMissing', { defaultValue: 'missing (will install)' }),
                  })}
                  {nostrRelay.detail ? ` — ${nostrRelay.detail}` : ''}
                </div>
              )}
              {nostrRelayMsg && (
                <div style={{ fontSize: '0.85rem', marginTop: 6 }}>{nostrRelayMsg}</div>
              )}
              <div style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <button
                  type="button"
                  className="btn secondary"
                  disabled={busy || nostrRelayBusy}
                  onClick={async () => {
                    setNostrRelayBusy(true)
                    setNostrRelayMsg(null)
                    try {
                      const st = await nostrApi.relayStatus()
                      setNostrRelay(st)
                    } catch (ex) {
                      setNostrRelayMsg(ex.message || String(ex))
                    } finally {
                      setNostrRelayBusy(false)
                    }
                  }}
                >
                  {t('nostr.relayRefresh', { defaultValue: 'Refresh status' })}
                </button>
                <button
                  type="button"
                  className="btn secondary"
                  disabled={busy || nostrRelayBusy}
                  onClick={async () => {
                    setNostrRelayBusy(true)
                    setNostrRelayMsg(
                      t('nostr.relayEnsuring', {
                        defaultValue: 'Installing/starting local Nostr relay…',
                      }),
                    )
                    try {
                      const ensured = await nostrApi.relayEnsure()
                      setNostrRelay(ensured)
                      setNostrRelayMsg(
                        ensured?.reachable || ensured?.ok
                          ? t('nostr.relayReady', {
                              defaultValue: 'Local Nostr relay is reachable on loopback.',
                            })
                          : ensured?.error ||
                              t('nostr.relayEnsureWarn', {
                                defaultValue: 'Relay not reachable yet — see Integrations later.',
                              }),
                      )
                    } catch (ex) {
                      setNostrRelayMsg(ex.message || String(ex))
                    } finally {
                      setNostrRelayBusy(false)
                    }
                  }}
                >
                  {t('nostr.relayInstallNow', { defaultValue: 'Install / start now' })}
                </button>
              </div>
            </div>
            <label style={{ display: 'block', marginTop: 16 }}>
              {t('nostr.relayUrl', { defaultValue: 'Local relay URL' })}
              <input
                value={draft.nostrRelayUrl}
                onChange={(e) => patch({ nostrRelayUrl: e.target.value })}
                placeholder="ws://127.0.0.1:7777"
                autoComplete="off"
                spellCheck={false}
              />
            </label>
            <p className="muted" style={{ fontSize: '0.85rem' }}>
              {t('nostr.relayHint', {
                defaultValue:
                  'Default ws://127.0.0.1:7777 (loopback). Level 1+ auto-installs the native relay under var/nostr/. Editable later under System → Integrations.',
              })}
            </p>
            <label style={{ display: 'block', marginTop: 8 }}>
              {t('nostr.operatorNpub', {
                defaultValue: 'Public login id (optional paste)',
              })}
              <input
                value={draft.nostrOperatorNpub}
                onChange={(e) => patch({ nostrOperatorNpub: e.target.value })}
                placeholder="npub1… (never nsec)"
                autoComplete="off"
                spellCheck={false}
              />
            </label>
            <p className="setup-tip">
              {t('nostr.extensionGuide', {
                defaultValue:
                  'No extension yet? Install a NIP-07 browser extension (e.g. Alby), then link under Security after setup. Skip is fine (Level 0).',
              })}
            </p>
          </section>
        )}

        {STEP_IDS[step] === 'template' && (
          <section className="setup-card">
            <h1>{t('template.title')}</h1>
            <p>{t('template.blurb')}</p>
            <p className="muted" style={{ fontSize: '0.9rem' }}>
              {t('template.ladderNote', {
                defaultValue:
                  'Bronze is open for selection. Silver and Gold appear for orientation until those packs ship.',
              })}
            </p>
            <div className="setup-tier-grid">
              {(tiers.length
                ? tiers.filter((tier) => {
                    const c = (tier.code || '').toUpperCase()
                    return c === 'BRONZE' || c === 'SILVER' || c === 'GOLD'
                  })
                : [
                    {
                      code: 'BRONZE',
                      label: 'Bronze',
                      blurb: t('template.bronzeBlurb', {
                        defaultValue: 'Team pack — recommended start',
                      }),
                      openForMetalSelection: true,
                    },
                    {
                      code: 'SILVER',
                      label: 'Silver',
                      blurb: t('template.silverBlurb', {
                        defaultValue:
                          'Growing company — Advanced sales/finance, Governance, Stakeholder, optional email/Telegram (off by default).',
                      }),
                      openForMetalSelection: true,
                    },
                    {
                      code: 'GOLD',
                      label: 'Gold',
                      blurb: t('template.goldBlurb', {
                        defaultValue: 'Full enterprise surface — coming soon',
                      }),
                      openForMetalSelection: false,
                    },
                  ]
              ).map((tier) => {
                // Prefer openForMetalSelection; accept legacy choosable from older API builds
                const openForMetalSelection =
                  tier.openForMetalSelection === true ||
                  (tier.openForMetalSelection == null && tier.choosable === true)
                // Gold stays locked; preview flag still unlocks Silver card for UI rehearsal if API lags
                const code = String(tier.code || '').toUpperCase()
                const previewUnlock = previewSilverWizard() && code === 'SILVER'
                const locked = !openForMetalSelection && !previewUnlock
                const blurb =
                  code === 'SILVER'
                    ? t('template.silverBlurb', {
                        defaultValue:
                          tier.blurb ||
                          'Growing company — Advanced sales/finance, Governance, Stakeholder, optional email/Telegram (off by default).',
                      })
                    : tier.blurb
                return (
                  <button
                    key={tier.code}
                    type="button"
                    disabled={locked}
                    title={
                      locked
                        ? t('template.notAvailable', {
                            defaultValue: 'Not productized yet — not open for metal selection',
                          })
                        : undefined
                    }
                    className={`setup-tier ${draft.templateTier === tier.code ? 'is-selected' : ''} ${locked ? 'is-locked' : ''}`}
                    onClick={() => {
                      if (locked) return
                      patch({ templateTier: tier.code, enabledFamilies: null })
                    }}
                  >
                    <strong>
                      {tier.label}
                      {locked ? (
                        <span className="badge badge-muted" style={{ marginLeft: 8, fontWeight: 600 }}>
                          {t('template.comingSoon', { defaultValue: 'Coming soon' })}
                        </span>
                      ) : previewUnlock && !openForMetalSelection ? (
                        <span className="badge badge-muted" style={{ marginLeft: 8, fontWeight: 600 }}>
                          {t('template.previewOnly', { defaultValue: 'Preview only' })}
                        </span>
                      ) : (
                        <span className="badge badge-ok" style={{ marginLeft: 8, fontWeight: 600 }}>
                          {t('template.available', { defaultValue: 'Open for selection' })}
                        </span>
                      )}
                    </strong>
                    <span>{blurb}</span>
                    {locked && code === 'SILVER' && (
                      <span className="setup-tier-honesty">
                        {t('template.silverLockedHint', {
                          defaultValue:
                            'Not open yet. When productized: Sales/Finance Advanced, Governance + Stakeholder desks, optional email/Telegram (off by default).',
                        })}
                      </span>
                    )}
                  </button>
                )
              })}
            </div>
          </section>
        )}

        {STEP_IDS[step] === 'families' && (
          <section className="setup-card">
            <h1>{t('families.title')}</h1>
            <p>
              {isSilverPack
                ? t('families.blurbSilver', {
                    tier: draft.templateTier,
                    defaultValue:
                      'Fixed pack for {{tier}}: Bronze desks plus Governance and Stakeholder. Depth and messaging options below.',
                  })
                : t('families.blurb', { tier: draft.templateTier })}
            </p>
            <ul className="setup-family-list">
              {familiesForTier.map((f) => {
                const fixed = isSilverPack && SILVER_FIXED_FAMILIES.includes(f)
                return (
                  <li key={f}>
                    {f}
                    {fixed ? (
                      <span className="badge badge-muted" style={{ marginLeft: 8, fontWeight: 600 }}>
                        {t('silverOptions.packFixed', { defaultValue: 'Pack — required' })}
                      </span>
                    ) : null}
                  </li>
                )
              })}
            </ul>
            {isSilverPack && (
              <p className="setup-tip">
                {t('silverOptions.fixedPackNote', {
                  defaultValue:
                    'Governance and Stakeholder stay on for Silver — they are not individually switched off here.',
                })}
              </p>
            )}
            {showSilverOptions && (
              <div className="setup-silver-options">
                <h2 className="section-label">
                  {t('silverOptions.title', { defaultValue: 'Silver pack options' })}
                </h2>
                {!isSilverPack && (
                  <p className="setup-tip">
                    {t('silverOptions.previewNote', {
                      defaultValue:
                        'Preview only — Silver is not open for metal selection yet. Finish still applies Bronze unless Silver is open.',
                    })}
                  </p>
                )}
                <p className="muted" style={{ fontSize: '0.9rem' }}>
                  {t('silverOptions.depthBlurb', {
                    defaultValue:
                      'Sales and Finance run at Advanced depth on Silver (pack-mandatory). Not toggled back to Basics here.',
                  })}
                </p>
                <div className="setup-depth-badges">
                  <span className="setup-depth-badge">
                    <strong>{t('silverOptions.sales', { defaultValue: 'Sales' })}</strong>
                    <span className="badge badge-ok">{pluginDepth.sales || 'ADVANCED'}</span>
                  </span>
                  <span className="setup-depth-badge">
                    <strong>{t('silverOptions.finance', { defaultValue: 'Finance' })}</strong>
                    <span className="badge badge-ok">{pluginDepth.finance || 'ADVANCED'}</span>
                  </span>
                </div>
                <p className="muted" style={{ fontSize: '0.9rem', marginTop: '0.85rem' }}>
                  {t('silverOptions.messagingBlurb', {
                    defaultValue:
                      'Optional outbound messaging. Leave off until you add credentials under System → Integrations.',
                  })}
                </p>
                <label className="setup-check">
                  <input
                    type="checkbox"
                    checked={!!messaging.emailEnabled}
                    onChange={(e) => patchMessaging('emailEnabled', e.target.checked)}
                  />
                  {t('silverOptions.emailEnabled', {
                    defaultValue: 'Enable outbound email (configure later)',
                  })}
                </label>
                <label className="setup-check">
                  <input
                    type="checkbox"
                    checked={!!messaging.telegramEnabled}
                    onChange={(e) => patchMessaging('telegramEnabled', e.target.checked)}
                  />
                  {t('silverOptions.telegramEnabled', {
                    defaultValue: 'Enable Telegram notifies (configure later)',
                  })}
                </label>
              </div>
            )}
            <label className="setup-check">
              <input
                type="checkbox"
                checked={!!draft.seedCoa}
                onChange={(e) => patch({ seedCoa: e.target.checked })}
              />
              {t('families.seedCoa')}
            </label>
            <label className="setup-check">
              <input
                type="checkbox"
                checked={draft.seedDemo !== false}
                onChange={(e) => patch({ seedDemo: e.target.checked })}
              />
              {t('families.seedDemo', {
                defaultValue:
                  'Create Orange Steak House demo company (showcase restaurant — separate from your live company)',
              })}
            </label>
            {needsHrPrivacyAck && (
              <div className="setup-privacy-box" style={{ marginTop: '1.25rem', padding: '0.85rem', border: '1px solid var(--border, #ccc)', borderRadius: 8 }}>
                <h2 style={{ fontSize: '1rem', marginTop: 0 }}>
                  {t('privacy.title', { defaultValue: 'People data & privacy' })}
                </h2>
                <p className="muted" style={{ fontSize: '0.9rem' }}>
                  {t('privacy.body', {
                    defaultValue:
                      'This pack includes people (HR) data. You (the operator) are the controller for personal data on this instance. steakOS does not hold your keys and does not replace your legal duties.',
                  })}
                </p>
                <ul className="muted" style={{ fontSize: '0.85rem', marginBottom: '0.75rem' }}>
                  <li>
                    {t('privacy.point1', {
                      defaultValue: 'Limit admin accounts; do not share the default admin password.',
                    })}
                  </li>
                  <li>
                    {t('privacy.point2', {
                      defaultValue: 'Only store personal data you need for employment and operations.',
                    })}
                  </li>
                  <li>
                    {t('privacy.point3', {
                      defaultValue:
                        'See the operator privacy guide in the docs for export, access, and backups.',
                    })}
                  </li>
                </ul>
                <p className="muted" style={{ fontSize: '0.85rem' }}>
                  <a href="/help/gdpr-configure.html" target="_blank" rel="noreferrer">
                    {t('privacy.guideLink', {
                      defaultValue: 'Configure steakOS for privacy (guide)',
                    })}
                  </a>
                  {' · '}
                  <a href="/help/operator-trust-runbook.html" target="_blank" rel="noreferrer">
                    {t('privacy.runbookLink', { defaultValue: 'Operator trust runbook' })}
                  </a>
                </p>
                <label className="setup-check" style={{ marginTop: '0.5rem' }}>
                  <input
                    type="checkbox"
                    checked={hrPrivacyChecked}
                    onChange={(e) => setHrPrivacyChecked(e.target.checked)}
                  />
                  {t('privacy.ackLabel', {
                    defaultValue:
                      'I understand I am the controller for people data on this instance and will configure access and retention responsibly.',
                  })}
                </label>
                {hrPrivacyAlreadyAcked && (
                  <p className="setup-tip" style={{ marginBottom: 0 }}>
                    {t('privacy.alreadyAcked', {
                      defaultValue:
                        'A previous acknowledgement exists on this tenant; please confirm again for this setup.',
                    })}
                  </p>
                )}
              </div>
            )}
          </section>
        )}

        {STEP_IDS[step] === 'accounts' && (
          <section className="setup-card">
            <h1>{t('steps.accounts', { defaultValue: 'Accounts & authenticator' })}</h1>
            <p>
              Create distinct <strong>Admin</strong> and <strong>CEO</strong> logins and enable an
              authenticator app for <strong>both</strong>. You need both before finishing setup.
              After setup, every login asks for a code from that app.
            </p>
            <AuthAppGuide compact />
            {accountsMsg && <p className="setup-tip">{accountsMsg}</p>}

            <h2 className="section-label">Admin</h2>
            <label className="setup-field">
              New Admin password
              <input
                type="password"
                autoComplete="new-password"
                value={draft.adminPassword}
                onChange={(e) => patch({ adminPassword: e.target.value })}
                placeholder={RULES_HINT}
              />
            </label>
            <p className="muted" style={{ fontSize: '0.9rem' }}>
              Status: {adminTotpOk ? 'Authenticator enabled' : 'Authenticator not enabled yet'}
              {meUser?.username ? ` · signed in as ${meUser.username}` : ''}
            </p>
            <p className="setup-tip" style={{ fontSize: '0.85rem' }}>
              Passwords must be {RULES_HINT}. Leave blank only if you already use the strong default
              from install docs. Click the button to show a QR code.
            </p>
            {!adminTotpOk && !adminEnrol && (
              <button
                type="button"
                className="btn btn-primary"
                disabled={busy}
                onClick={() => {
                  beginAdminTotp().catch((e) => setError(e.message || String(e)))
                }}
              >
                {busy ? 'Working…' : 'Set up Admin authenticator'}
              </button>
            )}
            {adminEnrol && (
              <form onSubmit={confirmAdminTotp} className="form-grid" style={{ marginTop: 12 }}>
                <p>
                  <strong>Scan this QR</strong> (or type the key into your app):
                </p>
                {adminEnrol.qrDataUrl ? (
                  <img
                    src={adminEnrol.qrDataUrl}
                    alt="Admin authenticator QR"
                    width={180}
                    height={180}
                    style={{ background: '#fff', padding: 8 }}
                  />
                ) : (
                  <p className="setup-tip">QR image missing — use the manual key below.</p>
                )}
                <p className="mono" style={{ wordBreak: 'break-all', fontSize: 12 }}>
                  {adminEnrol.secret}
                </p>
                <label>
                  Code from app
                  <input
                    value={adminCode}
                    onChange={(e) => setAdminCode(e.target.value)}
                    required
                    autoComplete="one-time-code"
                    inputMode="numeric"
                  />
                </label>
                <button type="submit" className="btn btn-primary" disabled={busy}>
                  Confirm Admin
                </button>
              </form>
            )}
            {adminRecovery && (
              <div style={{ marginTop: 8 }}>
                <strong>Admin recovery codes</strong> (save offline):
                <ul className="mono">
                  {adminRecovery.map((c) => (
                    <li key={c}>{c}</li>
                  ))}
                </ul>
              </div>
            )}

            <h2 className="section-label" style={{ marginTop: '1.5rem' }}>
              CEO
            </h2>
            <label className="setup-field">
              CEO username
              <input
                value={draft.ceoUsername}
                onChange={(e) => patch({ ceoUsername: e.target.value })}
              />
            </label>
            <label className="setup-field">
              CEO password
              <input
                type="password"
                autoComplete="new-password"
                value={draft.ceoPassword}
                onChange={(e) => patch({ ceoPassword: e.target.value })}
                placeholder={RULES_HINT}
              />
            </label>
            <label className="setup-field">
              CEO display name
              <input
                value={draft.ceoDisplayName}
                onChange={(e) => patch({ ceoDisplayName: e.target.value })}
              />
            </label>
            <p className="muted" style={{ fontSize: '0.9rem' }}>
              Status: {ceoTotpOk ? 'Authenticator enabled' : 'Authenticator not enabled yet'}
            </p>
            {!ceoTotpOk && !ceoEnrol && (
              <button type="button" className="btn btn-primary" disabled={busy} onClick={beginCeoTotp}>
                Create CEO + set up authenticator
              </button>
            )}
            {ceoEnrol && (
              <form onSubmit={confirmCeoTotp} className="form-grid" style={{ marginTop: 12 }}>
                {ceoEnrol.qrDataUrl && (
                  <img src={ceoEnrol.qrDataUrl} alt="CEO authenticator QR" width={180} height={180} />
                )}
                <p className="mono" style={{ wordBreak: 'break-all', fontSize: 12 }}>
                  {ceoEnrol.secret}
                </p>
                <label>
                  Code from app
                  <input value={ceoCode} onChange={(e) => setCeoCode(e.target.value)} required />
                </label>
                <button type="submit" className="btn btn-primary" disabled={busy}>
                  Confirm CEO
                </button>
              </form>
            )}
            {ceoRecovery && (
              <div style={{ marginTop: 8 }}>
                <strong>CEO recovery codes</strong> (save offline):
                <ul className="mono">
                  {ceoRecovery.map((c) => (
                    <li key={c}>{c}</li>
                  ))}
                </ul>
              </div>
            )}
            <p className="setup-tip" style={{ marginTop: '1rem' }}>
              Tip: most people use password + authenticator. Optional later: link a browser extension
              under Security (never paste a secret key into steakOS).
            </p>
          </section>
        )}

        {STEP_IDS[step] === 'summary' && (
          <section className="setup-card">
            <h1>{t('review.title')}</h1>
            <dl className="setup-summary">
              <dt>{t('company.title')}</dt>
              <dd>{draft.companyName || '—'}</dd>
              <dt>{t('template.title')}</dt>
              <dd>{draft.templateTier}</dd>
              <dt>{t('site.title')}</dt>
              <dd>{draft.siteName}</dd>
              <dt>{t('company.primaryRail')}</dt>
              <dd>
                {draft.primaryValueRail || draft.functionalCurrency || '—'}
                {draft.primaryValueRail === 'BTC'
                  ? ` · ${draft.bitcoinDisplayUnit || 'SATS'} · tips ${draft.displayFiat || 'EUR'}`
                  : ''}
              </dd>
              <dt>{t('bitcoin.title', { defaultValue: 'Bitcoin receive' })}</dt>
              <dd>
                {draft.btcIntegrationLevel === 'BTCPAY'
                  ? 'Level 3 — BTCPay'
                  : draft.btcIntegrationLevel === 'SINGLE_ADDRESS'
                    ? 'Level 2 — Single address'
                    : 'Level 1 — Not now'}
              </dd>
              <dt>{t('nostr.title', { defaultValue: 'Nostr' })}</dt>
              <dd>
                {draft.nostrIntegrationLevel === 'LOGIN_AUDIT_NOTIFY'
                  ? 'Level 2 — Login + audit + notify'
                  : draft.nostrIntegrationLevel === 'LOGIN_AUDIT'
                    ? 'Level 1 — Login + private audit'
                    : 'Level 0 — Not now'}
                {draft.nostrRelayUrl ? ` · ${draft.nostrRelayUrl}` : ''}
              </dd>
              <dt>{t('families.title')}</dt>
              <dd>{familiesForTier.join(', ')}</dd>
              {showSilverOptions && (
                <>
                  <dt>{t('silverOptions.depthLabel', { defaultValue: 'Plugin depth' })}</dt>
                  <dd>
                    {t('silverOptions.sales', { defaultValue: 'Sales' })}:{' '}
                    {pluginDepth.sales || 'ADVANCED'} ·{' '}
                    {t('silverOptions.finance', { defaultValue: 'Finance' })}:{' '}
                    {pluginDepth.finance || 'ADVANCED'}
                  </dd>
                  <dt>{t('silverOptions.messagingLabel', { defaultValue: 'Messaging' })}</dt>
                  <dd>
                    {t('silverOptions.emailShort', { defaultValue: 'Email' })}:{' '}
                    {messaging.emailEnabled
                      ? t('yes', { ns: 'common' })
                      : t('no', { ns: 'common' })}{' '}
                    · {t('silverOptions.telegramShort', { defaultValue: 'Telegram' })}:{' '}
                    {messaging.telegramEnabled
                      ? t('yes', { ns: 'common' })
                      : t('no', { ns: 'common' })}
                  </dd>
                </>
              )}
              <dt>Accounts</dt>
              <dd>
                Admin 2FA {adminTotpOk ? 'on' : 'off'} · CEO ({draft.ceoUsername || 'ceo'}) 2FA{' '}
                {ceoTotpOk ? 'on' : 'off'}
              </dd>
              <dt>COA</dt>
              <dd>{draft.seedCoa ? t('yes', { ns: 'common' }) : t('no', { ns: 'common' })}</dd>
              {needsHrPrivacyAck && (
                <>
                  <dt>{t('privacy.summaryLabel', { defaultValue: 'HR privacy ack' })}</dt>
                  <dd>
                    {hrPrivacyChecked
                      ? t('yes', { ns: 'common' })
                      : t('no', { ns: 'common' })}
                  </dd>
                </>
              )}
            </dl>
            {needsHrPrivacyAck && !hrPrivacyChecked && (
              <p className="setup-tip" style={{ color: 'var(--danger, #b00020)' }}>
                {t('privacy.required', {
                  defaultValue:
                    'Acknowledge privacy duties on the previous step (families) before finishing.',
                })}
              </p>
            )}
            {check && !check.ok && (
              <p className="setup-tip">
                {t('review.lastCheck', { issues: check.issues?.map((i) => i.message).join('; ') })}
              </p>
            )}
            <p className="setup-tip">{t('review.tip')}</p>
            {(!adminTotpOk || !ceoTotpOk) && (
              <p className="setup-tip" style={{ color: 'var(--danger, #b00020)' }}>
                Admin or CEO authenticator is not marked complete in this browser session. Go back to
                Accounts if needed, then Finish again.
              </p>
            )}
          </section>
        )}
      </main>

      <footer className="setup-footer">
        <button type="button" className="btn btn-ghost" disabled={step === 0 || busy} onClick={goBack}>
          {t('nav.back')}
        </button>
        {step < STEP_IDS.length - 1 ? (
          <button
            type="button"
            className="btn btn-primary"
            disabled={
              busy ||
              (step === 1 && !draft.companyName?.trim()) ||
              (STEP_IDS[step] === 'accounts' && (!adminTotpOk || !ceoTotpOk)) ||
              (STEP_IDS[step] === 'bitcoin' &&
                draft.btcIntegrationLevel === 'SINGLE_ADDRESS' &&
                !draft.btcReceiveAddress?.trim()) ||
              (STEP_IDS[step] === 'bitcoin' &&
                draft.btcIntegrationLevel === 'BTCPAY' &&
                (!draft.btcpayBaseUrl?.trim() ||
                  !draft.btcpayStoreId?.trim() ||
                  !draft.btcpayApiKey?.trim())) ||
              (STEP_IDS[step] === 'nostr' &&
                (() => {
                  const v = (draft.nostrOperatorNpub || '').trim().toLowerCase()
                  return v.startsWith('nsec') || v.includes('nsec1')
                })())
            }
            onClick={goNext}
          >
            {t('nav.next')}
          </button>
        ) : (
          <button
            type="button"
            className="btn btn-primary"
            disabled={busy}
            onClick={() => {
              finish().catch((e) => setError(e.message || String(e)))
            }}
          >
            {busy ? t('actions.working', { ns: 'common' }) : t('nav.finish')}
          </button>
        )}
      </footer>
    </div>
  )
}

/** D7 identity detect — extension + linked npub hint (no nsec). */
function NostrIdentityPanel({ draft, patch, t, onDetectLinked }) {
  const [hint, setHint] = useState(null)
  const [ext, setExt] = useState(null)

  useEffect(() => {
    try {
      setExt(typeof window !== 'undefined' && window.nostr?.getPublicKey ? 'yes' : 'no')
    } catch {
      setExt('no')
    }
  }, [])

  return (
    <div className="card" style={{ marginTop: 8, padding: 12 }}>
      <p style={{ marginTop: 0, fontSize: 14 }}>
        {t('nostr.detectTitle', { defaultValue: 'Identity detect' })}
      </p>
      <ul style={{ fontSize: 14, marginBottom: 8 }}>
        <li>
          {t('nostr.extLabel', { defaultValue: 'Browser extension (NIP-07)' })}:{' '}
          <strong>
            {ext === 'yes'
              ? t('nostr.extYes', { defaultValue: 'detected' })
              : t('nostr.extNo', { defaultValue: 'not detected' })}
          </strong>
        </li>
        {hint?.linkedNpubMasked && (
          <li>
            {t('nostr.linkedLabel', { defaultValue: 'Already linked on account' })}:{' '}
            <span className="mono">{hint.linkedNpubMasked}</span>
          </li>
        )}
      </ul>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={async () => {
            const h = await onDetectLinked?.()
            setHint(h)
          }}
        >
          {t('nostr.refreshLinked', { defaultValue: 'Refresh linked id' })}
        </button>
        {ext === 'yes' && (
          <button
            type="button"
            className="btn btn-ghost"
            onClick={async () => {
              try {
                const pk = await window.nostr.getPublicKey()
                if (pk) patch({ nostrOperatorNpub: pk })
              } catch {
                /* user rejected */
              }
            }}
          >
            {t('nostr.useExtension', { defaultValue: 'Use extension public key' })}
          </button>
        )}
      </div>
      {draft.nostrOperatorNpub?.toLowerCase?.().startsWith('nsec') && (
        <p className="setup-tip" style={{ color: 'var(--danger, #b00020)' }}>
          {t('nostr.nsecRejected', {
            defaultValue: 'Never paste a secret key (nsec). Public npub only.',
          })}
        </p>
      )}
    </div>
  )
}
