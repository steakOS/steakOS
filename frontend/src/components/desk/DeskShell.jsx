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

import { Plus, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'

/**
 * Shared master–detail desk chrome (ADR-047 / Nielsen WP-0).
 * Callers own data; this only lays out list | detail with progressive create.
 */
export default function DeskShell({
  title,
  subtitle,
  tabs,
  activeTab,
  onTabChange,
  newLabel,
  newExpanded = false,
  onToggleNew,
  newPanel,
  toolbar,
  list,
  hasSelection = false,
  emptyTitle,
  emptyBody,
  detail,
  detailFooter,
  processSlot,
  helpContext,
  className = '',
  narrowSheetOpen = false,
  onCloseSheet,
  /** 'split' = list|detail (default). 'single' = full-width list + modals (Buyers). */
  layout = 'split',
}) {
  const { t } = useTranslation(['common', 'desks'])
  const single = layout === 'single'
  // Never paint an empty white detail column — only mount col2 when a row is selected
  // (Buyers layout=single never shows col2). emptyTitle/emptyBody kept for API compat.

  const createLabel =
    newLabel ||
    t('desk.new', { ns: 'common', defaultValue: t('actions.new', { ns: 'common', defaultValue: 'New' }) })

  const helpAttrs = {}
  if (helpContext && typeof helpContext === 'object') {
    if (helpContext.desk) helpAttrs['data-help-desk'] = String(helpContext.desk)
    if (helpContext.tab) helpAttrs['data-help-tab'] = String(helpContext.tab)
    if (helpContext.resource) helpAttrs['data-help-resource'] = String(helpContext.resource)
    if (helpContext.objectId != null) helpAttrs['data-help-object-id'] = String(helpContext.objectId)
    if (helpContext.status) helpAttrs['data-help-status'] = String(helpContext.status)
    if (helpContext.processCode) helpAttrs['data-help-process'] = String(helpContext.processCode)
  }

  const showTabs = Array.isArray(tabs) && tabs.length > 0
  const showCol2 = !single && Boolean(hasSelection)
  const sheetOpen = showCol2 && Boolean(narrowSheetOpen)
  const fullWidthList = single || !showCol2
  const showTabRow =
    showTabs || typeof onToggleNew === 'function' || Boolean(processSlot)

  return (
    <div className={`desk-shell-root ${className}`.trim()} {...helpAttrs}>
      {(title || subtitle || showTabRow) && (
        <header className="desk-shell-header page-header" style={{ marginBottom: '1rem' }}>
          {title && <h1 className="page-title">{title}</h1>}
          {subtitle && <p className="page-sub">{subtitle}</p>}
          {showTabRow && (
            <div
              className="tab-row desk-tabs desk-shell-tabs toolbar-scroll"
              role={showTabs ? 'tablist' : undefined}
            >
              {processSlot ? (
                <div className="desk-shell-process-pill">{processSlot}</div>
              ) : null}
              {showTabs &&
                tabs.map((tabDef) => {
                  const Icon = tabDef.icon
                  const selected = activeTab === tabDef.id
                  return (
                    <button
                      key={tabDef.id}
                      type="button"
                      role="tab"
                      aria-selected={selected}
                      className={`btn ${selected ? 'btn-primary' : 'btn-ghost'}`}
                      onClick={() => onTabChange?.(tabDef.id)}
                    >
                      {Icon ? <Icon size={14} aria-hidden /> : null} {tabDef.label}
                    </button>
                  )
                })}
              {typeof onToggleNew === 'function' && !newExpanded && (
                <button
                  type="button"
                  className="btn btn-secondary desk-new-collapsed desk-new-in-tabs"
                  onClick={onToggleNew}
                >
                  <Plus size={14} aria-hidden /> {createLabel}
                </button>
              )}
            </div>
          )}
        </header>
      )}

      <div
        className={`desk-shell${fullWidthList ? ' desk-shell--single' : ''}${sheetOpen ? ' desk-shell-sheet-open' : ''}`}
      >
        <div className="desk-shell-col1">
          {typeof onToggleNew === 'function' && newExpanded && (
            <div className="desk-new-expanded card" id="desk-create">
              <div className="desk-new-expanded-bar">
                <h2 className="section-label" style={{ marginTop: 0, marginBottom: 0 }}>
                  {createLabel}
                </h2>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={onToggleNew}
                  aria-label={t('actions.cancel', { ns: 'common', defaultValue: 'Cancel' })}
                >
                  <X size={16} aria-hidden />
                </button>
              </div>
              {newPanel}
            </div>
          )}

          {toolbar && <div className="desk-shell-toolbar toolbar">{toolbar}</div>}

          <div className="desk-shell-list">{list}</div>
        </div>

        {showCol2 && (
          <div
            className={`desk-shell-col2${sheetOpen ? ' desk-shell-sheet' : ''}`}
            aria-hidden={false}
          >
            {sheetOpen && (
              <div className="desk-shell-sheet-bar">
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={onCloseSheet}
                  aria-label={t('desk.closeSheet', { ns: 'common', defaultValue: 'Close detail' })}
                >
                  <X size={16} aria-hidden />{' '}
                  {t('actions.close', { ns: 'common', defaultValue: 'Close' })}
                </button>
              </div>
            )}

            <div className="desk-shell-detail">
              {detail}
              {detailFooter && (
                <footer className="desk-detail-footer">{detailFooter}</footer>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
