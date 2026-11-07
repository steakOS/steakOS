// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { X, ExternalLink, Ticket, Plus, AlertTriangle } from 'lucide-react'
import { digitalApi, helpApi } from '../api/client'
import { markdownToHtml, stripRelatedSection } from '../utils/markdown'
import { isUnboundDataProduct } from '../config/domainRegistry'

/**
 * Context-sensitive help drawer — domain master data, desks, and feature routes.
 * Every open panel offers creating an IT support ticket.
 */
/**
 * @param {object} [helpContext] ADR-044 HelpContext from DeskShell detail
 *   { path, desk, tab, resource, objectId, status, processCode }
 */
export default function HelpDrawer({
  open,
  onClose,
  initialTopic = null,
  helpContext = null,
}) {
  const location = useLocation()
  const navigate = useNavigate()
  const { t, i18n } = useTranslation('help')
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [ticketOpen, setTicketOpen] = useState(false)
  const [ticketBusy, setTicketBusy] = useState(false)
  const [ticketMsg, setTicketMsg] = useState(null)
  const [ticketErr, setTicketErr] = useState(null)
  const [ticketForm, setTicketForm] = useState({
    name: '',
    ticketKind: 'INCIDENT',
    priority: 'NORMAL',
  })
  const [focusKey, setFocusKey] = useState(null)
  const shellRef = useRef({ relatedTopics: [], suggestedActions: [] })

  // Ctrl+K / deep links: open a specific help topic on this page
  useEffect(() => {
    if (!open) return
    if (initialTopic) setFocusKey(initialTopic)
  }, [open, initialTopic])

  // Lock page scroll while help is open so the wheel only moves the drawer body
  useEffect(() => {
    if (!open) return undefined
    const prevOverflow = document.body.style.overflow
    const prevPaddingRight = document.body.style.paddingRight
    const scrollbarGap = window.innerWidth - document.documentElement.clientWidth
    document.body.style.overflow = 'hidden'
    if (scrollbarGap > 0) {
      document.body.style.paddingRight = `${scrollbarGap}px`
    }
    return () => {
      document.body.style.overflow = prevOverflow
      document.body.style.paddingRight = prevPaddingRight
    }
  }, [open])

  useEffect(() => {
    if (!open) {
      setFocusKey(null)
      shellRef.current = { relatedTopics: [], suggestedActions: [] }
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    if (!focusKey) {
      setTicketOpen(false)
      setTicketMsg(null)
      setTicketErr(null)
      setTicketForm({
        name: '',
        ticketKind: 'INCIDENT',
        priority: 'NORMAL',
      })
    }

    const load = focusKey
      ? helpApi.get(focusKey, i18n.language).then((d) => ({
          route: location.pathname,
          context: { path: location.pathname, kind: 'topic' },
          topic: {
            helpKey: d.helpKey,
            title: d.title,
            routePattern: d.routePattern,
            family: d.family,
          },
          body: d.body,
          relatedTopics: shellRef.current.relatedTopics,
          suggestedActions: shellRef.current.suggestedActions,
        }))
      : helpApi
          .resolve(
            helpContext
              ? {
                  path: helpContext.path || location.pathname,
                  desk: helpContext.desk,
                  tab: helpContext.tab,
                  resource: helpContext.resource,
                  objectId: helpContext.objectId,
                  status: helpContext.status,
                  processCode: helpContext.processCode,
                }
              : location.pathname,
            i18n.language,
          )
          .then((d) => {
            shellRef.current = {
              relatedTopics: d.relatedTopics || [],
              suggestedActions: d.suggestedActions || [],
            }
            return d
          })

    load
      .then((d) => {
        if (!cancelled) setData(d)
      })
      .catch((e) => {
        if (!cancelled) setError(e.message || String(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open, location.pathname, i18n.language, focusKey, helpContext])

  if (!open) return null

  // Strip markdown ## Related — drawer renders relatedTopics once below
  const body = stripRelatedSection(data?.body?.bodyText || '')
  const title = data?.topic?.title || t('title')
  const ctx = data?.context || {}
  const actions = data?.suggestedActions || []
  const related = data?.relatedTopics || []
  const unboundDomain =
    !!data?.unbound ||
    (ctx.kind === 'domain' && isUnboundDataProduct(ctx.package, ctx.resource))

  const contextBits = []
  if (helpContext?.desk || helpContext?.tab || helpContext?.resource) {
    if (helpContext.desk) contextBits.push(helpContext.desk)
    if (helpContext.tab) contextBits.push(helpContext.tab)
    if (helpContext.resource) contextBits.push(helpContext.resource)
    if (helpContext.status) contextBits.push(helpContext.status)
  } else if (ctx.kind === 'domain') {
    contextBits.push(ctx.package, ctx.resource, ctx.mode)
  } else if (ctx.desk || ctx.tab) {
    if (ctx.desk) contextBits.push(ctx.desk)
    if (ctx.tab) contextBits.push(ctx.tab)
    if (ctx.resource) contextBits.push(ctx.resource)
    if (ctx.status) contextBits.push(ctx.status)
  } else {
    contextBits.push(location.pathname)
  }

  const defaultSubject = () => {
    const bits = contextBits.filter(Boolean).join(' · ')
    return bits ? `Help: ${bits}` : 'IT support request'
  }

  const submitTicket = async (e) => {
    e.preventDefault()
    setTicketBusy(true)
    setTicketErr(null)
    setTicketMsg(null)
    try {
      const subject = (ticketForm.name || defaultSubject()).trim()
      const ctxLine = [
        helpContext?.desk && `desk=${helpContext.desk}`,
        helpContext?.tab && `tab=${helpContext.tab}`,
        helpContext?.resource && `resource=${helpContext.resource}`,
        helpContext?.objectId != null && `id=${helpContext.objectId}`,
        helpContext?.status && `status=${helpContext.status}`,
        `path=${helpContext?.path || location.pathname}`,
      ]
        .filter(Boolean)
        .join(' ')
      const ticket = await digitalApi.tickets.create({
        name: subject,
        ticketKind: ticketForm.ticketKind,
        priority: ticketForm.priority,
        description: ctxLine || undefined,
        impactNote: ctxLine || undefined,
      })
      setTicketMsg(
        t('ticketCreated', {
          defaultValue: 'Ticket {{number}} opened.',
          number: ticket.ticketNumber || ticket.id,
        }),
      )
      setTicketForm({ name: '', ticketKind: 'INCIDENT', priority: 'NORMAL' })
    } catch (err) {
      setTicketErr(err.message || String(err))
    } finally {
      setTicketBusy(false)
    }
  }

  return (
    <div
      className="help-drawer-backdrop"
      onClick={onClose}
      role="presentation"
      onWheel={(e) => e.stopPropagation()}
    >
      <aside
        className="help-drawer"
        onClick={(e) => e.stopPropagation()}
        onWheel={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={t('title', { defaultValue: 'Help' })}
      >
        <header className="help-drawer-header">
          <div>
            <div className="help-drawer-kicker">{contextBits.join(' · ')}</div>
            <h2 style={{ margin: 0 }}>{title}</h2>
            {data?.topic?.synthetic && (
              <span className="badge badge-draft" style={{ marginTop: 6 }}>
                {t('generated')}
              </span>
            )}
          </div>
          <button type="button" className="btn btn-ghost" onClick={onClose} aria-label={t('title')}>
            <X size={18} />
          </button>
        </header>
        <div className="help-drawer-body">
          {loading && <p className="muted">{t('loading')}</p>}
          {error && <div className="setup-error">{error}</div>}
          {!loading && unboundDomain && (
            <div className="help-unbound-banner" role="status">
              <AlertTriangle size={18} aria-hidden="true" />
              <div>
                <strong>
                  {t('unboundTitle', { defaultValue: 'Unbound domain' })}
                </strong>
                <p>
                  {t('unboundBody', {
                    defaultValue:
                      'This data product is for reference and future use only. It does not currently drive any business logic on desks or processes.',
                  })}
                </p>
              </div>
            </div>
          )}
          {!loading && body && (
            <div
              className="help-markdown"
              dangerouslySetInnerHTML={{ __html: markdownToHtml(body) }}
            />
          )}
          {!loading && !body && !error && <p className="muted">{t('empty')}</p>}

          {actions.length > 0 && (
            <div style={{ marginTop: 20 }}>
              <div className="section-label">{t('actions')}</div>
              <div className="help-actions">
                {actions.map((a) =>
                  a.external || a.path?.startsWith('/tour') ? (
                    <a key={a.path + a.label} className="btn btn-secondary" href={a.path}>
                      {a.label} <ExternalLink size={12} style={{ marginLeft: 4 }} />
                    </a>
                  ) : (
                    <Link
                      key={a.path + a.label}
                      className="btn btn-secondary"
                      to={a.path}
                      onClick={onClose}
                    >
                      {a.label}
                    </Link>
                  ),
                )}
              </div>
            </div>
          )}

          {related.length > 0 && (
            <div style={{ marginTop: 20 }}>
              <div className="section-label">{t('related')}</div>
              <div className="help-actions help-related">
                {related.map((topic) => (
                  <button
                    key={topic.helpKey}
                    type="button"
                    className="btn btn-ghost help-related-link"
                    title={topic.routePattern || topic.helpKey}
                    onClick={() => setFocusKey(topic.helpKey)}
                  >
                    {topic.title}
                  </button>
                ))}
              </div>
              {focusKey && (
                <button
                  type="button"
                  className="btn btn-ghost"
                  style={{ marginTop: 8 }}
                  onClick={() => setFocusKey(null)}
                >
                  {t('backToContext', { defaultValue: 'Back to page help' })}
                </button>
              )}
            </div>
          )}

          <div className="help-it-support" style={{ marginTop: 20 }}>
            <div className="section-label">{t('itSupport', { defaultValue: 'IT Support' })}</div>
            <p className="muted" style={{ fontSize: 13, marginTop: 0, marginBottom: 0 }}>
              {t('itSupportHint', {
                defaultValue:
                  'Need help from IT? Open an internal ticket from this page context, or go to IT Support.',
              })}
            </p>
            <div className="help-actions">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => {
                  setTicketOpen((v) => !v)
                  if (!ticketForm.name) {
                    setTicketForm((f) => ({ ...f, name: defaultSubject() }))
                  }
                }}
              >
                <Ticket size={14} />{' '}
                {ticketOpen
                  ? t('ticketHideForm', { defaultValue: 'Hide ticket form' })
                  : t('ticketCreate', { defaultValue: 'Create IT support ticket' })}
              </button>
              <Link
                className="btn btn-secondary"
                to="/help/it-support"
                onClick={onClose}
              >
                <Plus size={14} /> {t('ticketFullPage', { defaultValue: 'IT Support page' })}
              </Link>
            </div>
            {ticketOpen && (
              <form className="form-grid" style={{ marginTop: 12 }} onSubmit={submitTicket}>
                <label>
                  {t('ticketSubject', { defaultValue: 'Subject' })}
                  <input
                    required
                    value={ticketForm.name}
                    onChange={(e) => setTicketForm((f) => ({ ...f, name: e.target.value }))}
                  />
                </label>
                <div className="form-row-2">
                  <label>
                    Kind
                    <select
                      value={ticketForm.ticketKind}
                      onChange={(e) =>
                        setTicketForm((f) => ({ ...f, ticketKind: e.target.value }))
                      }
                    >
                      <option value="INCIDENT">INCIDENT</option>
                      <option value="REQUEST">REQUEST</option>
                      <option value="CHANGE">CHANGE</option>
                    </select>
                  </label>
                  <label>
                    Priority
                    <select
                      value={ticketForm.priority}
                      onChange={(e) =>
                        setTicketForm((f) => ({ ...f, priority: e.target.value }))
                      }
                    >
                      {['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((p) => (
                        <option key={p} value={p}>
                          {p}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                {ticketErr && <p style={{ color: 'var(--danger, #b00020)' }}>{ticketErr}</p>}
                {ticketMsg && <p className="muted">{ticketMsg}</p>}
                <button type="submit" className="btn btn-primary" disabled={ticketBusy}>
                  {ticketBusy
                    ? t('loading')
                    : t('ticketSubmit', { defaultValue: 'Open ticket' })}
                </button>
                {ticketMsg && (
                  <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => {
                      onClose()
                      navigate('/help/it-support')
                    }}
                  >
                    {t('ticketViewAll', { defaultValue: 'View all tickets' })}
                  </button>
                )}
              </form>
            )}
          </div>

          <p className="muted" style={{ marginTop: 16, fontSize: 13 }}>
            <Link to="/help" onClick={onClose}>
              {t('center')}
            </Link>
          </p>
        </div>
      </aside>
    </div>
  )
}
