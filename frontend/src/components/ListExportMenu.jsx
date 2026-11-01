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

import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Download, ChevronDown } from 'lucide-react'
import {
  downloadBlob,
  fetchAllListRows,
  rowsToCsv,
  rowsToExcelXml,
} from '../utils/tableExport'

/**
 * Toolbar control: Download → CSV | Excel. Fetches all matching rows via `fetcher`.
 *
 * @param {object} props
 * @param {(args: {max:number,offset:number}) => Promise<{items?: object[], total?: number}>} props.fetcher
 * @param {object} [props.filters] — sort/q forwarded to fetcher
 * @param {{ key: string, label: string }[]} props.columns
 * @param {string} [props.fileBase] — filename stem without extension
 * @param {string} [props.sheetName]
 * @param {boolean} [props.disabled]
 */
export default function ListExportMenu({
  fetcher,
  filters = {},
  columns,
  fileBase = 'export',
  sheetName = 'Export',
  disabled = false,
}) {
  const { t } = useTranslation('common')
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const rootRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const onDoc = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) setOpen(false)
    }
    const onKey = (e) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDoc)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const runExport = async (format) => {
    if (busy || !fetcher || !columns?.length) return
    setBusy(true)
    setOpen(false)
    try {
      const records = await fetchAllListRows(fetcher, filters)
      const headers = columns.map((c) => c.label)
      const rows = records.map((rec) => columns.map((c) => rec?.[c.key]))
      const stamp = new Date().toISOString().slice(0, 10)
      const base = `${fileBase}-${stamp}`
      if (format === 'csv') {
        downloadBlob(rowsToCsv(headers, rows), `${base}.csv`, 'text/csv;charset=utf-8')
      } else {
        downloadBlob(
          rowsToExcelXml(headers, rows, sheetName),
          `${base}.xls`,
          'application/vnd.ms-excel',
        )
      }
    } catch (e) {
      // Surface via alert — DomainList can replace with banner later on rollout
      window.alert(e?.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="list-export-menu" ref={rootRef}>
      <button
        type="button"
        className="btn"
        disabled={disabled || busy || !columns?.length}
        aria-haspopup="menu"
        aria-expanded={open}
        title={t('actions.download', { defaultValue: 'Download' })}
        onClick={() => setOpen((v) => !v)}
      >
        <Download size={16} />{' '}
        {busy
          ? t('actions.exporting', { defaultValue: 'Exporting…' })
          : t('actions.download', { defaultValue: 'Download' })}{' '}
        <ChevronDown size={14} aria-hidden />
      </button>
      {open && (
        <div className="list-export-menu-panel" role="menu">
          <button
            type="button"
            role="menuitem"
            className="list-export-menu-item"
            onClick={() => runExport('csv')}
          >
            {t('actions.downloadCsv', { defaultValue: 'CSV (.csv)' })}
          </button>
          <button
            type="button"
            role="menuitem"
            className="list-export-menu-item"
            onClick={() => runExport('excel')}
          >
            {t('actions.downloadExcel', { defaultValue: 'Excel (.xls)' })}
          </button>
        </div>
      )}
    </div>
  )
}
