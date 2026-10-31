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
import { useTranslation } from 'react-i18next'
import { Eye, Pencil, Plus, RefreshCw, Trash2, Warehouse } from 'lucide-react'
import { domainsApi, meApi } from '../api/client'
import MasterEntityFields from '../forms/MasterEntityFields'
import { getMasterFormSpec } from '../forms/masterFormSpecs'
import useRecordView from '../hooks/useRecordView'
import ResponsiveDataList from '../components/ResponsiveDataList'
import DeskNewFab from '../components/DeskNewFab'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
import DeskShell from '../components/desk/DeskShell'
import ConfirmDialog from '../components/ConfirmDialog'
import RowActionSlots from '../components/RowActionSlots'
import useDeskSelection from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'

const assetMasterSpec = getMasterFormSpec('asset', 'PhysicalAsset')

const PKG = 'asset'
const RESOURCE = 'PhysicalAsset'
const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'

const TAB_DEFS = [{ id: 'assets', labelKey: 'asset.tabs.assets', icon: Warehouse }]

function statusBadgeClass(s) {
  if (s === 'IN_SERVICE') return 'badge badge-ok'
  if (s === 'MAINTENANCE') return 'badge badge-warn'
  if (s === 'RETIRED') return 'badge badge-muted'
  return 'badge badge-draft'
}

/**
 * Asset desk — physical plant/vehicles/tools (Buyers UI 2.0 / ADR-050).
 */
export default function AssetDesk() {
  const { t } = useTranslation(['desks', 'common'])
  const loadAllRef = useRef(async () => {})
  const deepLinkOpened = useRef(null)

  const tabs = useMemo(
    () => TAB_DEFS.map((d) => ({ id: d.id, label: t(d.labelKey), icon: d.icon })),
    [t],
  )
  const deskKey = 'asset'
  const defaultTab = 'assets'

  const [tab, setTab] = useState(() => {
    if (typeof window === 'undefined') return defaultTab
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    if (urlTab === 'assets') return urlTab
    try {
      const raw = window.localStorage.getItem(`steakos.desk.default.${deskKey}`)
      const parsed = raw ? JSON.parse(raw) : null
      if (parsed?.tab === 'assets') return String(parsed.tab)
    } catch {
      /* ignore */
    }
    return defaultTab
  })
  const [selectedId, setSelectedId] = useState(() => {
    if (typeof window === 'undefined') return null
    const id = new URLSearchParams(window.location.search).get('id')
    return id || null
  })
  const [newExpanded, setNewExpanded] = useState(false)
  const [companyId, setCompanyId] = useState('default')
  const [discardOpen, setDiscardOpen] = useState(false)
  const [pendingNav, setPendingNav] = useState(null)

  const { loadMemory, saveMemory } = useDeskMemory({ deskKey, companyId })

  useDeskSelection({
    deskKey,
    defaultTab,
    tab,
    setTab,
    selectedId,
    setSelectedId,
  })

  useEffect(() => {
    saveMemory({ tab })
  }, [tab, saveMemory])

  useEffect(() => {
    if (companyId === 'default') return
    const mem = loadMemory()
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    if (!urlTab && mem?.tab && mem.tab !== tab && mem.tab === 'assets') {
      setTab(mem.tab)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyId, deskKey])

  useEffect(() => {
    let cancelled = false
    meApi
      .get()
      .then((me) => {
        if (cancelled) return
        const cid = me?.activeCompanyId ?? me?.activeCompany?.id
        if (cid != null && cid !== '') setCompanyId(String(cid))
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  const [assets, setAssets] = useState([])
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [assetForm, setAssetForm] = useState(() => assetMasterSpec.empty())
  const [assetPage, setAssetPage] = useState({ max: 25, offset: 0, total: 0 })
  const [assetSort, setAssetSort] = useState('id')
  const [assetOrder, setAssetOrder] = useState('desc')
  const onAssetSortChange = useCallback(({ sort: s, order: o }) => {
    setAssetSort(s || 'id')
    setAssetOrder(o === 'asc' ? 'asc' : 'desc')
    setAssetPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const ASSET_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'status', label: 'Status' },
    ],
    [],
  )

  const onMasterField = (e) => {
    const { name, value, type, checked } = e.target
    setAssetForm((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
  }

  const { viewModal, openView, openEdit } = useRecordView({
    modalEdit: true,
    canModalEdit: () => true,
    onSaved: () => {
      loadAllRef.current().catch(() => {})
    },
  })

  const loadAll = useCallback(async () => {
    const res = await domainsApi.list(PKG, RESOURCE, {
      max: assetPage.max,
      offset: assetPage.offset,
      sort: assetSort,
      order: assetOrder,
    })
    const items = res.items || res || []
    setAssets(Array.isArray(items) ? items : [])
    setAssetPage((pg) => ({
      ...pg,
      max: res.max ?? pg.max,
      offset: res.offset ?? pg.offset,
      total: res.total ?? (Array.isArray(items) ? items.length : 0),
    }))
  }, [assetPage.max, assetPage.offset, assetSort, assetOrder])

  loadAllRef.current = loadAll

  useEffect(() => {
    setLoading(true)
    loadAll()
      .catch((e) => setError(e.message || String(e)))
      .finally(() => setLoading(false))
  }, [loadAll])

  const refresh = () => {
    setLoading(true)
    setError(null)
    loadAll()
      .catch((e) => setError(e.message || String(e)))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      if (!selectedId) return
      if (deepLinkOpened.current === String(selectedId)) return
      try {
        const full = await domainsApi.get(PKG, RESOURCE, selectedId)
        if (cancelled || !full) return
        openView(PKG, RESOURCE, full)
        deepLinkOpened.current = String(selectedId)
      } catch (e) {
        if (!cancelled) setError(e.message || String(e))
      }
    }
    run()
    return () => {
      cancelled = true
    }
  }, [selectedId, openView])

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
        await loadAllRef.current()
      } catch (err) {
        setError(err.message || String(err))
      }
    },
    [t],
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
                openView(PKG, RESOURCE, record)
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
    [onDelete, openEdit, openView, t],
  )

  const resetCreateForm = useCallback(() => {
    setAssetForm(assetMasterSpec.empty())
  }, [])

  const createDirty = useMemo(() => {
    const empty = assetMasterSpec.empty()
    return Boolean(
      (assetForm.name || '').trim() ||
        (assetForm.description || '').trim() ||
        (assetForm.assetTag || '').trim() ||
        (assetForm.location || '').trim() ||
        (assetForm.ownerName || '').trim() ||
        assetForm.status !== empty.status ||
        assetForm.itManaged !== empty.itManaged ||
        (assetForm.configItemId || '').toString().trim() ||
        assetForm.financialDepreciation !== empty.financialDepreciation ||
        assetForm.requiresMaintenance !== empty.requiresMaintenance,
    )
  }, [assetForm])

  const applyCollapseCreate = useCallback(() => {
    setNewExpanded(false)
    resetCreateForm()
  }, [resetCreateForm])

  const requestToggleNew = useCallback(() => {
    if (newExpanded && createDirty) {
      setPendingNav({ type: 'collapse' })
      setDiscardOpen(true)
      return
    }
    if (newExpanded) applyCollapseCreate()
    else setNewExpanded(true)
  }, [newExpanded, createDirty, applyCollapseCreate])

  const requestTabChange = useCallback(
    (nextTab) => {
      if (nextTab === tab) return
      if (newExpanded && createDirty) {
        setPendingNav({ type: 'tab', tab: nextTab })
        setDiscardOpen(true)
        return
      }
      setTab(nextTab)
      setSelectedId(null)
      setNewExpanded(false)
      resetCreateForm()
    },
    [tab, newExpanded, createDirty, resetCreateForm],
  )

  const confirmDiscard = useCallback(() => {
    const nav = pendingNav
    setDiscardOpen(false)
    setPendingNav(null)
    applyCollapseCreate()
    if (nav?.type === 'tab' && nav.tab) {
      setTab(nav.tab)
      setSelectedId(null)
    }
  }, [pendingNav, applyCollapseCreate])

  const cancelDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
  }, [])

  const onCreate = async (e) => {
    e.preventDefault()
    setBusy('create')
    setError(null)
    setInfo(null)
    try {
      const created = await domainsApi.create(PKG, RESOURCE, assetMasterSpec.toBody(assetForm))
      setInfo(
        t('toast.namedCreated', {
          ns: 'common',
          what: t('asset.register', { defaultValue: 'Physical asset' }),
          name: created?.name || assetForm.name,
        }),
      )
      resetCreateForm()
      setNewExpanded(false)
      await loadAll()
    } catch (err) {
      setError(err.message || String(err))
    } finally {
      setBusy(null)
    }
  }

  const newLabel = t('asset.newAsset', { defaultValue: 'New asset' })
  const helpContext = useMemo(() => ({ desk: 'asset', tab }), [tab])

  const newPanel = (
    <form className="form-grid" onSubmit={onCreate}>
      <MasterEntityFields
        spec={assetMasterSpec}
        form={assetForm}
        onChange={onMasterField}
        disabled={!!busy}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('asset.createAsset', { defaultValue: 'Create asset' })}
      </button>
    </form>
  )

  const list = (
    <>
      <ResponsiveDataList
        items={assets}
        empty="—"
        onSelect={(a) => openView(PKG, RESOURCE, a)}
        sortFields={ASSET_SORT_MENU}
        sort={assetSort}
        order={assetOrder}
        onSortChange={onAssetSortChange}
        fields={[
          { id: 'name', primary: true, render: (a) => a.name },
          { id: 'assetTag', secondary: true, render: (a) => a.assetTag || '—' },
          { id: 'status', meta: true, render: (a) => a.status || '—' },
        ]}
        actions={(a) => renderRowActions(a)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={assetSort}
                  order={assetOrder}
                  onSortChange={onAssetSortChange}
                />
                <th>{t('wb.tag', { ns: 'common', defaultValue: 'Tag' })}</th>
                <th>{t('wb.location', { ns: 'common', defaultValue: 'Location' })}</th>
                <ListSortHeader
                  field="status"
                  label={t('wb.status', { ns: 'common', defaultValue: 'Status' })}
                  sort={assetSort}
                  order={assetOrder}
                  onSortChange={onAssetSortChange}
                />
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {assets.map((a) => (
                <tr
                  key={a.id}
                  className="row-click"
                  onClick={() => openView(PKG, RESOURCE, a)}
                >
                  <td>{a.name}</td>
                  <td>{a.assetTag || '—'}</td>
                  <td>{a.location || '—'}</td>
                  <td>
                    <span className={statusBadgeClass(a.status)}>{a.status || '—'}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>{renderRowActions(a)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={assetPage.total}
        max={assetPage.max}
        offset={assetPage.offset}
        disabled={loading}
        onOffset={(o) => setAssetPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setAssetPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  return (
    <div className="page asset-desk-page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      <div className="toolbar" style={{ marginBottom: '1.5rem' }}>
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
      </div>

      <DeskShell
        layout="single"
        title={
          <>
            <Warehouse size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('asset.title', { defaultValue: 'Assets' })}
          </>
        }
        subtitle={null}
        tabs={tabs}
        activeTab={tab}
        onTabChange={requestTabChange}
        newLabel={newLabel}
        newExpanded={newExpanded}
        onToggleNew={requestToggleNew}
        newPanel={newPanel}
        list={list}
        hasSelection={false}
        emptyTitle={t('desk.emptyTitle', { ns: 'common' })}
        emptyBody={t('desk.emptyBodyMasters', {
          ns: 'common',
          defaultValue: 'Use + New to create, or View on a row for the full record.',
        })}
        detail={null}
        detailFooter={null}
        processSlot={null}
        helpContext={helpContext}
        narrowSheetOpen={false}
      />

      <DeskNewFab label={newLabel} onClick={() => !newExpanded && requestToggleNew()} />

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
