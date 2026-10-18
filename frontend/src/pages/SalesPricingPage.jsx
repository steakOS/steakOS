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

import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Tags, Plus, RefreshCw, Save } from 'lucide-react'
import { salesApi } from '../api/client'
import CustomerSelect from '../components/CustomerSelect'
import CatalogueItemSelect from '../components/CatalogueItemSelect'
import CurrencySelect from '../components/CurrencySelect'

const LIST_KINDS = ['STANDARD', 'CUSTOMER', 'VOLUME']
const emptyForm = () => ({
  name: '',
  code: '',
  listKind: 'STANDARD',
  currencyCode: 'EUR',
  active: true,
  validFrom: '',
  validTo: '',
  customerId: '',
  description: '',
  defaultDiscountPercent: '',
})
const emptyLine = () => ({ catalogueItemId: '', name: '', unitPrice: '', minQty: '1' })

function toDateInput(v) {
  if (v == null || v === '') return ''
  if (typeof v === 'number') return new Date(v).toISOString().slice(0, 10)
  const s = String(v)
  return s.length >= 10 ? s.slice(0, 10) : s
}

function friendlyErr(e, t) {
  if (e?.status === 403 || e?.code === 'ADVANCED_REQUIRES_SILVER') {
    return t('pricing.silverOnly', { defaultValue: 'Available on Silver (or Iron eval)' })
  }
  return e?.message || String(e)
}

function headerBody(src) {
  const body = {
    name: src.name.trim(),
    code: src.code.trim() || undefined,
    listKind: src.listKind || 'STANDARD',
    currencyCode: src.currencyCode || 'EUR',
    active: !!src.active,
    description: src.description.trim() || undefined,
    validFrom: src.validFrom || undefined,
    validTo: src.validTo || undefined,
  }
  if (src.defaultDiscountPercent !== '') body.defaultDiscountPercent = Number(src.defaultDiscountPercent)
  if (src.listKind === 'CUSTOMER' || src.customerId) {
    body.customerId = src.customerId ? Number(src.customerId) : null
  }
  return body
}

/** SalesAdvanced price lists desk (OI-S04) — create, select, edit, lines. */
export default function SalesPricingPage() {
  const { t } = useTranslation(['desks', 'common', 'sales'])
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [selected, setSelected] = useState(null)
  const [editForm, setEditForm] = useState(emptyForm())
  const [lines, setLines] = useState([])
  const [lineDraft, setLineDraft] = useState(emptyLine())
  const [form, setForm] = useState(emptyForm())
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await salesApi.pricelists.list({ max: 50, offset: 0 })
      setItems(data?.items || [])
      setTotal(data?.total ?? (data?.items || []).length)
    } catch (e) {
      setItems([])
      setTotal(0)
      setError(friendlyErr(e, t))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    load()
  }, [load])

  const selectList = async (id) => {
    setBusy('select')
    setError(null)
    try {
      const full = await salesApi.pricelists.get(id)
      setSelected(full)
      setEditForm({
        name: full.name || '',
        code: full.code || '',
        listKind: full.listKind || 'STANDARD',
        currencyCode: full.currencyCode || 'EUR',
        active: full.active !== false,
        validFrom: toDateInput(full.validFrom),
        validTo: toDateInput(full.validTo),
        customerId: full.customerId != null ? String(full.customerId) : '',
        description: full.description || '',
        defaultDiscountPercent:
          full.defaultDiscountPercent != null ? String(full.defaultDiscountPercent) : '',
      })
      setLines(
        (full.lines || []).map((ln) => ({
          catalogueItemId: ln.catalogueItemId != null ? String(ln.catalogueItemId) : '',
          name: ln.name || ln.itemCode || '',
          unitPrice: ln.unitPrice != null ? String(ln.unitPrice) : '',
          minQty: ln.minQuantity != null ? String(ln.minQuantity) : '1',
        })),
      )
      setLineDraft(emptyLine())
    } catch (e) {
      setError(friendlyErr(e, t))
    } finally {
      setBusy(null)
    }
  }

  const needCustomer = (src) =>
    src.listKind === 'CUSTOMER' && !src.customerId
      ? t('pricing.customerRequired', {
          defaultValue: 'Customer is required for customer price lists.',
        })
      : null

  const onCreate = async (e) => {
    e.preventDefault()
    if (!form.name.trim()) return
    const miss = needCustomer(form)
    if (miss) {
      setError(miss)
      return
    }
    setBusy('create')
    setError(null)
    setInfo(null)
    try {
      const pl = await salesApi.pricelists.create(headerBody(form))
      setInfo(t('pricing.created', { defaultValue: 'Price list “{{name}}” created.', name: pl.name }))
      setForm(emptyForm())
      await load()
      await selectList(pl.id)
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const onSaveHeader = async (e) => {
    e.preventDefault()
    if (!selected?.id) return
    const miss = needCustomer(editForm)
    if (miss) {
      setError(miss)
      return
    }
    setBusy('save')
    setError(null)
    setInfo(null)
    try {
      const pl = await salesApi.pricelists.update(selected.id, headerBody(editForm))
      setInfo(t('pricing.saved', { defaultValue: 'Price list saved.' }))
      await load()
      await selectList(pl.id)
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const addLineDraft = () => {
    if (!lineDraft.name.trim() || lineDraft.unitPrice === '') {
      setError(t('pricing.lineNeedNamePrice', { defaultValue: 'Each line needs a name and a unit price.' }))
      return
    }
    setLines((prev) => [...prev, { ...lineDraft, name: lineDraft.name.trim() }])
    setLineDraft(emptyLine())
    setError(null)
  }

  const onSaveLines = async () => {
    if (!selected?.id) return
    setBusy('lines')
    setError(null)
    setInfo(null)
    try {
      const payload = lines.map((ln) => {
        const row = {
          name: ln.name,
          unitPrice: Number(ln.unitPrice),
          minQuantity: ln.minQty !== '' ? Number(ln.minQty) : 1,
        }
        if (ln.catalogueItemId) row.catalogueItemId = Number(ln.catalogueItemId)
        return row
      })
      const pl = await salesApi.pricelists.setLines(selected.id, payload)
      setInfo(t('pricing.linesSaved', { defaultValue: 'Lines saved.' }))
      await load()
      await selectList(pl.id)
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const fields = (src, setSrc, prefix) => (
    <>
      <label>
        {t('pricing.fieldName', { defaultValue: 'Name' })} *
        <input required value={src.name} disabled={!!busy}
          onChange={(e) => setSrc((f) => ({ ...f, name: e.target.value }))} />
      </label>
      <div className="form-row-2">
        <label>
          {t('pricing.fieldCode', { defaultValue: 'Code' })}
          <input value={src.code} disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, code: e.target.value }))} />
        </label>
        <label>
          {t('pricing.fieldKind', { defaultValue: 'Kind' })}
          <select value={src.listKind} disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, listKind: e.target.value }))}>
            {LIST_KINDS.map((k) => <option key={k} value={k}>{k}</option>)}
          </select>
        </label>
      </div>
      <div className="form-row-2">
        <label>
          {t('pricing.fieldCurrency', { defaultValue: 'Currency' })}
          <CurrencySelect id={`${prefix}-ccy`} value={src.currencyCode} disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, currencyCode: e.target.value }))} />
        </label>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 22 }}>
          <input type="checkbox" checked={!!src.active} disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, active: e.target.checked }))} />
          {t('pricing.fieldActive', { defaultValue: 'Active' })}
        </label>
      </div>
      {(src.listKind === 'CUSTOMER' || src.customerId) && (
        <label>
          {t('pricing.fieldCustomer', { defaultValue: 'Customer' })}
          {src.listKind === 'CUSTOMER' ? ' *' : ''}
          <CustomerSelect id={`${prefix}-cust`} value={src.customerId} disabled={!!busy}
            onChange={(id) => setSrc((f) => ({ ...f, customerId: id || '' }))} />
        </label>
      )}
      <div className="form-row-2">
        <label>
          {t('pricing.fieldValidFrom', { defaultValue: 'Valid from' })}
          <input type="date" value={src.validFrom} disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, validFrom: e.target.value }))} />
        </label>
        <label>
          {t('pricing.fieldValidTo', { defaultValue: 'Valid to' })}
          <input type="date" value={src.validTo} disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, validTo: e.target.value }))} />
        </label>
      </div>
      <label>
        {t('pricing.fieldDiscount', { defaultValue: 'Default discount %' })}
        <input type="number" step="0.01" min="0" value={src.defaultDiscountPercent} disabled={!!busy}
          onChange={(e) => setSrc((f) => ({ ...f, defaultDiscountPercent: e.target.value }))} />
      </label>
      <label>
        {t('pricing.fieldDescription', { defaultValue: 'Description' })}
        <textarea rows={2} value={src.description} disabled={!!busy}
          onChange={(e) => setSrc((f) => ({ ...f, description: e.target.value }))} />
      </label>
    </>
  )

  return (
    <div className="page">
      <header className="page-header">
        <h1>
          <Tags size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('pricing.title', { defaultValue: 'Price lists' })}
        </h1>
        <p className="page-sub">
          {t('pricing.subtitle', {
            defaultValue: 'SalesAdvanced mid-market pricing lists. Silver pack / Iron eval.',
          })}
        </p>
        <p><Link to="/sales">{t('pricing.backSales', { defaultValue: '← Sales desk' })}</Link></p>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      <div className="toolbar" style={{ marginBottom: '1rem' }}>
        <button type="button" className="btn" onClick={load} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
      </div>

      <div className="two-col finance-tx-grid list-first-narrow">
        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('pricing.newList', { defaultValue: 'New price list' })}
          </h2>
          <form className="form-grid" onSubmit={onCreate}>
            {fields(form, setForm, 'new')}
            <button type="submit" className="btn btn-primary" disabled={!!busy}>
              <Plus size={16} /> {t('pricing.create', { defaultValue: 'Create list' })}
            </button>
          </form>
        </section>

        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('pricing.list', { defaultValue: 'Lists' })}
            {!loading && <span className="muted" style={{ fontWeight: 400, marginLeft: 8 }}>({total})</span>}
          </h2>
          {loading ? (
            <p className="muted">{t('pricing.loading', { defaultValue: 'Loading…' })}</p>
          ) : items.length === 0 ? (
            <p className="muted">
              {t('pricing.empty', {
                defaultValue: 'No price lists yet. Create one with the form on the left.',
              })}
            </p>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t('pricing.colName', { defaultValue: 'Name' })}</th>
                    <th>{t('pricing.colKind', { defaultValue: 'Kind' })}</th>
                    <th>{t('pricing.colActive', { defaultValue: 'Active' })}</th>
                    <th>{t('pricing.colLines', { defaultValue: 'Lines' })}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((row) => (
                    <tr key={row.id}
                      className={selected?.id === row.id ? 'row-selected' : 'row-click'}
                      onClick={() => selectList(row.id)}>
                      <td>{row.name || row.code || `#${row.id}`}</td>
                      <td>{row.listKind || '—'}</td>
                      <td>{row.active === false
                        ? t('pricing.no', { defaultValue: 'no' })
                        : t('pricing.yes', { defaultValue: 'yes' })}</td>
                      <td>{row.lineCount ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>

      {selected && (
        <section className="card" style={{ marginTop: '1rem' }}>
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('pricing.detail', { defaultValue: 'Selected list' })} — {selected.name}
          </h2>
          <form className="form-grid" onSubmit={onSaveHeader}>
            {fields(editForm, setEditForm, 'edit')}
            <button type="submit" className="btn btn-primary" disabled={!!busy}>
              <Save size={16} /> {t('actions.save', { ns: 'common', defaultValue: 'Save' })}
            </button>
          </form>

          <h3 className="section-label" style={{ marginTop: '1.25rem' }}>
            {t('pricing.lines', { defaultValue: 'Lines' })}
          </h3>
          {lines.length === 0 ? (
            <p className="muted">
              {t('pricing.linesEmpty', {
                defaultValue: 'No lines yet. Add catalogue items or named prices below.',
              })}
            </p>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t('pricing.colLineName', { defaultValue: 'Name' })}</th>
                    <th>{t('pricing.colUnitPrice', { defaultValue: 'Unit price' })}</th>
                    <th>{t('pricing.colMinQty', { defaultValue: 'Min qty' })}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {lines.map((ln, i) => (
                    <tr key={`${ln.name}-${i}`}>
                      <td>{ln.name}{ln.catalogueItemId
                        ? <span className="muted mono"> · #{ln.catalogueItemId}</span> : null}</td>
                      <td>{ln.unitPrice}</td>
                      <td>{ln.minQty}</td>
                      <td>
                        <button type="button" className="btn btn-ghost" disabled={!!busy}
                          onClick={() => setLines((prev) => prev.filter((_, j) => j !== i))}>
                          {t('actions.remove', { ns: 'common', defaultValue: 'Remove' })}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="form-grid" style={{ marginTop: '0.75rem' }}>
            <label>
              {t('pricing.fieldCatalogueItem', { defaultValue: 'Catalogue item' })}
              <CatalogueItemSelect id="pl-line-item" nature="GOODS" forChannel="sale"
                value={lineDraft.catalogueItemId} disabled={!!busy}
                onChange={(id, item) => setLineDraft((d) => ({
                  ...d,
                  catalogueItemId: id || '',
                  name: item?.name || (id ? d.name : ''),
                  unitPrice: item?.listPrice != null ? String(item.listPrice) : id ? d.unitPrice : '',
                }))} />
            </label>
            <div className="form-row-2">
              <label>
                {t('pricing.fieldLineName', { defaultValue: 'Line name' })} *
                <input value={lineDraft.name} disabled={!!busy}
                  onChange={(e) => setLineDraft((d) => ({ ...d, name: e.target.value }))} />
              </label>
              <label>
                {t('pricing.fieldUnitPrice', { defaultValue: 'Unit price' })} *
                <input type="number" step="0.01" min="0" value={lineDraft.unitPrice} disabled={!!busy}
                  onChange={(e) => setLineDraft((d) => ({ ...d, unitPrice: e.target.value }))} />
              </label>
            </div>
            <label>
              {t('pricing.fieldMinQty', { defaultValue: 'Min quantity' })}
              <input type="number" step="0.01" min="0" value={lineDraft.minQty} disabled={!!busy}
                onChange={(e) => setLineDraft((d) => ({ ...d, minQty: e.target.value }))} />
            </label>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <button type="button" className="btn" onClick={addLineDraft} disabled={!!busy}>
                <Plus size={16} /> {t('pricing.addLine', { defaultValue: 'Add line' })}
              </button>
              <button type="button" className="btn btn-primary" onClick={onSaveLines} disabled={!!busy}>
                <Save size={16} /> {t('pricing.saveLines', { defaultValue: 'Save lines' })}
              </button>
            </div>
          </div>
        </section>
      )}
    </div>
  )
}
