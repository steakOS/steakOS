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

import { Fragment, useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Plus, RefreshCw, Pencil, Trash2, Eye, ExternalLink, ChevronDown, ChevronRight } from 'lucide-react'
import { domainsApi } from '../api/client'
import {
  findResource,
  getCreateMeta,
  getInteraction,
  getLineChildForParent,
} from '../config/domainRegistry'
import { catalogLifecycle, lifecycleBadgeClass } from '../config/catalogLifecycle'
import { getMasterFormSpec } from '../forms/masterFormSpecs'
import ErrorDialog from '../components/ErrorDialog'
import RecordViewModal from '../components/RecordViewModal'
import ResponsiveDataList from '../components/ResponsiveDataList'
import DomainParentLines from '../components/DomainParentLines'
import ListPager from '../components/ListPager'
import usePagedList from '../hooks/usePagedList'
import { statusBadgeClass } from '../utils/humanize'
import { domainGroupLabel, domainResourceLabel } from '../utils/domainLabels'

export default function DomainList() {
  const { package: pkg, resource } = useParams()
  const { t } = useTranslation(['domain', 'common'])
  const meta = findResource(pkg, resource)
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
  const [expandedIds, setExpandedIds] = useState(() => new Set())

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
    : resource === 'Address' ||
        resource === 'Invoice' ||
        resource === 'Account' ||
        resource === 'Treasury' ||
        (resource === 'Task' && pkg === 'core')
      ? 5 + (lineChild ? 1 : 0)
      : 4 + (lineChild ? 1 : 0)

  const createMode = createMeta.createMode || 'domain'
  const interaction =
    createMeta.interaction || getInteraction(pkg, resource) || 'domainFullPage'
  const deskPath = createMeta.deskPath
  const deskLabel =
    createMeta.deskLabel ||
    t('masterData.deskDefault', { ns: 'common', defaultValue: 'process desk' })
  const lifecycle = catalogLifecycle(pkg, resource)
  const preferDesk = interaction === 'deskShell' && deskPath
  const lifecycleLabel =
    lifecycle === 'in_use'
      ? t('pages.catalogs.inUse', { ns: 'common' })
      : lifecycle === 'system'
        ? t('pages.catalogs.system', { ns: 'common' })
        : t('pages.catalogs.reserve', { ns: 'common' })

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

  const [qDraft, setQDraft] = useState('')
  const [q, setQ] = useState('')

  const filters = useMemo(() => (q.trim() ? { q: q.trim() } : {}), [q])

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
    setError(null)
    setErrorDetail(null)
    setQ('')
    setQDraft('')
    setExpandedIds(new Set())
  }, [pkg, resource])

  const load = () => {
    setError(null)
    reload().catch((e) => showError(e, t('actions.loadFailed', { ns: 'common' })))
  }

  const onDelete = async (id, name) => {
    const label = name ? `"${name}" (#${id})` : `#${id}`
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
    if (resource === 'Task' && pkg === 'core')
      return `U${row.urgency ?? 2}/I${row.importance ?? 2}`
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
      <Link
        className="btn btn-secondary"
        to={`/domains/${pkg}/${resource}/${row.id}`}
        title={t('actions.edit', { ns: 'common' })}
      >
        <Pencil size={14} />
      </Link>
      <button
        type="button"
        className="btn btn-ghost btn-danger"
        title={t('actions.delete', { ns: 'common' })}
        onClick={() => onDelete(row.id, row.name)}
      >
        <Trash2 size={14} />
      </button>
    </>
  )

  return (
    <div>
      <h1 className="page-title" style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        {resourceLabel}
        <span className="muted" style={{ fontWeight: 500, fontSize: '1rem' }}>
          {groupLabel}
        </span>
        {(meta?.group?.navRole === 'admin' || meta?.group?.primary === false) && (
          <span className={lifecycleBadgeClass(lifecycle)} title={lifecycleLabel}>
            {lifecycleLabel}
          </span>
        )}
      </h1>
      <p className="page-sub">
        {t(total === 1 ? 'actions.records_one' : 'actions.records_other', {
          ns: 'common',
          count: total,
        })}
        {createMode !== 'domain' && (
          <>
            {' · '}
            <span className="badge badge-draft" style={{ fontWeight: 600 }}>
              {createMode === 'parentOnly'
                ? t('masterData.classLine', { ns: 'common', defaultValue: 'Line item' })
                : t('masterData.classProcess', {
                    ns: 'common',
                    defaultValue: 'Process document',
                  })}
            </span>
          </>
        )}
      </p>

      {(preferDesk || createMode === 'deskRedirect') && deskPath && (
        <div className="banner-info" style={{ marginBottom: '0.75rem' }}>
          {t('masterData.createViaDesk', {
            ns: 'common',
            desk: deskLabel,
            defaultValue:
              'Create new records on the {{desk}} — master data is for list, view, and edit support.',
          })}{' '}
          {preferDesk && (
            <Link to={deskPath} className="btn btn-secondary" style={{ marginLeft: 8 }}>
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
        editPath={
          viewing?.id != null ? `/domains/${pkg}/${resource}/${viewing.id}` : undefined
        }
      />

      <div className="toolbar" style={{ flexWrap: 'wrap', gap: 8 }}>
        {createMode === 'domain' && (
          <Link className="btn btn-primary" to={`/domains/${pkg}/${resource}/new`}>
            <Plus size={16} /> {t('actions.create', { ns: 'common' })}
          </Link>
        )}
        {createMode === 'deskRedirect' && deskPath && (
          <Link className="btn btn-primary" to={deskPath}>
            <ExternalLink size={16} />{' '}
            {t('masterData.openDesk', {
              ns: 'common',
              desk: deskLabel,
              defaultValue: 'Create on {{desk}}',
            })}
          </Link>
        )}
        {createMode === 'parentOnly' && deskPath && (
          <Link className="btn" to={deskPath}>
            <ExternalLink size={16} />{' '}
            {t('masterData.openParentDesk', {
              ns: 'common',
              desk: deskLabel,
              defaultValue: 'Open {{desk}}',
            })}
          </Link>
        )}
        <form
          style={{ display: 'flex', gap: 6, alignItems: 'center', marginLeft: 'auto' }}
          onSubmit={(e) => {
            e.preventDefault()
            setQ(qDraft)
          }}
        >
          <input
            type="search"
            value={qDraft}
            onChange={(e) => setQDraft(e.target.value)}
            placeholder={t('masterData.search', {
              ns: 'common',
              defaultValue: 'Search…',
            })}
            style={{ minWidth: 160 }}
            aria-label={t('actions.search', { ns: 'common', defaultValue: 'Search' })}
          />
          <button type="submit" className="btn" disabled={loading}>
            {t('actions.search', { ns: 'common', defaultValue: 'Search' })}
          </button>
          {(q || qDraft) && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setQDraft('')
                setQ('')
              }}
            >
              {t('actions.clear', { ns: 'common', defaultValue: 'Clear' })}
            </button>
          )}
        </form>
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
      </div>

      {loading && items.length === 0 ? (
        <p className="muted">{t('actions.loading', { ns: 'common' })}</p>
      ) : (
        <>
        <ResponsiveDataList
          items={items}
          empty={emptyMsg}
          loading={loading}
          onSelect={(row) => setViewing(row)}
          fields={[
            {
              id: 'name',
              primary: true,
              render: (row) => row.name || `#${row.id}`,
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
          ]}
          actions={rowActions}
        >
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  {lineChild && <th style={{ width: 36 }} aria-label={t('masterData.linesHeading', { ns: 'common', defaultValue: 'Lines' })} />}
                  <th>{t('wb.name', { ns: 'common' })}</th>
                  {listColumns ? (
                    listColumns.map((c) => <th key={c.key}>{c.label}</th>)
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
                      <th>{t('wb.status', { ns: 'common' })}</th>
                    </>
                  ) : resource === 'Account' ? (
                    <>
                      <th>{t('wb.code', { ns: 'common' })}</th>
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
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th>
                        {t('pages.work.deadline', {
                          ns: 'common',
                          defaultValue: 'Deadline',
                        })}
                      </th>
                      <th className="col-priority-low">U/I</th>
                    </>
                  ) : (
                    <>
                      <th className="col-priority-low">
                        {t('wb.description', { ns: 'common' })}
                      </th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
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
                        {row.name || `#${row.id}`}
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
                        <td className="mono col-priority-low">
                          {row.urgency ?? 2}/{row.importance ?? 2}
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
