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
import { Eye, FlaskConical, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react'
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
import ContentRail from '../components/ContentRail'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import useDeskSelection from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'

const projectMasterSpec = getMasterFormSpec('development', 'DevelopmentProject')

const PKG = 'development'
const RESOURCE = 'DevelopmentProject'
const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'

const TAB_DEFS = [
  { id: 'projects', labelKey: 'development.tabs.projects', icon: FlaskConical },
]

/**
 * Development desk — NPD / revision projects (Buyers UI 2.0 / ADR-050).
 */
export default function DevelopmentDesk() {
  const { t } = useTranslation(['desks', 'common'])
  const loadAllRef = useRef(async () => {})
  const deepLinkOpened = useRef(null)

  const tabs = useMemo(
    () => TAB_DEFS.map((d) => ({ id: d.id, label: t(d.labelKey), icon: d.icon })),
    [t],
  )
  const deskKey = 'development'
  const defaultTab = 'projects'

  const [tab, setTab] = useState(() => {
    if (typeof window === 'undefined') return 'projects'
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    if (urlTab) return urlTab
    try {
      const raw = window.localStorage.getItem('steakos.desk.default.development')
      const parsed = raw ? JSON.parse(raw) : null
      if (parsed?.tab) return String(parsed.tab)
    } catch {
      /* ignore */
    }
    return 'projects'
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
    if (!urlTab && mem?.tab && mem.tab !== tab) {
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

  const [projects, setProjects] = useState([])
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [projectForm, setProjectForm] = useState(() => projectMasterSpec.empty())
  const [pendingProjectContent, setPendingProjectContent] = useState([])
  const [projectPage, setProjectPage] = useState({ max: 25, offset: 0, total: 0 })
  const [projectSort, setProjectSort] = useState('id')
  const [projectOrder, setProjectOrder] = useState('desc')
  const getBelowFieldsRef = useRef(null)
  const onProjectSortChange = useCallback(({ sort: s, order: o }) => {
    setProjectSort(s || 'id')
    setProjectOrder(o === 'asc' ? 'asc' : 'desc')
    setProjectPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const PROJECT_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'status', label: 'Status' },
    ],
    [],
  )

  const onMasterField = (e) => {
    const { name, value, type, checked } = e.target
    setProjectForm((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
  }

  const { viewModal, openView, openEdit } = useRecordView({
    modalEdit: true,
    canModalEdit: () => true,
    onSaved: () => {
      loadAllRef.current().catch(() => {})
    },
    getBelowFields: (ctx) => getBelowFieldsRef.current?.(ctx) ?? null,
  })

  getBelowFieldsRef.current = ({ resource, record }) => {
    if (resource !== RESOURCE || !record?.id) return null
    return (
      <details className="content-rail-collapse" style={{ marginTop: '1.25rem' }}>
        <summary className="btn btn-ghost" style={{ cursor: 'pointer', listStyle: 'inside' }}>
          {t('desk.attachmentsNotes', { ns: 'common', defaultValue: 'Attachments & notes' })}
        </summary>
        <ContentRail package={PKG} resource={RESOURCE} id={record.id} />
      </details>
    )
  }

  const loadAll = useCallback(async () => {
    const res = await domainsApi.list(PKG, RESOURCE, {
      max: projectPage.max,
      offset: projectPage.offset,
      sort: projectSort,
      order: projectOrder,
    })
    const items = res.items || res || []
    setProjects(Array.isArray(items) ? items : [])
    setProjectPage((pg) => ({
      ...pg,
      max: res.max ?? pg.max,
      offset: res.offset ?? pg.offset,
      total: res.total ?? (Array.isArray(items) ? items.length : 0),
    }))
  }, [projectPage.max, projectPage.offset, projectSort, projectOrder])

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
    setProjectForm(projectMasterSpec.empty())
    setPendingProjectContent([])
  }, [])

  const createDirty = useMemo(() => {
    const empty = projectMasterSpec.empty()
    return Boolean(
      (projectForm.name || '').trim() ||
        (projectForm.description || '').trim() ||
        projectForm.projectKind !== empty.projectKind ||
        projectForm.projectStatus !== empty.projectStatus ||
        (projectForm.catalogueItemId || '').toString().trim() ||
        projectForm.startDate ||
        projectForm.endDate ||
        pendingProjectContent.length,
    )
  }, [projectForm, pendingProjectContent])

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
      const created = await domainsApi.create(PKG, RESOURCE, projectMasterSpec.toBody(projectForm))
      if (pendingProjectContent.length && created?.id != null) {
        await attachPendingContent(PKG, RESOURCE, created.id, pendingProjectContent)
        setPendingProjectContent([])
      }
      setInfo(
        t('toast.namedCreated', {
          ns: 'common',
          what: t('labels.project', { ns: 'common', defaultValue: 'Project' }),
          name: created?.name || projectForm.name,
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

  const newLabel = t('development.newProject')
  const helpContext = useMemo(
    () => ({ desk: 'development', tab: 'projects' }),
    [],
  )

  const newPanel = (
    <form className="form-grid" onSubmit={onCreate}>
      <MasterEntityFields
        spec={projectMasterSpec}
        form={projectForm}
        onChange={onMasterField}
        disabled={!!busy}
      />
      <PendingContentComposer
        package={PKG}
        resource={RESOURCE}
        pending={pendingProjectContent}
        onChange={setPendingProjectContent}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('development.createProject')}
      </button>
    </form>
  )

  const list = (
    <>
      <ResponsiveDataList
        items={projects}
        empty="—"
        onSelect={(p) => openView(PKG, RESOURCE, p)}
        sortFields={PROJECT_SORT_MENU}
        sort={projectSort}
        order={projectOrder}
        onSortChange={onProjectSortChange}
        fields={[
          { id: 'name', primary: true, render: (p) => p.name },
          { id: 'kind', secondary: true, render: (p) => p.projectKind || '—' },
          { id: 'status', meta: true, render: (p) => p.projectStatus || '—' },
        ]}
        actions={(p) => renderRowActions(p)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={projectSort}
                  order={projectOrder}
                  onSortChange={onProjectSortChange}
                />
                <th>{t('wb.kind', { ns: 'common', defaultValue: 'Kind' })}</th>
                <ListSortHeader
                  field="status"
                  label={t('wb.status', { ns: 'common', defaultValue: 'Status' })}
                  sort={projectSort}
                  order={projectOrder}
                  onSortChange={onProjectSortChange}
                />
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {projects.map((p) => (
                <tr
                  key={p.id}
                  className="row-click"
                  onClick={() => openView(PKG, RESOURCE, p)}
                >
                  <td>{p.name}</td>
                  <td>{p.projectKind || '—'}</td>
                  <td>{p.projectStatus || '—'}</td>
                  <td onClick={(e) => e.stopPropagation()}>{renderRowActions(p)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={projectPage.total}
        max={projectPage.max}
        offset={projectPage.offset}
        disabled={loading}
        onOffset={(o) => setProjectPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setProjectPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  return (
    <div className="page development-desk-page">
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
            <FlaskConical size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('development.title')}
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
