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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Tags, Plus, RefreshCw, Save, Eye, Pencil, Trash2 } from 'lucide-react'
import ResponsiveDataList from '../components/ResponsiveDataList'
import { domainsApi, salesApi } from '../api/client'
import CustomerSelect from '../components/CustomerSelect'
import CatalogueItemSelect from '../components/CatalogueItemSelect'
import CurrencySelect from '../components/CurrencySelect'
import DeskShell from '../components/desk/DeskShell'
import DeskNewFab from '../components/DeskNewFab'
import ConfirmDialog from '../components/ConfirmDialog'
import RowActionSlots from '../components/RowActionSlots'
import useRecordView from '../hooks/useRecordView'
import useDeskSelection from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'

const PKG = 'sales'
const RESOURCE = 'PriceList'
const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'

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

function linesFromFull(full) {
  return (full?.lines || []).map((ln) => ({
    catalogueItemId: ln.catalogueItemId != null ? String(ln.catalogueItemId) : '',
    name: ln.name || ln.itemCode || '',
    unitPrice: ln.unitPrice != null ? String(ln.unitPrice) : '',
    minQty: ln.minQuantity != null ? String(ln.minQuantity) : '1',
  }))
}

/**
 * SalesAdvanced price lists desk (OI-S04) — Buyers UI 2.0 (ADR-050): single-column + modal.
 */
export default function SalesPricingPage() {
  const { t } = useTranslation(['desks', 'common', 'sales'])
  const loadRef = useRef(async () => {})
  const deepLinkOpened = useRef(null)
  const getBelowFieldsRef = useRef(null)
  const selectedRef = useRef(null)
  const linesRef = useRef([])
  const busyRef = useRef(null)

  const [tab] = useState('lists')
  const [selectedId, setSelectedId] = useState(() => {
    if (typeof window === 'undefined') return null
    return new URLSearchParams(window.location.search).get('id') || null
  })
  const [newExpanded, setNewExpanded] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [pendingNav, setPendingNav] = useState(null)
  const [companyId] = useState('default')

  const { saveMemory } = useDeskMemory({ deskKey: 'sales-pricing', companyId })

  useDeskSelection({
    deskKey: 'sales-pricing',
    defaultTab: 'lists',
    tab,
    setTab: () => {},
    selectedId,
    setSelectedId,
  })

  useEffect(() => {
    saveMemory({ tab })
  }, [tab, saveMemory])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('new') === '1') {
      setNewExpanded(true)
      params.delete('new')
      const qs = params.toString()
      window.history.replaceState(null, '', `${window.location.pathname}${qs ? `?${qs}` : ''}`)
    }
  }, [])

  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [selected, setSelected] = useState(null)
  const [lines, setLines] = useState([])
  const [lineDraft, setLineDraft] = useState(emptyLine())
  const [form, setForm] = useState(emptyForm())
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)

  selectedRef.current = selected
  linesRef.current = lines
  busyRef.current = busy

  const {
    viewModal,
    openView,
    openEdit,
    updateViewingRecord,
  } = useRecordView({
    modalEdit: true,
    canModalEdit: () => true,
    onSaved: (updated) => {
      loadRef.current().catch(() => {})
      if (updated?.id != null) {
        setSelected((prev) =>
          prev && String(prev.id) === String(updated.id) ? { ...prev, ...updated } : prev,
        )
      }
    },
    getBelowFields: (ctx) => getBelowFieldsRef.current?.(ctx) ?? null,
  })

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

  loadRef.current = load

  useEffect(() => {
    load()
  }, [load])

  const hydrateFromFull = useCallback((full) => {
    setSelected(full)
    setLines(linesFromFull(full))
    setLineDraft(emptyLine())
  }, [])

  const openPriceListView = useCallback(
    async (rowOrId) => {
      const id = typeof rowOrId === 'object' ? rowOrId?.id : rowOrId
      if (id == null || id === '') return
      setBusy('select')
      setError(null)
      try {
        const full = await salesApi.pricelists.get(id)
        hydrateFromFull(full)
        // Seed without lines so the field grid does not dump the array as JSON.
        const { lines: _lines, ...seed } = full
        openView(PKG, RESOURCE, seed)
        deepLinkOpened.current = String(id)
        setSelectedId(String(id))
      } catch (e) {
        setError(friendlyErr(e, t))
      } finally {
        setBusy(null)
      }
    },
    [hydrateFromFull, openView, t],
  )

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      if (!selectedId) return
      if (deepLinkOpened.current === String(selectedId)) return
      try {
        const full = await salesApi.pricelists.get(selectedId)
        if (cancelled || !full) return
        hydrateFromFull(full)
        const { lines: _lines, ...seed } = full
        openView(PKG, RESOURCE, seed)
        deepLinkOpened.current = String(selectedId)
      } catch (e) {
        if (!cancelled) setError(friendlyErr(e, t))
      }
    }
    run()
    return () => {
      cancelled = true
    }
  }, [selectedId, hydrateFromFull, openView, t])

  const resetCreateForms = useCallback(() => {
    setForm(emptyForm())
  }, [])

  const createDirty = useMemo(() => {
    const d = emptyForm()
    return Boolean(
      (form.name || '').trim() ||
        (form.code || '').trim() ||
        form.listKind !== d.listKind ||
        form.currencyCode !== d.currencyCode ||
        !form.active ||
        form.validFrom ||
        form.validTo ||
        form.customerId ||
        (form.description || '').trim() ||
        form.defaultDiscountPercent !== '',
    )
  }, [form])

  const applyCollapseCreate = useCallback(() => {
    setNewExpanded(false)
    resetCreateForms()
  }, [resetCreateForms])

  const requestToggleNew = useCallback(() => {
    if (newExpanded && createDirty) {
      setPendingNav({ type: 'collapse' })
      setDiscardOpen(true)
      return
    }
    if (newExpanded) applyCollapseCreate()
    else setNewExpanded(true)
  }, [newExpanded, createDirty, applyCollapseCreate])

  const keyboardIds = useMemo(() => items.map((i) => i.id), [items])

  useDeskKeyboard({
    enabled: !newExpanded,
    itemIds: keyboardIds,
    selectedId,
    setSelectedId: (id) => {
      setSelectedId(id)
      if (id) {
        deepLinkOpened.current = null
      }
    },
    onToggleNew: requestToggleNew,
  })

  const confirmDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
    applyCollapseCreate()
    void pendingNav
  }, [pendingNav, applyCollapseCreate])

  const cancelDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
  }, [])

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
      resetCreateForms()
      setNewExpanded(false)
      await load()
      const full = await salesApi.pricelists.get(pl.id)
      hydrateFromFull(full)
      const { lines: _lines, ...seed } = full
      openView(PKG, RESOURCE, seed)
      deepLinkOpened.current = String(pl.id)
      setSelectedId(String(pl.id))
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const addLineDraft = useCallback(() => {
    const draft = lineDraft
    if (!draft.name.trim() || draft.unitPrice === '') {
      setError(
        t('pricing.lineNeedNamePrice', {
          defaultValue: 'Each line needs a name and a unit price.',
        }),
      )
      return
    }
    setLines((prev) => [...prev, { ...draft, name: draft.name.trim() }])
    setLineDraft(emptyLine())
    setError(null)
  }, [lineDraft, t])

  const onSaveLines = useCallback(async () => {
    const pl = selectedRef.current
    if (!pl?.id) return
    setBusy('lines')
    setError(null)
    setInfo(null)
    try {
      const payload = linesRef.current.map((ln) => {
        const row = {
          name: ln.name,
          unitPrice: Number(ln.unitPrice),
          minQuantity: ln.minQty !== '' ? Number(ln.minQty) : 1,
        }
        if (ln.catalogueItemId) row.catalogueItemId = Number(ln.catalogueItemId)
        return row
      })
      const updated = await salesApi.pricelists.setLines(pl.id, payload)
      setInfo(t('pricing.linesSaved', { defaultValue: 'Lines saved.' }))
      hydrateFromFull(updated)
      updateViewingRecord({ ...updated, lines: undefined })
      await loadRef.current()
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }, [hydrateFromFull, t, updateViewingRecord])

  const onDelete = useCallback(
    async (record) => {
      const label =
        record?.name || t('view.untitled', { ns: 'common', defaultValue: 'this record' })
      if (!window.confirm(t('actions.confirmDelete', { ns: 'common', label }))) return
      setError(null)
      try {
        await domainsApi.remove(PKG, RESOURCE, record.id)
        setInfo(
          t('toast.namedDeleted', {
            ns: 'common',
            name: label,
            defaultValue: `Deleted ${label}`,
          }),
        )
        if (selectedRef.current && String(selectedRef.current.id) === String(record.id)) {
          setSelected(null)
          setLines([])
        }
        if (String(selectedId) === String(record.id)) {
          setSelectedId(null)
          deepLinkOpened.current = null
        }
        await loadRef.current()
      } catch (err) {
        setError(err.message || String(err))
      }
    },
    [selectedId, t],
  )

  const renderRowActions = useCallback(
    (record) => {
      if (!record?.id && record?.id !== 0) return null
      return (
        <RowActionSlots
          view={
            <button
              type="button"
              className={ROW_ACTION_CLASS}
              title={t('view.open', { ns: 'common' })}
              aria-label={t('view.open', { ns: 'common' })}
              onClick={(e) => {
                e.stopPropagation()
                openPriceListView(record)
              }}
            >
              <Eye size={14} />
            </button>
          }
          edit={
            <button
              type="button"
              className={ROW_ACTION_CLASS}
              title={t('actions.edit', { ns: 'common' })}
              aria-label={t('actions.edit', { ns: 'common' })}
              onClick={(e) => {
                e.stopPropagation()
                openEdit(PKG, RESOURCE, record)
              }}
            >
              <Pencil size={14} />
            </button>
          }
          delete={
            <button
              type="button"
              className={ROW_ACTION_CLASS}
              title={t('actions.delete', { ns: 'common' })}
              aria-label={t('actions.delete', { ns: 'common' })}
              onClick={(e) => {
                e.stopPropagation()
                onDelete(record)
              }}
            >
              <Trash2 size={14} />
            </button>
          }
        />
      )
    },
    [onDelete, openEdit, openPriceListView, t],
  )

  const fields = (src, setSrc, prefix) => (
    <>
      <label>
        {t('pricing.fieldName', { defaultValue: 'Name' })} *
        <input
          required
          value={src.name}
          disabled={!!busy}
          onChange={(e) => setSrc((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          {t('pricing.fieldCode', { defaultValue: 'Code' })}
          <input
            value={src.code}
            disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, code: e.target.value }))}
          />
        </label>
        <label>
          {t('pricing.fieldKind', { defaultValue: 'Kind' })}
          <select
            value={src.listKind}
            disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, listKind: e.target.value }))}
          >
            {LIST_KINDS.map((k) => (
              <option key={k} value={k}>
                {k}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="form-row-2">
        <label>
          {t('pricing.fieldCurrency', { defaultValue: 'Currency' })}
          <CurrencySelect
            id={`${prefix}-ccy`}
            value={src.currencyCode}
            disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, currencyCode: e.target.value }))}
          />
        </label>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 22 }}>
          <input
            type="checkbox"
            checked={!!src.active}
            disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, active: e.target.checked }))}
          />
          {t('pricing.fieldActive', { defaultValue: 'Active' })}
        </label>
      </div>
      {(src.listKind === 'CUSTOMER' || src.customerId) && (
        <label>
          {t('pricing.fieldCustomer', { defaultValue: 'Customer' })}
          {src.listKind === 'CUSTOMER' ? ' *' : ''}
          <CustomerSelect
            id={`${prefix}-cust`}
            value={src.customerId}
            disabled={!!busy}
            onChange={(id) => setSrc((f) => ({ ...f, customerId: id || '' }))}
          />
        </label>
      )}
      <div className="form-row-2">
        <label>
          {t('pricing.fieldValidFrom', { defaultValue: 'Valid from' })}
          <input
            type="date"
            value={src.validFrom}
            disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, validFrom: e.target.value }))}
          />
        </label>
        <label>
          {t('pricing.fieldValidTo', { defaultValue: 'Valid to' })}
          <input
            type="date"
            value={src.validTo}
            disabled={!!busy}
            onChange={(e) => setSrc((f) => ({ ...f, validTo: e.target.value }))}
          />
        </label>
      </div>
      <label>
        {t('pricing.fieldDiscount', { defaultValue: 'Default discount %' })}
        <input
          type="number"
          step="0.01"
          min="0"
          value={src.defaultDiscountPercent}
          disabled={!!busy}
          onChange={(e) => setSrc((f) => ({ ...f, defaultDiscountPercent: e.target.value }))}
        />
      </label>
      <label>
        {t('pricing.fieldDescription', { defaultValue: 'Description' })}
        <textarea
          rows={2}
          value={src.description}
          disabled={!!busy}
          onChange={(e) => setSrc((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
    </>
  )

  getBelowFieldsRef.current = ({ resource, record }) => {
    if (resource !== RESOURCE || !record) return null
    const live = selectedRef.current
    const pl = live && String(live.id) === String(record.id) ? live : record
    const currentLines = live && String(live.id) === String(record.id) ? lines : linesFromFull(pl)
    const isBusy = !!busyRef.current
    return (
      <>
        <h3 className="section-label" style={{ marginTop: '0.5rem' }}>
          {t('pricing.lines', { defaultValue: 'Lines' })}
        </h3>
        {currentLines.length === 0 ? (
          <p className="muted">
            {t('pricing.linesEmpty', {
              defaultValue: 'No lines yet. Add catalogue items or named prices below.',
            })}
          </p>
        ) : (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t('pricing.colLineName', { defaultValue: 'Name' })}</th>
                  <th>{t('pricing.colUnitPrice', { defaultValue: 'Unit price' })}</th>
                  <th>{t('pricing.colMinQty', { defaultValue: 'Min qty' })}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {currentLines.map((ln, i) => (
                  <tr key={`${ln.name}-${i}`}>
                    <td>{ln.name || ln.sku || '—'}</td>
                    <td>{ln.unitPrice}</td>
                    <td>{ln.minQty}</td>
                    <td>
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={isBusy}
                        onClick={() => setLines((prev) => prev.filter((_, j) => j !== i))}
                      >
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
            <CatalogueItemSelect
              id="pl-line-item"
              nature="GOODS"
              forChannel="sale"
              value={lineDraft.catalogueItemId}
              disabled={isBusy}
              onChange={(id, item) =>
                setLineDraft((d) => ({
                  ...d,
                  catalogueItemId: id || '',
                  name: item?.name || (id ? d.name : ''),
                  unitPrice: item?.listPrice != null ? String(item.listPrice) : id ? d.unitPrice : '',
                }))
              }
            />
          </label>
          <div className="form-row-2">
            <label>
              {t('pricing.fieldLineName', { defaultValue: 'Line name' })} *
              <input
                value={lineDraft.name}
                disabled={isBusy}
                onChange={(e) => setLineDraft((d) => ({ ...d, name: e.target.value }))}
              />
            </label>
            <label>
              {t('pricing.fieldUnitPrice', { defaultValue: 'Unit price' })} *
              <input
                type="number"
                step="0.01"
                min="0"
                value={lineDraft.unitPrice}
                disabled={isBusy}
                onChange={(e) => setLineDraft((d) => ({ ...d, unitPrice: e.target.value }))}
              />
            </label>
          </div>
          <label>
            {t('pricing.fieldMinQty', { defaultValue: 'Min quantity' })}
            <input
              type="number"
              step="0.01"
              min="0"
              value={lineDraft.minQty}
              disabled={isBusy}
              onChange={(e) => setLineDraft((d) => ({ ...d, minQty: e.target.value }))}
            />
          </label>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button type="button" className="btn" onClick={addLineDraft} disabled={isBusy}>
              <Plus size={16} /> {t('pricing.addLine', { defaultValue: 'Add line' })}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={onSaveLines}
              disabled={isBusy}
            >
              <Save size={16} /> {t('pricing.saveLines', { defaultValue: 'Save lines' })}
            </button>
          </div>
        </div>
      </>
    )
  }

  const helpContext = useMemo(() => {
    const base = { desk: 'sales-pricing', tab: 'lists' }
    if (selected) {
      return {
        ...base,
        resource: 'PriceList',
        objectId: selected.id,
        status: selected.active === false ? 'INACTIVE' : 'ACTIVE',
      }
    }
    return base
  }, [selected])

  const newPanel = (
    <form className="form-grid" onSubmit={onCreate}>
      {fields(form, setForm, 'new')}
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('pricing.create', { defaultValue: 'Create list' })}
      </button>
    </form>
  )

  const list = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('pricing.list', { defaultValue: 'Lists' })}
        {!loading && (
          <span className="muted" style={{ fontWeight: 400, marginLeft: 8 }}>
            ({total})
          </span>
        )}
      </h2>
      {loading && (
        <p className="muted">{t('pricing.loading', { defaultValue: 'Loading…' })}</p>
      )}
      <ResponsiveDataList
        items={items}
        empty={t('pricing.empty', {
          defaultValue: 'No price lists yet. Create one with New.',
        })}
        loading={loading}
        onSelect={(row) => openPriceListView(row)}
        fields={[
          {
            id: 'name',
            primary: true,
            render: (row) => entityDisplayLabel(row, { fallback: '—' }),
          },
          {
            id: 'kind',
            secondary: true,
            render: (row) => row.listKind || '—',
          },
          {
            id: 'active',
            secondary: true,
            render: (row) =>
              row.active === false
                ? t('pricing.no', { defaultValue: 'no' })
                : t('pricing.yes', { defaultValue: 'yes' }),
          },
          {
            id: 'lines',
            meta: true,
            render: (row) => row.lineCount ?? '—',
          },
        ]}
        actions={(row) => renderRowActions(row)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('pricing.colName', { defaultValue: 'Name' })}</th>
                <th>{t('pricing.colKind', { defaultValue: 'Kind' })}</th>
                <th>{t('pricing.colActive', { defaultValue: 'Active' })}</th>
                <th>{t('pricing.colLines', { defaultValue: 'Lines' })}</th>
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">
                    {t('pricing.empty', {
                      defaultValue: 'No price lists yet. Create one with New.',
                    })}
                  </td>
                </tr>
              )}
              {items.map((row) => (
                <tr
                  key={row.id}
                  className="row-click"
                  onClick={() => openPriceListView(row)}
                >
                  <td>{entityDisplayLabel(row, { fallback: '—' })}</td>
                  <td>{row.listKind || '—'}</td>
                  <td>
                    {row.active === false
                      ? t('pricing.no', { defaultValue: 'no' })
                      : t('pricing.yes', { defaultValue: 'yes' })}
                  </td>
                  <td>{row.lineCount ?? '—'}</td>
                  <td onClick={(e) => e.stopPropagation()}>{renderRowActions(row)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
    </>
  )

  return (
    <div className="page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      <div className="toolbar" style={{ marginBottom: '1.5rem' }}>
        <button type="button" className="btn" onClick={load} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
        <Link className="btn btn-secondary" to="/sales">
          {t('pricing.backSales', { defaultValue: '← Sales desk' })}
        </Link>
      </div>

      <DeskShell
        layout="single"
        title={
          <>
            <Tags size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('pricing.title', { defaultValue: 'Price lists' })}
          </>
        }
        subtitle={null}
        newLabel={t('pricing.newList', { defaultValue: 'New price list' })}
        newExpanded={newExpanded}
        onToggleNew={requestToggleNew}
        newPanel={newPanel}
        list={list}
        hasSelection={false}
        detail={null}
        helpContext={helpContext}
        narrowSheetOpen={false}
      />

      <DeskNewFab
        label={t('pricing.newList', { defaultValue: 'New price list' })}
        onClick={() => !newExpanded && requestToggleNew()}
      />

      <ConfirmDialog
        open={discardOpen}
        title={t('desk.discardCreateTitle', {
          ns: 'common',
          defaultValue: 'Discard create form?',
        })}
        body={t('desk.discardCreateBody', {
          ns: 'common',
          defaultValue: 'You have unsaved changes in the create form. Discard them?',
        })}
        confirmLabel={t('desk.discard', { ns: 'common', defaultValue: 'Discard' })}
        tone="danger"
        onConfirm={confirmDiscard}
        onCancel={cancelDiscard}
      />
    </div>
  )
}
