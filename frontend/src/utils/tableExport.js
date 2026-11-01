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

/** Page size when walking DomainList export (matches server HARD_MAX). */
export const EXPORT_PAGE_SIZE = 100

/**
 * @param {unknown} value
 * @returns {string}
 */
export function cellToExportText(value) {
  if (value == null) return ''
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : ''
  if (typeof value === 'object') {
    if (value instanceof Date) return value.toISOString()
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }
  return String(value)
}

/**
 * @param {string} text
 * @returns {string}
 */
function csvEscape(text) {
  const s = String(text ?? '')
  if (/[",\r\n]/.test(s)) return `"${s.replace(/"/g, '""')}"`
  return s
}

/**
 * @param {string[]} headers
 * @param {Array<Array<unknown>>} rows
 * @returns {string}
 */
export function rowsToCsv(headers, rows) {
  const lines = [
    headers.map(csvEscape).join(','),
    ...rows.map((row) => row.map((c) => csvEscape(cellToExportText(c))).join(',')),
  ]
  // UTF-8 BOM so Excel opens non-ASCII correctly
  return `\uFEFF${lines.join('\r\n')}`
}

/**
 * SpreadsheetML 2003 XML — opens in Excel / LibreOffice as a workbook (.xls).
 * @param {string[]} headers
 * @param {Array<Array<unknown>>} rows
 * @param {string} [sheetName]
 * @returns {string}
 */
export function rowsToExcelXml(headers, rows, sheetName = 'Export') {
  const safeSheet = String(sheetName || 'Export')
    .replace(/[\\/*?[\]:]/g, '_')
    .slice(0, 31) || 'Export'
  const esc = (s) =>
    String(s ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')

  const cellXml = (v) => {
    const text = cellToExportText(v)
    if (text !== '' && /^-?\d+(\.\d+)?$/.test(text)) {
      return `<Cell><Data ss:Type="Number">${esc(text)}</Data></Cell>`
    }
    return `<Cell><Data ss:Type="String">${esc(text)}</Data></Cell>`
  }

  const headerRow = `<Row>${headers.map((h) => cellXml(h)).join('')}</Row>`
  const dataRows = rows.map((row) => `<Row>${row.map(cellXml).join('')}</Row>`).join('')

  return `<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
 <Worksheet ss:Name="${esc(safeSheet)}">
  <Table>
   ${headerRow}
   ${dataRows}
  </Table>
 </Worksheet>
</Workbook>
`
}

/**
 * @param {BlobPart} content
 * @param {string} filename
 * @param {string} mime
 */
export function downloadBlob(content, filename, mime) {
  const blob = content instanceof Blob ? content : new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/**
 * Walk paged list API until all matching rows are collected.
 * @param {(args: {max:number,offset:number}) => Promise<{items?: unknown[], total?: number}>} fetcher
 * @param {object} [baseFilters]
 * @returns {Promise<unknown[]>}
 */
export async function fetchAllListRows(fetcher, baseFilters = {}) {
  const all = []
  let offset = 0
  let total = Infinity
  while (offset < total) {
    const data = await fetcher({
      max: EXPORT_PAGE_SIZE,
      offset,
      ...baseFilters,
    })
    const items = Array.isArray(data?.items) ? data.items : []
    total = data?.total != null ? Number(data.total) : offset + items.length
    all.push(...items)
    if (items.length === 0) break
    offset += items.length
    if (all.length >= total) break
    // safety: avoid runaway if total is wrong
    if (offset > 100000) break
  }
  return all
}
