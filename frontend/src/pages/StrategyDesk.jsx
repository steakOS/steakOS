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
import ListSortHeader from '../components/ListSortHeader'
import DeskShell from '../components/desk/DeskShell'
import DeskProcessSlot from '../components/desk/DeskProcessSlot'
import ConfirmDialog from '../components/ConfirmDialog'
import RowActionSlots from '../components/RowActionSlots'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
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
  const [competitorContentMap, setCompetitorContentMap] = useState({})
  const [topicContentMap, setTopicContentMap] = useState({})
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const competitorsRef = useRef([])
  const topicsRef = useRef([])

  const [competitorForm, setCompetitorForm] = useState(() => competitorMasterSpec.empty())
  const [topicForm, setTopicForm] = useState(() => strategyItemMasterSpec.empty())
  const [pendingCompetitorContent, setPendingCompetitorContent] = useState([])
  const [pendingTopicContent, setPendingTopicContent] = useState([])
  const [competitorPage, setCompetitorPage] = useState({ max: 25, offset: 0, total: 0 })
  const [topicPage, setTopicPage] = useState({ max: 25, offset: 0, total: 0 })
  const [competitorSort, setCompetitorSort] = useState('id')
  const [competitorOrder, setCompetitorOrder] = useState('desc')
  const [topicSort, setTopicSort] = useState('id')
  const [topicOrder, setTopicOrder] = useState('desc')
  const getBelowFieldsRef = useRef(null)
  const onCompetitorSortChange = useCallback(({ sort: s, order: o }) => {
    setCompetitorSort(s || 'id')
    setCompetitorOrder(o === 'asc' ? 'asc' : 'desc')
    setCompetitorPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onTopicSortChange = useCallback(({ sort: s, order: o }) => {
    setTopicSort(s || 'id')
    setTopicOrder(o === 'asc' ? 'asc' : 'desc')
    setTopicPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const COMPETITOR_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'type', label: 'Type' },
      { value: 'status', label: 'Status' },
    ],
    [],
  )
  const TOPIC_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'type', label: 'Type' },
      { value: 'status', label: 'Status' },
    ],
    [],
  )

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
    getBelowFields: (ctx) => getBelowFieldsRef.current?.(ctx) ?? null,
  })

  const collapsedContentRail = (pkg, resource, id, onChanged) => (
    <details className="content-rail-collapse" style={{ marginTop: '1.25rem' }}>
      <summary className="btn btn-ghost" style={{ cursor: 'pointer', listStyle: 'inside' }}>
        {t('desk.attachmentsNotes', { ns: 'common', defaultValue: 'Attachments & notes' })}
      </summary>
      <ContentRail package={pkg} resource={resource} id={id} onChanged={onChanged} />
    </details>
  )

  getBelowFieldsRef.current = ({ resource, record }) => {
    if (!record?.id) return null
    if (resource === 'Competitor') {
      return collapsedContentRail('strategy', 'Competitor', record.id, async () =>
        setCompetitorContentMap(
          await fetchContentMap('strategy', 'Competitor', competitorsRef.current),
        ),
      )
    }
    if (resource === 'StrategyItem') {
      return collapsedContentRail('strategy', 'StrategyItem', record.id, async () =>
        setTopicContentMap(
          await fetchContentMap('strategy', 'StrategyItem', topicsRef.current),
        ),
      )
    }
    return null
  }

  const loadAll = useCallback(async () => {
    const [c, items] = await Promise.all([
      domainsApi.list('strategy', 'Competitor', {
        max: competitorPage.max,
        offset: competitorPage.offset,
        sort: competitorSort,
        order: competitorOrder,
      }),
      domainsApi.list('strategy', 'StrategyItem', {
        max: topicPage.max,
        offset: topicPage.offset,
        sort: topicSort,
        order: topicOrder,
      }),
    ])
    const comps = c.items || []
    const tops = items.items || []
    setCompetitors(comps)
    setTopics(tops)
    competitorsRef.current = comps
    topicsRef.current = tops
    const [cMap, tMap] = await Promise.all([
      fetchContentMap('strategy', 'Competitor', comps),
      fetchContentMap('strategy', 'StrategyItem', tops),
    ])
    setCompetitorContentMap(cMap)
    setTopicContentMap(tMap)
    setCompetitorPage((pg) => ({
      ...pg,
      max: c.max ?? pg.max,
      offset: c.offset ?? pg.offset,
      total: c.total ?? comps.length,
    }))
    setTopicPage((pg) => ({
      ...pg,
      max: items.max ?? pg.max,
      offset: items.offset ?? pg.offset,
      total: items.total ?? tops.length,
    }))
  }, [
    competitorPage.max,
    competitorPage.offset,
    topicPage.max,
    topicPage.offset,
    competitorSort,
    competitorOrder,
    topicSort,
    topicOrder,
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
    setPendingCompetitorContent([])
    setPendingTopicContent([])
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'competitors') {
      return Boolean(
        (competitorForm.name || '').trim() ||
          (competitorForm.type || '').trim() ||
          (competitorForm.description || '').trim() ||
          (competitorForm.sources || '').trim() ||
          pendingCompetitorContent.length,
      )
    }
    if (tab === 'topics') {
      return Boolean(
        (topicForm.name || '').trim() ||
          (topicForm.type || '').trim() ||
          (topicForm.description || '').trim() ||
          pendingTopicContent.length,
      )
    }
    return false
  }, [tab, competitorForm, topicForm, pendingCompetitorContent, pendingTopicContent])

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
      const created = await domainsApi.create(
        'strategy',
        'Competitor',
        competitorMasterSpec.toBody(competitorForm),
      )
      if (pendingCompetitorContent.length && created?.id != null) {
        await attachPendingContent(
          'strategy',
          'Competitor',
          created.id,
          pendingCompetitorContent,
        )
        setPendingCompetitorContent([])
      }
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
      const created = await domainsApi.create(
        'strategy',
        'StrategyItem',
        strategyItemMasterSpec.toBody(topicForm),
      )
      if (pendingTopicContent.length && created?.id != null) {
        await attachPendingContent('strategy', 'StrategyItem', created.id, pendingTopicContent)
        setPendingTopicContent([])
      }
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

  const processCode = tab === 'competitors' ? 'STR_COMPETITOR_WATCH' : 'STR_STRATEGY_ITEM'
  const selectedRow = useMemo(() => {
    if (selectedId == null || selectedId === '') return null
    const list = tab === 'competitors' ? competitors : topics
    return list.find((r) => String(r.id) === String(selectedId)) || null
  }, [tab, selectedId, competitors, topics])
  const processStatus = selectedRow?.status ?? null
  const processSlot = (
    <DeskProcessSlot processCode={processCode} status={processStatus} />
  )
  const helpContext = useMemo(() => {
    const base = { desk: deskKey, tab, processCode }
    if (selectedRow) {
      return {
        ...base,
        resource: tab === 'topics' ? 'StrategyItem' : 'Competitor',
        objectId: selectedRow.id,
        status: selectedRow.status,
      }
    }
    return base
  }, [tab, processCode, selectedRow])

  const competitorCreatePanel = (
    <form className="form-grid" onSubmit={onCreateCompetitor}>
      <MasterEntityFields
        spec={competitorMasterSpec}
        form={competitorForm}
        onChange={onMasterField(setCompetitorForm)}
        disabled={!!busy}
        showDescription={false}
      />
      <PendingContentComposer
        package="strategy"
        resource="Competitor"
        pending={pendingCompetitorContent}
        onChange={setPendingCompetitorContent}
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
        showDescription
      />
      <PendingContentComposer
        package="strategy"
        resource="StrategyItem"
        pending={pendingTopicContent}
        onChange={setPendingTopicContent}
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
        sortFields={COMPETITOR_SORT_MENU}
        sort={competitorSort}
        order={competitorOrder}
        onSortChange={onCompetitorSortChange}
        fields={[
          { id: 'name', primary: true, render: (v) => v.name },
          { id: 'type', secondary: true, render: (v) => v.type || '—' },
          { id: 'status', meta: true, render: (v) => v.status || '—' },
          {
            id: 'content',
            priority: 'low',
            render: (v) => <ContentChannelIcons summary={competitorContentMap[v.id]} />,
          },
        ]}
        actions={(v) => renderRowActions('Competitor', v)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={competitorSort}
                  order={competitorOrder}
                  onSortChange={onCompetitorSortChange}
                />
                <ListSortHeader
                  field="type"
                  label={t('strategy.competitorType', { defaultValue: 'Type' })}
                  sort={competitorSort}
                  order={competitorOrder}
                  onSortChange={onCompetitorSortChange}
                />
                <ListSortHeader
                  field="status"
                  label={t('wb.status', { ns: 'common' })}
                  sort={competitorSort}
                  order={competitorOrder}
                  onSortChange={onCompetitorSortChange}
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common', defaultValue: 'Content' })}</th>
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
                  <td>{v.type || '—'}</td>
                  <td>
                    <span className={statusBadgeClass(v.status)}>{v.status || '—'}</span>
                  </td>
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                    <ContentChannelIcons summary={competitorContentMap[v.id]} />
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
        sortFields={TOPIC_SORT_MENU}
        sort={topicSort}
        order={topicOrder}
        onSortChange={onTopicSortChange}
        fields={[
          { id: 'name', primary: true, render: (v) => v.name },
          { id: 'type', secondary: true, render: (v) => v.type || '—' },
          {
            id: 'description',
            meta: true,
            render: (v) => {
              const d = (v.description || '').trim()
              if (!d) return '—'
              return d.length > 80 ? `${d.slice(0, 80)}…` : d
            },
          },
          { id: 'itemKind', meta: true, render: (v) => v.itemKind || '—' },
          { id: 'status', meta: true, render: (v) => v.status || '—' },
          {
            id: 'content',
            priority: 'low',
            render: (v) => <ContentChannelIcons summary={topicContentMap[v.id]} />,
          },
        ]}
        actions={(v) => renderRowActions('StrategyItem', v)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={topicSort}
                  order={topicOrder}
                  onSortChange={onTopicSortChange}
                />
                <ListSortHeader
                  field="type"
                  label={t('strategy.topicType', { defaultValue: 'Type' })}
                  sort={topicSort}
                  order={topicOrder}
                  onSortChange={onTopicSortChange}
                />
                <th>{t('wb.description', { ns: 'common', defaultValue: 'Description' })}</th>
                <th>{t('wb.kind', { ns: 'common' })}</th>
                <ListSortHeader
                  field="status"
                  label={t('wb.status', { ns: 'common' })}
                  sort={topicSort}
                  order={topicOrder}
                  onSortChange={onTopicSortChange}
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common', defaultValue: 'Content' })}</th>
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
                  <td>{v.type || '—'}</td>
                  <td>
                    {(() => {
                      const d = (v.description || '').trim()
                      if (!d) return '—'
                      return d.length > 80 ? `${d.slice(0, 80)}…` : d
                    })()}
                  </td>
                  <td>{v.itemKind || '—'}</td>
                  <td>
                    <span className={statusBadgeClass(v.status)}>{v.status || '—'}</span>
                  </td>
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                    <ContentChannelIcons summary={topicContentMap[v.id]} />
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
        processSlot={processSlot}
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
