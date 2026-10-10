// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Plus, RefreshCw, Ticket } from 'lucide-react'
import { digitalApi } from '../api/client'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import ListPager from '../components/ListPager'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import ResponsiveDataList from '../components/ResponsiveDataList'

function statusClass(s) {
  // ADR-039 — prefer shared statusBadgeClass; keep local map for compatibility
  if (['ACTIVE', 'DONE', 'RESOLVED', 'CLOSED'].includes(s)) return 'badge badge-ok'
  if (['BLOCKED', 'ERROR', 'FAILED', 'REJECTED'].includes(s)) return 'badge badge-danger'
  if (['RETIRED', 'CANCELLED', 'INACTIVE', 'ARCHIVED'].includes(s)) return 'badge badge-muted'
  if (['IN_PROGRESS', 'PLANNED', 'OPEN'].includes(s)) return 'badge badge-warn'
  return 'badge badge-draft'
}

/**
 * Help → IT Support — internal tickets (same DIG3 path as Digital desk Support tab).
 */
export default function ItSupportPage() {
  const { t } = useTranslation(['desks', 'common', 'content'])
  const [searchParams, setSearchParams] = useSearchParams()
  const { viewModal, ViewButton } = useRecordView()
  const [tickets, setTickets] = useState([])
  const [ciOptions, setCiOptions] = useState([])
  const [selectedTicket, setSelectedTicket] = useState(null)
  const [ticketContentMap, setTicketContentMap] = useState({})
  const [pendingTicketContent, setPendingTicketContent] = useState([])
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [tixPage, setTixPage] = useState({ max: 25, offset: 0, total: 0 })
  const [tixForm, setTixForm] = useState({
    name: '',
    ticketKind: 'INCIDENT',
    priority: 'NORMAL',
    configItemId: '',
    solutionId: '',
  })
  const showForm = searchParams.get('new') === '1' || searchParams.get('new') === 'true'

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [tix, cis] = await Promise.all([
        digitalApi.tickets.list({ max: tixPage.max, offset: tixPage.offset }),
        digitalApi.cis.list({ max: 100, offset: 0 }),
      ])
      const items = tix.items || tix || []
      const list = Array.isArray(items) ? items : []
      setTickets(list)
      setTixPage((pg) => ({
        ...pg,
        total: tix.total != null ? tix.total : list.length,
      }))
      setCiOptions(cis.items || cis || [])
      setTicketContentMap(await fetchContentMap('digital', 'ItTicket', list))
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [tixPage.max, tixPage.offset])

  useEffect(() => {
    load()
  }, [load])

  const run = async (label, fn) => {
    setBusy(label)
    setError(null)
    setInfo(null)
    try {
      await fn()
      await load()
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="page">
      <div className="dashboard-hero">
        <div>
          <h1 className="page-title">
            <Ticket size={22} style={{ marginRight: 8, verticalAlign: 'middle' }} />
            {t('nav.itSupport', { ns: 'common', defaultValue: 'IT Support' })}
          </h1>
          <p className="page-sub">
            Internal IT tickets (incidents and requests). Not customer after-sales — use the Service
            desk for those.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setSearchParams({ new: '1' })}
          >
            <Plus size={16} /> New ticket
          </button>
          <button type="button" className="btn btn-ghost" disabled={loading || !!busy} onClick={load}>
            <RefreshCw size={16} /> Refresh
          </button>
          <Link className="btn btn-secondary" to="/digital">
            Digital / IT desk →
          </Link>
        </div>
      </div>

      {error && <div className="banner-error">{error}</div>}
      {info && <p className="muted">{info}</p>}

      <div className="two-col finance-tx-grid">
        <section className="card">
          {(showForm || searchParams.has('new')) && (
            <>
              <h2 className="section-label" style={{ marginTop: 0 }}>
                Open internal ticket
              </h2>
              <form
                className="form-grid"
                onSubmit={(e) => {
                  e.preventDefault()
                  run('tix', async () => {
                    const ticket = await digitalApi.tickets.create({
                      ...tixForm,
                      configItemId: tixForm.configItemId || undefined,
                      solutionId: tixForm.solutionId || undefined,
                    })
                    if (pendingTicketContent.length) {
                      await attachPendingContent(
                        'digital',
                        'ItTicket',
                        ticket.id,
                        pendingTicketContent,
                      )
                      setPendingTicketContent([])
                    }
                    setInfo(
                      t('toast.numberOpened', {
                        ns: 'common',
                        what: t('labels.ticket', { ns: 'common' }),
                        number: ticket.ticketNumber,
                      }),
                    )
                    setTixForm({
                      name: '',
                      ticketKind: 'INCIDENT',
                      priority: 'NORMAL',
                      configItemId: '',
                      solutionId: '',
                    })
                    setSelectedTicket(ticket)
                    setSearchParams({})
                  })
                }}
              >
                <label>
                  Subject *
                  <input
                    required
                    autoFocus={showForm}
                    value={tixForm.name}
                    onChange={(e) => setTixForm((f) => ({ ...f, name: e.target.value }))}
                  />
                </label>
                <div className="form-row-2">
                  <label>
                    Kind
                    <select
                      value={tixForm.ticketKind}
                      onChange={(e) => setTixForm((f) => ({ ...f, ticketKind: e.target.value }))}
                    >
                      {['INCIDENT', 'REQUEST'].map((x) => (
                        <option key={x} value={x}>
                          {x}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Priority
                    <select
                      value={tixForm.priority}
                      onChange={(e) => setTixForm((f) => ({ ...f, priority: e.target.value }))}
                    >
                      {['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((x) => (
                        <option key={x} value={x}>
                          {x}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                <label>
                  CI
                  <select
                    value={tixForm.configItemId}
                    onChange={(e) => setTixForm((f) => ({ ...f, configItemId: e.target.value }))}
                  >
                    <option value="">—</option>
                    {(Array.isArray(ciOptions) ? ciOptions : ciOptions.items || []).map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </label>
                <PendingContentComposer
                  package="digital"
                  resource="ItTicket"
                  pending={pendingTicketContent}
                  onChange={setPendingTicketContent}
                />
                <div style={{ display: 'flex', gap: 8 }}>
                  <button type="submit" className="btn btn-primary" disabled={!!busy}>
                    <Plus size={16} /> Open ticket
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => setSearchParams({})}
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </>
          )}

          <h2 className="section-label">Tickets ({tixPage.total || tickets.length})</h2>
          <ResponsiveDataList
            items={tickets}
            empty="—"
            selectedKey={selectedTicket?.id}
            onSelect={(row) => setSelectedTicket(row)}
            fields={[
              {
                id: 'number',
                primary: true,
                render: (row) => (
                  <>
                    <span className="mono">{row.ticketNumber}</span>{' '}
                    <span className={statusClass(row.ticketStatus)}>{row.ticketStatus}</span>
                  </>
                ),
              },
              {
                id: 'subject',
                secondary: true,
                render: (row) => row.name,
              },
            ]}
            actions={(row) => <ViewButton pkg="digital" resource="ItTicket" record={row} />}
          >
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('wb.number', { ns: 'common' })}</th>
                    <th>{t('wb.subject', { ns: 'common' })}</th>
                    <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                    <th>{t('wb.status', { ns: 'common' })}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {tickets.map((row) => (
                    <tr
                      key={row.id}
                      className={selectedTicket?.id === row.id ? 'row-selected' : 'row-click'}
                      onClick={() => setSelectedTicket(row)}
                    >
                      <td className="mono">{row.ticketNumber}</td>
                      <td>{row.name}</td>
                      <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                        <ContentChannelIcons summary={ticketContentMap[row.id]} />
                      </td>
                      <td>
                        <span className={statusClass(row.ticketStatus)}>{row.ticketStatus}</span>
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                        <ViewButton pkg="digital" resource="ItTicket" record={row} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </ResponsiveDataList>
          <ListPager
            total={tixPage.total}
            max={tixPage.max}
            offset={tixPage.offset}
            disabled={loading}
            onOffset={(o) => setTixPage((pg) => ({ ...pg, offset: o }))}
            onMax={(m) => setTixPage((pg) => ({ ...pg, max: m, offset: 0 }))}
          />
        </section>

        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            Ticket detail
          </h2>
          {!selectedTicket && <p className="muted">{t('digital.selectTicket')}</p>}
          {selectedTicket && (
            <>
              <div className="inv-detail-hero">
                <div>
                  <div className="about-display" style={{ fontSize: '1.35rem' }}>
                    {selectedTicket.ticketNumber}
                  </div>
                  <div className="muted">
                    {selectedTicket.name} · {selectedTicket.ticketKind} ·{' '}
                    <span className={statusClass(selectedTicket.ticketStatus)}>
                      {selectedTicket.ticketStatus}
                    </span>
                  </div>
                </div>
              </div>
              <div className="toolbar">
                {selectedTicket.ticketStatus === 'OPEN' && (
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={!!busy}
                    onClick={() =>
                      run('ts', async () => {
                        const ticket = await digitalApi.tickets.start(selectedTicket.id)
                        setSelectedTicket(ticket)
                      })
                    }
                  >
                    {t('digital.start')}
                  </button>
                )}
                {['OPEN', 'IN_PROGRESS'].includes(selectedTicket.ticketStatus) && (
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={!!busy}
                    onClick={() =>
                      run('tr', async () => {
                        const ticket = await digitalApi.tickets.resolve(selectedTicket.id)
                        setSelectedTicket(ticket)
                      })
                    }
                  >
                    {t('digital.resolve')}
                  </button>
                )}
                {selectedTicket.ticketStatus !== 'CLOSED' &&
                  selectedTicket.ticketStatus !== 'CANCELLED' && (
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={!!busy}
                      onClick={() =>
                        run('tc', async () => {
                          const ticket = await digitalApi.tickets.close(selectedTicket.id)
                          setSelectedTicket(ticket)
                        })
                      }
                    >
                      {t('actions.close', { ns: 'common' })}
                    </button>
                  )}
              </div>
              <ContentRail
                package="digital"
                resource="ItTicket"
                id={selectedTicket.id}
                onChanged={async () =>
                  setTicketContentMap(await fetchContentMap('digital', 'ItTicket', tickets))
                }
              />
            </>
          )}
        </section>
      </div>
      {viewModal}
    </div>
  )
}
