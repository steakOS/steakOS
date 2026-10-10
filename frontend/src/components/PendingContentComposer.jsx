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

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link2, Paperclip, FileText, Plus, Trash2 } from 'lucide-react'
import { contentApi } from '../api/client'

/**
 * Queue companion content before a host record exists (create-time Content Rail).
 * Quiet chrome: Link · File · Content only (no title/hint/filters/refresh).
 * Call attachPendingContent(pkg, resource, hostId, pending) after create.
 *
 * pending item shapes:
 *  { kind:'link', title, url, key }
 *  { kind:'body', contentTypeCode, title, bodyText, key }
 *  { kind:'file', file: File, key }
 */
export default function PendingContentComposer({
  package: pkg,
  resource,
  pending,
  onChange,
}) {
  const { t } = useTranslation('content')
  const [allowed, setAllowed] = useState(['Note'])
  const [mode, setMode] = useState(null)
  const [linkForm, setLinkForm] = useState({ title: '', url: '' })
  const [bodyForm, setBodyForm] = useState({
    contentTypeCode: 'Note',
    title: '',
    bodyText: '',
  })

  useEffect(() => {
    if (!pkg || !resource) return
    contentApi
      .profile(pkg, resource)
      .then((p) => {
        const codes = p.allowedTypeCodes || ['Note']
        setAllowed(codes)
        setBodyForm((f) => ({
          ...f,
          contentTypeCode: codes.includes(f.contentTypeCode) ? f.contentTypeCode : codes[0],
        }))
      })
      .catch(() => setAllowed(['Note']))
  }, [pkg, resource])

  const add = (item) => onChange([...(pending || []), { ...item, key: `${Date.now()}-${Math.random()}` }])
  const remove = (key) => onChange((pending || []).filter((p) => p.key !== key))

  return (
    <div className="pending-content-composer">
      <div className="toolbar content-rail-actions" style={{ marginBottom: 0 }}>
        <button
          type="button"
          className={`btn ${mode === 'link' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setMode(mode === 'link' ? null : 'link')}
        >
          <Link2 size={14} aria-hidden />
          <span>{t('rail.link')}</span>
        </button>
        <label className="btn btn-ghost content-rail-file-btn">
          <Paperclip size={14} aria-hidden />
          <span>{t('rail.file')}</span>
          <input
            type="file"
            className="content-rail-file-input"
            onChange={(e) => {
              const file = e.target.files?.[0]
              e.target.value = ''
              if (!file) return
              add({ kind: 'file', file, name: file.name })
            }}
          />
        </label>
        <button
          type="button"
          className={`btn ${mode === 'body' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setMode(mode === 'body' ? null : 'body')}
        >
          <FileText size={14} aria-hidden />
          <span>{t('rail.content')}</span>
        </button>
      </div>

      {mode === 'link' && (
        <div className="form-grid" style={{ marginTop: 8 }}>
          <label>
            {t('pending.url')}
            <input
              type="url"
              value={linkForm.url}
              onChange={(e) => setLinkForm((f) => ({ ...f, url: e.target.value }))}
              placeholder={t('pending.urlPlaceholder')}
            />
          </label>
          <label>
            {t('pending.titleField')}
            <input
              value={linkForm.title}
              onChange={(e) => setLinkForm((f) => ({ ...f, title: e.target.value }))}
              placeholder={t('pending.titlePlaceholder')}
            />
          </label>
          <button
            type="button"
            className="btn btn-primary"
            disabled={!linkForm.url.trim()}
            onClick={() => {
              add({ kind: 'link', ...linkForm })
              setLinkForm({ title: '', url: '' })
              setMode(null)
            }}
          >
            <Plus size={14} /> {t('pending.queueLink')}
          </button>
        </div>
      )}

      {mode === 'body' && (
        <div className="form-grid" style={{ marginTop: 8 }}>
          <div className="form-row-2">
            <label>
              {t('pending.type')}
              <select
                value={bodyForm.contentTypeCode}
                onChange={(e) =>
                  setBodyForm((f) => ({ ...f, contentTypeCode: e.target.value }))
                }
              >
                {allowed.map((code) => (
                  <option key={code} value={code}>
                    {code}
                  </option>
                ))}
              </select>
            </label>
            <label>
              {t('pending.titleField')}
              <input
                value={bodyForm.title}
                onChange={(e) => setBodyForm((f) => ({ ...f, title: e.target.value }))}
                placeholder={t('pending.titleExample')}
              />
            </label>
          </div>
          <label>
            {t('pending.bodyMd')}
            <textarea
              rows={4}
              value={bodyForm.bodyText}
              onChange={(e) => setBodyForm((f) => ({ ...f, bodyText: e.target.value }))}
              placeholder={t('pending.bodyPlaceholder')}
            />
          </label>
          <button
            type="button"
            className="btn btn-primary"
            disabled={!bodyForm.bodyText.trim()}
            onClick={() => {
              add({ kind: 'body', ...bodyForm })
              setBodyForm({
                contentTypeCode: allowed[0] || 'Note',
                title: '',
                bodyText: '',
              })
              setMode(null)
            }}
          >
            <Plus size={14} /> {t('pending.queueNote')}
          </button>
        </div>
      )}

      {(pending || []).length > 0 && (
        <ul className="pending-content-list">
          {pending.map((p) => (
            <li key={p.key}>
              <span className="badge badge-draft">
                {p.kind === 'link' && 'LINK'}
                {p.kind === 'file' && 'FILE'}
                {p.kind === 'body' && (p.contentTypeCode || 'BODY')}
              </span>{' '}
              {p.kind === 'link' && (p.title || p.url)}
              {p.kind === 'file' && (p.file?.name || p.name)}
              {p.kind === 'body' && (p.title || p.bodyText?.slice(0, 60) || t('pending.noteFallback'))}
              <button
                type="button"
                className="btn btn-ghost"
                style={{ marginLeft: 6 }}
                onClick={() => remove(p.key)}
                title={t('pending.remove')}
              >
                <Trash2 size={12} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * Flush pending items to a saved host id.
 * @returns {{ ok: number, errors: string[] }}
 */
export async function attachPendingContent(pkg, resource, hostId, pending) {
  if (!hostId || !pending?.length) return { ok: 0, errors: [] }
  let ok = 0
  const errors = []
  for (const p of pending) {
    try {
      if (p.kind === 'link') {
        await contentApi.addLink(pkg, resource, hostId, { title: p.title, url: p.url })
      } else if (p.kind === 'body') {
        await contentApi.addBody(pkg, resource, hostId, {
          contentTypeCode: p.contentTypeCode || 'Note',
          title: p.title,
          bodyText: p.bodyText,
        })
      } else if (p.kind === 'file' && p.file) {
        await contentApi.addFile(pkg, resource, hostId, p.file)
      }
      ok += 1
    } catch (e) {
      errors.push(e.message || String(e))
    }
  }
  return { ok, errors }
}
