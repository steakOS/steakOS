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
import ListExportMenu from '../components/ListExportMenu'
import {
  deskHref,
  domainListReturnKey,
  findResource,
  getCreateMeta,
  getInteraction,
  getLineChildForParent,
  listReturnHref,
} from '../config/domainRegistry'

import { getMasterFormSpec, hasMasterFormSpec } from '../forms/masterFormSpecs'
import ErrorDialog from '../components/ErrorDialog'
import RecordViewModal from '../components/RecordViewModal'
import RecordEditModal from '../components/RecordEditModal'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import { isContentCompanionHost } from '../utils/contentCompanion'
import { fetchContentMap } from '../utils/contentSummary'
import ResponsiveDataList from '../components/ResponsiveDataList'
import RowActionSlots from '../components/RowActionSlots'
import DomainParentLines from '../components/DomainParentLines'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
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
  'amountEur',
  'customerName',
  'kind',
  'active',
  'priority',
  'referenceText',
  'description',
])
const TASK_SORT_FIELDS = new Set([
  'id',
  'name',
  'code',
  'status',
  'dateCreated',
  'lastUpdated',
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
  /** Modal edit when Task or a master-form resource (ADR-048 / ADR-050). Else Edit slot blank. */
  const supportsModalEdit = isTaskList || hasMasterFormSpec(pkg, resource)
  const canModalEditRow = useCallback(
    (row) => {
      if (!supportsModalEdit || !row) return false
      if (resource === 'PurchaseOrder') {
        const st = row.poStatus || row.status
        return st === 'DRAFT'
      }
      return true
    },
    [supportsModalEdit, resource],
  )

  const toggleExpand = (id) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const showContentCol = isContentCompanionHost(pkg, resource)
  const tableColSpan = listColumns
    ? 2 + listColumns.length + (lineChild ? 1 : 0) + (showContentCol ? 1 : 0)
    : resource === 'Task' && pkg === 'core'
      ? 7 + (lineChild ? 1 : 0) + (showContentCol ? 1 : 0)
      : resource === 'Address' ||
          resource === 'Invoice' ||
          resource === 'Account' ||
          resource === 'Treasury'
        ? 5 + (lineChild ? 1 : 0) + (showContentCol ? 1 : 0)
        : 3 + (lineChild ? 1 : 0) + (showContentCol ? 1 : 0)

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

  const [sort, setSort] = useState('id')
  const [order, setOrder] = useState('desc')
  const returnOffsetApplied = useRef(false)

  const filters = useMemo(() => {
    // No page-level search — operators use top-bar FTS (ADR-050)
    return { sort: sort || 'id', order: order === 'asc' ? 'asc' : 'desc' }
  }, [sort, order])

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

  const [contentMap, setContentMap] = useState({})
  useEffect(() => {
    if (!isContentCompanionHost(pkg, resource)) {
      setContentMap({})
      return undefined
    }
    let cancelled = false
    fetchContentMap(pkg, resource, items).then((map) => {
      if (!cancelled) setContentMap(map)
    })
    return () => {
      cancelled = true
    }
  }, [pkg, resource, items])

  useEffect(() => {
    setViewing(null)
    setEditing(null)
    setError(null)
    setErrorDetail(null)
    setExpandedIds(new Set())
    setContentMap({})
    returnOffsetApplied.current = false
    const params = new URLSearchParams(
      typeof window !== 'undefined' ? window.location.search : '',
    )
    // Drop legacy ?q= — local list search removed (header FTS only)
    if (params.has('q') && typeof window !== 'undefined') {
      params.delete('q')
      const qs = params.toString()
      window.history.replaceState(
        null,
        '',
        `${window.location.pathname}${qs ? `?${qs}` : ''}`,
      )
    }
    const urlSort = params.get('sort')
    const urlOrder = params.get('order')
    setSort(urlSort || 'id')
    setOrder(urlOrder === 'asc' ? 'asc' : 'desc')
  }, [pkg, resource, isTaskList])

  // Optional URL sync for shareable sorted/paged lists (F4-lite)
  useEffect(() => {
    if (typeof window === 'undefined') return
    const params = new URLSearchParams(window.location.search)
    params.delete('q')
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
  }, [pkg, resource, sort, order, offset, max, isTaskList])

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
          sort,
          order,
          offset,
          max,
        }),
      )
    } catch {
      /* ignore */
    }
  }, [pkg, resource, sort, order, offset, max])

  const listQuerySuffix = useMemo(() => {
    const href = listReturnHref(pkg, resource, {
      offset,
      max,
      sort,
      order,
    })
    const i = href.indexOf('?')
    return i >= 0 ? href.slice(i) : ''
  }, [pkg, resource, offset, max, sort, order])

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
      }),
    [deskPath, defaultTab],
  )

  const deskNewHref = useMemo(
    () =>
      deskHref({
        deskPath,
        defaultTab,
        new: true,
      }),
    [deskPath, defaultTab],
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
    return row.status || '—'
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

  /** View / edit / delete — same pill chrome as delete (transparent + reddish outline). */
  const rowActionClass = 'btn btn-ghost btn-danger'

  const rowActions = (row) => (
    <RowActionSlots
      view={
        <button
          type="button"
          className={rowActionClass}
          title={t('view.open', { ns: 'common' })}
          onClick={() => setViewing(row)}
        >
          <Eye size={14} />
        </button>
      }
      edit={
        canModalEditRow(row) ? (
          <button
            type="button"
            className={rowActionClass}
            title={t('actions.edit', { ns: 'common' })}
            aria-label={t('actions.edit', { ns: 'common' })}
            onClick={() => setEditing(row)}
          >
            <Pencil size={14} />
          </button>
        ) : null
      }
      delete={
        <button
          type="button"
          className={rowActionClass}
          title={t('actions.delete', { ns: 'common' })}
          onClick={() => onDelete(row.id, row)}
        >
          <Trash2 size={14} />
        </button>
      }
    />
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

  /**
   * Preferred export column order/labels (UI table). ListExportMenu still exports
   * the union of all fields on fetched records (ADR-050).
   */
  const exportPreferredColumns = useMemo(() => {
    const nameCol = {
      key: 'name',
      label: t('wb.name', { ns: 'common', defaultValue: 'Name' }),
    }
    const idCol = { key: 'id', label: 'Id' }
    if (listColumns?.length) {
      return [
        idCol,
        nameCol,
        ...listColumns.map((c) => ({ key: c.key, label: c.label || c.key })),
      ]
    }
    if (resource === 'Address') {
      return [
        idCol,
        nameCol,
        { key: 'city', label: 'City' },
        { key: 'countryCode', label: 'Country' },
        { key: 'addressType', label: 'Type' },
      ]
    }
    if (resource === 'Invoice') {
      return [
        idCol,
        nameCol,
        { key: 'invoiceNumber', label: 'Number' },
        { key: 'amountEur', label: 'Amount' },
        { key: 'invoiceStatus', label: t('wb.status', { ns: 'common', defaultValue: 'Status' }) },
      ]
    }
    if (resource === 'Account') {
      return [
        idCol,
        nameCol,
        { key: 'code', label: 'Code' },
        { key: 'accountType', label: 'Type' },
        { key: 'currencyCode', label: 'Ccy' },
      ]
    }
    if (resource === 'Treasury') {
      return [
        idCol,
        nameCol,
        { key: 'accountKind', label: 'Kind' },
        { key: 'balanceEur', label: 'EUR' },
        { key: 'balanceSats', label: 'Sats' },
      ]
    }
    if (pkg === 'core' && resource === 'Task') {
      return [
        idCol,
        nameCol,
        { key: 'status', label: t('wb.status', { ns: 'common', defaultValue: 'Status' }) },
        { key: 'deadlineAt', label: 'Deadline' },
        { key: 'urgency', label: 'Urgency' },
        { key: 'importance', label: 'Importance' },
        { key: 'assigneeHumanNpub', label: 'Assignee' },
      ]
    }
    // Description stays on CRUD forms / view modal — not a default list column.
    return [
      idCol,
      nameCol,
      { key: 'status', label: t('wb.status', { ns: 'common', defaultValue: 'Status' }) },
    ]
  }, [listColumns, pkg, resource, t])

  const searchAndRefresh = (
    <>
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
      <ListExportMenu
        fetcher={fetcher}
        filters={filters}
        columns={exportPreferredColumns}
        fileBase={`${pkg}-${resource}`.toLowerCase()}
        sheetName={resourceLabel || resource}
        disabled={loading}
        onError={(e) =>
          showError(e, t('actions.downloadFailed', { ns: 'common', defaultValue: 'Download failed' }))
        }
      />
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
          viewing && canModalEditRow(viewing)
            ? (row) => setEditing(row || viewing)
            : undefined
        }
        editPath={undefined}
        belowFields={
          viewing?.id != null && isContentCompanionHost(pkg, resource) ? (
            <details className="content-rail-collapse" style={{ marginTop: '1.25rem' }}>
              <summary className="muted" style={{ cursor: 'pointer' }}>
                Content
              </summary>
              <ContentRail package={pkg} resource={resource} id={viewing.id} />
            </details>
          ) : null
        }
      />

      {supportsModalEdit && (
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
      )}

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
            ...(showContentCol
              ? [
                  {
                    id: 'content',
                    priority: 'low',
                    render: (row) => <ContentChannelIcons summary={contentMap[row.id]} />,
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
                    <>{thSort('status', t('wb.status', { ns: 'common' }))}</>
                  )}
                  {showContentCol ? (
                    <th className="col-priority-low">
                      {t('wb.content', { ns: 'common', defaultValue: 'Content' })}
                    </th>
                  ) : null}
                  <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
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
                      <td>
                        {row.status ? (
                          <span className={statusBadgeClass(row.status)}>{row.status}</span>
                        ) : (
                          '—'
                        )}
                      </td>
                    )}
                    {showContentCol ? (
                      <td className="col-priority-low">
                        <ContentChannelIcons summary={contentMap[row.id]} />
                      </td>
                    ) : null}
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
