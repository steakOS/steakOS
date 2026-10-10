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

import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Link2,
  Paperclip,
  FileText,
  RefreshCw,
  Plus,
  Pin,
  Trash2,
  Download,
  Eye,
  Pencil,
  X,
  Code2,
} from 'lucide-react'
import { contentApi } from '../api/client'
import { markdownToHtml, markdownPreview } from '../utils/markdown'

/**
 * Object Companion Content Rail (CT0 / CT8) — Link · File · typed BODY.
 */
export default function ContentRail({ package: pkg, resource, id, onChanged }) {
  const { t } = useTranslation(['content', 'common'])
  const [rail, setRail] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [busy, setBusy] = useState(null)
  const [mode, setMode] = useState(null) // link | body | edit | null
  const [editingId, setEditingId] = useState(null)
  const [viewing, setViewing] = useState(null) // ObjectContent item
  const [viewSource, setViewSource] = useState(false)
  const [linkForm, setLinkForm] = useState({ title: '', url: '' })
  const [bodyForm, setBodyForm] = useState({
    contentTypeCode: 'Note',
    title: '',
    bodyText: '',
  })

  const load = useCallback(async () => {
    if (!pkg || !resource || !id) return
    setError(null)
    try {
      const data = await contentApi.rail(pkg, resource, id)
      setRail(data)
      if (data?.allowedTypeCodes?.length && !data.allowedTypeCodes.includes(bodyForm.contentTypeCode)) {
        setBodyForm((f) => ({ ...f, contentTypeCode: data.allowedTypeCodes[0] || 'Note' }))
      }
      // refresh open viewer if still present
      setViewing((v) => {
        if (!v) return null
        const next = (data.items || []).find((x) => x.id === v.id)
        return next || null
      })
    } catch (e) {
      setError(e.message || String(e))
    }
  }, [pkg, resource, id])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    if (!viewing) return undefined
    const onKey = (e) => {
      if (e.key === 'Escape') setViewing(null)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [viewing])

  const run = async (label, fn) => {
    setBusy(label)
    setError(null)
    setInfo(null)
    try {
      await fn()
      await load()
      onChanged?.()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(null)
    }
  }

  if (!id || id === 'new') return null

  const items = rail?.items || []
  const allowed = rail?.allowedTypeCodes || ['Note']

  const openView = (it) => {
    setViewSource(false)
    setViewing(it)
  }

  const startEditBody = (it) => {
    if (!it || it.channel !== 'BODY') return
    setViewing(null)
    setEditingId(it.id)
    setBodyForm({
      contentTypeCode: it.contentTypeCode || allowed[0] || 'Note',
      title: it.title || it.name || '',
      bodyText: it.bodyText || '',
    })
    setMode('edit')
  }

  const cancelBodyForm = () => {
    setMode(null)
    setEditingId(null)
    setBodyForm({ contentTypeCode: allowed[0] || 'Note', title: '', bodyText: '' })
  }

  return (
    <section className="card content-rail" style={{ marginTop: '1.25rem' }}>
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      <div className="toolbar content-rail-actions" style={{ marginBottom: items.length || mode ? 8 : 0 }}>
        <button
          type="button"
          className={`btn ${mode === 'link' ? 'btn-primary' : 'btn-ghost'}`}
          disabled={!!busy}
          onClick={() => {
            setEditingId(null)
            setMode(mode === 'link' ? null : 'link')
          }}
        >
          <Link2 size={14} aria-hidden />
          <span>{t('rail.link')}</span>
        </button>
        <label className={`btn content-rail-file-btn ${busy === 'file' ? 'btn-primary' : 'btn-ghost'}`}>
          <Paperclip size={14} aria-hidden />
          <span>{t('rail.file')}</span>
          <input
            type="file"
            className="content-rail-file-input"
            disabled={!!busy}
            onChange={(e) => {
              const file = e.target.files?.[0]
              e.target.value = ''
              if (!file) return
              run('file', async () => {
                await contentApi.addFile(pkg, resource, id, file)
                setInfo(t('rail.uploaded', { name: file.name }))
              })
            }}
          />
        </label>
        <button
          type="button"
          className={`btn ${mode === 'body' ? 'btn-primary' : 'btn-ghost'}`}
          disabled={!!busy}
          onClick={() => {
            setEditingId(null)
            setBodyForm({ contentTypeCode: allowed[0] || 'Note', title: '', bodyText: '' })
            setMode(mode === 'body' ? null : 'body')
          }}
        >
          <FileText size={14} aria-hidden />
          <span>{t('rail.content')}</span>
        </button>
        <button type="button" className="btn btn-ghost" onClick={load} disabled={!!busy} title={t('actions.refresh', { ns: 'common' })}>
          <RefreshCw size={14} />
        </button>
      </div>

      {mode === 'link' && (
        <form
          className="form-grid"
          style={{ marginTop: 12 }}
          onSubmit={(e) => {
            e.preventDefault()
            run('link', async () => {
              await contentApi.addLink(pkg, resource, id, linkForm)
              setInfo(t('rail.linkAdded'))
              setLinkForm({ title: '', url: '' })
              setMode(null)
            })
          }}
        >
          <label>
            {t('rail.url')}
            <input
              required
              type="url"
              value={linkForm.url}
              onChange={(e) => setLinkForm((f) => ({ ...f, url: e.target.value }))}
              placeholder={t('rail.urlPlaceholder')}
            />
          </label>
          <label>
            {t('rail.titleField')}
            <input
              value={linkForm.title}
              onChange={(e) => setLinkForm((f) => ({ ...f, title: e.target.value }))}
            />
          </label>
          <button type="submit" className="btn btn-primary" disabled={!!busy}>
            <Plus size={14} /> {t('rail.addLink')}
          </button>
        </form>
      )}

      {(mode === 'body' || mode === 'edit') && (
        <form
          className="form-grid"
          style={{ marginTop: 12 }}
          onSubmit={(e) => {
            e.preventDefault()
            if (mode === 'edit' && editingId) {
              run('edit', async () => {
                await contentApi.update(editingId, {
                  title: bodyForm.title,
                  bodyText: bodyForm.bodyText,
                })
                setInfo(t('rail.updated'))
                cancelBodyForm()
              })
              return
            }
            run('body', async () => {
              await contentApi.addBody(pkg, resource, id, bodyForm)
              setInfo(t('rail.savedType', { type: bodyForm.contentTypeCode }))
              cancelBodyForm()
            })
          }}
        >
          {mode === 'edit' && (
            <p className="muted" style={{ margin: 0, fontSize: 13 }}>
              {t('rail.editingType', {
                type: bodyForm.contentTypeCode || 'Note',
                defaultValue: 'Editing {{type}}',
              })}
            </p>
          )}
          <div className="form-row-2">
            <label>
              {t('rail.type')}
              <select
                value={bodyForm.contentTypeCode}
                disabled={mode === 'edit'}
                onChange={(e) =>
                  setBodyForm((f) => ({ ...f, contentTypeCode: e.target.value }))
                }
              >
                {allowed.map((code) => (
                  <option key={code} value={code}>
                    {code}
                  </option>
                ))}
                {mode === 'edit' &&
                  bodyForm.contentTypeCode &&
                  !allowed.includes(bodyForm.contentTypeCode) && (
                    <option value={bodyForm.contentTypeCode}>{bodyForm.contentTypeCode}</option>
                  )}
              </select>
            </label>
            <label>
              {t('rail.titleField')}
              <input
                value={bodyForm.title}
                onChange={(e) => setBodyForm((f) => ({ ...f, title: e.target.value }))}
                placeholder={t('rail.titleOptional')}
              />
            </label>
          </div>
          <label>
            {t('rail.bodyMd')}
            <textarea
              required
              rows={5}
              value={bodyForm.bodyText}
              onChange={(e) => setBodyForm((f) => ({ ...f, bodyText: e.target.value }))}
              placeholder={t('rail.bodyPlaceholder')}
            />
          </label>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button type="submit" className="btn btn-primary" disabled={!!busy}>
              {mode === 'edit' ? (
                <>
                  <Pencil size={14} /> {t('rail.saveChanges')}
                </>
              ) : (
                <>
                  <Plus size={14} /> {t('rail.saveContent')}
                </>
              )}
            </button>
            <button type="button" className="btn btn-ghost" disabled={!!busy} onClick={cancelBodyForm}>
              {t('rail.cancelEdit')}
            </button>
          </div>
        </form>
      )}

      {items.length > 0 && (
        <div className="content-rail-list" style={{ marginTop: mode ? 8 : 0 }}>
          <table className="content-rail-table">
            <tbody>
              {items.map((it) => (
                <tr key={it.id}>
                  <td>
                    <span className="badge badge-draft">
                      {it.channel}
                      {it.contentTypeCode ? ` · ${it.contentTypeCode}` : ''}
                    </span>
                    {it.pinned ? ' 📌' : ''}
                  </td>
                  <td>
                    {it.channel === 'BODY' ? (
                      <button
                        type="button"
                        className="btn-link-title"
                        onClick={() => openView(it)}
                        title={t('rail.viewContent')}
                      >
                        {it.title || it.name || t('rail.untitled')}
                      </button>
                    ) : (
                      it.title || it.name
                    )}
                  </td>
                  <td className="muted" style={{ maxWidth: 280, fontSize: 13 }}>
                    {it.channel === 'LINK' && (
                      <a href={it.url} target="_blank" rel="noreferrer">
                        {it.url}
                      </a>
                    )}
                    {it.channel === 'FILE' && (
                      <span>
                        {it.fileName} ({it.byteSize != null ? `${it.byteSize} B` : '—'})
                      </span>
                    )}
                    {it.channel === 'BODY' && (
                      <button
                        type="button"
                        className="content-preview-btn"
                        onClick={() => openView(it)}
                        title={t('rail.viewFull')}
                      >
                        {markdownPreview(it.bodyText) || t('rail.emptyBody')}
                      </button>
                    )}
                  </td>
                  <td>
                    {it.channel === 'BODY' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        title={t('rail.view')}
                        onClick={() => openView(it)}
                      >
                        <Eye size={14} />
                      </button>
                    )}
                    {it.channel === 'BODY' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        title={t('rail.edit')}
                        disabled={!!busy}
                        onClick={() => startEditBody(it)}
                      >
                        <Pencil size={14} />
                      </button>
                    )}
                    {it.channel === 'FILE' && (
                      <a
                        className="btn btn-secondary"
                        href={contentApi.downloadUrl(it.id)}
                        target="_blank"
                        rel="noreferrer"
                      >
                        <Download size={14} />
                      </a>
                    )}
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={!!busy}
                      title={t('rail.pin')}
                      onClick={() =>
                        run('pin', async () => {
                          await contentApi.pin(it.id, !it.pinned)
                        })
                      }
                    >
                      <Pin size={14} />
                    </button>
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={!!busy}
                      onClick={() =>
                        run('del', async () => {
                          await contentApi.remove(it.id)
                          setInfo(t('rail.removed'))
                          if (viewing?.id === it.id) setViewing(null)
                        })
                      }
                    >
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {viewing && viewing.channel === 'BODY' && (
        <div
          className="error-dialog-backdrop content-view-backdrop"
          role="presentation"
          onClick={() => setViewing(null)}
        >
          <div
            className="content-view-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="content-view-title"
            onClick={(e) => e.stopPropagation()}
          >
            <header className="content-view-header">
              <div>
                <div className="content-view-meta">
                  <span className="badge badge-draft">
                    {viewing.contentTypeCode || 'BODY'}
                  </span>
                  {viewing.pinned ? (
                    <span className="muted" style={{ fontSize: 12 }}>
                      {t('rail.pinned')}
                    </span>
                  ) : null}
                </div>
                <h2 id="content-view-title" className="content-view-title">
                  {viewing.title || viewing.name || t('rail.title')}
                </h2>
              </div>
              <div className="content-view-actions">
                <button
                  type="button"
                  className={`btn btn-ghost ${viewSource ? 'btn-primary' : ''}`}
                  title={viewSource ? t('rail.renderedTitle') : t('rail.sourceTitle')}
                  onClick={() => setViewSource((v) => !v)}
                >
                  <Code2 size={16} /> {viewSource ? t('rail.rendered') : t('rail.source')}
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  title={t('rail.close')}
                  onClick={() => setViewing(null)}
                >
                  <X size={16} />
                </button>
              </div>
            </header>
            <div className="content-view-body">
              {viewSource ? (
                <pre className="content-view-source mono">{viewing.bodyText || ''}</pre>
              ) : (
                <div
                  className="content-md-body"
                  // Safe: markdownToHtml escapes raw HTML then applies MD
                  dangerouslySetInnerHTML={{
                    __html: markdownToHtml(viewing.bodyText || ''),
                  }}
                />
              )}
            </div>
            <footer className="content-view-footer">
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => startEditBody(viewing)}
              >
                <Pencil size={14} /> {t('rail.edit')}
              </button>
              <button type="button" className="btn" onClick={() => setViewing(null)}>
                {t('rail.close')}
              </button>
            </footer>
          </div>
        </div>
      )}
    </section>
  )
}
