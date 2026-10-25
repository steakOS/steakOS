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

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Handshake, RefreshCw, Plus } from 'lucide-react'
import { stakeholderApi } from '../api/client'
import DeskShell from '../components/desk/DeskShell'
import DeskNewFab from '../components/DeskNewFab'
import ConfirmDialog from '../components/ConfirmDialog'
import useDeskSelection, { scrollSelectedIntoView } from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import useNarrowViewport from '../hooks/useNarrowViewport'
import { entityDisplayLabel, labelFromList } from '../utils/entityDisplayLabel'

const PARTY_KINDS = [
  'REGULATOR',
  'COMMUNITY',
  'MEDIA',
  'PARTNER',
  'NGO',
  'INVESTOR',
  'OTHER',
]
const PARTY_STATUSES = ['ACTIVE', 'INACTIVE', 'WATCH']
const CHANNELS = ['EMAIL', 'MEETING', 'CALL', 'PRESS', 'OTHER']
const DIRECTIONS = ['IN', 'OUT', 'INTERNAL']

const emptyParty = () => ({
  name: '',
  partyKind: 'OTHER',
  status: 'ACTIVE',
  contactName: '',
  contactEmail: '',
  ethicsNote: '',
  description: '',
})
const emptyComms = () => ({
  stakeholderPartyId: '',
  subject: '',
  summary: '',
  channel: 'EMAIL',
  direction: 'OUT',
  occurredAt: '',
  status: 'LOGGED',
})

function isSilverGate(e) {
  if (!e || e.status !== 403) return false
  const code = e.code || e.body?.code || ''
  return code === 'STAKEHOLDER_REQUIRES_SILVER' || /REQUIRES_SILVER/i.test(code)
}

function toDateInput(v) {
  if (v == null || v === '') return ''
  const d = typeof v === 'number' ? new Date(v) : new Date(v)
  if (Number.isNaN(d.getTime())) return String(v).slice(0, 10)
  return d.toISOString().slice(0, 10)
}

function statusClass(s) {
  if (['ACTIVE', 'LOGGED'].includes(s)) return 'badge badge-ok'
  if (['INACTIVE'].includes(s)) return 'badge badge-muted'
  if (['WATCH'].includes(s)) return 'badge badge-warn'
  return 'badge badge-draft'
}

/**
 * StakeholderBasics desk (G7) — DeskShell master–detail (ADR-047 / Nielsen).
 */
export default function StakeholderDesk() {
  const { t } = useTranslation(['desks', 'common'])
  const narrow = useNarrowViewport()

  const [tab, setTab] = useState(() => {
    if (typeof window === 'undefined') return 'parties'
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    return urlTab === 'comms' ? 'comms' : 'parties'
  })
  const [selectedId, setSelectedId] = useState(() => {
    if (typeof window === 'undefined') return null
    return new URLSearchParams(window.location.search).get('id') || null
  })
  const [newExpanded, setNewExpanded] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [pendingNav, setPendingNav] = useState(null)
  const [companyId] = useState('default')

  const { saveMemory } = useDeskMemory({ deskKey: 'stakeholder', companyId })

  useDeskSelection({
    deskKey: 'stakeholder',
    defaultTab: 'parties',
    tab,
    setTab,
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

  const [parties, setParties] = useState([])
  const [comms, setComms] = useState([])
  const [partyTotal, setPartyTotal] = useState(0)
  const [commsTotal, setCommsTotal] = useState(0)
  const [selected, setSelected] = useState(null)
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({})
  const [partyForm, setPartyForm] = useState(emptyParty)
  const [commsForm, setCommsForm] = useState(emptyComms)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [silverGate, setSilverGate] = useState(false)

  const tabs = useMemo(
    () => [
      { id: 'parties', label: t('stakeholder.parties', { defaultValue: 'Parties' }) },
      { id: 'comms', label: t('stakeholder.comms', { defaultValue: 'Communications' }) },
    ],
    [t],
  )

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [p, c] = await Promise.all([
        stakeholderApi.parties.list({ max: 100, offset: 0 }),
        stakeholderApi.comms.list({ max: 50, offset: 0 }),
      ])
      setParties(p?.items || [])
      setPartyTotal(p?.total ?? (p?.items || []).length)
      setComms(c?.items || [])
      setCommsTotal(c?.total ?? (c?.items || []).length)
      setSilverGate(false)
    } catch (e) {
      setParties([])
      setComms([])
      setPartyTotal(0)
      setCommsTotal(0)
      if (isSilverGate(e)) {
        setSilverGate(true)
        setError(null)
      } else {
        setSilverGate(false)
        setError(e.message || String(e))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const items = tab === 'parties' ? parties : comms
  const total = tab === 'parties' ? partyTotal : commsTotal

  useEffect(() => {
    setEditing(false)
    if (!selectedId) {
      setSelected(null)
      return
    }
    const row = items.find((x) => String(x.id) === String(selectedId))
    if (row) setSelected(row)
    else if (!loading) setSelected(null)
  }, [tab, selectedId, items, loading])

  useEffect(() => {
    if (!selectedId) return
    const id = window.requestAnimationFrame(() => {
      scrollSelectedIntoView('.desk-shell-list')
    })
    return () => window.cancelAnimationFrame(id)
  }, [selectedId, selected?.id, tab])

  const run = async (fn) => {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      await fn()
      await load()
    } catch (e) {
      if (isSilverGate(e)) {
        setSilverGate(true)
        setError(null)
      } else setError(e.message || String(e))
    } finally {
      setBusy(null)
    }
  }

  const partyName = (id) => labelFromList(parties, id)

  const confirmDelete = (label) =>
    window.confirm(
      t('actions.confirmDelete', { ns: 'common', label, defaultValue: `Delete ${label}?` }),
    )

  const resetCreateForms = useCallback(() => {
    setPartyForm(emptyParty())
    setCommsForm(emptyComms())
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'parties') {
      const d = emptyParty()
      return Boolean(
        (partyForm.name || '').trim() ||
          partyForm.partyKind !== d.partyKind ||
          partyForm.status !== d.status ||
          (partyForm.contactName || '').trim() ||
          (partyForm.contactEmail || '').trim() ||
          (partyForm.ethicsNote || '').trim() ||
          (partyForm.description || '').trim(),
      )
    }
    return Boolean(
      commsForm.stakeholderPartyId ||
        (commsForm.subject || '').trim() ||
        (commsForm.summary || '').trim() ||
        commsForm.occurredAt,
    )
  }, [tab, partyForm, commsForm])

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
      setNewExpanded(false)
      resetCreateForms()
      setSelectedId(null)
      setTab(nextTab)
    },
    [tab, newExpanded, createDirty, resetCreateForms],
  )

  const keyboardIds = useMemo(() => items.map((i) => i.id), [items])

  useDeskKeyboard({
    enabled: !newExpanded && !silverGate,
    itemIds: keyboardIds,
    selectedId,
    setSelectedId,
    onToggleNew: requestToggleNew,
  })

  const confirmDiscard = useCallback(() => {
    const nav = pendingNav
    setDiscardOpen(false)
    setPendingNav(null)
    applyCollapseCreate()
    if (nav?.type === 'tab' && nav.tab) {
      setSelectedId(null)
      setTab(nav.tab)
    }
  }, [pendingNav, applyCollapseCreate])

  const cancelDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
  }, [])

  const partyFields = (form, setForm) => (
    <>
      <label>
        {t('stakeholder.fieldName', { defaultValue: 'Name' })} *
        <input
          required
          value={form.name || ''}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          {t('stakeholder.fieldKind', { defaultValue: 'Kind' })}
          <select
            value={form.partyKind || 'OTHER'}
            onChange={(e) => setForm((f) => ({ ...f, partyKind: e.target.value }))}
          >
            {PARTY_KINDS.map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('stakeholder.fieldStatus', { defaultValue: 'Status' })}
          <select
            value={form.status || 'ACTIVE'}
            onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
          >
            {PARTY_STATUSES.map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="form-row-2">
        <label>
          {t('stakeholder.fieldContactName', { defaultValue: 'Contact name' })}
          <input
            value={form.contactName || ''}
            onChange={(e) => setForm((f) => ({ ...f, contactName: e.target.value }))}
          />
        </label>
        <label>
          {t('stakeholder.fieldContactEmail', { defaultValue: 'Contact email' })}
          <input
            type="email"
            value={form.contactEmail || ''}
            onChange={(e) => setForm((f) => ({ ...f, contactEmail: e.target.value }))}
          />
        </label>
      </div>
      <label>
        {t('stakeholder.fieldDescription', { defaultValue: 'Description' })}
        <textarea
          rows={2}
          value={form.description || ''}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
      <label>
        {t('stakeholder.fieldEthics', { defaultValue: 'Ethics note' })}
        <textarea
          rows={2}
          value={form.ethicsNote || ''}
          onChange={(e) => setForm((f) => ({ ...f, ethicsNote: e.target.value }))}
        />
      </label>
    </>
  )

  const partySelect = (value, onChange) => (
    <label>
      {t('stakeholder.fieldParty', { defaultValue: 'Party' })} *
      <select required value={value || ''} onChange={onChange}>
        <option value="">{t('stakeholder.pickParty', { defaultValue: 'Select party…' })}</option>
        {parties.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name}
          </option>
        ))}
      </select>
    </label>
  )

  const commsFields = (form, setForm) => (
    <>
      {partySelect(form.stakeholderPartyId, (e) =>
        setForm((f) => ({ ...f, stakeholderPartyId: e.target.value })),
      )}
      <label>
        {t('stakeholder.fieldSubject', { defaultValue: 'Subject' })} *
        <input
          required
          value={form.subject || ''}
          onChange={(e) => setForm((f) => ({ ...f, subject: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          {t('stakeholder.fieldChannel', { defaultValue: 'Channel' })}
          <select
            value={form.channel || 'EMAIL'}
            onChange={(e) => setForm((f) => ({ ...f, channel: e.target.value }))}
          >
            {CHANNELS.map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('stakeholder.fieldDirection', { defaultValue: 'Direction' })}
          <select
            value={form.direction || 'OUT'}
            onChange={(e) => setForm((f) => ({ ...f, direction: e.target.value }))}
          >
            {DIRECTIONS.map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label>
        {t('stakeholder.fieldOccurredAt', { defaultValue: 'Occurred at' })}
        <input
          type="date"
          value={form.occurredAt || ''}
          onChange={(e) => setForm((f) => ({ ...f, occurredAt: e.target.value }))}
        />
      </label>
      <label>
        {t('stakeholder.fieldSummary', { defaultValue: 'Summary' })}
        <textarea
          rows={2}
          value={form.summary || ''}
          onChange={(e) => setForm((f) => ({ ...f, summary: e.target.value }))}
        />
      </label>
    </>
  )

  const detailActions = (label, onEdit, onDelete) => (
    <div className="toolbar">
      <button type="button" className="btn" disabled={!!busy} onClick={onEdit}>
        {t('actions.edit', { ns: 'common', defaultValue: 'Edit' })}
      </button>
      <button
        type="button"
        className="btn btn-ghost"
        disabled={!!busy}
        onClick={() => {
          if (!confirmDelete(label)) return
          onDelete()
        }}
      >
        {t('actions.delete', { ns: 'common', defaultValue: 'Delete' })}
      </button>
    </div>
  )

  const editToolbar = () => (
    <div className="toolbar">
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        {t('actions.save', { ns: 'common', defaultValue: 'Save' })}
      </button>
      <button
        type="button"
        className="btn btn-ghost"
        onClick={() => setEditing(false)}
        disabled={!!busy}
      >
        {t('actions.cancel', { ns: 'common', defaultValue: 'Cancel' })}
      </button>
    </div>
  )

  const hasSelection = Boolean(selected?.id)

  const helpContext = useMemo(() => {
    const base = { desk: 'stakeholder', tab }
    if (selected) {
      return {
        ...base,
        resource: tab === 'parties' ? 'StakeholderParty' : 'StakeholderComms',
        objectId: selected.id,
        status: selected.status,
      }
    }
    return base
  }, [tab, selected])

  const newLabel =
    tab === 'parties'
      ? t('stakeholder.newParty', { defaultValue: 'New party' })
      : t('stakeholder.newComms', { defaultValue: 'Log communication' })

  const newPanel =
    tab === 'parties' ? (
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run(async () => {
            const created = await stakeholderApi.parties.create(partyForm)
            setInfo(
              t('toast.namedCreated', {
                ns: 'common',
                what: t('stakeholder.partyLabel', { defaultValue: 'Party' }),
                name: created.name,
              }),
            )
            resetCreateForms()
            setNewExpanded(false)
            setSelectedId(String(created.id))
          })
        }}
      >
        {partyFields(partyForm, setPartyForm)}
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('stakeholder.createParty', { defaultValue: 'Create party' })}
        </button>
      </form>
    ) : (
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run(async () => {
            const created = await stakeholderApi.comms.create({
              stakeholderPartyId: Number(commsForm.stakeholderPartyId),
              subject: commsForm.subject,
              summary: commsForm.summary || null,
              channel: commsForm.channel,
              direction: commsForm.direction,
              occurredAt: commsForm.occurredAt || null,
              status: 'LOGGED',
            })
            setInfo(
              t('toast.namedCreated', {
                ns: 'common',
                what: t('stakeholder.commsLabel', { defaultValue: 'Communication' }),
                name: created.subject,
              }),
            )
            resetCreateForms()
            setNewExpanded(false)
            setSelectedId(String(created.id))
          })
        }}
      >
        {commsFields(commsForm, setCommsForm)}
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} />{' '}
          {t('stakeholder.createComms', { defaultValue: 'Log communication' })}
        </button>
      </form>
    )

  const listTable = (cols, emptyKey, emptyDefault) =>
    items.length === 0 ? (
      <p className="muted">{t(emptyKey, { defaultValue: emptyDefault })}</p>
    ) : (
      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              {cols.map((c) => (
                <th key={c.key}>{c.label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr
                key={row.id}
                data-selected={selected?.id === row.id ? 'true' : undefined}
                className={selected?.id === row.id ? 'row-selected' : 'row-click'}
                onClick={() => setSelectedId(String(row.id))}
              >
                {cols.map((c) => (
                  <td key={c.key}>{c.render(row)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )

  const list =
    tab === 'parties' ? (
      <>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {t('stakeholder.partiesList', { defaultValue: 'Parties' })} ({total})
        </h2>
        {loading && !error && !silverGate ? (
          <p className="muted">{t('stakeholder.loading', { defaultValue: 'Loading…' })}</p>
        ) : (
          listTable(
            [
              {
                key: 'name',
                label: t('stakeholder.colName', { defaultValue: 'Name / subject' }),
                render: (r) => entityDisplayLabel(r),
              },
              {
                key: 'status',
                label: t('stakeholder.colStatus', { defaultValue: 'Status' }),
                render: (r) => <span className={statusClass(r.status)}>{r.status || '—'}</span>,
              },
              {
                key: 'kind',
                label: t('stakeholder.colKind', { defaultValue: 'Kind' }),
                render: (r) => r.partyKind || '—',
              },
            ],
            'stakeholder.emptyParties',
            'No parties yet. Register a stakeholder with New.',
          )
        )}
      </>
    ) : (
      <>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {t('stakeholder.commsList', { defaultValue: 'Communications' })} ({total})
        </h2>
        {loading && !error && !silverGate ? (
          <p className="muted">{t('stakeholder.loading', { defaultValue: 'Loading…' })}</p>
        ) : (
          listTable(
            [
              {
                key: 'subject',
                label: t('stakeholder.colName', { defaultValue: 'Name / subject' }),
                render: (r) => entityDisplayLabel(r),
              },
              {
                key: 'status',
                label: t('stakeholder.colStatus', { defaultValue: 'Status' }),
                render: (r) => <span className={statusClass(r.status)}>{r.status || '—'}</span>,
              },
              {
                key: 'channel',
                label: t('stakeholder.colChannel', { defaultValue: 'Channel' }),
                render: (r) => r.channel || '—',
              },
            ],
            'stakeholder.emptyComms',
            'No communications logged yet.',
          )
        )}
      </>
    )

  let detail = null
  if (selected && tab === 'parties') {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        {!editing && (
          <>
            <div className="inv-detail-hero">
              <div>
                <div className="about-display" style={{ fontSize: '1.25rem' }}>
                  {selected.name}
                </div>
                <div className="muted">
                  <span className={statusClass(selected.status)}>{selected.status}</span>
                  {' · '}
                  {selected.partyKind}
                  {selected.contactName ? ` · ${selected.contactName}` : ''}
                  {selected.contactEmail ? ` · ${selected.contactEmail}` : ''}
                </div>
              </div>
            </div>
            {selected.description && <p>{selected.description}</p>}
            {selected.ethicsNote && (
              <p className="muted">
                <strong>{t('stakeholder.fieldEthics', { defaultValue: 'Ethics note' })}:</strong>{' '}
                {selected.ethicsNote}
              </p>
            )}
            {detailActions(
              selected.name,
              () => {
                setEditForm({
                  name: selected.name || '',
                  partyKind: selected.partyKind || 'OTHER',
                  status: selected.status || 'ACTIVE',
                  contactName: selected.contactName || '',
                  contactEmail: selected.contactEmail || '',
                  ethicsNote: selected.ethicsNote || '',
                  description: selected.description || '',
                })
                setEditing(true)
              },
              () =>
                run(async () => {
                  await stakeholderApi.parties.delete(selected.id)
                  setSelectedId(null)
                  setInfo(
                    t('stakeholder.deleted', {
                      defaultValue: 'Deleted {{name}}',
                      name: selected.name,
                    }),
                  )
                }),
            )}
          </>
        )}
        {editing && (
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault()
              run(async () => {
                const updated = await stakeholderApi.parties.update(selected.id, editForm)
                setSelected(updated)
                setEditing(false)
                setInfo(
                  t('toast.namedStatus', {
                    ns: 'common',
                    what: t('stakeholder.partyLabel', { defaultValue: 'Party' }),
                    name: updated.name,
                    status: updated.status,
                  }),
                )
              })
            }}
          >
            {partyFields(editForm, setEditForm)}
            {editToolbar()}
          </form>
        )}
      </div>
    )
  } else if (selected && tab === 'comms') {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        {!editing && (
          <>
            <div className="inv-detail-hero">
              <div>
                <div className="about-display" style={{ fontSize: '1.25rem' }}>
                  {selected.subject}
                </div>
                <div className="muted">
                  <span className={statusClass(selected.status)}>{selected.status}</span>
                  {' · '}
                  {selected.channel} / {selected.direction}
                  {' · '}
                  {partyName(selected.stakeholderPartyId)}
                  {selected.occurredAt ? ` · ${toDateInput(selected.occurredAt)}` : ''}
                </div>
              </div>
            </div>
            {selected.summary && <p>{selected.summary}</p>}
            {detailActions(
              selected.subject,
              () => {
                setEditForm({
                  stakeholderPartyId: String(
                    selected.stakeholderPartyId || selected.partyId || '',
                  ),
                  subject: selected.subject || '',
                  summary: selected.summary || '',
                  channel: selected.channel || 'EMAIL',
                  direction: selected.direction || 'OUT',
                  occurredAt: toDateInput(selected.occurredAt),
                  status: selected.status || 'LOGGED',
                })
                setEditing(true)
              },
              () =>
                run(async () => {
                  await stakeholderApi.comms.delete(selected.id)
                  setSelectedId(null)
                  setInfo(
                    t('stakeholder.deleted', {
                      defaultValue: 'Deleted {{name}}',
                      name: selected.subject,
                    }),
                  )
                }),
            )}
          </>
        )}
        {editing && (
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault()
              run(async () => {
                const updated = await stakeholderApi.comms.update(selected.id, {
                  stakeholderPartyId: Number(editForm.stakeholderPartyId),
                  subject: editForm.subject,
                  summary: editForm.summary || null,
                  channel: editForm.channel,
                  direction: editForm.direction,
                  occurredAt: editForm.occurredAt || null,
                  status: editForm.status || 'LOGGED',
                })
                setSelected(updated)
                setEditing(false)
                setInfo(
                  t('toast.namedStatus', {
                    ns: 'common',
                    what: t('stakeholder.commsLabel', { defaultValue: 'Communication' }),
                    name: updated.subject,
                    status: updated.status,
                  }),
                )
              })
            }}
          >
            {commsFields(editForm, setEditForm)}
            {editToolbar()}
          </form>
        )}
      </div>
    )
  }

  return (
    <div className="page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      {silverGate && (
        <section className="card" style={{ marginBottom: '1rem' }}>
          <strong>
            {t('stakeholder.silverRequired', { defaultValue: 'Silver pack required' })}
          </strong>
          <p className="muted" style={{ margin: '0.5rem 0 0' }}>
            {t('stakeholder.silverRequiredBody', {
              defaultValue:
                'Stakeholder registers are part of the Silver pack. Upgrade this company to Silver (or use Iron eval) to manage parties and communications.',
            })}
          </p>
        </section>
      )}

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
        <button type="button" className="btn" onClick={load} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
      </div>

      {!silverGate && (
        <>
          <DeskShell
            title={
              <>
                <Handshake size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
                {t('stakeholder.title', { defaultValue: 'Stakeholders' })}
              </>
            }
            subtitle={t('stakeholder.subtitle', {
              defaultValue:
                'External parties, ethics/comms log, and light contract register. Silver pack / Iron eval.',
            })}
            tabs={tabs}
            activeTab={tab}
            onTabChange={requestTabChange}
            newLabel={newLabel}
            newExpanded={newExpanded}
            onToggleNew={requestToggleNew}
            newPanel={newPanel}
            list={list}
            hasSelection={hasSelection}
            emptyTitle={
              tab === 'parties'
                ? t('stakeholder.selectParty', {
                    defaultValue: 'Select a party from the list.',
                  })
                : t('stakeholder.selectComms', {
                    defaultValue: 'Select a communication from the list.',
                  })
            }
            emptyBody={t('desk.emptyBody', {
              ns: 'common',
              defaultValue: 'Choose a row from the list to see details.',
            })}
            detail={detail}
            helpContext={helpContext}
            narrowSheetOpen={narrow && hasSelection}
            onCloseSheet={() => setSelectedId(null)}
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
        </>
      )}
    </div>
  )
}
