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

/**
 * Lightweight Markdown → safe HTML (CT7). Escape HTML first, then apply MD.
 * Supports: headings, bold/italic, lists, links, inline/fenced code, paragraphs.
 */

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function inlineFormat(text) {
  let t = escapeHtml(text)
  // code
  t = t.replace(/`([^`]+)`/g, '<code>$1</code>')
  // bold
  t = t.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  t = t.replace(/__([^_]+)__/g, '<strong>$1</strong>')
  // italic
  t = t.replace(/(?<!\*)\*([^*]+)\*(?!\*)/g, '<em>$1</em>')
  t = t.replace(/(?<!_)_([^_]+)_(?!_)/g, '<em>$1</em>')
  // links [text](url) — only http(s)
  t = t.replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
  return t
}

/**
 * @param {string} md
 * @returns {string} HTML
 */
export function markdownToHtml(md) {
  if (!md || !String(md).trim()) return '<p class="md-empty muted">No content.</p>'

  // Normalize Windows/Mac CRs so every return is a real line break
  const lines = String(md).replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n')
  const out = []
  let i = 0
  let inCode = false
  let codeBuf = []
  let listType = null // ul | ol
  let listBuf = []

  const flushList = () => {
    if (!listType || !listBuf.length) {
      listType = null
      listBuf = []
      return
    }
    const tag = listType
    out.push(`<${tag}>${listBuf.map((li) => `<li>${inlineFormat(li)}</li>`).join('')}</${tag}>`)
    listType = null
    listBuf = []
  }

  while (i < lines.length) {
    const line = lines[i]

    if (line.trim().startsWith('```')) {
      if (inCode) {
        out.push(`<pre class="md-code"><code>${escapeHtml(codeBuf.join('\n'))}</code></pre>`)
        codeBuf = []
        inCode = false
      } else {
        flushList()
        inCode = true
      }
      i += 1
      continue
    }

    if (inCode) {
      codeBuf.push(line)
      i += 1
      continue
    }

    const h = /^(#{1,3})\s+(.+)$/.exec(line)
    if (h) {
      flushList()
      const level = h[1].length
      out.push(`<h${level}>${inlineFormat(h[2].trim())}</h${level}>`)
      i += 1
      continue
    }

    const ul = /^[-*+]\s+(.+)$/.exec(line)
    if (ul) {
      if (listType && listType !== 'ul') flushList()
      listType = 'ul'
      listBuf.push(ul[1])
      i += 1
      continue
    }

    const ol = /^(\d+)\.\s+(.+)$/.exec(line)
    if (ol) {
      if (listType && listType !== 'ol') flushList()
      listType = 'ol'
      listBuf.push(ol[2])
      i += 1
      continue
    }

    if (!line.trim()) {
      flushList()
      // blank line → paragraph break (extra spacing between blocks)
      i += 1
      continue
    }

    flushList()
    // Soft line breaks (CR/LF within a paragraph) → <br>, not collapsed spaces
    const para = [line]
    i += 1
    while (
      i < lines.length &&
      lines[i].trim() &&
      !/^(#{1,3})\s/.test(lines[i]) &&
      !/^[-*+]\s/.test(lines[i]) &&
      !/^\d+\.\s/.test(lines[i]) &&
      !lines[i].trim().startsWith('```')
    ) {
      para.push(lines[i])
      i += 1
    }
    out.push(`<p>${para.map((l) => inlineFormat(l)).join('<br>\n')}</p>`)
  }

  flushList()
  if (inCode) {
    out.push(`<pre class="md-code"><code>${escapeHtml(codeBuf.join('\n'))}</code></pre>`)
  }

  return out.join('\n')
}

/** Plain one-line preview for tables. */
export function markdownPreview(md, maxLen = 100) {
  if (!md) return ''
  const plain = String(md)
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/[#*_`[\]]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return plain.length > maxLen ? `${plain.slice(0, maxLen)}…` : plain
}
