// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Bitcoin, RefreshCw } from 'lucide-react'
import { financeOpsApi } from '../api/client'

/**
 * A2 — Connect live BTCPay (LIVE company) or show DEMO mock-only honesty.
 */
export default function FinancePayments() {
  const { t } = useTranslation(['desks', 'common'])
  const [settings, setSettings] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [busy, setBusy] = useState(false)
  const [probe, setProbe] = useState(null)
  const [form, setForm] = useState({
    provider: 'MOCK',
    liveEnabled: false,
    baseUrl: '',
    storeId: '',
    defaultCurrency: 'EUR',
    apiKey: '',
    webhookSecret: '',
  })

  const load = useCallback(async () => {
    setError(null)
    try {
      const s = await financeOpsApi.paymentsSettingsGet()
      setSettings(s)
      setForm((f) => ({
        ...f,
        provider: s.demoForcedMock ? 'MOCK' : s.provider || 'MOCK',
        liveEnabled: s.demoForcedMock ? false : !!s.liveEnabled,
        baseUrl: s.btcpay?.baseUrl || '',
        storeId: s.btcpay?.storeId || '',
        defaultCurrency: s.btcpay?.defaultCurrency || 'EUR',
        apiKey: '',
        webhookSecret: '',
      }))
    } catch (e) {
      setError(e.message || String(e))
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const demoLocked = !!settings?.demoForcedMock
  const btcLevel = settings?.btcIntegration || null
  const level3 = btcLevel?.level === 'BTCPAY'
  const level1 = !btcLevel?.level || btcLevel?.level === 'DEFERRED'
  const level2 = btcLevel?.level === 'SINGLE_ADDRESS'
  const livePathLocked = demoLocked || !level3

  const save = async (e) => {
    e?.preventDefault?.()
    setBusy(true)
    setError(null)
    setInfo(null)
    setProbe(null)
    try {
      if (!demoLocked && !level3 && (form.provider === 'BTCPAY' || form.liveEnabled)) {
        throw new Error(
          t('finance.needLevel3', {
            defaultValue:
              'Set Bitcoin integration level to BTCPay (Level 3) in System → Integrations before enabling live BTCPay.',
          }),
        )
      }
      const body = {
        provider: livePathLocked ? 'MOCK' : form.provider,
        liveEnabled: livePathLocked ? false : form.liveEnabled,
        btcpay: {
          baseUrl: form.baseUrl,
          storeId: form.storeId,
          defaultCurrency: form.defaultCurrency,
        },
      }
      if (form.apiKey?.trim()) body.btcpay.apiKey = form.apiKey.trim()
      if (form.webhookSecret?.trim()) body.btcpay.webhookSecret = form.webhookSecret.trim()
      const s = await financeOpsApi.paymentsSettingsPut(body)
      setSettings(s)
      setInfo(t('finance.paymentsSaved', { defaultValue: 'Payment settings saved.' }))
      setForm((f) => ({ ...f, apiKey: '', webhookSecret: '' }))
    } catch (ex) {
      setError(ex.message || String(ex))
    } finally {
      setBusy(false)
    }
  }

  const runProbe = async () => {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      const r = await financeOpsApi.paymentsProbe()
      setProbe(r)
      if (r.ok) {
        setInfo(
          t('finance.paymentsProbeOk', {
            defaultValue: 'Probe OK — store {{name}}',
            name: r.name || r.storeId || '',
          }),
        )
      } else {
        setError(r.error || t('finance.paymentsProbeFail', { defaultValue: 'Probe failed' }))
      }
    } catch (ex) {
      setError(ex.message || String(ex))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page finance-payments-page">
      <header className="page-header" style={{ marginBottom: '1.25rem' }}>
        <h1 className="page-title">
          <Bitcoin size={28} style={{ marginRight: 8, verticalAlign: 'middle' }} />
          {t('finance.paymentsTitle', { defaultValue: 'Payments' })}
        </h1>
        <p className="page-sub">
          {t('finance.paymentsSubtitle', {
            defaultValue:
              'Connect BTCPay Server for live Bitcoin receive on your LIVE company. Demo companies stay on mock settle.',
          })}
        </p>
      </header>

      {error && (
        <p className="banner-error" role="alert">
          {error}
        </p>
      )}
      {info && (
        <p className="banner-info" role="status">
          {info}
        </p>
      )}

      {demoLocked && (
        <div className="banner-info" role="status">
          {t('finance.demoMockOnly', {
            defaultValue:
              'Demo company: mock settle only. Switch to your LIVE company to connect BTCPay for real Lightning / on-chain payments.',
          })}
        </div>
      )}

      {level1 && !demoLocked && (
        <div className="banner-info" role="status">
          {t('finance.level1Deferred', {
            defaultValue:
              'Bitcoin receive deferred (Level 1). Mock settle only. Upgrade under System → Integrations to enable single-address or BTCPay.',
          })}{' '}
          <Link to="/system/integrations">
            {t('finance.openSettings', { defaultValue: 'Open Integrations' })}
          </Link>
        </div>
      )}

      {level2 && !demoLocked && (
        <div className="banner-info" role="status">
          {t('finance.level2Manual', {
            defaultValue:
              'Level 2 — single address. Invoices can show your receive address / QR; settle is manual mark paid. Upgrade to Level 3 for Lightning via BTCPay.',
          })}{' '}
          {btcLevel?.receiveAddressMasked && (
            <span className="mono"> {btcLevel.receiveAddressMasked}</span>
          )}{' '}
          <Link to="/system/integrations">
            {t('finance.upgradeLevel', { defaultValue: 'Change level' })}
          </Link>
        </div>
      )}

      <section className="card" style={{ padding: '1rem', marginTop: '1rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}>
          <div>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('finance.paymentsStatus', { defaultValue: 'Status' })}
            </h2>
            <p>
              {t('finance.btcLevel', { defaultValue: 'Bitcoin level' })}:{' '}
              <strong>{btcLevel?.uiLabel || '—'}</strong>
            </p>
            <p>
              {t('finance.activeProvider', { defaultValue: 'Active provider' })}:{' '}
              <strong>{settings?.activeProvider || '—'}</strong>
            </p>
            <p className="muted">{settings?.note}</p>
            <p className="muted">
              {t('finance.apiKeyConfigured', { defaultValue: 'API key' })}:{' '}
              {settings?.btcpay?.apiKeyConfigured ? '✓' : '—'} ·{' '}
              {t('finance.webhookConfigured', { defaultValue: 'Webhook secret' })}:{' '}
              {settings?.btcpay?.webhookConfigured ? '✓' : '—'} · liveReady:{' '}
              {settings?.liveReady ? '✓' : '—'}
            </p>
          </div>
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={load}>
            <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
          </button>
        </div>
      </section>

      <form className="card form-grid" style={{ padding: '1rem', marginTop: '1rem' }} onSubmit={save}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {t('finance.btcpaySettings', { defaultValue: 'BTCPay Server (operator-run)' })}
        </h2>
        <p className="muted">
          {t('finance.noCustody', {
            defaultValue: 'steakOS does not custody your keys. You install and run BTCPay.',
          })}{' '}
          <Link to="/help">{t('finance.paymentsHelpLink', { defaultValue: 'Help: Live payments' })}</Link>
        </p>

        <label>
          {t('finance.provider', { defaultValue: 'Provider' })}
          <select
            value={form.provider}
            disabled={livePathLocked || busy}
            onChange={(e) => setForm({ ...form, provider: e.target.value })}
          >
            <option value="MOCK">MOCK</option>
            <option value="BTCPAY">BTCPAY</option>
          </select>
        </label>

        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={form.liveEnabled}
            disabled={livePathLocked || busy}
            onChange={(e) => setForm({ ...form, liveEnabled: e.target.checked })}
          />
          {t('finance.liveEnabled', { defaultValue: 'Live enabled (use BTCPay when fully configured)' })}
        </label>

        <label>
          {t('finance.btcpayBaseUrl', { defaultValue: 'BTCPay base URL' })}
          <input
            value={form.baseUrl}
            disabled={livePathLocked || busy}
            onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
            placeholder="https://pay.example.com"
            autoComplete="off"
          />
        </label>

        <label>
          {t('finance.btcpayStoreId', { defaultValue: 'Store ID' })}
          <input
            value={form.storeId}
            disabled={livePathLocked || busy}
            onChange={(e) => setForm({ ...form, storeId: e.target.value })}
            autoComplete="off"
          />
        </label>

        <label>
          {t('finance.defaultCurrency', { defaultValue: 'Default currency' })}
          <input
            value={form.defaultCurrency}
            disabled={livePathLocked || busy}
            onChange={(e) => setForm({ ...form, defaultCurrency: e.target.value })}
          />
        </label>

        <label>
          {t('finance.apiKeyWriteOnly', { defaultValue: 'API key (write-only)' })}
          <input
            type="password"
            value={form.apiKey}
            disabled={livePathLocked || busy}
            onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
            placeholder={settings?.btcpay?.apiKeyConfigured ? '•••••••• (leave blank to keep)' : ''}
            autoComplete="new-password"
          />
        </label>

        <label>
          {t('finance.webhookSecretWriteOnly', { defaultValue: 'Webhook secret (write-only)' })}
          <input
            type="password"
            value={form.webhookSecret}
            disabled={livePathLocked || busy}
            onChange={(e) => setForm({ ...form, webhookSecret: e.target.value })}
            placeholder={settings?.btcpay?.webhookConfigured ? '•••••••• (leave blank to keep)' : ''}
            autoComplete="new-password"
          />
        </label>

        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button type="submit" className="btn btn-primary" disabled={busy || livePathLocked}>
            {t('actions.save', { ns: 'common', defaultValue: 'Save' })}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={busy || livePathLocked}
            onClick={runProbe}
          >
            {t('finance.probe', { defaultValue: 'Probe connection' })}
          </button>
          <Link className="btn btn-secondary" to="/finance/invoices">
            {t('finance.invoicesTitle', { defaultValue: 'Invoices' })}
          </Link>
        </div>

        {probe && (
          <pre className="mono" style={{ fontSize: 12, overflow: 'auto' }}>
            {JSON.stringify(probe, null, 2)}
          </pre>
        )}
      </form>
    </div>
  )
}
