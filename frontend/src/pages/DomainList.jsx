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

import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Plus, RefreshCw, Pencil, Trash2, Eye, ExternalLink, ChevronDown, ChevronRight } from 'lucide-react'
import { agentsApi, deliveryApi, domainsApi, meApi } from '../api/client'
import {
  deskHref,
  domainListReturnKey,
  findResource,
  getCreateMeta,
  getInteraction,
  getLineChildForParent,
  listReturnHref,
} from '../config/domainRegistry'

import { getMasterFormSpec } from '../forms/masterFormSpecs'
import ErrorDialog from '../components/ErrorDialog'
import RecordViewModal from '../components/RecordViewModal'
import RecordEditModal from '../components/RecordEditModal'
import ResponsiveDataList from '../components/ResponsiveDataList'
import DomainParentLines from '../components/DomainParentLines'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
import ListFilterBar from '../components/ListFilterBar'
import usePagedList from '../hooks/usePagedList'
import { statusBadgeClass } from '../utils/humanize'
import { domainGroupLabel, domainResourceLabel } from '../utils/domainLabels'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'
import {
  buildAssigneeOptions,
  filterAssigneeOptionsForDevMode,
  formatAssigneeDisplay,
  humanNpubForUsername,
  splitPersonName,
} from '../utils/taskAssignee'

const TASK_LEVEL_LABEL = { 1: 'Low', 2: 'Medium', 3: 'High' }
function taskLevelKey(n) {
  const v = Number(n)
  if (v === 1 || v === 2 || v === 3) return v
  return 2
}

const DOMAIN_SORT_FIELDS = new Set([
  'id',
  'name',
  'code',
  'status',
  'dateCreated',
  'lastUpdated',
])
const TASK_SORT_FIELDS = new Set([
  ...DOMAIN_SORT_FIELDS,
  'deadlineAt',
  'urgency',
  'importance',
  'assigneeHumanNpub',
])

function sortableField(pkg, resource, field) {
  if (!field) return false
  if (pkg === 'core' && resource === 'Task') return TASK_SORT_FIELDS.has(field)
  return DOMAIN_SORT_FIELDS.has(field)
}

export default function DomainList() {
  const { package: pkg, resource } = useParams()
  const location = useLocation()
  const { t } = useTranslation(['domain', 'common'])
  const meta = findResource(pkg, resource)
  const [companionWarn, setCompanionWarn] = useState(
    () => location.state?.companionWarn || null,
  )
  useEffect(() => {
    if (location.state?.companionWarn) {
      setCompanionWarn(location.state.companionWarn)
    }
  }, [location.state])
  const isTaskList = pkg === 'core' && resource === 'Task'
  const [assigneeOptions, setAssigneeOptions] = useState([])
  const [meUsername, setMeUsername] = useState(null)
  useEffect(() => {
    if (!isTaskList) return undefined
    let cancelled = false
    Promise.all([
      meApi.get().catch(() => null),
      deliveryApi.resources.list({ assignees: true, max: 500, offset: 0 }).catch(() => ({ items: [] })),
      agentsApi.agents.list({ max: 200 }).catch(() => ({ items: [] })),
    ]).then(([me, res, ag]) => {
      if (cancelled) return
      const username = me?.username || null
      setMeUsername(username)
      const humans = Array.isArray(res?.items) ? res.items : []
      const agents = Array.isArray(ag?.items) ? ag.items : []
      const meNpub = humanNpubForUsername(username)
      const match = humans.find(
        (h) => humanNpubForUsername(h.personRef || h.username) === meNpub,
      )
      const meLabel = match?.name ? splitPersonName(match.name).label : username
      setAssigneeOptions(
        filterAssigneeOptionsForDevMode(
          buildAssigneeOptions({ humans, agents, meUsername: username, meLabel }),
          !!me?.developmentMode,
        ),
      )
    })
    return () => {
      cancelled = true
    }
  }, [isTaskList])
  const assigneeLabel = useCallback(
    (row) =>
      formatAssigneeDisplay(row.assigneeHumanNpub, row.assigneeAgentId, {
        options: assigneeOptions,
        meUsername,
      }),
    [assigneeOptions, meUsername],
  )
  const createMeta = useMemo(() => getCreateMeta(pkg, resource), [pkg, resource])
  const lineChild = useMemo(() => getLineChildForParent(pkg, resource), [pkg, resource])
  const masterSpec = useMemo(() => getMasterFormSpec(pkg, resource), [pkg, resource])
  const listColumns = masterSpec?.listColumns || null
  const resourceLabel = domainResourceLabel(
    (keys, opts) => t(keys, { ns: 'domain', ...opts }),
    pkg,
    resource,
  )
  const groupLabel = domainGroupLabel(
    (keys, opts) => t(keys, { ns: 'common', ...opts }),
    pkg,
    meta?.group?.label || pkg,
  )
  const [error, setError] = useState(null)
  const [errorDetail, setErrorDetail] = useState(null)
  const [viewing, setViewing] = useState(null)
  const [editing, setEditing] = useState(null)
  const [expandedIds, setExpandedIds] = useState(() => new Set())
  const modalEdit = isTaskList

  const toggleExpand = (id) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const tableColSpan = listColumns
    ? 2 + listColumns.length + (lineChild ? 1 : 0)
    : resource === 'Task' && pkg === 'core'
      ? 7 + (lineChild ? 1 : 0)
      : resource === 'Address' ||
          resource === 'Invoice' ||
          resource === 'Account' ||
          resource === 'Treasury'
        ? 5 + (lineChild ? 1 : 0)
        : 4 + (lineChild ? 1 : 0)

  const createMode = createMeta.createMode || 'domain'
  const interaction =
    createMeta.interaction || getInteraction(pkg, resource) || 'domainFullPage'
  const deskPath = createMeta.deskPath
  const defaultTab = createMeta.defaultTab
  const deskLabel =
    createMeta.deskLabel ||
    t('masterData.deskDefault', { ns: 'common', defaultValue: 'process desk' })
  const preferDesk = interaction === 'deskShell' && !!deskPath

  const showError = (e, title) => {
    const payload = {
      message: e?.message || String(e),
      code: e?.code || 'ERROR',
      status: e?.status ?? null,
      detail: e?.detail || null,
      stackTrace: e?.stackTrace || null,
    }
    setError(payload.message)
    setErrorDetail({ ...payload, title })
  }

  const [q, setQ] = useState('')
  const [sort, setSort] = useState('id')
  const [order, setOrder] = useState('desc')
  const returnOffsetApplied = useRef(false)

  const filters = useMemo(() => {
    const f = { sort: sort || 'id', order: order === 'asc' ? 'asc' : 'desc' }
    // Task list: no page-level search (use top-bar FTS); ignore leftover ?q=
    if (!isTaskList && q.trim()) f.q = q.trim()
    return f
  }, [q, sort, order, isTaskList])

  const onSortChange = useCallback(({ sort: nextSort, order: nextOrder }) => {
    setSort(nextSort || 'id')
    setOrder(nextOrder === 'asc' ? 'asc' : 'desc')
  }, [])

  const sortFieldsForMenu = useMemo(() => {
    const rows = [{ value: 'id', label: 'Id' }, { value: 'name', label: 'Name' }]
    if (listColumns?.length) {
      listColumns.forEach((c) => {
        if (sortableField(pkg, resource, c.key) && !rows.some((r) => r.value === c.key)) {
          rows.push({ value: c.key, label: c.label || c.key })
        }
      })
    } else if (resource === 'Task' && pkg === 'core') {
      rows.push(
        { value: 'status', label: 'Status' },
        { value: 'deadlineAt', label: 'Deadline' },
        { value: 'urgency', label: 'Urgency' },
        { value: 'importance', label: 'Importance' },
        { value: 'assigneeHumanNpub', label: 'Assignee' },
      )
    } else if (sortableField(pkg, resource, 'status')) {
      rows.push({ value: 'status', label: 'Status' })
    }
    if (sortableField(pkg, resource, 'code')) {
      rows.push({ value: 'code', label: 'Code' })
    }
    return rows
  }, [listColumns, pkg, resource])

  const fetcher = useCallback(
    ({ max, offset, ...rest }) => domainsApi.list(pkg, resource, { max, offset, ...rest }),
    [pkg, resource],
  )

  const {
    items,
    total,
    max,
    offset,
    loading,
    setOffset,
    setMax,
    reload,
  } = usePagedList({
    fetcher,
    filters,
    deps: [pkg, resource],
  })

  useEffect(() => {
    setViewing(null)
    setEditing(null)
    setError(null)
    setErrorDetail(null)
    setExpandedIds(new Set())
    returnOffsetApplied.current = false
    const params = new URLSearchParams(
      typeof window !== 'undefined' ? window.location.search : '',
    )
    setQ(isTaskList ? '' : params.get('q') || '')
    const urlSort = params.get('sort')
    const urlOrder = params.get('order')
    setSort(urlSort || 'id')
    setOrder(urlOrder === 'asc' ? 'asc' : 'desc')
  }, [pkg, resource, isTaskList])

  // Optional URL sync for shareable filtered lists (F4-lite)
  useEffect(() => {
    if (typeof window === 'undefined') return
    const params = new URLSearchParams(window.location.search)
    if (!isTaskList && q.trim()) params.set('q', q.trim())
    else params.delete('q')
    if (sort && sort !== 'id') params.set('sort', sort)
    else params.delete('sort')
    if (order && order !== 'desc') params.set('order', order)
    else params.delete('order')
    if (offset > 0) params.set('offset', String(offset))
    else params.delete('offset')
    if (max && max !== 25) params.set('max', String(max))
    else params.delete('max')
    const qs = params.toString()
    const next = `${window.location.pathname}${qs ? `?${qs}` : ''}`
    if (next !== `${window.location.pathname}${window.location.search}`) {
      window.history.replaceState(null, '', next)
    }
  }, [pkg, resource, q, sort, order, offset, max, isTaskList])

  // Restore pager from ?offset=&max= once after list mounts / resource change
  useEffect(() => {
    if (loading || returnOffsetApplied.current) return
    const params = new URLSearchParams(
      typeof window !== 'undefined' ? window.location.search : '',
    )
    const urlMax = params.get('max')
    const urlOffset = params.get('offset')
    if (!urlMax && !urlOffset) {
      returnOffsetApplied.current = true
      return
    }
    returnOffsetApplied.current = true
    const run = async () => {
      try {
        if (urlMax && Number(urlMax) !== max) {
          await setMax(Number(urlMax))
        }
        if (urlOffset && Number(urlOffset) > 0) {
          await setOffset(Number(urlOffset))
        }
      } catch (e) {
        showError(e, t('actions.loadFailed', { ns: 'common' }))
      }
    }
    run()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, pkg, resource])

  useEffect(() => {
    try {
      sessionStorage.setItem(
        domainListReturnKey(pkg, resource),
        JSON.stringify({
          q: isTaskList ? '' : q.trim(),
          sort,
          order,
          offset,
          max,
        }),
      )
    } catch {
      /* ignore */
    }
  }, [pkg, resource, q, sort, order, offset, max, isTaskList])

  const listQuerySuffix = useMemo(() => {
    const href = listReturnHref(pkg, resource, {
      q: isTaskList ? '' : q,
      offset,
      max,
      sort,
      order,
    })
    const i = href.indexOf('?')
    return i >= 0 ? href.slice(i) : ''
  }, [pkg, resource, q, offset, max, sort, order, isTaskList])

  const thSort = useCallback(
    (field, label, className) => {
      if (!sortableField(pkg, resource, field)) {
        return (
          <th className={className}>{label}</th>
        )
      }
      return (
        <ListSortHeader
          field={field}
          label={label}
          sort={sort}
          order={order}
          onSortChange={onSortChange}
          className={className}
        />
      )
    },
    [pkg, resource, sort, order, onSortChange],
  )

  const deskOpenHref = useCallback(
    (rowId) =>
      deskHref({
        deskPath,
        defaultTab,
        id: rowId,
        returnQ: q.trim() || undefined,
      }),
    [deskPath, defaultTab, q],
  )

  const deskNewHref = useMemo(
    () =>
      deskHref({
        deskPath,
        defaultTab,
        new: true,
        returnQ: q.trim() || undefined,
      }),
    [deskPath, defaultTab, q],
  )

  const deskTabHref = useMemo(
    () => deskHref({ deskPath, defaultTab }),
    [deskPath, defaultTab],
  )

  const domainFormHref = useCallback(
    (rowId) => `/domains/${pkg}/${resource}/${rowId}${listQuerySuffix}`,
    [pkg, resource, listQuerySuffix],
  )

  const domainNewHref = useMemo(
    () => `/domains/${pkg}/${resource}/new${listQuerySuffix}`,
    [pkg, resource, listQuerySuffix],
  )

  const load = () => {
    setError(null)
    reload().catch((e) => showError(e, t('actions.loadFailed', { ns: 'common' })))
  }

  const onDelete = async (id, row) => {
    const label = entityDisplayLabel(row, {
      fallback: t('view.untitled', { ns: 'common', defaultValue: 'this record' }),
    })
    if (!window.confirm(t('actions.confirmDelete', { ns: 'common', label }))) return
    try {
      await domainsApi.remove(pkg, resource, id)
      load()
    } catch (e) {
      showError(e, t('actions.deleteFailed', { ns: 'common', label }))
    }
  }

  const emptyMsg =
    createMode === 'domain'
      ? t('masterData.emptyCreate', {
          ns: 'common',
          name: resourceLabel,
          defaultValue: `No rows yet. Create the first ${resourceLabel}.`,
        })
      : createMode === 'deskRedirect'
        ? t('masterData.emptyDesk', {
            ns: 'common',
            name: resourceLabel,
            desk: deskLabel,
            defaultValue: `No ${resourceLabel} rows yet — create on ${deskLabel}.`,
          })
        : t('masterData.emptyParentOnly', {
            ns: 'common',
            name: resourceLabel,
            defaultValue: `No ${resourceLabel} rows yet — add lines on a parent document.`,
          })

  const rowSecondary = (row) => {
    if (listColumns?.length) {
      const c = listColumns[0]
      let v = row[c.key]
      if (c.format) v = c.format(v, row)
      return v == null || v === '' ? '—' : String(v)
    }
    if (resource === 'Address') return [row.city, row.countryCode].filter(Boolean).join(', ') || '—'
    if (resource === 'Invoice')
      return row.invoiceNumber
        ? `${row.invoiceNumber} · ${row.invoiceStatus || row.status || '—'}`
        : row.invoiceStatus || row.status || '—'
    if (resource === 'Account') return [row.code, row.accountType].filter(Boolean).join(' · ') || '—'
    if (resource === 'Treasury')
      return row.balanceEur != null
        ? `${row.accountKind || '—'} · ${row.balanceEur} EUR`
        : row.accountKind || '—'
    if (resource === 'Task' && pkg === 'core') {
      const due = row.deadlineAt ? String(row.deadlineAt).slice(0, 10) : null
      return [row.status || 'OPEN', due].filter(Boolean).join(' · ')
    }
    return row.description || row.status || '—'
  }

  const rowMeta = (row) => {
    if (listColumns?.length > 1) {
      return listColumns
        .slice(1, 3)
        .map((c) => {
          let v = row[c.key]
          if (c.format) v = c.format(v, row)
          if (v == null || v === '') return null
          return String(v)
        })
        .filter(Boolean)
        .join(' · ')
    }
    if (resource === 'Invoice' && row.amountEur != null) return `${row.amountEur} EUR`
    if (resource === 'Task' && pkg === 'core') {
      const u = TASK_LEVEL_LABEL[taskLevelKey(row.urgency)]
      const i = TASK_LEVEL_LABEL[taskLevelKey(row.importance)]
      const who = assigneeLabel(row)
      return [`U ${u} · I ${i}`, who].filter(Boolean).join(' · ')
    }
    if (resource === 'Account') return row.currencyCode || null
    if (resource === 'Treasury' && row.balanceSats != null) return `${row.balanceSats} sats`
    if (resource === 'Address') return row.addressType || null
    return row.status && resource !== 'Invoice' ? row.status : null
  }

  const rowActions = (row) => (
    <>
      <button
        type="button"
        className="btn btn-ghost"
        title={t('view.open', { ns: 'common' })}
        onClick={() => setViewing(row)}
      >
        <Eye size={14} />
      </button>
      {modalEdit ? (
        <button
          type="button"
          className="btn btn-secondary"
          title={t('actions.edit', { ns: 'common' })}
          onClick={() => setEditing(row)}
        >
          <Pencil size={14} />
        </button>
      ) : (
        <Link
          className="btn btn-secondary"
          to={preferDesk ? deskOpenHref(row.id) : domainFormHref(row.id)}
          title={
            preferDesk
              ? t('masterData.openDesk', {
                  ns: 'common',
                  desk: deskLabel,
                  defaultValue: 'Open on {{desk}}',
                })
              : t('actions.edit', { ns: 'common' })
          }
        >
          {preferDesk ? <ExternalLink size={14} /> : <Pencil size={14} />}
        </Link>
      )}
      <button
        type="button"
        className="btn btn-ghost btn-danger"
        title={t('actions.delete', { ns: 'common' })}
        onClick={() => onDelete(row.id, row)}
      >
        <Trash2 size={14} />
      </button>
    </>
  )

  const titleBlock = (
    <h1 className="page-title domain-list-title">
      <span className="domain-list-title-main">{resourceLabel}</span>
      <span className="muted domain-list-title-group">{groupLabel}</span>
      <span className="muted domain-list-title-count">
        (
        {t(total === 1 ? 'actions.records_one' : 'actions.records_other', {
          ns: 'common',
          count: total,
        })}
        )
      </span>
    </h1>
  )

  const createActions = (
    <>
      {createMode === 'domain' && preferDesk && deskPath && (
        <Link className="btn btn-primary" to={deskNewHref}>
          <Plus size={16} /> {t('actions.create', { ns: 'common' })}
        </Link>
      )}
      {createMode === 'domain' && !preferDesk && (
        <Link className="btn btn-primary" to={domainNewHref}>
          <Plus size={16} /> {t('actions.create', { ns: 'common' })}
        </Link>
      )}
      {createMode === 'deskRedirect' && deskPath && (
        <Link className="btn btn-primary" to={deskNewHref}>
          <ExternalLink size={16} />{' '}
          {t('masterData.openDesk', {
            ns: 'common',
            desk: deskLabel,
            defaultValue: 'Create on {{desk}}',
          })}
        </Link>
      )}
      {createMode === 'parentOnly' && deskPath && (
        <Link className="btn" to={deskTabHref}>
          <ExternalLink size={16} />{' '}
          {t('masterData.openParentDesk', {
            ns: 'common',
            desk: deskLabel,
            defaultValue: 'Open {{desk}}',
          })}
        </Link>
      )}
    </>
  )

  const searchAndRefresh = (
    <>
      {!isTaskList && (
        <ListFilterBar
          q={q}
          onQChange={setQ}
          placeholder={t('masterData.search', {
            ns: 'common',
            defaultValue: 'Search…',
          })}
          ariaLabel={t('actions.search', { ns: 'common', defaultValue: 'Search' })}
        />
      )}
      {createMode === 'parentOnly' && createMeta.parentResource && (
        <Link
          className="btn btn-secondary"
          to={`/domains/${pkg}/${createMeta.parentResource}`}
        >
          {t('masterData.viewParents', {
            ns: 'common',
            parent: createMeta.parentResource,
            defaultValue: 'View {{parent}} list',
          })}
        </Link>
      )}
      <button type="button" className="btn" onClick={load} disabled={loading}>
        <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
      </button>
    </>
  )

  return (
    <div className={isTaskList ? 'domain-list domain-list--task' : 'domain-list'}>
      {isTaskList ? (
        <header className="page-header domain-list-header domain-list-header--task">
          <div className="domain-list-header-start">
            {titleBlock}
            {createActions}
          </div>
          <div className="domain-list-header-end">{searchAndRefresh}</div>
        </header>
      ) : (
        <>
          {titleBlock}
          {createMode !== 'domain' && (
            <p className="page-sub">
              <span className="badge badge-draft" style={{ fontWeight: 600 }}>
                {createMode === 'parentOnly'
                  ? t('masterData.classLine', { ns: 'common', defaultValue: 'Line item' })
                  : t('masterData.classProcess', {
                      ns: 'common',
                      defaultValue: 'Process document',
                    })}
              </span>
            </p>
          )}
        </>
      )}

      {(preferDesk || createMode === 'deskRedirect') && deskPath && (
        <div className="banner-info" style={{ marginBottom: '0.75rem' }}>
          {t('masterData.createViaDesk', {
            ns: 'common',
            desk: deskLabel,
            defaultValue:
              'Create new records on the {{desk}} — master data is for list, view, and edit support.',
          })}{' '}
          {preferDesk && (
            <Link to={deskTabHref} className="btn btn-secondary" style={{ marginLeft: 8 }}>
              {t('masterData.openDesk', {
                ns: 'common',
                desk: deskLabel,
                defaultValue: 'Open {{desk}}',
              })}
            </Link>
          )}
        </div>
      )}
      {createMode === 'parentOnly' && (
        <div className="banner-info" style={{ marginBottom: '0.75rem' }}>
          {t('masterData.parentOnly', {
            ns: 'common',
            parent: createMeta.parentResource || t('masterData.parentDoc', { ns: 'common', defaultValue: 'parent document' }),
            defaultValue:
              'Line items are created under a {{parent}} (multi-line editor on the process desk), not as standalone master data.',
          })}
        </div>
      )}
      {createMode === 'blocked' && (
        <div className="banner-error" style={{ marginBottom: '0.75rem' }}>
          {createMeta.reason ||
            t('masterData.createBlocked', {
              ns: 'common',
              defaultValue: 'Standalone create is not available for this resource yet.',
            })}
        </div>
      )}

      {companionWarn && (
        <div className="banner-info" style={{ marginBottom: '0.75rem' }} role="status">
          {companionWarn}
          <button
            type="button"
            className="btn"
            style={{ marginLeft: 8 }}
            onClick={() => setCompanionWarn(null)}
          >
            {t('actions.dismiss', { ns: 'common', defaultValue: 'Dismiss' })}
          </button>
        </div>
      )}

      {error && (
        <div
          className="banner-error"
          style={{ cursor: 'pointer' }}
          onClick={() => setErrorDetail((d) => d || { message: error })}
        >
          {error}
          {errorDetail && (
            <span className="muted" style={{ marginLeft: 8, fontSize: '0.85rem' }}>
              {t('actions.clickDetails', { ns: 'common' })}
            </span>
          )}
        </div>
      )}

      <ErrorDialog
        error={errorDetail}
        title={errorDetail?.title || t('actions.error', { ns: 'common' })}
        onClose={() => setErrorDetail(null)}
      />

      <RecordViewModal
        open={!!viewing}
        onClose={() => setViewing(null)}
        package={pkg}
        resource={resource}
        recordId={viewing?.id}
        seed={viewing}
        onEdit={
          modalEdit
            ? (row) => setEditing(row || viewing)
            : undefined
        }
        editPath={
          modalEdit
            ? undefined
            : viewing?.id != null
              ? preferDesk
                ? deskOpenHref(viewing.id)
                : domainFormHref(viewing.id)
              : undefined
        }
      />

      <RecordEditModal
        open={!!editing}
        onClose={() => setEditing(null)}
        package={pkg}
        resource={resource}
        recordId={editing?.id}
        seed={editing}
        onSaved={() => {
          reload().catch((e) => showError(e, t('actions.loadFailed', { ns: 'common' })))
        }}
      />

      {!isTaskList && (
        <div className="toolbar" style={{ flexWrap: 'wrap', gap: 8 }}>
          {createActions}
          {searchAndRefresh}
        </div>
      )}

      {loading && items.length === 0 ? (
        <p className="muted">{t('actions.loading', { ns: 'common' })}</p>
      ) : (
        <>
        <ResponsiveDataList
          items={items}
          empty={emptyMsg}
          loading={loading}
          onSelect={(row) => setViewing(row)}
          sortFields={sortFieldsForMenu}
          sort={sort}
          order={order}
          onSortChange={onSortChange}
          fields={[
            {
              id: 'name',
              primary: true,
              render: (row) =>
                entityDisplayLabel(row, {
                  fallback: t('view.untitled', { ns: 'common', defaultValue: 'Untitled' }),
                }),
            },
            {
              id: 'secondary',
              secondary: true,
              render: (row) => rowSecondary(row),
            },
            {
              id: 'meta',
              meta: true,
              render: (row) => rowMeta(row) || '',
            },
            ...(isTaskList
              ? [
                  {
                    id: 'assignee',
                    priority: 'low',
                    render: (row) => assigneeLabel(row),
                  },
                ]
              : []),
          ]}
          actions={rowActions}
        >
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  {lineChild && <th style={{ width: 36 }} aria-label={t('masterData.linesHeading', { ns: 'common', defaultValue: 'Lines' })} />}
                  {thSort('name', t('wb.name', { ns: 'common' }))}
                  {listColumns ? (
                    listColumns.map((c) => (
                      <Fragment key={c.key}>
                        {thSort(c.key, c.label)}
                      </Fragment>
                    ))
                  ) : resource === 'Address' ? (
                    <>
                      <th>City</th>
                      <th className="col-priority-low">Country</th>
                      <th className="col-priority-low">Type</th>
                    </>
                  ) : resource === 'Invoice' ? (
                    <>
                      <th>{t('wb.number', { ns: 'common' })}</th>
                      <th>EUR</th>
                      {thSort('status', t('wb.status', { ns: 'common' }))}
                    </>
                  ) : resource === 'Account' ? (
                    <>
                      {thSort('code', t('wb.code', { ns: 'common' }))}
                      <th>{t('wb.type', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.ccy', { ns: 'common' })}</th>
                    </>
                  ) : resource === 'Treasury' ? (
                    <>
                      <th>{t('wb.kind', { ns: 'common' })}</th>
                      <th>EUR</th>
                      <th className="col-priority-low">Sats</th>
                    </>
                  ) : resource === 'Task' && pkg === 'core' ? (
                    <>
                      {thSort('status', t('wb.status', { ns: 'common' }))}
                      {thSort(
                        'deadlineAt',
                        t('pages.work.deadline', {
                          ns: 'common',
                          defaultValue: 'Deadline',
                        }),
                      )}
                      {thSort(
                        'urgency',
                        t('pages.work.urgency', { ns: 'common', defaultValue: 'Urgency' }),
                        'col-priority-low',
                      )}
                      {thSort(
                        'importance',
                        t('pages.work.importance', {
                          ns: 'common',
                          defaultValue: 'Importance',
                        }),
                        'col-priority-low',
                      )}
                      {thSort(
                        'assigneeHumanNpub',
                        t('pages.work.assignee', { ns: 'common', defaultValue: 'Assignee' }),
                        'col-priority-low',
                      )}
                    </>
                  ) : (
                    <>
                      <th className="col-priority-low">
                        {t('wb.description', { ns: 'common' })}
                      </th>
                      {thSort('status', t('wb.status', { ns: 'common' }))}
                    </>
                  )}
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.length === 0 && (
                  <tr>
                    <td colSpan={tableColSpan} className="muted">
                      {emptyMsg}
                    </td>
                  </tr>
                )}
                {items.map((row) => (
                  <Fragment key={row.id}>
                  <tr>
                    {lineChild && (
                      <td>
                        <button
                          type="button"
                          className="btn btn-ghost"
                          style={{ padding: 4 }}
                          aria-expanded={expandedIds.has(row.id)}
                          title={t('masterData.toggleLines', {
                            ns: 'common',
                            defaultValue: 'Show lines',
                          })}
                          onClick={() => toggleExpand(row.id)}
                        >
                          {expandedIds.has(row.id) ? (
                            <ChevronDown size={14} />
                          ) : (
                            <ChevronRight size={14} />
                          )}
                        </button>
                      </td>
                    )}
                    <td>
                      <button
                        type="button"
                        className="btn-link-title"
                        onClick={() => setViewing(row)}
                        title={t('view.open', { ns: 'common' })}
                      >
                        {entityDisplayLabel(row, {
                          fallback: t('view.untitled', { ns: 'common', defaultValue: 'Untitled' }),
                        })}
                      </button>
                    </td>
                    {listColumns ? (
                      listColumns.map((c) => {
                        let v = row[c.key]
                        if (c.format) v = c.format(v, row)
                        else if (v == null || v === '') v = '—'
                        return (
                          <td
                            key={c.key}
                            className={[c.muted && 'muted', c.mono && 'mono']
                              .filter(Boolean)
                              .join(' ')}
                          >
                            {v}
                          </td>
                        )
                      })
                    ) : resource === 'Address' ? (
                      <>
                        <td>{row.city || '—'}</td>
                        <td className="muted col-priority-low">{row.countryCode || '—'}</td>
                        <td className="col-priority-low">{row.addressType || '—'}</td>
                      </>
                    ) : resource === 'Invoice' ? (
                      <>
                        <td className="mono">{row.invoiceNumber || '—'}</td>
                        <td>{row.amountEur != null ? row.amountEur : '—'}</td>
                        <td>
                          {row.invoiceStatus || row.status ? (
                            <span className={statusBadgeClass(row.invoiceStatus || row.status)}>
                              {row.invoiceStatus || row.status}
                            </span>
                          ) : (
                            '—'
                          )}
                        </td>
                      </>
                    ) : resource === 'Account' ? (
                      <>
                        <td className="mono">{row.code || '—'}</td>
                        <td>{row.accountType || '—'}</td>
                        <td className="col-priority-low">{row.currencyCode || '—'}</td>
                      </>
                    ) : resource === 'Treasury' ? (
                      <>
                        <td>{row.accountKind || '—'}</td>
                        <td>{row.balanceEur != null ? row.balanceEur : '—'}</td>
                        <td className="mono col-priority-low">
                          {row.balanceSats != null ? row.balanceSats : '—'}
                        </td>
                      </>
                    ) : resource === 'Task' && pkg === 'core' ? (
                      <>
                        <td>
                          <span className={statusBadgeClass(row.status || 'OPEN')}>
                            {row.status || 'OPEN'}
                          </span>
                        </td>
                        <td>
                          {row.deadlineAt ? String(row.deadlineAt).slice(0, 10) : '—'}
                        </td>
                        <td className="col-priority-low">
                          {TASK_LEVEL_LABEL[taskLevelKey(row.urgency)]}
                        </td>
                        <td className="col-priority-low">
                          {TASK_LEVEL_LABEL[taskLevelKey(row.importance)]}
                        </td>
                        <td className="muted col-priority-low" style={{ fontSize: '0.85rem' }}>
                          {assigneeLabel(row)}
                        </td>
                      </>
                    ) : (
                      <>
                        <td className="muted col-priority-low">{row.description || '—'}</td>
                        <td>
                          {row.status ? (
                            <span className={statusBadgeClass(row.status)}>{row.status}</span>
                          ) : (
                            '—'
                          )}
                        </td>
                      </>
                    )}
                    <td style={{ whiteSpace: 'nowrap' }}>{rowActions(row)}</td>
                  </tr>
                  {lineChild && expandedIds.has(row.id) && (
                    <DomainParentLines
                      lineChild={lineChild}
                      parentId={row.id}
                      colSpan={tableColSpan}
                    />
                  )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </ResponsiveDataList>
        <ListPager
          total={total}
          max={max}
          offset={offset}
          disabled={loading}
          onOffset={(o) => setOffset(o).catch((e) => showError(e, t('actions.loadFailed', { ns: 'common' })))}
          onMax={(m) => setMax(m).catch((e) => showError(e, t('actions.loadFailed', { ns: 'common' })))}
        />
        </>
      )}
    </div>
  )
}
