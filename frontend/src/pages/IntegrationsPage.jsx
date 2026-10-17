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

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { nostrApi, financeOpsApi, meApi, agentTokenApi, setupApi } from '../api/client'

const L2_WARN_DISMISS_KEY = 'steakos.btcLevel2WarnDismissed'

/**
 * System → Integrations — Nostr, Bitcoin receive level, messaging stubs, Hermes agent tokens.
 */
export default function IntegrationsPage() {
  const { t } = useTranslation('common')
  const [error, setError] = useState(null)
  const [isAdmin, setIsAdmin] = useState(false)
  const [isCeo, setIsCeo] = useState(false)
  const [nostr, setNostr] = useState(null)
  const [btcLevel, setBtcLevel] = useState(null)
  const [messaging, setMessaging] = useState({ emailEnabled: false, telegramEnabled: false })
  const [probe, setProbe] = useState(null)
  const [busy, setBusy] = useState(false)
  const [btcBusy, setBtcBusy] = useState(false)
  const [msg, setMsg] = useState(null)
  const [btcMsg, setBtcMsg] = useState(null)
  const [form, setForm] = useState({
    level: 'DEFERRED',
    relayUrl: 'ws://127.0.0.1:7777',
    operatorNpub: '',
  })
  const [btcForm, setBtcForm] = useState({
    level: 'DEFERRED',
    receiveAddress: '',
    receiveAddressLabel: '',
    ceoTrainingAck: false,
    confirmDowngrade: false,
  })
  const [l2WarnDismissed, setL2WarnDismissed] = useState(() => {
    try {
      return sessionStorage.getItem(L2_WARN_DISMISS_KEY) === '1'
    } catch {
      return false
    }
  })
  const [agentTokens, setAgentTokens] = useState([])
  const [agentUsername, setAgentUsername] = useState('HiveA:bookkeeper')
  const [agentLabel, setAgentLabel] = useState('olympus primary')
  const [agentHost, setAgentHost] = useState('olympus')
  const [mintedToken, setMintedToken] = useState(null)
  const [agentMsg, setAgentMsg] = useState(null)

  const load = async () => {
    setError(null)
    try {
      const me = await meApi.get()
      setIsAdmin(!!me?.isAdmin)
      setIsCeo(!!me?.isCeo)
      const [n, b, st] = await Promise.all([
        nostrApi.settingsGet().catch(() => null),
        financeOpsApi.btcLevelGet().catch(() => null),
        setupApi.status().catch(() => null),
      ])
      setNostr(n)
      setBtcLevel(b)
      setMessaging({
        emailEnabled: !!st?.messaging?.emailEnabled,
        telegramEnabled: !!st?.messaging?.telegramEnabled,
      })
      if (n) {
        setForm({
          level: n.level || 'DEFERRED',
          relayUrl: n.relayUrl || 'ws://127.0.0.1:7777',
          operatorNpub: n.operatorNpub || '',
        })
      }
      if (b) {
        setBtcForm((f) => ({
          ...f,
          level: b.level || 'DEFERRED',
          receiveAddress: b.receiveAddress || '',
          receiveAddressLabel: b.receiveAddressLabel || '',
        }))
      }
      try {
        const tok = await agentTokenApi.list(false)
        setAgentTokens(tok?.tokens || [])
      } catch {
        setAgentTokens([])
      }
    } catch (e) {
      setError(e.message || String(e))
    }
  }

  useEffect(() => {
    load()
  }, [])

  const saveNostr = async () => {
    setBusy(true)
    setMsg(null)
    setError(null)
    try {
      const r = await nostrApi.settingsPut({
        level: form.level,
        relayUrl: form.relayUrl,
        operatorNpub: form.operatorNpub,
      })
      setNostr(r)
      setMsg(t('pages.integrations.nostrSaved', { defaultValue: 'Nostr settings saved.' }))
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const runProbe = async () => {
    setBusy(true)
    setError(null)
    try {
      const r = await nostrApi.probe()
      setProbe(r)
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const runRelayEnsure = async () => {
    setBusy(true)
    setError(null)
    setMsg(
      t('pages.integrations.relayEnsuring', {
        defaultValue: 'Installing/starting local Nostr relay…',
      }),
    )
    try {
      const r = await nostrApi.relayEnsure()
      setProbe(r)
      setMsg(
        r?.reachable || r?.ok
          ? t('pages.integrations.relayReady', {
              defaultValue: 'Local Nostr relay is reachable.',
            })
          : r?.error ||
              t('pages.integrations.relayEnsureWarn', {
                defaultValue: 'Relay ensure finished but not reachable — check var/nostr/relay.log',
              }),
      )
    } catch (e) {
      setError(e.message || String(e))
      setMsg(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">
        {t('nav.integrations', { defaultValue: 'Integrations' })}
      </h1>
      <p className="page-sub">
        {t('pages.integrations.subtitle', {
          defaultValue:
            'Operator integrations: Nostr relay, Bitcoin receive (BTCPay), and Hermes agent tokens.',
        })}
      </p>
      {error && <div className="setup-error">{error}</div>}
      {msg && <p className="muted">{msg}</p>}

      <div className="card" style={{ marginTop: 16 }}>
        <h2>{t('pages.integrations.nostrTitle', { defaultValue: 'Nostr' })}</h2>
        <p className="page-sub">
          {t('pages.integrations.nostrHint', {
            defaultValue:
              'Private local relay for login, audit, and (later) in-app notify. Never paste nsec here. Link identity under Security (NIP-07).',
          })}
        </p>
        {nostr && (
          <p>
            {t('pages.integrations.current', { defaultValue: 'Current' })}:{' '}
            <strong>{nostr.uiLabel || nostr.level}</strong>
            {nostr.demoNoPublicPublish ? ' · demo — no publish' : ''}
            {nostr.companyBotConfigured ? ' · company bot key sealed' : ' · no company bot key'}
          </p>
        )}
        {isAdmin ? (
          <div style={{ marginTop: 12 }}>
            <label>
              {t('pages.integrations.level', { defaultValue: 'Usage level' })}
              <select
                value={form.level}
                disabled={busy}
                onChange={(e) => setForm({ ...form, level: e.target.value })}
              >
                <option value="DEFERRED">0 — Not now</option>
                <option value="LOGIN_AUDIT">1 — Login + private audit</option>
                <option value="LOGIN_AUDIT_NOTIFY">2 — Login + audit + notify</option>
              </select>
            </label>
            <label style={{ display: 'block', marginTop: 8 }}>
              {t('pages.integrations.relayUrl', { defaultValue: 'Relay URL' })}
              <input
                value={form.relayUrl}
                disabled={busy}
                onChange={(e) => setForm({ ...form, relayUrl: e.target.value })}
                placeholder="ws://127.0.0.1:7777"
                autoComplete="off"
                spellCheck={false}
                style={{ display: 'block', width: '100%', marginTop: 4 }}
              />
            </label>
            <label style={{ display: 'block', marginTop: 8 }}>
              {t('pages.integrations.operatorNpub', {
                defaultValue: 'Operator public npub (optional paste)',
              })}
              <input
                value={form.operatorNpub}
                disabled={busy}
                onChange={(e) => setForm({ ...form, operatorNpub: e.target.value })}
                placeholder="npub1… (never nsec)"
                autoComplete="off"
                spellCheck={false}
                style={{ display: 'block', width: '100%', marginTop: 4 }}
              />
            </label>
            <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <button type="button" className="btn btn-primary" disabled={busy} onClick={saveNostr}>
                {busy ? '…' : t('pages.integrations.saveNostr', { defaultValue: 'Save Nostr' })}
              </button>
              <button type="button" className="btn" disabled={busy} onClick={runProbe}>
                {t('pages.integrations.probe', { defaultValue: 'Probe relay' })}
              </button>
              <button type="button" className="btn secondary" disabled={busy} onClick={runRelayEnsure}>
                {t('pages.integrations.relayEnsure', {
                  defaultValue: 'Install / start local relay',
                })}
              </button>
              <Link to="/security" className="btn btn-secondary">
                {t('pages.integrations.openSecurity', { defaultValue: 'Security → NIP-07 link' })}
              </Link>
            </div>
            {probe && (
              <p className="mono" style={{ marginTop: 8, fontSize: 13 }}>
                Probe: {probe.ok ? 'ok' : 'fail'} — {probe.detail}
              </p>
            )}
          </div>
        ) : (
          <p className="muted">
            {t('pages.integrations.adminOnly', {
              defaultValue: 'Admin can change Nostr level and relay URL. CEO can review here.',
            })}
          </p>
        )}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>
          {t('pages.settings.btcLevelTitle', {
            defaultValue: 'Bitcoin integration level',
          })}
        </h2>
        <p className="page-sub">
          {t('pages.settings.btcLevelHint', {
            defaultValue:
              'How this company receives Bitcoin. Separate from primary value rail. Lightning marketing only at Level 3 (BTCPay).',
          })}
        </p>
        {btcLevel && (
          <p>
            {t('pages.settings.btcLevelCurrent', { defaultValue: 'Current' })}:{' '}
            <strong>{btcLevel.uiLabel || btcLevel.level}</strong>
            {btcLevel.demoForcedMock ? ' · demo mock only' : ''}
          </p>
        )}
        {btcLevel?.level === 'SINGLE_ADDRESS' && !l2WarnDismissed && (
          <div
            className="banner-error"
            role="status"
            style={{
              marginTop: 8,
              background: '#fff7ed',
              color: '#9a3412',
              border: '1px solid #fed7aa',
              padding: '0.75rem',
              borderRadius: 8,
            }}
          >
            <p style={{ marginTop: 0 }}>
              <strong>
                {t('pages.settings.btcLevel2WarnTitle', {
                  defaultValue: 'Security notice (Level 2)',
                })}
              </strong>
            </p>
            <p style={{ fontSize: 14 }}>
              {t('pages.settings.btcLevel2WarnBody', {
                defaultValue:
                  'You are receiving Bitcoin to a single software-wallet address. This is meant for occasional payments only. Address reuse, device malware, and weak backups can cause permanent loss. steakOS does not hold your keys. Complete advanced Bitcoin security training, and plan to upgrade to BTCPay Server (Level 3) for regular Lightning / on-chain receive.',
              })}
            </p>
            {btcLevel.receiveAddressMasked && (
              <p className="mono" style={{ fontSize: 13 }}>
                {btcLevel.receiveAddressLabel ? `${btcLevel.receiveAddressLabel}: ` : ''}
                {btcLevel.receiveAddressMasked}
              </p>
            )}
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                try {
                  sessionStorage.setItem(L2_WARN_DISMISS_KEY, '1')
                } catch {
                  /* ignore */
                }
                setL2WarnDismissed(true)
              }}
            >
              {t('pages.settings.btcLevel2Dismiss', {
                defaultValue: 'Dismiss until next login',
              })}
            </button>
          </div>
        )}
        <p style={{ marginTop: 8, fontSize: 14 }}>
          {btcLevel?.trainingNudge === 'BASIC' && (
            <Link to="/help?topic=training.btc.basic">
              {t('pages.settings.btcTrainingBasic', {
                defaultValue: 'Start basic Bitcoin training',
              })}
            </Link>
          )}
          {btcLevel?.trainingNudge === 'ADVANCED' && (
            <Link to="/help?topic=training.btc.security-advanced">
              {t('pages.settings.btcTrainingAdvanced', {
                defaultValue: 'Advanced Bitcoin security training',
              })}
            </Link>
          )}
          {btcLevel?.level === 'BTCPAY' && (
            <Link to="/finance/payments">
              {t('pages.settings.btcOpenPayments', {
                defaultValue: 'Open Finance → Payments',
              })}
            </Link>
          )}
        </p>
        {btcMsg && <p className="muted">{btcMsg}</p>}
        {isAdmin && (
          <div style={{ marginTop: 12 }}>
            <label>
              {t('pages.settings.btcChangeLevel', { defaultValue: 'Change level (Admin)' })}
              <select
                value={btcForm.level}
                disabled={btcBusy}
                onChange={(e) => setBtcForm({ ...btcForm, level: e.target.value })}
              >
                <option value="DEFERRED">1 — Not now (DEFERRED)</option>
                <option value="SINGLE_ADDRESS">2 — Single address</option>
                <option value="BTCPAY">3 — BTCPay</option>
              </select>
            </label>
            {btcForm.level === 'SINGLE_ADDRESS' && (
              <>
                <label style={{ display: 'block', marginTop: 8 }}>
                  Receive address
                  <input
                    value={btcForm.receiveAddress}
                    disabled={btcBusy}
                    onChange={(e) =>
                      setBtcForm({ ...btcForm, receiveAddress: e.target.value })
                    }
                    placeholder="bc1…"
                    autoComplete="off"
                    spellCheck={false}
                    style={{ display: 'block', width: '100%', marginTop: 4 }}
                  />
                </label>
                <label style={{ display: 'block', marginTop: 8 }}>
                  Label (optional)
                  <input
                    value={btcForm.receiveAddressLabel}
                    disabled={btcBusy}
                    onChange={(e) =>
                      setBtcForm({ ...btcForm, receiveAddressLabel: e.target.value })
                    }
                    style={{ display: 'block', width: '100%', marginTop: 4 }}
                  />
                </label>
              </>
            )}
            {(btcForm.level === 'SINGLE_ADDRESS' || btcForm.level === 'BTCPAY') &&
              btcLevel?.level !== btcForm.level &&
              (btcLevel?.uiLevel || 1) < (btcForm.level === 'BTCPAY' ? 3 : 2) && (
                <label className="checkbox-label" style={{ display: 'block', marginTop: 8 }}>
                  <input
                    type="checkbox"
                    checked={btcForm.ceoTrainingAck}
                    disabled={btcBusy}
                    onChange={(e) =>
                      setBtcForm({ ...btcForm, ceoTrainingAck: e.target.checked })
                    }
                  />{' '}
                  {t('pages.settings.btcCeoAck', {
                    defaultValue:
                      'CEO acknowledges training for this upgrade (required)',
                  })}
                  {isCeo ? ' (you are CEO)' : ''}
                </label>
              )}
            {btcLevel?.level === 'BTCPAY' && btcForm.level !== 'BTCPAY' && (
              <label className="checkbox-label" style={{ display: 'block', marginTop: 8 }}>
                <input
                  type="checkbox"
                  checked={btcForm.confirmDowngrade}
                  disabled={btcBusy}
                  onChange={(e) =>
                    setBtcForm({ ...btcForm, confirmDowngrade: e.target.checked })
                  }
                />{' '}
                {t('pages.settings.btcConfirmDowngrade', {
                  defaultValue:
                    'Confirm downgrade from Level 3 (disables live BTCPay)',
                })}
              </label>
            )}
            <button
              type="button"
              className="btn btn-primary"
              style={{ marginTop: 12 }}
              disabled={btcBusy}
              onClick={async () => {
                setBtcBusy(true)
                setBtcMsg(null)
                setError(null)
                try {
                  const body = {
                    level: btcForm.level,
                    ceoTrainingAck: btcForm.ceoTrainingAck,
                    confirmDowngrade: btcForm.confirmDowngrade,
                  }
                  if (btcForm.level === 'SINGLE_ADDRESS') {
                    body.btcReceiveAddress = btcForm.receiveAddress
                    body.btcReceiveAddressLabel = btcForm.receiveAddressLabel
                  }
                  const r = await financeOpsApi.btcLevelPut(body)
                  setBtcLevel(r)
                  setBtcMsg(
                    t('pages.settings.btcLevelSaved', {
                      defaultValue: 'Bitcoin integration level saved.',
                    }),
                  )
                  setBtcForm((f) => ({
                    ...f,
                    ceoTrainingAck: false,
                    confirmDowngrade: false,
                    receiveAddress: r?.receiveAddress || f.receiveAddress,
                  }))
                } catch (e) {
                  setError(e.message || String(e))
                } finally {
                  setBtcBusy(false)
                }
              }}
            >
              {btcBusy
                ? '…'
                : t('pages.settings.saveBtcLevel', { defaultValue: 'Save Bitcoin level' })}
            </button>
          </div>
        )}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>
          {t('pages.integrations.messagingTitle', {
            defaultValue: 'Email & Telegram',
          })}
        </h2>
        <p className="page-sub">
          {t('pages.integrations.messagingHint', {
            defaultValue:
              'Outbound adapters. Enable toggles under Settings → Pack options (Silver). Secrets (SMTP / bot token) will live here — stubs for now.',
          })}
        </p>
        {!messaging.emailEnabled && !messaging.telegramEnabled ? (
          <p className="muted">
            {t('pages.integrations.messagingDisabled', {
              defaultValue:
                'Messaging is off for this company. Turn on email or Telegram under Settings → Pack options when on Silver.',
            })}
          </p>
        ) : (
          <ul className="inv-list" style={{ marginTop: 8 }}>
            {messaging.emailEnabled && (
              <li>
                {t('pages.integrations.messagingEmailOn', {
                  defaultValue:
                    'Outbound email enabled — configure SMTP credentials here soon.',
                })}
              </li>
            )}
            {messaging.telegramEnabled && (
              <li>
                {t('pages.integrations.messagingTelegramOn', {
                  defaultValue:
                    'Telegram notifies enabled — configure bot token here soon.',
                })}
              </li>
            )}
          </ul>
        )}
        {isAdmin && (
          <div style={{ marginTop: 12 }}>
            <Link to="/settings" className="btn btn-ghost">
              {t('pages.integrations.openSettingsPack', {
                defaultValue: 'Open Settings → Pack options',
              })}
            </Link>
          </div>
        )}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>{t('pages.settings.hermesTitle')}</h2>
        <p className="page-sub">{t('pages.settings.hermesBlurb')}</p>
        <div className="banner-info" role="status" style={{ marginBottom: 12 }}>
          <strong>
            {t('pages.settings.activationChecklistTitle', { defaultValue: 'Activate agent (API)' })}
          </strong>
          <p style={{ margin: '0.35rem 0 0' }}>
            {t('pages.settings.activationChecklistBlurb', {
              defaultValue:
                'Agent Basics: mint a token here, paste into your operator-owned agent host, prove a read, optionally propose for HITL. Agents do not settle money by default.',
            })}{' '}
            <Link to="/agents/agents">{t('features.agents', { defaultValue: 'Agents' })}</Link>
          </p>
        </div>
        <label>
          {t('pages.settings.agentUsername')}
          <input
            value={agentUsername}
            onChange={(e) => setAgentUsername(e.target.value)}
            placeholder="HiveA:bookkeeper"
            style={{ display: 'block', width: '100%', marginTop: 4 }}
          />
        </label>
        <label style={{ display: 'block', marginTop: 8 }}>
          {t('pages.settings.agentLabel')}
          <input
            value={agentLabel}
            onChange={(e) => setAgentLabel(e.target.value)}
            style={{ display: 'block', width: '100%', marginTop: 4 }}
          />
        </label>
        <label style={{ display: 'block', marginTop: 8 }}>
          {t('pages.settings.agentHost')}
          <input
            value={agentHost}
            onChange={(e) => setAgentHost(e.target.value)}
            placeholder="olympus"
            style={{ display: 'block', width: '100%', marginTop: 4 }}
          />
        </label>
        <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button
            type="button"
            className="btn btn-primary"
            disabled={busy || !agentUsername.trim()}
            onClick={async () => {
              setBusy(true)
              setError(null)
              setAgentMsg(null)
              setMintedToken(null)
              try {
                const r = await agentTokenApi.mint({
                  username: agentUsername.trim(),
                  label: agentLabel.trim() || undefined,
                  hostLabel: agentHost.trim() || undefined,
                })
                setMintedToken(r?.result?.token || null)
                setAgentMsg(r?.result?.warning || t('pages.settings.tokenMinted'))
                await load()
              } catch (e) {
                setError(e.message || String(e))
              } finally {
                setBusy(false)
              }
            }}
          >
            {busy ? t('actions.loading') : t('pages.settings.mintToken')}
          </button>
        </div>
        {mintedToken && (
          <div className="setup-error" style={{ marginTop: 12, background: '#fff7ed', color: '#9a3412', borderColor: '#fed7aa' }}>
            <strong>{t('pages.settings.tokenOnce')}</strong>
            <pre className="mono" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', margin: '8px 0 0' }}>
              {mintedToken}
            </pre>
          </div>
        )}
        {agentMsg && !mintedToken && <p className="muted" style={{ marginTop: 8 }}>{agentMsg}</p>}
        <h3 style={{ marginTop: 16, fontSize: '1rem' }}>{t('pages.settings.activeTokens')}</h3>
        {agentTokens.length === 0 ? (
          <p className="muted">{t('pages.settings.noTokens')}</p>
        ) : (
          <ul className="inv-list">
            {agentTokens.map((tok) => (
              <li key={tok.id} style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                <span className="mono">{tok.tokenPrefix}…</span>
                <span>{tok.username}</span>
                <span className="muted">{tok.label}</span>
                {tok.hostLabel ? <span className="muted">@{tok.hostLabel}</span> : null}
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={busy}
                  onClick={async () => {
                    if (!window.confirm(t('pages.settings.confirmRevoke'))) return
                    setBusy(true)
                    try {
                      await agentTokenApi.revoke(tok.id)
                      setMintedToken(null)
                      await load()
                    } catch (e) {
                      setError(e.message || String(e))
                    } finally {
                      setBusy(false)
                    }
                  }}
                >
                  {t('pages.settings.revoke')}
                </button>
              </li>
            ))}
          </ul>
        )}
        <p className="muted" style={{ marginTop: 12, fontSize: '0.85rem' }}>
          {t('pages.settings.hermesDocHint')}
        </p>
      </div>


      <div style={{ marginTop: 16 }}>
        <Link to="/finance/payments" className="btn btn-secondary">
          {t('pages.integrations.openPayments', { defaultValue: 'Finance → Payments (BTCPay keys)' })}
        </Link>
      </div>
    </div>
  )
}
