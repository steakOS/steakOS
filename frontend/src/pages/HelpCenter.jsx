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

import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { helpApi } from '../api/client'
import { markdownToHtml } from '../utils/markdown'

const ROLE_HELP_KEYS = [
  'help.roles.overview',
  'help.roles.presets',
  'help.roles.matrix',
  'help.users.invite',
  'help.users.danger',
  'help.users.privacy',
  'help.roles.demo',
]

const SETTINGS_HELP_KEYS = ['system.settings', 'help.partners.locale']

const WORK_HELP_KEYS = ['help.work.actionNow', 'app.dashboard']

const FINANCE_HELP_KEYS = ['finance.postingRules', 'finance.invoices', 'finance.treasury']

function topicLabelKey(helpKey) {
  if (ROLE_HELP_KEYS.includes(helpKey)) return `pages.help.roleTopics.${helpKey}`
  if (SETTINGS_HELP_KEYS.includes(helpKey)) return `pages.help.settingsTopics.${helpKey}`
  if (WORK_HELP_KEYS.includes(helpKey)) return `pages.help.workTopics.${helpKey}`
  if (FINANCE_HELP_KEYS.includes(helpKey)) return `pages.help.financeTopics.${helpKey}`
  return `pages.help.roleTopics.${helpKey}`
}

/**
 * Help center — tour, IT support, Roles, Settings language, Action-NOW.
 */
export default function HelpCenter() {
  const { t, i18n } = useTranslation('common')
  const [topics, setTopics] = useState([])
  const [selectedKey, setSelectedKey] = useState('help.roles.overview')
  const [article, setArticle] = useState(null)
  const [err, setErr] = useState(null)

  useEffect(() => {
    let cancelled = false
    helpApi
      .topics()
      .then((r) => {
        if (cancelled) return
        const items = r.items || r || []
        setTopics(Array.isArray(items) ? items : [])
      })
      .catch((e) => {
        if (!cancelled) setErr(e.message || String(e))
      })
    return () => {
      cancelled = true
    }
  }, [])

  const roleTopics = useMemo(
    () =>
      ROLE_HELP_KEYS.map((key) => topics.find((x) => x.helpKey === key)).filter(Boolean),
    [topics],
  )
  const settingsTopics = useMemo(
    () =>
      SETTINGS_HELP_KEYS.map((key) => topics.find((x) => x.helpKey === key)).filter(Boolean),
    [topics],
  )
  const workTopics = useMemo(
    () =>
      WORK_HELP_KEYS.map((key) => topics.find((x) => x.helpKey === key)).filter(Boolean),
    [topics],
  )
  const financeTopics = useMemo(
    () =>
      FINANCE_HELP_KEYS.map((key) => topics.find((x) => x.helpKey === key)).filter(Boolean),
    [topics],
  )

  useEffect(() => {
    if (!selectedKey) return
    let cancelled = false
    helpApi
      .get(selectedKey, i18n.language)
      .then((d) => {
        if (!cancelled) setArticle(d)
      })
      .catch((e) => {
        if (!cancelled) setErr(e.message || String(e))
      })
    return () => {
      cancelled = true
    }
  }, [selectedKey, i18n.language])

  const body = article?.body?.bodyText || ''

  const renderTopicPicker = (list, emptyLabel) => (
    <div className="two-col finance-tx-grid" style={{ marginTop: 12 }}>
      <ul className="setup-family-list" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
        {list.length === 0 && !err && <li className="muted">{emptyLabel}</li>}
        {list.map((topic) => (
          <li key={topic.helpKey} style={{ marginBottom: 6 }}>
            <button
              type="button"
              className={`btn ${topic.helpKey === selectedKey ? 'btn-primary' : 'btn-secondary'}`}
              style={{ width: '100%', justifyContent: 'flex-start' }}
              onClick={() => setSelectedKey(topic.helpKey)}
            >
              {t(topicLabelKey(topic.helpKey), { defaultValue: topic.title })}
            </button>
          </li>
        ))}
      </ul>
      <div>
        {article && list.some((x) => x.helpKey === selectedKey) && (
          <>
            <h3 style={{ marginTop: 0 }}>
              {t(topicLabelKey(selectedKey), {
                defaultValue: article.title || article.topic?.title,
              })}
            </h3>
            {body ? (
              <div
                className="help-markdown"
                dangerouslySetInnerHTML={{ __html: markdownToHtml(body) }}
              />
            ) : (
              <p className="muted">
                {t('pages.help.rolesEmptyBody', {
                  defaultValue: 'No body yet — restart API if seeds just landed.',
                })}
              </p>
            )}
          </>
        )}
      </div>
    </div>
  )

  return (
    <div>
      <h1 className="page-title">{t('pages.help.title')}</h1>
      <p className="page-sub">{t('pages.help.subtitle')}</p>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>{t('nav.itSupport', { defaultValue: 'IT Support' })}</h2>
        <p className="page-sub">
          {t('pages.help.itSupportBody', {
            defaultValue:
              'Open an internal IT ticket (incident or request). Same tickets as Digital → Support.',
          })}
        </p>
        <p style={{ marginTop: 12, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          <Link className="btn btn-primary" to="/help/it-support?new=1">
            {t('pages.help.itSupportNew', {
              defaultValue: '{{label}} — new ticket',
              label: t('nav.itSupport', { defaultValue: 'IT Support' }),
            })}
          </Link>
          <Link className="btn btn-primary" to="/help/it-support">
            {t('pages.help.viewTickets', { defaultValue: 'View tickets' })}
          </Link>
        </p>
      </div>

      <div className="card" style={{ marginTop: 12 }}>
        <h2>{t('pages.help.rolesTitle', { defaultValue: 'Roles & access' })}</h2>
        <p className="page-sub">
          {t('pages.help.rolesBody', {
            defaultValue:
              'Small-team roles (Office, Ops, Finance…), invite steps, and safety. Also available from the Help drawer (?) on Roles and Users.',
          })}{' '}
          <Link to="/roles">{t('nav.roles', { defaultValue: 'Roles' })}</Link>
          {' · '}
          <Link to="/users">{t('nav.users', { defaultValue: 'Users' })}</Link>
          {'.'}
        </p>
        {err && <p style={{ color: 'var(--danger)' }}>{err}</p>}
        {renderTopicPicker(
          roleTopics,
          t('pages.help.rolesLoading', { defaultValue: 'Loading role help topics…' }),
        )}
      </div>

      <div className="card" style={{ marginTop: 12 }}>
        <h2>{t('pages.help.settingsTitle', { defaultValue: 'Settings · language & region' })}</h2>
        <p className="page-sub">
          {t('pages.help.settingsBody', {
            defaultValue:
              'Screen language vs partner communication language. Also available from the Help drawer (?) on Settings.',
          })}{' '}
          <Link to="/settings">{t('nav.settings')}</Link>
          {'.'}
        </p>
        {renderTopicPicker(
          settingsTopics,
          t('pages.help.settingsLoading', { defaultValue: 'Loading settings help…' }),
        )}
      </div>

      <div className="card" style={{ marginTop: 12 }}>
        <h2>{t('pages.help.workTitle', { defaultValue: 'Action - NOW' })}</h2>
        <p className="page-sub">
          {t('pages.help.workBody', {
            defaultValue:
              'Priority board for open tasks (urgency × importance). Also on the dashboard mini board.',
          })}{' '}
          <Link to="/work">{t('pages.work.nav', { defaultValue: 'My work' })}</Link>
          {' · '}
          <Link to="/dashboard">{t('nav.dashboard', { defaultValue: 'Dashboard' })}</Link>
          {'.'}
        </p>
        {renderTopicPicker(
          workTopics,
          t('pages.help.workLoading', { defaultValue: 'Loading Action - NOW help…' }),
        )}
      </div>

      <div className="card" style={{ marginTop: 12 }}>
        <h2>{t('pages.help.financeTitle', { defaultValue: 'Finance · posting rules' })}</h2>
        <p className="page-sub">
          {t('pages.help.financeBody', {
            defaultValue:
              'Auto journals on settle / vendor pay / payroll. Admin edits rules; CEO can view. Also from Finance → Posting rules.',
          })}{' '}
          <Link to="/finance/posting-rules">
            {t('features.postingRules', { defaultValue: 'Posting rules' })}
          </Link>
          {'.'}
        </p>
        {renderTopicPicker(
          financeTopics,
          t('pages.help.financeLoading', { defaultValue: 'Loading finance help…' }),
        )}
      </div>

      <div className="card" style={{ marginTop: 12 }}>
        <h2>{t('pages.help.tourTitle')}</h2>
        <p className="page-sub">{t('pages.help.tourBody')}</p>
        <p style={{ marginTop: 12 }}>
          <a className="btn btn-primary" href="/tour/index.html">
            {t('pages.help.startTour')}
          </a>
        </p>
      </div>

      <div className="card" style={{ marginTop: 12 }}>
        <h2>{t('pages.help.setupTitle')}</h2>
        <ul className="setup-family-list">
          <li>
            <a href="/setup">{t('pages.help.wizard')}</a> {t('pages.help.wizardHint')}
          </li>
          <li>
            <a href="/settings">{t('nav.settings')}</a> {t('pages.help.settingsHint')}
          </li>
        </ul>
      </div>

      <div className="card" style={{ marginTop: 12 }}>
        <h2>{t('pages.help.docsTitle')}</h2>
        <ul className="setup-family-list">
          <li>
            <code>docs/frontend/FRONTEND-CONCEPT.md</code>
          </li>
          <li>
            <code>docs/ROLES-SMALL-COMPANY-CONCEPT.md</code>
          </li>
          <li>
            <code>docs/usertesting/ROLES-USER-TEST.md</code>
          </li>
          <li>
            <code>docs/architecture/</code>
          </li>
        </ul>
      </div>

      <div className="card" style={{ marginTop: 12 }}>
        <h2>
          {t('pages.help.legalTitle', {
            defaultValue: 'Privacy & operator trust',
          })}
        </h2>
        <p className="page-sub">
          {t('pages.help.legalBody', {
            defaultValue:
              'You are the controller for personal data on this self-hosted instance. These short guides open in a new browser tab.',
          })}
        </p>
        <p style={{ marginTop: 12, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          <a
            className="btn btn-primary"
            href="/help/gdpr-configure.html"
            target="_blank"
            rel="noreferrer"
          >
            {t('pages.help.legalPrivacyBtn', {
              defaultValue: 'Privacy configuration',
            })}
          </a>
          <a
            className="btn btn-secondary"
            href="/help/operator-trust-runbook.html"
            target="_blank"
            rel="noreferrer"
          >
            {t('pages.help.legalRunbookBtn', {
              defaultValue: 'Operator trust runbook',
            })}
          </a>
        </p>
      </div>
    </div>
  )
}
