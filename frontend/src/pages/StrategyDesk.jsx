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
import { Target, Users, Plus, Eye, Pencil, Trash2, RefreshCw } from 'lucide-react'
import { domainsApi, meApi } from '../api/client'
import MasterEntityFields from '../forms/MasterEntityFields'
import { getMasterFormSpec } from '../forms/masterFormSpecs'
import ResponsiveDataList from '../components/ResponsiveDataList'
import DeskNewFab from '../components/DeskNewFab'
import ListPager from '../components/ListPager'
import DeskShell from '../components/desk/DeskShell'
import ConfirmDialog from '../components/ConfirmDialog'
import RowActionSlots from '../components/RowActionSlots'
import useRecordView from '../hooks/useRecordView'
import useDeskSelection from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'

const competitorMasterSpec = getMasterFormSpec('strategy', 'Competitor')
const strategyItemMasterSpec = getMasterFormSpec('strategy', 'StrategyItem')

const TAB_DEFS = [
  { id: 'competitors', labelKey: 'strategy.tabs.competitors', icon: Users },
  { id: 'topics', labelKey: 'strategy.tabs.topics', icon: Target },
]

const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'

function statusBadgeClass(s) {
  if (s === 'ACTIVE') return 'badge badge-ok'
  if (s === 'INACTIVE' || s === 'ARCHIVED') return 'badge badge-muted'
  if (s === 'WATCH') return 'badge badge-warn'
  return 'badge badge-draft'
}

/**
 * Strategy desk — Buyers UI 2.0 (ADR-050 / PDR-034): single-column + modal registers.
 */
export default function StrategyDesk() {
  const { t } = useTranslation(['desks', 'common'])
  const loadAllRef = useRef(async () => {})
  const deepLinkOpened = useRef(null)

  const tabs = useMemo(
    () => TAB_DEFS.map((d) => ({ id: d.id, label: t(d.labelKey), icon: d.icon })),
    [t],
  )
  const deskKey = 'strategy'
  const defaultTab = 'competitors'

  const [tab, setTab] = useState(() => {
    if (typeof window === 'undefined') return defaultTab
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    if (urlTab === 'competitors' || urlTab === 'topics') return urlTab
    try {
      const raw = window.localStorage.getItem(`steakos.desk.default.${deskKey}`)
      const parsed = raw ? JSON.parse(raw) : null
      if (parsed?.tab === 'competitors' || parsed?.tab === 'topics') return String(parsed.tab)
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
    if (!urlTab && mem?.tab && mem.tab !== tab && (mem.tab === 'competitors' || mem.tab === 'topics')) {
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

  const [competitors, setCompetitors] = useState([])
  const [topics, setTopics] = useState([])
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)

  const [competitorForm, setCompetitorForm] = useState(() => competitorMasterSpec.empty())
  const [topicForm, setTopicForm] = useState(() => strategyItemMasterSpec.empty())
  const [competitorPage, setCompetitorPage] = useState({ max: 25, offset: 0, total: 0 })
  const [topicPage, setTopicPage] = useState({ max: 25, offset: 0, total: 0 })

  const onMasterField =
    (setter) =>
    (e) => {
      const { name, value, type, checked } = e.target
      setter((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
    }

  const { viewModal, openView, openEdit } = useRecordView({
    modalEdit: true,
    canModalEdit: () => true,
    onSaved: () => {
      loadAllRef.current().catch(() => {})
    },
  })

  const loadAll = useCallback(async () => {
    const [c, items] = await Promise.all([
      domainsApi.list('strategy', 'Competitor', {
        max: competitorPage.max,
        offset: competitorPage.offset,
      }),
      domainsApi.list('strategy', 'StrategyItem', {
        max: topicPage.max,
        offset: topicPage.offset,
      }),
    ])
    setCompetitors(c.items || [])
    setTopics(items.items || [])
    setCompetitorPage((pg) => ({
      ...pg,
      max: c.max ?? pg.max,
      offset: c.offset ?? pg.offset,
      total: c.total ?? (c.items || []).length,
    }))
    setTopicPage((pg) => ({
      ...pg,
      max: items.max ?? pg.max,
      offset: items.offset ?? pg.offset,
      total: items.total ?? (items.items || []).length,
    }))
  }, [
    competitorPage.max,
    competitorPage.offset,
    topicPage.max,
    topicPage.offset,
  ])

  loadAllRef.current = loadAll

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      await loadAll()
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [loadAll])

  useEffect(() => {
    setLoading(true)
    loadAll()
      .catch((e) => setError(e.message || String(e)))
      .finally(() => setLoading(false))
  }, [loadAll])

  // Deep-link ?id= → open view modal once for the active tab resource.
  useEffect(() => {
    let cancelled = false
    const run = async () => {
      if (!selectedId) return
      if (deepLinkOpened.current === String(selectedId)) return
      const resource = tab === 'topics' ? 'StrategyItem' : 'Competitor'
      const list = tab === 'topics' ? topics : competitors
      let record = list.find((r) => String(r.id) === String(selectedId))
      try {
        if (!record) {
          record = await domainsApi.get('strategy', resource, selectedId)
        }
        if (!cancelled && record) {
          openView('strategy', resource, record)
          deepLinkOpened.current = String(selectedId)
        }
      } catch (e) {
        if (!cancelled) setError(e.message || String(e))
      }
    }
    run()
    return () => {
      cancelled = true
    }
  }, [selectedId, tab, competitors, topics, openView])

  const onDelete = useCallback(
    async (resource, record) => {
      const label =
        record?.name || t('view.untitled', { ns: 'common', defaultValue: 'this record' })
      if (!window.confirm(t('actions.confirmDelete', { ns: 'common', label }))) return
      setError(null)
      try {
        await domainsApi.remove('strategy', resource, record.id)
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
    (resource, record) => {
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
                openView('strategy', resource, record)
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
                openEdit('strategy', resource, record)
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
                onDelete(resource, record)
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

  const resetCreateForms = useCallback(() => {
    setCompetitorForm(competitorMasterSpec.empty())
    setTopicForm(strategyItemMasterSpec.empty())
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'competitors') {
      return Boolean((competitorForm.name || '').trim() || (competitorForm.description || '').trim())
    }
    if (tab === 'topics') {
      return Boolean((topicForm.name || '').trim() || (topicForm.description || '').trim())
    }
    return false
  }, [tab, competitorForm, topicForm])

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
      resetCreateForms()
    },
    [tab, newExpanded, createDirty, resetCreateForms],
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

  const onCreateCompetitor = async (e) => {
    e.preventDefault()
    setBusy('c')
    setError(null)
    try {
      await domainsApi.create('strategy', 'Competitor', competitorMasterSpec.toBody(competitorForm))
      setInfo(
        t('toast.namedCreated', {
          ns: 'common',
          what: t('strategy.competitors'),
          name: competitorForm.name,
        }),
      )
      setCompetitorForm(competitorMasterSpec.empty())
      setNewExpanded(false)
      await loadAll()
    } catch (err) {
      setError(err.message || String(err))
    } finally {
      setBusy(null)
    }
  }

  const onCreateTopic = async (e) => {
    e.preventDefault()
    setBusy('t')
    setError(null)
    try {
      await domainsApi.create(
        'strategy',
        'StrategyItem',
        strategyItemMasterSpec.toBody(topicForm),
      )
      setInfo(
        t('toast.namedCreated', {
          ns: 'common',
          what: t('strategy.items'),
          name: topicForm.name,
        }),
      )
      setTopicForm(strategyItemMasterSpec.empty())
      setNewExpanded(false)
      await loadAll()
    } catch (err) {
      setError(err.message || String(err))
    } finally {
      setBusy(null)
    }
  }

  const newLabel =
    tab === 'competitors'
      ? t('strategy.newCompetitor', { defaultValue: 'New competitor' })
      : t('strategy.newTopic', { defaultValue: 'New topic' })

  const helpContext = useMemo(() => ({ desk: deskKey, tab }), [tab])

  const competitorCreatePanel = (
    <form className="form-grid" onSubmit={onCreateCompetitor}>
      <MasterEntityFields
        spec={competitorMasterSpec}
        form={competitorForm}
        onChange={onMasterField(setCompetitorForm)}
        disabled={!!busy}
        showDescription={false}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('strategy.createCompetitor', { defaultValue: 'Create competitor' })}
      </button>
    </form>
  )

  const topicCreatePanel = (
    <form className="form-grid" onSubmit={onCreateTopic}>
      <MasterEntityFields
        spec={strategyItemMasterSpec}
        form={topicForm}
        onChange={onMasterField(setTopicForm)}
        disabled={!!busy}
        showDescription={false}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('strategy.createTopic', { defaultValue: 'Create topic' })}
      </button>
    </form>
  )

  const newPanel = tab === 'competitors' ? competitorCreatePanel : topicCreatePanel

  const competitorsList = (
    <>
      <ResponsiveDataList
        items={competitors}
        empty="—"
        onSelect={(v) => openView('strategy', 'Competitor', v)}
        fields={[
          { id: 'name', primary: true, render: (v) => v.name },
          { id: 'status', secondary: true, render: (v) => v.status || '—' },
        ]}
        actions={(v) => renderRowActions('Competitor', v)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.name', { ns: 'common' })}</th>
                <th>{t('wb.status', { ns: 'common' })}</th>
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {competitors.map((v) => (
                <tr
                  key={v.id}
                  className="row-click"
                  onClick={() => openView('strategy', 'Competitor', v)}
                >
                  <td>{v.name}</td>
                  <td>
                    <span className={statusBadgeClass(v.status)}>{v.status || '—'}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('Competitor', v)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={competitorPage.total}
        max={competitorPage.max}
        offset={competitorPage.offset}
        disabled={loading}
        onOffset={(o) => setCompetitorPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setCompetitorPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const topicsList = (
    <>
      <ResponsiveDataList
        items={topics}
        empty="—"
        onSelect={(v) => openView('strategy', 'StrategyItem', v)}
        fields={[
          { id: 'name', primary: true, render: (v) => v.name },
          { id: 'itemKind', secondary: true, render: (v) => v.itemKind || '—' },
          { id: 'status', meta: true, render: (v) => v.status || '—' },
        ]}
        actions={(v) => renderRowActions('StrategyItem', v)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.name', { ns: 'common' })}</th>
                <th>{t('wb.kind', { ns: 'common' })}</th>
                <th>{t('wb.status', { ns: 'common' })}</th>
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {topics.map((v) => (
                <tr
                  key={v.id}
                  className="row-click"
                  onClick={() => openView('strategy', 'StrategyItem', v)}
                >
                  <td>{v.name}</td>
                  <td>{v.itemKind || '—'}</td>
                  <td>
                    <span className={statusBadgeClass(v.status)}>{v.status || '—'}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('StrategyItem', v)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={topicPage.total}
        max={topicPage.max}
        offset={topicPage.offset}
        disabled={loading}
        onOffset={(o) => setTopicPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setTopicPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const list = tab === 'competitors' ? competitorsList : topicsList

  return (
    <div className="page strategy-desk-page">
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
            <Target size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('strategy.title', { defaultValue: 'Strategy' })}
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
