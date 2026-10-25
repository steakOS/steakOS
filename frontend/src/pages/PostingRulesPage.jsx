// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { BookOpen, RefreshCw, Save, RotateCcw } from 'lucide-react'
import { financeLedgerApi, meApi } from '../api/client'
import StubBadge from '../components/StubBadge'

const EVENT_ORDER = [
  'invoice.settle',
  'payable.pay',
  'payroll.recognize',
  'payroll.pay',
  'invoice.issue',
  'payable.create',
]

const RAIL_ORDER = ['SEPA', 'BTC_ONCHAIN', 'BTC_LIGHTNING', 'BTC_WALLET_ADDRESS', 'OTHER']

function cloneRuleset(ruleset) {
  return ruleset ? structuredClone(ruleset) : { version: 1, accountingMode: 'CASH', events: {}, railWallet: {} }
}

/**
 * PDR-027 PR4/PR5 — Admin posting rules: mode, event legs, rail wallets, reseed + change log.
 * CEO may view; only Admin can save / reseed.
 */
export default function PostingRulesPage() {
  const { t } = useTranslation(['desks', 'common'])
  const [me, setMe] = useState(null)
  const [mode, setMode] = useState('CASH')
  const [draft, setDraft] = useState(null)
  const [changeLog, setChangeLog] = useState([])
  const [stubEvents, setStubEvents] = useState(['invoice.issue', 'payable.create'])
  const [err, setErr] = useState(null)
  const [info, setInfo] = useState(null)
  const [busy, setBusy] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [reason, setReason] = useState('')
  const [advancedJson, setAdvancedJson] = useState(false)

  // Admin profile (not CEO-as-admin view-only): prefer true Admin for writes
  const trulyAdmin = useMemo(() => {
    if (!me) return false
    return me.roleProfileId === 'admin' || (me.isAdmin && !me.isCeo)
  }, [me])

  const canView = useMemo(() => {
    if (!me) return false
    return !!(me.isAdmin || me.isCeo || me.permissions?.desks?.finance?.see)
  }, [me])

  const load = useCallback(async () => {
    setErr(null)
    try {
      const m = await meApi.get()
      setMe(m)
      if (!m.isAdmin && !m.isCeo && !m.permissions?.desks?.finance?.see) {
        setErr(
          t('finance.postingRules.adminOrCeo', {
            defaultValue: 'Admin or CEO required to view posting rules',
          }),
        )
        return
      }
      const d = await financeLedgerApi.postingRules()
      setMode(d.accountingMode || d.ruleset?.accountingMode || 'CASH')
      setDraft(cloneRuleset(d.ruleset))
      setChangeLog(d.changeLog || [])
      if (Array.isArray(d.accrualStubEvents)) setStubEvents(d.accrualStubEvents)
      setDirty(false)
      setReason('')
    } catch (e) {
      setErr(e.message || String(e))
    }
  }, [t])

  useEffect(() => {
    load()
  }, [load])

  const eventKeys = useMemo(() => {
    if (!draft?.events) return EVENT_ORDER
    const keys = Object.keys(draft.events)
    const ordered = EVENT_ORDER.filter((k) => keys.includes(k))
    keys.forEach((k) => {
      if (!ordered.includes(k)) ordered.push(k)
    })
    return ordered
  }, [draft])

  const patchMode = (next) => {
    setMode(next)
    setDraft((d) => (d ? { ...d, accountingMode: next } : d))
    setDirty(true)
  }

  const patchLeg = (eventKey, legIdx, field, value) => {
    setDraft((d) => {
      if (!d) return d
      const next = cloneRuleset(d)
      const ev = next.events[eventKey]
      if (!ev?.legs?.[legIdx]) return d
      ev.legs[legIdx] = { ...ev.legs[legIdx], [field]: value }
      return next
    })
    setDirty(true)
  }

  const patchRail = (rail, code) => {
    setDraft((d) => {
      if (!d) return d
      const next = cloneRuleset(d)
      if (!next.railWallet) next.railWallet = {}
      next.railWallet[rail] = code
      return next
    })
    setDirty(true)
  }

  const save = async () => {
    if (!trulyAdmin) {
      setErr(
        t('finance.postingRules.adminOnlyEdit', {
          defaultValue: 'Only Admin can save posting rules (CEO is view-only).',
        }),
      )
      return
    }
    setBusy(true)
    setErr(null)
    setInfo(null)
    try {
      const body = {
        accountingMode: mode,
        ruleset: { ...draft, accountingMode: mode },
        reason: reason.trim() || undefined,
      }
      const r = await financeLedgerApi.postingRulesSave(body)
      setInfo(
        t('finance.postingRules.saved', {
          defaultValue: 'Posting rules saved{{ticket}}.',
          ticket: r.itTicketNumber ? ` · IT ${r.itTicketNumber}` : '',
        }),
      )
      setDirty(false)
      await load()
    } catch (e) {
      setErr(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const reseed = async () => {
    if (!trulyAdmin) return
    const ok = window.confirm(
      t('finance.postingRules.reseedConfirm', {
        defaultValue:
          'Reset posting rules to SBM restaurant defaults? Journals are not deleted. An IT ticket will be opened.',
      }),
    )
    if (!ok) return
    setBusy(true)
    setErr(null)
    setInfo(null)
    try {
      const r = await financeLedgerApi.postingRulesReseed({
        reason: reason.trim() || 'Admin reseed to SBM defaults',
      })
      setInfo(
        t('finance.postingRules.reseeded', {
          defaultValue: 'Reseeded to SBM defaults{{ticket}}.',
          ticket: r.itTicketNumber ? ` · IT ${r.itTicketNumber}` : '',
        }),
      )
      setDirty(false)
      await load()
    } catch (e) {
      setErr(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const isStub = (key) => stubEvents.includes(key) || !!draft?.events?.[key]?.stub

  return (
    <div className="page">
      <header className="page-header" style={{ marginBottom: '1rem' }}>
        <h1 className="page-title">
          {t('finance.postingRules.title', { defaultValue: 'Posting rules' })}
        </h1>
        <p className="page-sub">
          {t('finance.postingRules.subtitle', {
            defaultValue:
              'Company journal rules for settle, vendor pay, and payroll · Admin edits · CEO view',
          })}{' '}
          · <Link to="/finance/control">{t('finance.tabs.control')}</Link>
          {' · '}
          <Link to="/finance/transactions">{t('finance.ledgerTitle')}</Link>
          {' · '}
          <Link to="/help?help=finance.postingRules">
            {t('finance.postingRules.helpLink', { defaultValue: 'Help' })}
          </Link>
        </p>
      </header>

      {err && <div className="banner-error">{err}</div>}
      {info && <div className="banner-info">{info}</div>}

      {canView && !trulyAdmin && (
        <div className="banner-info">
          {t('finance.postingRules.ceoViewOnly', {
            defaultValue: 'CEO view — only Admin can change posting rules or reseed.',
          })}
        </div>
      )}

      <div className="card" style={{ marginBottom: '1rem' }}>
        <div className="form-row" style={{ flexWrap: 'wrap', gap: '1rem', alignItems: 'flex-end' }}>
          <label>
            {t('finance.postingRules.accountingMode', { defaultValue: 'Accounting mode' })}
            <select
              value={mode}
              disabled={!trulyAdmin || busy}
              onChange={(e) => patchMode(e.target.value)}
            >
              <option value="CASH">
                {t('finance.postingRules.modeCash', {
                  defaultValue: 'Cash (recommended) — books update when paid',
                })}
              </option>
              <option value="ACCRUAL">
                {t('finance.postingRules.modeAccrual', {
                  defaultValue: 'Accrual — issue/create stubs + payment (hooks pending)',
                })}
              </option>
            </select>
          </label>
          {trulyAdmin && (
            <label style={{ flex: 1, minWidth: '12rem' }}>
              {t('finance.postingRules.reason', { defaultValue: 'Reason (optional)' })}
              <input
                type="text"
                value={reason}
                disabled={busy}
                onChange={(e) => setReason(e.target.value)}
                placeholder={t('finance.postingRules.reasonPh', {
                  defaultValue: 'Why are you changing policy?',
                })}
              />
            </label>
          )}
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <button type="button" className="btn-secondary" disabled={busy} onClick={load}>
              <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
            </button>
            {trulyAdmin && (
              <>
                <button
                  type="button"
                  className="btn-primary"
                  disabled={busy || !dirty}
                  onClick={save}
                >
                  <Save size={16} />{' '}
                  {busy
                    ? t('actions.saving', { ns: 'common', defaultValue: 'Saving…' })
                    : t('actions.save', { ns: 'common', defaultValue: 'Save' })}
                </button>
                <button type="button" className="btn-secondary" disabled={busy} onClick={reseed}>
                  <RotateCcw size={16} />{' '}
                  {t('finance.postingRules.reseed', { defaultValue: 'Reseed defaults' })}
                </button>
              </>
            )}
          </div>
        </div>
        {mode === 'ACCRUAL' && (
          <p className="muted" style={{ marginTop: '0.75rem' }}>
            {t('finance.postingRules.accrualNote', {
              defaultValue:
                'Accrual stub rules (invoice.issue / payable.create) are in the pack. Issue/create hooks are not wired yet — cash settle/pay/payroll still post today.',
            })}
          </p>
        )}
      </div>

      <div className="card" style={{ marginBottom: '1rem' }}>
        <h2 className="section-label">
          <BookOpen size={16} style={{ verticalAlign: 'middle' }} />{' '}
          {t('finance.postingRules.events', { defaultValue: 'Posting events' })}
        </h2>
        {eventKeys.map((eventKey) => {
          const ev = draft?.events?.[eventKey]
          if (!ev) return null
          const legs = ev.legs || []
          return (
            <div key={eventKey} style={{ marginBottom: '1.25rem' }}>
              <h3 className="section-label" style={{ marginBottom: '0.35rem' }}>
                <code>{eventKey}</code>
                {isStub(eventKey) && (
                  <StubBadge
                    label={t('finance.postingRules.stub', { defaultValue: 'stub' })}
                    hint={t('finance.postingRules.accrualNote', {
                      defaultValue: 'Rules present; issue/create hooks not wired yet',
                    })}
                  />
                )}
              </h3>
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('finance.postingRules.side', { defaultValue: 'Side' })}</th>
                      <th>{t('finance.postingRules.account', { defaultValue: 'Account' })}</th>
                      <th>{t('finance.postingRules.amountFrom', { defaultValue: 'Amount from' })}</th>
                      <th>{t('finance.postingRules.optional', { defaultValue: 'Optional' })}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {legs.map((leg, idx) => (
                      <tr key={`${eventKey}-${idx}`}>
                        <td>
                          <select
                            value={leg.side || 'DEBIT'}
                            disabled={!trulyAdmin || busy}
                            onChange={(e) => patchLeg(eventKey, idx, 'side', e.target.value)}
                          >
                            <option value="DEBIT">DEBIT</option>
                            <option value="CREDIT">CREDIT</option>
                          </select>
                        </td>
                        <td>
                          {leg.accountFrom ? (
                            <span>
                              <code>railWallet</code>
                              <span className="muted"> ← {leg.accountFrom}</span>
                            </span>
                          ) : (
                            <input
                              type="text"
                              value={leg.accountCode || ''}
                              disabled={!trulyAdmin || busy}
                              onChange={(e) =>
                                patchLeg(eventKey, idx, 'accountCode', e.target.value)
                              }
                              style={{ width: '5rem' }}
                            />
                          )}
                        </td>
                        <td>
                          <code>{leg.amountFrom || '—'}</code>
                        </td>
                        <td>{leg.optional ? '✓' : ''}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )
        })}
      </div>

      <div className="card" style={{ marginBottom: '1rem' }}>
        <h2 className="section-label">
          {t('finance.postingRules.railWallet', { defaultValue: 'Rail → wallet account' })}
        </h2>
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('finance.postingRules.rail', { defaultValue: 'Rail' })}</th>
                <th>{t('finance.postingRules.coaCode', { defaultValue: 'COA code' })}</th>
              </tr>
            </thead>
            <tbody>
              {RAIL_ORDER.map((rail) => (
                <tr key={rail}>
                  <td>
                    <code>{rail}</code>
                  </td>
                  <td>
                    <input
                      type="text"
                      value={draft?.railWallet?.[rail] || ''}
                      disabled={!trulyAdmin || busy}
                      onChange={(e) => patchRail(rail, e.target.value)}
                      style={{ width: '5rem' }}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card" style={{ marginBottom: '1rem' }}>
        <h2 className="section-label">
          {t('finance.postingRules.changeLog', { defaultValue: 'Recent policy changes' })}
        </h2>
        {changeLog.length === 0 ? (
          <p className="muted">
            {t('finance.postingRules.noChanges', {
              defaultValue: 'No Admin saves yet for this company.',
            })}
          </p>
        ) : (
          <ul className="plain-list">
            {changeLog.map((c) => (
              <li key={c.id || c.at}>
                <strong>{c.at}</strong> · {c.action} · {c.actor}
                {c.accountingMode ? ` · ${c.accountingMode}` : ''}
                {c.itTicketNumber ? (
                  <>
                    {' · '}
                    <Link to="/help/it-support">IT {c.itTicketNumber}</Link>
                  </>
                ) : null}
                <div className="muted">{c.summary}</div>
                {c.reason ? (
                  <div className="muted">
                    {t('finance.postingRules.reason', { defaultValue: 'Reason' })}: {c.reason}
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <button
          type="button"
          className="btn-link"
          onClick={() => setAdvancedJson((v) => !v)}
        >
          {advancedJson
            ? t('finance.postingRules.hideJson', { defaultValue: 'Hide JSON' })
            : t('finance.postingRules.showJson', { defaultValue: 'Show raw JSON' })}
        </button>
        {advancedJson && (
          <pre
            style={{
              maxHeight: '24rem',
              overflow: 'auto',
              fontSize: '0.8rem',
              marginTop: '0.5rem',
            }}
          >
            {JSON.stringify({ accountingMode: mode, ruleset: draft }, null, 2)}
          </pre>
        )}
      </div>
    </div>
  )
}
