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
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ExternalLink } from 'lucide-react'
import { domainsApi } from '../api/client'
import {
  deskHref,
  domainListReturnKey,
  getCreateMeta,
  getInteraction,
  listReturnHref,
  safeReturnTo,
} from '../config/domainRegistry'
import { getMasterFormSpec } from '../forms/masterFormSpecs'
import MasterEntityFields from '../forms/MasterEntityFields'
import SchemaFormFields, {
  emptyFromSchema,
  bodyFromSchemaForm,
} from '../forms/SchemaFormFields'
import ErrorDialog from '../components/ErrorDialog'
import ContentRail from '../components/ContentRail'
import SimpleStatusSelect from '../components/SimpleStatusSelect'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import MoneyHint from '../components/MoneyHint'
import TaskAssigneeSelect from '../components/TaskAssigneeSelect'
import { humanizeLabel } from '../utils/humanize'
import { domainGroupLabel, domainResourceLabel } from '../utils/domainLabels'
import { isContentCompanionHost } from '../utils/contentCompanion'

const emptyBasic = { name: '', description: '', status: '', code: '' }

const emptyTask = {
  name: '',
  description: '',
  status: 'OPEN',
  assigneeHumanNpub: '',
  assigneeAgentId: '',
  startAt: '',
  endAt: '',
  deadlineAt: '',
  urgency: '2',
  importance: '2',
}

/** Human-selectable Task statuses only. AI:* is set by Coder / Hermes via API. */
const TASK_STATUSES = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED']

function isAiTaskStatus(status) {
  return typeof status === 'string' && status.startsWith('AI:')
}

/** Map server status into the edit dropdown (never expose AI:* as a choice). */
function humanTaskStatusForForm(status) {
  if (!status || isAiTaskStatus(status)) return 'OPEN'
  return TASK_STATUSES.includes(status) ? status : 'OPEN'
}
const EISENHOWER_LEVELS = [
  { value: '1', label: '1 · Low' },
  { value: '2', label: '2 · Medium' },
  { value: '3', label: '3 · High' },
]

function dateInputValue(iso) {
  if (!iso) return ''
  const s = String(iso)
  return s.length >= 10 ? s.slice(0, 10) : s
}

const emptyBom = {
  name: '',
  description: '',
  productId: '',
  bomStatus: 'ACTIVE',
  status: '',
}

const emptyBomItem = {
  name: '',
  description: '',
  bomId: '',
  componentProductId: '',
  quantity: '1',
  unit: 'EA',
  lineNo: '1',
  status: '',
}

function currentPeriodYyyyMm() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const emptyPayroll = {
  name: '',
  description: '',
  period: currentPeriodYyyyMm(),
  payrollStatus: 'OPEN',
  currencyCode: 'EUR',
  status: '',
}

const emptyProductionOrder = {
  name: '',
  description: '',
  productId: '',
  quantity: '1',
  unit: 'EA',
  prodStatus: 'DRAFT',
  status: '',
}

const emptyInvoice = {
  name: '',
  description: '',
  invoiceNumber: '',
  customerName: '',
  customerContact: '',
  amountEur: '',
  currencyCode: 'EUR',
  invoiceStatus: 'DRAFT',
  issueDate: '',
  dueDate: '',
  referenceText: '',
  recurring: false,
  status: '',
}

const emptyAccount = {
  name: '',
  description: '',
  code: '',
  accountType: 'ASSET',
  accountSubType: '',
  currencyCode: 'EUR',
  parentCode: '',
  systemAccount: false,
  active: true,
  status: '',
}

const emptyTreasury = {
  name: '',
  description: '',
  accountKind: 'FIAT_BANK',
  currencyCode: 'EUR',
  balanceEur: '0',
  balanceSats: '0',
  externalRef: '',
  active: true,
  status: '',
}

const emptyAddress = {
  name: '',
  description: '',
  addressType: 'COMMERCIAL',
  street: '',
  streetNo: '',
  street2: '',
  street3: '',
  street4: '',
  street5: '',
  street6: '',
  lot: '',
  building: '',
  room: '',
  locality: '',
  postalCode: '',
  city: '',
  region: '',
  county: '',
  countryCode: 'DE',
  latitude: '',
  longitude: '',
  status: '',
  active: true,
}

const ADDRESS_TYPES = [
  'COMMERCIAL',
  'RESIDENTIAL',
  'DELIVERY',
  'PERSONAL',
  'POBOX',
  'GOVERNMENTAL',
]

/** Legacy specialized DomainForm resources (pre-P1 packs / non-master) */
const LEGACY_SPECIAL = new Set([
  'Task',
  'Address',
  'Invoice',
  'Account',
  'Treasury',
  'BillOfMaterials',
  'BomItem',
  'Payroll',
  'ProductionOrder',
])

function emptyFor(resource, pkg) {
  const master = getMasterFormSpec(pkg, resource)
  if (master) return master.empty()
  if (resource === 'Task') return { ...emptyTask }
  if (resource === 'Address') return { ...emptyAddress }
  if (resource === 'Invoice') return { ...emptyInvoice }
  if (resource === 'Account') return { ...emptyAccount }
  if (resource === 'Treasury') return { ...emptyTreasury }
  if (resource === 'BillOfMaterials') return { ...emptyBom }
  if (resource === 'BomItem') return { ...emptyBomItem }
  if (resource === 'Payroll') return { ...emptyPayroll, period: currentPeriodYyyyMm() }
  if (resource === 'ProductionOrder') return { ...emptyProductionOrder }
  return { ...emptyBasic }
}

export default function DomainForm() {
  const { package: pkg, resource, id } = useParams()
  const location = useLocation()
  const { t } = useTranslation(['domain', 'common'])
  const resourceLabel = domainResourceLabel(
    (keys, opts) => t(keys, { ns: 'domain', ...opts }),
    pkg,
    resource,
  )
  const isNew = !id || id === 'new'
  const createMeta = useMemo(() => getCreateMeta(pkg, resource), [pkg, resource])
  const interaction =
    createMeta.interaction || getInteraction(pkg, resource) || 'domainFullPage'
  const preferDesk = interaction === 'deskShell' && !!createMeta.deskPath
  const originReturnTo = useMemo(() => {
    const params = new URLSearchParams(location.search || '')
    return safeReturnTo(params.get('returnTo') || location.state?.returnTo)
  }, [location.search, location.state])

  const listReturnHrefResolved = useMemo(() => {
    // Prefer explicit origin (My work, dashboard, company, …) over domain list.
    if (originReturnTo) return originReturnTo
    const fromSearch = (() => {
      const params = new URLSearchParams(location.search || '')
      const q = params.get('q') || params.get('returnQ') || ''
      const offset = params.get('offset')
      const max = params.get('max')
      if (q || offset || max) {
        return listReturnHref(pkg, resource, { q, offset, max })
      }
      return null
    })()
    if (fromSearch) return fromSearch
    try {
      const raw = sessionStorage.getItem(domainListReturnKey(pkg, resource))
      if (raw) {
        const parsed = JSON.parse(raw)
        return listReturnHref(pkg, resource, {
          q: parsed?.q,
          offset: parsed?.offset,
          max: parsed?.max,
        })
      }
    } catch {
      /* ignore */
    }
    return listReturnHref(pkg, resource)
  }, [pkg, resource, location.search, originReturnTo])

  const deskOpenHrefResolved = useMemo(() => {
    if (!createMeta.deskPath) return null
    return deskHref({
      deskPath: createMeta.deskPath,
      defaultTab: createMeta.defaultTab,
      id: isNew ? undefined : id,
      new: isNew,
    })
  }, [createMeta.deskPath, createMeta.defaultTab, isNew, id])

  const masterSpec = useMemo(() => getMasterFormSpec(pkg, resource), [pkg, resource])
  const isMasterPack = !!masterSpec
  const isLegacySpecial = LEGACY_SPECIAL.has(resource) && !isMasterPack
  const useSchemaForm = !isMasterPack && !isLegacySpecial
  const isAddress = resource === 'Address'
  const isInvoice = resource === 'Invoice'
  const isAccount = resource === 'Account'
  const isTreasury = resource === 'Treasury'
  const isBom = resource === 'BillOfMaterials'
  const isBomItem = resource === 'BomItem'
  const isPayroll = resource === 'Payroll' && pkg === 'human'
  const isProductionOrder = resource === 'ProductionOrder'
  const isTask = resource === 'Task' && pkg === 'core'
  const needsProduct = isBom || isProductionOrder || isBomItem
  const navigate = useNavigate()
  const [form, setForm] = useState(() => emptyFor(resource, pkg))
  const [schema, setSchema] = useState(null)
  const [schemaError, setSchemaError] = useState(null)
  const [countries, setCountries] = useState([])
  const [products, setProducts] = useState([])
  const [boms, setBoms] = useState([])
  /** When editing, last server AI:* status (display-only; not a dropdown option). */
  const [agentStatusHint, setAgentStatusHint] = useState(null)
  const [error, setError] = useState(null)
  const [errorDetail, setErrorDetail] = useState(null)
  const [loading, setLoading] = useState(!isNew)
  const [pendingContent, setPendingContent] = useState([])
  const [companionWarn, setCompanionWarn] = useState(
    () => location.state?.companionWarn || null,
  )

  const companionEligible = isContentCompanionHost(pkg, resource)

  useEffect(() => {
    if (isAddress) {
      domainsApi
        .list('helper', 'Countries')
        .then((data) => setCountries(data.items || []))
        .catch(() => setCountries([]))
    }
  }, [isAddress])

  useEffect(() => {
    if (needsProduct) {
      // Ops FKs use catalogue GOODS (ids = catalogue item ids)
      import('../api/client').then(({ catalogueApi }) =>
        catalogueApi
          .list({ nature: 'GOODS', activeOnly: true })
          .then((data) => setProducts(data.items || []))
          .catch(() => setProducts([])),
      )
    }
  }, [needsProduct])

  useEffect(() => {
    if (isBomItem) {
      domainsApi
        .list('production', 'BillOfMaterials')
        .then((data) => setBoms(data.items || []))
        .catch(() => setBoms([]))
    }
  }, [isBomItem])

  // P2: load schema for non-pack, non-legacy resources
  useEffect(() => {
    if (!useSchemaForm) {
      setSchema(null)
      setSchemaError(null)
      return
    }
    let cancelled = false
    setSchemaError(null)
    domainsApi
      .schema(pkg, resource)
      .then((s) => {
        if (cancelled) return
        setSchema(s)
        if (isNew) {
          setForm(emptyFromSchema(s))
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setSchema(null)
          setSchemaError(e.message || String(e))
        }
      })
    return () => {
      cancelled = true
    }
  }, [pkg, resource, useSchemaForm, isNew])

  useEffect(() => {
    setPendingContent([])
    if (!location.state?.companionWarn) setCompanionWarn(null)
  }, [pkg, resource, id])

  useEffect(() => {
    if (location.state?.companionWarn) {
      setCompanionWarn(location.state.companionWarn)
    }
  }, [location.state])

  useEffect(() => {
    if (isNew) {
      if (!useSchemaForm) {
        setForm(emptyFor(resource, pkg))
      }
      setAgentStatusHint(null)
      setLoading(false)
      return
    }
    setLoading(true)
    domainsApi
      .get(pkg, resource, id)
      .then((row) => {
        if (masterSpec) {
          setForm(masterSpec.fromRow(row, masterSpec.empty()))
        } else if (isTask) {
          const rawStatus = row.status || 'OPEN'
          setAgentStatusHint(isAiTaskStatus(rawStatus) ? rawStatus : null)
          setForm({
            ...emptyTask,
            name: row.name || '',
            description: row.description || '',
            status: humanTaskStatusForForm(rawStatus),
            assigneeHumanNpub: row.assigneeHumanNpub || '',
            assigneeAgentId: row.assigneeAgentId != null ? String(row.assigneeAgentId) : '',
            startAt: dateInputValue(row.startAt),
            endAt: dateInputValue(row.endAt),
            deadlineAt: dateInputValue(row.deadlineAt),
            urgency: row.urgency != null ? String(row.urgency) : '2',
            importance: row.importance != null ? String(row.importance) : '2',
          })
        } else if (isAddress) {
          setForm({
            ...emptyAddress,
            name: row.name || '',
            description: row.description || '',
            addressType: row.addressType || 'COMMERCIAL',
            street: row.street || '',
            streetNo: row.streetNo || '',
            street2: row.street2 || '',
            street3: row.street3 || '',
            street4: row.street4 || '',
            street5: row.street5 || '',
            street6: row.street6 || '',
            lot: row.lot || '',
            building: row.building || '',
            room: row.room || '',
            locality: row.locality || '',
            postalCode: row.postalCode || '',
            city: row.city || '',
            region: row.region || '',
            county: row.county || '',
            countryCode: row.countryCode || 'DE',
            latitude: row.latitude ?? '',
            longitude: row.longitude ?? '',
            status: row.status || '',
            active: row.active !== false,
          })
        } else if (isInvoice) {
          setForm({
            ...emptyInvoice,
            name: row.name || '',
            description: row.description || '',
            invoiceNumber: row.invoiceNumber || '',
            customerName: row.customerName || '',
            customerContact: row.customerContact || '',
            amountEur: row.amountEur ?? '',
            currencyCode: row.currencyCode || 'EUR',
            invoiceStatus: row.invoiceStatus || 'DRAFT',
            issueDate: (row.issueDate || '').toString().slice(0, 10),
            dueDate: (row.dueDate || '').toString().slice(0, 10),
            referenceText: row.referenceText || '',
            recurring: !!row.recurring,
            status: row.status || '',
          })
        } else if (isAccount) {
          setForm({
            ...emptyAccount,
            name: row.name || '',
            description: row.description || '',
            code: row.code || '',
            accountType: row.accountType || 'ASSET',
            accountSubType: row.accountSubType || '',
            currencyCode: row.currencyCode || 'EUR',
            parentCode: row.parentCode || '',
            systemAccount: !!row.systemAccount,
            active: row.active !== false,
            status: row.status || '',
          })
        } else if (isTreasury) {
          setForm({
            ...emptyTreasury,
            name: row.name || '',
            description: row.description || '',
            accountKind: row.accountKind || 'FIAT_BANK',
            currencyCode: row.currencyCode || 'EUR',
            balanceEur: row.balanceEur ?? '0',
            balanceSats: row.balanceSats ?? '0',
            externalRef: row.externalRef || '',
            active: row.active !== false,
            status: row.status || '',
          })
        } else if (isBom) {
          setForm({
            ...emptyBom,
            name: row.name || '',
            description: row.description || '',
            productId: row.productId != null ? String(row.productId) : '',
            bomStatus: row.bomStatus || 'ACTIVE',
            status: row.status || '',
          })
        } else if (isBomItem) {
          setForm({
            ...emptyBomItem,
            name: row.name || '',
            description: row.description || '',
            bomId: row.bomId != null ? String(row.bomId) : '',
            componentProductId:
              row.componentProductId != null ? String(row.componentProductId) : '',
            quantity: row.quantity != null ? String(row.quantity) : '1',
            unit: row.unit || 'EA',
            lineNo: row.lineNo != null ? String(row.lineNo) : '1',
            status: row.status || '',
          })
        } else if (isPayroll) {
          setForm({
            ...emptyPayroll,
            name: row.name || '',
            description: row.description || '',
            period: row.period || currentPeriodYyyyMm(),
            payrollStatus: row.payrollStatus || 'OPEN',
            currencyCode: row.currencyCode || 'EUR',
            status: row.status || '',
          })
        } else if (isProductionOrder) {
          setForm({
            ...emptyProductionOrder,
            name: row.name || '',
            description: row.description || '',
            productId: row.productId != null ? String(row.productId) : '',
            quantity: row.quantity != null ? String(row.quantity) : '1',
            unit: row.unit || 'EA',
            prodStatus: row.prodStatus || 'DRAFT',
            status: row.status || '',
          })
        } else if (schema) {
          const base = emptyFromSchema(schema)
          for (const key of Object.keys(base)) {
            if (row[key] != null && row[key] !== undefined) {
              if (typeof row[key] === 'boolean') base[key] = row[key]
              else if (String(key).endsWith('At') || String(key).endsWith('Date')) {
                base[key] = String(row[key]).slice(0, 10)
              } else base[key] = String(row[key])
            }
          }
          if (row.name != null) base.name = row.name || ''
          if (row.description != null) base.description = row.description || ''
          setForm(base)
        } else {
          setForm({
            name: row.name || '',
            description: row.description || '',
            status: row.status || '',
            code: row.code || '',
          })
        }
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [
    pkg,
    resource,
    id,
    isNew,
    isTask,
    isAddress,
    isInvoice,
    isAccount,
    isTreasury,
    isBom,
    isBomItem,
    isPayroll,
    isProductionOrder,
    masterSpec,
    schema,
    useSchemaForm,
  ])

  const onChange = (e) => {
    const { name, value, type, checked } = e.target
    setForm((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    let body
    if (masterSpec) {
      body = masterSpec.toBody(form)
      if (form.status) body.status = form.status
      else if (form.status === '') body.status = null
      // Product/Service masters → catalogue (nature GOODS/SERVICE)
      if (resource === 'Product') {
        body.nature = 'GOODS'
        if (body.sku && !body.code) body.code = body.sku
      }
      if (resource === 'Service') {
        body.nature = 'SERVICE'
        if (body.listPriceEur != null && body.listPrice == null) body.listPrice = body.listPriceEur
      }
    } else if (useSchemaForm && schema) {
      body = bodyFromSchemaForm(schema, form)
    } else if (isTask) {
      body = {
        name: form.name,
        description: form.description || null,
        // UI never sends AI:* — agents set those via API only
        status: humanTaskStatusForForm(form.status),
        assigneeHumanNpub: form.assigneeHumanNpub || null,
        assigneeAgentId: form.assigneeAgentId === '' ? null : form.assigneeAgentId,
        startAt: form.startAt || null,
        endAt: form.endAt || null,
        deadlineAt: form.deadlineAt || null,
        urgency: form.urgency === '' ? 2 : Number(form.urgency),
        importance: form.importance === '' ? 2 : Number(form.importance),
      }
    } else if (isAddress) {
      body = {
        name: form.name,
        description: form.description || null,
        addressType: form.addressType,
        street: form.street,
        streetNo: form.streetNo || null,
        street2: form.street2 || null,
        street3: form.street3 || null,
        street4: form.street4 || null,
        street5: form.street5 || null,
        street6: form.street6 || null,
        lot: form.lot || null,
        building: form.building || null,
        room: form.room || null,
        locality: form.locality || null,
        postalCode: form.postalCode || null,
        city: form.city,
        region: form.region || null,
        county: form.county || null,
        countryCode: form.countryCode,
        latitude: form.latitude === '' ? null : form.latitude,
        longitude: form.longitude === '' ? null : form.longitude,
        status: form.status || null,
        active: form.active,
      }
    } else if (isInvoice) {
      body = {
        name: form.name || form.invoiceNumber || 'Invoice',
        description: form.description || null,
        invoiceNumber: form.invoiceNumber || null,
        customerName: form.customerName || null,
        customerContact: form.customerContact || null,
        amountEur: form.amountEur === '' ? null : form.amountEur,
        amount: form.amountEur === '' ? null : form.amountEur,
        currencyCode: form.currencyCode || 'EUR',
        invoiceStatus: form.invoiceStatus || 'DRAFT',
        issueDate: form.issueDate || null,
        dueDate: form.dueDate || null,
        referenceText: form.referenceText || null,
        recurring: !!form.recurring,
        status: form.status || form.invoiceStatus || null,
      }
    } else if (isAccount) {
      body = {
        name: form.name,
        description: form.description || null,
        code: form.code,
        accountType: form.accountType,
        accountSubType: form.accountSubType || null,
        currencyCode: form.currencyCode || 'EUR',
        parentCode: form.parentCode || null,
        systemAccount: !!form.systemAccount,
        active: form.active !== false,
        status: form.status || null,
      }
    } else if (isTreasury) {
      body = {
        name: form.name,
        description: form.description || null,
        accountKind: form.accountKind,
        currencyCode: form.currencyCode || 'EUR',
        balanceEur: form.balanceEur === '' ? 0 : form.balanceEur,
        balanceSats: form.balanceSats === '' ? 0 : form.balanceSats,
        externalRef: form.externalRef || null,
        active: form.active !== false,
        status: form.status || null,
      }
    } else if (isBom) {
      if (!form.productId) {
        setError('Product is required for a bill of materials')
        return
      }
      body = {
        name: form.name || undefined,
        description: form.description || null,
        productId: form.productId,
        bomStatus: form.bomStatus || 'ACTIVE',
        status: form.status || form.bomStatus || null,
      }
    } else if (isBomItem) {
      if (!form.bomId) {
        setError('Parent BOM is required')
        return
      }
      if (!form.componentProductId) {
        setError('Component product is required')
        return
      }
      body = {
        name: form.name || undefined,
        description: form.description || null,
        bomId: form.bomId,
        componentProductId: form.componentProductId,
        quantity: form.quantity === '' ? 1 : form.quantity,
        unit: form.unit || 'EA',
        lineNo: form.lineNo === '' ? 1 : Number(form.lineNo),
      }
    } else if (isPayroll) {
      let period = (form.period || '').trim() || currentPeriodYyyyMm()
      if (period.length > 7) period = period.slice(0, 7)
      body = {
        name: form.name || `Payroll ${period}`,
        description: form.description || null,
        period,
        payrollStatus: form.payrollStatus || 'OPEN',
        currencyCode: form.currencyCode || 'EUR',
        status: form.status || form.payrollStatus || 'OPEN',
      }
    } else if (isProductionOrder) {
      if (!form.productId) {
        setError('Product is required for a production order')
        return
      }
      body = {
        name: form.name || undefined,
        description: form.description || null,
        productId: form.productId,
        quantity: form.quantity === '' ? 1 : form.quantity,
        unit: form.unit || 'EA',
        prodStatus: form.prodStatus || 'DRAFT',
        status: form.status || form.prodStatus || null,
      }
    } else {
      body = {
        name: form.name,
        description: form.description || null,
      }
      if (form.status) body.status = form.status
      if (form.code) body.code = form.code
    }

    try {
      const useCatalogue =
        masterSpec && (resource === 'Product' || resource === 'Service')
      if (isNew) {
        let created
        if (useCatalogue) {
          const { catalogueApi } = await import('../api/client')
          created = await catalogueApi.create(body)
        } else {
          created = await domainsApi.create(pkg, resource, body)
        }
        let warn = null
        if (companionEligible && pendingContent.length) {
          const result = await attachPendingContent(
            useCatalogue ? 'catalogue' : pkg,
            useCatalogue ? 'CatalogueItem' : resource,
            created.id,
            pendingContent,
          )
          if (result.errors?.length) {
            warn = `Record created, but some companion content failed: ${result.errors.join('; ')}`
          }
        }
        setPendingContent([])
        // After create, return to origin/list (same as update) — never open edit/:id
        const afterCreateDest = useCatalogue
          ? originReturnTo ||
            (resource === 'Service'
              ? '/delivery'
              : pkg === 'production'
                ? '/scm/shopfloor'
                : '/scm/buyers')
          : listReturnHrefResolved
        if (warn) {
          navigate(afterCreateDest, { state: { companionWarn: warn } })
        } else {
          navigate(afterCreateDest)
        }
      } else if (useCatalogue) {
        const { catalogueApi } = await import('../api/client')
        await catalogueApi.update(id, body)
        navigate(
          originReturnTo ||
            (resource === 'Service'
              ? '/delivery'
              : pkg === 'production'
                ? '/scm/shopfloor'
                : '/scm/buyers'),
        )
      } else {
        await domainsApi.update(pkg, resource, id, body)
        navigate(listReturnHrefResolved)
      }
    } catch (err) {
      setError(err.message)
      setErrorDetail({
        message: err.message,
        code: err.code || 'ERROR',
        status: err.status ?? null,
        detail: err.detail || null,
        stackTrace: err.stackTrace || null,
        title: isNew ? t('domainForm.createFailed', { ns: 'common' }) : t('domainForm.updateFailed', { ns: 'common' }),
      })
    }
  }

  const deskLabel =
    createMeta.deskLabel ||
    t('masterData.deskDefault', { ns: 'common', defaultValue: 'process desk' })

  // P0: process / line creates must not use empty DomainForm (after all hooks)
  if (isNew && createMeta.createMode === 'deskRedirect' && createMeta.deskPath) {
    return (
      <Navigate
        to={deskHref({
          deskPath: createMeta.deskPath,
          defaultTab: createMeta.defaultTab,
          new: true,
        })}
        replace
      />
    )
  }

  if (isNew && (createMeta.createMode === 'parentOnly' || createMeta.createMode === 'blocked')) {
    const deskPath = createMeta.deskPath
    return (
      <div>
        <h1 className="page-title">
          {t('actions.create', { ns: 'common' })} {resourceLabel}
        </h1>
        <div className="card" style={{ maxWidth: 560 }}>
          <p style={{ marginTop: 0 }}>
            {createMeta.createMode === 'parentOnly'
              ? t('masterData.parentOnly', {
                  ns: 'common',
                  parent:
                    createMeta.parentResource ||
                    t('masterData.parentDoc', {
                      ns: 'common',
                      defaultValue: 'parent document',
                    }),
                  defaultValue:
                    'Line items are created under a {{parent}} (multi-line editor on the process desk), not as standalone master data.',
                })
              : createMeta.reason ||
                t('masterData.createBlocked', {
                  ns: 'common',
                  defaultValue: 'Standalone create is not available for this resource yet.',
                })}
          </p>
          <div className="toolbar" style={{ marginBottom: 0 }}>
            {deskPath && (
              <Link
                className="btn btn-primary"
                to={deskHref({
                  deskPath,
                  defaultTab: createMeta.defaultTab,
                  new: true,
                })}
              >
                <ExternalLink size={16} />{' '}
                {t('masterData.openDesk', {
                  ns: 'common',
                  desk: deskLabel,
                  defaultValue: 'Create on {{desk}}',
                })}
              </Link>
            )}
            <Link className="btn" to={listReturnHrefResolved}>
              {t('actions.back', { ns: 'common', defaultValue: 'Back to list' })}
            </Link>
            {createMeta.parentResource && (
              <Link
                className="btn btn-secondary"
                to={`/domains/${pkg}/${createMeta.parentResource}`}
              >
                {t('masterData.viewParents', {
                  ns: 'common',
                  parent: createMeta.parentResource,
                  defaultValue: 'View {{parent}} list',
                })}
              </Link>
            )}
          </div>
        </div>
      </div>
    )
  }

  if (loading) return <p className="muted">{t('actions.loading', { ns: 'common' })}</p>

  return (
    <div>
      <div
        className="toolbar domain-form-sticky-nav"
        style={{
          position: 'sticky',
          top: 'calc(var(--app-topbar-offset, 3.5rem) + 0.25rem)',
          zIndex: 3,
          marginBottom: '0.75rem',
          padding: '0.5rem 0',
          background: 'color-mix(in srgb, var(--steak-bg, #f6f4f1) 92%, transparent)',
          backdropFilter: 'blur(6px)',
          flexWrap: 'wrap',
          gap: 8,
        }}
      >
        <Link className="btn" to={listReturnHrefResolved}>
          {t('actions.back', { ns: 'common', defaultValue: 'Back to list' })}
        </Link>
        {preferDesk && deskOpenHrefResolved && (
          <Link className="btn btn-primary" to={deskOpenHrefResolved}>
            <ExternalLink size={16} />{' '}
            {t('masterData.openDesk', {
              ns: 'common',
              desk: deskLabel,
              defaultValue: 'Open on {{desk}}',
            })}
          </Link>
        )}
      </div>
      <h1 className="page-title">
        {isNew ? t('actions.create', { ns: 'common' }) : t('actions.edit', { ns: 'common' })}{' '}
        {resourceLabel}
      </h1>
      <p className="page-sub">
        {domainGroupLabel((keys, opts) => t(keys, { ns: 'common', ...opts }), pkg)}
        {' · '}
        {isAddress
          ? t('domainForm.postalAddress', { ns: 'common' })
          : isInvoice
            ? t('domainForm.invoiceHint', { ns: 'common' })
            : isAccount
              ? t('domainForm.coaHint', { ns: 'common' })
              : isTreasury
                ? t('domainForm.treasuryHint', { ns: 'common' })
                : isBom
                  ? t('domainForm.bomHint', { ns: 'common' })
                  : isBomItem
                    ? t('domainForm.bomItemHint', {
                        ns: 'common',
                        defaultValue:
                          'BOM line: parent bill of materials + component product and quantity.',
                      })
                    : isProductionOrder
                      ? t('domainForm.prodOrderHint', { ns: 'common' })
                      : t('domainForm.formFields', { ns: 'common' })}
      </p>

      {error && (
        <div
          className="banner-error"
          style={{ cursor: 'pointer' }}
          onClick={() => setErrorDetail((d) => d || { message: error })}
        >
          {error}
          <span className="muted" style={{ marginLeft: 8, fontSize: '0.85rem' }}>
            {t('actions.clickDetails', { ns: 'common' })}
          </span>
        </div>
      )}

      <ErrorDialog
        error={errorDetail}
        title={errorDetail?.title || t('actions.error', { ns: 'common' })}
        onClose={() => setErrorDetail(null)}
      />

      <form className="card form-grid domain-form" onSubmit={onSubmit}>
        {!isNew && id ? (
          <label>
            {t('view.uniqueId', { ns: 'common', defaultValue: 'Unique ID' })}
            <input
              type="text"
              className="mono"
              value={String(id)}
              readOnly
              disabled
              aria-readonly="true"
              title={t('view.uniqueIdHint', {
                ns: 'common',
                defaultValue: 'System identifier for this record. Read-only when editing.',
              })}
            />
          </label>
        ) : null}
        {isMasterPack && (
          <>
            <MasterEntityFields spec={masterSpec} form={form} onChange={onChange} />
            <label>
              {t('wb.status', { ns: 'common', defaultValue: 'Status' })}
              <SimpleStatusSelect
                name="status"
                value={form.status || ''}
                onChange={onChange}
              />
            </label>
          </>
        )}

        {useSchemaForm && schemaError && (
          <p className="banner-error" style={{ margin: 0 }}>
            Schema load failed: {schemaError}. Falling back to basic fields.
          </p>
        )}
        {useSchemaForm && schema && !schemaError && (
          <SchemaFormFields schema={schema} form={form} onChange={onChange} />
        )}
        {useSchemaForm && !schema && !schemaError && (
          <p className="muted">{t('actions.loading', { ns: 'common' })}</p>
        )}
        {useSchemaForm && schemaError && (
          <>
            <label>
              {t('domainForm.name', { ns: 'common' })} *
              <input name="name" value={form.name || ''} onChange={onChange} required maxLength={255} />
            </label>
            <label>
              Description
              <textarea
                name="description"
                value={form.description || ''}
                onChange={onChange}
                rows={3}
              />
            </label>
          </>
        )}

        {isLegacySpecial && (
          <>
        <label>
          {t('domainForm.name', { ns: 'common' })}{' '}
          {isInvoice || isBom || isBomItem || isPayroll ? '' : '*'}
          <input
            name="name"
            value={form.name}
            onChange={onChange}
            required={!isInvoice && !isBom && !isBomItem && !isPayroll}
            maxLength={255}
            placeholder={
              isInvoice
                ? t('ph.optionalLabel', { ns: 'common' })
                : isBom
                  ? t('ph.optionalBom', { ns: 'common' })
                  : isBomItem
                    ? t('ph.optionalBomItem', {
                        ns: 'common',
                        defaultValue: 'Optional — defaults to component product name',
                      })
                    : undefined
            }
          />
        </label>
        <label>
          Description
          <textarea name="description" value={form.description} onChange={onChange} rows={3} />
        </label>

        {isTask && (
          <>
            <div className="form-row-2">
              <label>
                {t('pages.work.status', { ns: 'common', defaultValue: 'Status' })}
                <select name="status" value={humanTaskStatusForForm(form.status)} onChange={onChange}>
                  {TASK_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
                {agentStatusHint ? (
                  <span className="muted" style={{ display: 'block', fontSize: '0.75rem', marginTop: '0.25rem' }}>
                    {t('pages.work.agentStatusHint', {
                      ns: 'common',
                      defaultValue:
                        'Agent status {{status}} (Coder/Hermes). Not selectable here — saving applies the human status above.',
                      status: agentStatusHint,
                    })}
                  </span>
                ) : null}
              </label>
              <label>
                {t('pages.work.deadline', { ns: 'common', defaultValue: 'Deadline' })}
                <input type="date" name="deadlineAt" value={form.deadlineAt} onChange={onChange} />
              </label>
            </div>
            <div className="form-row-2">
              <label>
                {t('pages.work.startAt', { ns: 'common', defaultValue: 'Start' })}
                <input type="date" name="startAt" value={form.startAt} onChange={onChange} />
              </label>
              <label>
                {t('pages.work.endAt', { ns: 'common', defaultValue: 'End' })}
                <input type="date" name="endAt" value={form.endAt} onChange={onChange} />
              </label>
            </div>
            <div className="form-row-2">
              <label>
                {t('pages.work.urgency', { ns: 'common', defaultValue: 'Urgency' })}
                <select name="urgency" value={form.urgency} onChange={onChange}>
                  {EISENHOWER_LEVELS.map((l) => (
                    <option key={l.value} value={l.value}>
                      {l.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                {t('pages.work.importance', { ns: 'common', defaultValue: 'Importance' })}
                <select name="importance" value={form.importance} onChange={onChange}>
                  {EISENHOWER_LEVELS.map((l) => (
                    <option key={l.value} value={l.value}>
                      {l.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <TaskAssigneeSelect
              assigneeHumanNpub={form.assigneeHumanNpub}
              assigneeAgentId={form.assigneeAgentId}
              defaultToMe={isNew}
              onChange={({ assigneeHumanNpub, assigneeAgentId }) =>
                setForm((f) => ({
                  ...f,
                  assigneeHumanNpub: assigneeHumanNpub || '',
                  assigneeAgentId: assigneeAgentId || '',
                }))
              }
            />
          </>
        )}

        {(isBom || isProductionOrder) && (
          <>
            <label>
              {t('domainForm.productRequired', { ns: 'common' })}
              <select name="productId" value={form.productId} onChange={onChange} required>
                <option value="">{t('domainForm.selectProduct', { ns: 'common' })}</option>
                {products.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                    {p.code || p.sku ? ` (${p.code || p.sku})` : ''}
                  </option>
                ))}
              </select>
            </label>
            {products.length === 0 && (
              <p className="muted" style={{ margin: 0 }}>
                {t('domainForm.noProducts', { ns: 'common' })}{' '}
                <Link to="/scm/buyers">{t('domainForm.scmDesk', { ns: 'common', defaultValue: 'Buyers Desk' })}</Link>
                {' · '}
                catalogue GOODS items.
              </p>
            )}
          </>
        )}

        {isBom && (
          <label>
            BOM status
            <select name="bomStatus" value={form.bomStatus} onChange={onChange}>
              {['DRAFT', 'ACTIVE', 'OBSOLETE'].map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
        )}

        {isPayroll && (
          <>
            <label>
              Period (YYYY-MM) *
              <input
                type="month"
                name="period"
                value={form.period}
                onChange={onChange}
                required
              />
            </label>
            <label>
              Payroll status
              <select name="payrollStatus" value={form.payrollStatus} onChange={onChange}>
                {['OPEN', 'READY', 'SUBMITTED', 'CLOSED'].map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Currency
              <input name="currencyCode" value={form.currencyCode} onChange={onChange} />
            </label>
          </>
        )}

        {isBomItem && (
          <>
            <label>
              Parent BOM *
              <select name="bomId" value={form.bomId} onChange={onChange} required>
                <option value="">— select BOM —</option>
                {boms.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name || b.code || 'BOM'}
                    {b.productName ? ` · ${b.productName}` : ''}
                    {b.bomStatus ? ` (${b.bomStatus})` : ''}
                  </option>
                ))}
              </select>
            </label>
            {boms.length === 0 && (
              <p className="muted" style={{ margin: 0 }}>
                No BOMs yet —{' '}
                <Link to="/domains/production/BillOfMaterials/new">create a bill of materials</Link>{' '}
                first (finished product + status).
              </p>
            )}
            <label>
              Component product *
              <select
                name="componentProductId"
                value={form.componentProductId}
                onChange={(e) => {
                  const idVal = e.target.value
                  const prod = products.find((p) => String(p.id) === String(idVal))
                  setForm((f) => ({
                    ...f,
                    componentProductId: idVal,
                    name: f.name || prod?.name || '',
                    unit: f.unit && f.unit !== 'EA' ? f.unit : prod?.unit || f.unit || 'EA',
                  }))
                }}
                required
              >
                <option value="">— select component product —</option>
                {products.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                    {p.code || p.sku ? ` (${p.code || p.sku})` : ''}
                  </option>
                ))}
              </select>
            </label>
            {products.length === 0 && (
              <p className="muted" style={{ margin: 0 }}>
                No catalogue GOODS items — create products on the SCM desk first.
              </p>
            )}
            <div className="form-row-2">
              <label>
                Quantity *
                <input name="quantity" value={form.quantity} onChange={onChange} required />
              </label>
              <label>
                Unit
                <input name="unit" value={form.unit} onChange={onChange} />
              </label>
            </div>
            <label>
              Line no.
              <input
                name="lineNo"
                value={form.lineNo}
                onChange={onChange}
                inputMode="numeric"
                min={1}
              />
            </label>
          </>
        )}

        {isProductionOrder && (
          <>
            <div className="form-row-2">
              <label>
                Quantity *
                <input name="quantity" value={form.quantity} onChange={onChange} required />
              </label>
              <label>
                Unit
                <input name="unit" value={form.unit} onChange={onChange} />
              </label>
            </div>
            <label>
              Production status
              <select name="prodStatus" value={form.prodStatus} onChange={onChange}>
                {['DRAFT', 'RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'].map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>
          </>
        )}

        {isInvoice && (
          <>
            <div className="form-row-2">
              <label>
                Invoice number
                <input name="invoiceNumber" value={form.invoiceNumber} onChange={onChange} placeholder={t('ph.invNumber', { ns: 'common' })} />
              </label>
              <label>
                Status
                <select name="invoiceStatus" value={form.invoiceStatus} onChange={onChange}>
                  {['DRAFT', 'ISSUED', 'PART_PAID', 'PAID', 'VOID'].map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <div className="form-row-2">
              <label>
                Customer name
                <input name="customerName" value={form.customerName} onChange={onChange} />
              </label>
              <label>
                Contact
                <input name="customerContact" value={form.customerContact} onChange={onChange} />
              </label>
            </div>
            <div className="form-row-2">
              <label>
                {t('money.amountPrimary', {
                  defaultValue: 'Amount ({{cur}}) *',
                  cur: form.currencyCode || 'EUR',
                })}
                <input
                  name="amountEur"
                  value={form.amountEur}
                  onChange={onChange}
                  required
                  placeholder={t('ph.money', { ns: 'common' })}
                />
                <MoneyHint
                  primaryRail={
                    form.currencyCode === 'BTC'
                      ? 'BTC'
                      : form.currencyCode === 'USD'
                        ? 'USD'
                        : 'EUR'
                  }
                  amount={form.amountEur}
                />
              </label>
              <label>
                Currency
                <input name="currencyCode" value={form.currencyCode} onChange={onChange} />
              </label>
            </div>
            <div className="form-row-2">
              <label>
                Issue date
                <input type="date" name="issueDate" value={form.issueDate} onChange={onChange} />
              </label>
              <label>
                Due date
                <input type="date" name="dueDate" value={form.dueDate} onChange={onChange} />
              </label>
            </div>
            <label>
              Reference
              <input name="referenceText" value={form.referenceText} onChange={onChange} />
            </label>
            <label className="checkbox-label">
              <input type="checkbox" name="recurring" checked={!!form.recurring} onChange={onChange} />
              Recurring
            </label>
          </>
        )}

        {isAccount && (
          <>
            <div className="form-row-2">
              <label>
                Code *
                <input name="code" value={form.code} onChange={onChange} required placeholder={t('ph.thousand', { ns: 'common' })} />
              </label>
              <label>
                Type *
                <select name="accountType" value={form.accountType} onChange={onChange} required>
                  {['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'].map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <div className="form-row-2">
              <label>
                Subtype
                <input
                  name="accountSubType"
                  value={form.accountSubType}
                  onChange={onChange}
                  placeholder={t('ph.accountKind', { ns: 'common' })}
                />
              </label>
              <label>
                Currency
                <input name="currencyCode" value={form.currencyCode} onChange={onChange} />
              </label>
            </div>
            <label>
              Parent code
              <input name="parentCode" value={form.parentCode} onChange={onChange} />
            </label>
            <label className="checkbox-label">
              <input type="checkbox" name="systemAccount" checked={!!form.systemAccount} onChange={onChange} />
              System account
            </label>
            <label className="checkbox-label">
              <input type="checkbox" name="active" checked={form.active !== false} onChange={onChange} />
              Active
            </label>
          </>
        )}

        {isTreasury && (
          <>
            <div className="form-row-2">
              <label>
                Kind *
                <select name="accountKind" value={form.accountKind} onChange={onChange} required>
                  {['FIAT_BANK', 'BTC_WALLET', 'LN_WALLET', 'FIAT_BRIDGE', 'OTHER'].map((k) => (
                    <option key={k} value={k}>
                      {k}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Currency
                <input name="currencyCode" value={form.currencyCode} onChange={onChange} />
              </label>
            </div>
            <div className="form-row-2">
              <label>
                Balance EUR
                <input name="balanceEur" value={form.balanceEur} onChange={onChange} />
              </label>
              <label>
                Balance sats
                <input name="balanceSats" value={form.balanceSats} onChange={onChange} />
              </label>
            </div>
            <label>
              External ref
              <input name="externalRef" value={form.externalRef} onChange={onChange} placeholder={t('ph.treasuryLabel', { ns: 'common' })} />
            </label>
            <label className="checkbox-label">
              <input type="checkbox" name="active" checked={form.active !== false} onChange={onChange} />
              Active
            </label>
          </>
        )}

        {isAddress && (
          <>
            <label>
              Address type
              <select name="addressType" value={form.addressType} onChange={onChange}>
                {ADDRESS_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </label>
            <div className="form-row-2">
              <label>
                Street *
                <input name="street" value={form.street} onChange={onChange} required />
              </label>
              <label>
                No.
                <input name="streetNo" value={form.streetNo} onChange={onChange} />
              </label>
            </div>
            {[2, 3, 4, 5, 6].map((n) => (
              <label key={n}>
                Street line {n}
                <input name={`street${n}`} value={form[`street${n}`]} onChange={onChange} />
              </label>
            ))}
            <div className="form-row-2">
              <label>
                Building
                <input name="building" value={form.building} onChange={onChange} />
              </label>
              <label>
                Room / suite
                <input name="room" value={form.room} onChange={onChange} />
              </label>
            </div>
            <div className="form-row-2">
              <label>
                Lot
                <input name="lot" value={form.lot} onChange={onChange} />
              </label>
              <label>
                Locality
                <input name="locality" value={form.locality} onChange={onChange} />
              </label>
            </div>
            <div className="form-row-2">
              <label>
                Postal code
                <input name="postalCode" value={form.postalCode} onChange={onChange} />
              </label>
              <label>
                City *
                <input name="city" value={form.city} onChange={onChange} required />
              </label>
            </div>
            <div className="form-row-2">
              <label>
                Region / state
                <input name="region" value={form.region} onChange={onChange} />
              </label>
              <label>
                County
                <input name="county" value={form.county} onChange={onChange} />
              </label>
            </div>
            <label>
              Country *
              <select name="countryCode" value={form.countryCode} onChange={onChange} required>
                {countries.length === 0 && <option value={form.countryCode}>{form.countryCode}</option>}
                {countries.map((c) => (
                  <option key={c.id || c.code} value={c.code}>
                    {c.code} — {c.name}
                  </option>
                ))}
              </select>
            </label>
            <div className="form-row-2">
              <label>
                Latitude
                <input name="latitude" value={form.latitude} onChange={onChange} placeholder={t('ph.lat', { ns: 'common' })} />
              </label>
              <label>
                Longitude
                <input name="longitude" value={form.longitude} onChange={onChange} placeholder={t('ph.lng', { ns: 'common' })} />
              </label>
            </div>
            <label className="checkbox-label">
              <input type="checkbox" name="active" checked={!!form.active} onChange={onChange} />
              Active
            </label>
          </>
        )}

        {!isTask && !isAddress && !isInvoice && !isAccount && !isTreasury && (
          <>
            <label>
              {t('wb.status', { ns: 'common', defaultValue: 'Status' })}
              <SimpleStatusSelect
                name="status"
                value={form.status || ''}
                onChange={onChange}
              />
            </label>
            {(resource === 'ContentType' ||
              resource === 'ContentTypeConfig' ||
              resource === 'SupportedLocale' ||
              resource === 'Countries' ||
              resource === 'Currencies' ||
              resource === 'SimpleStatus') && (
              <label>
                Code
                <input name="code" value={form.code || ''} onChange={onChange} placeholder={t('ph.uniqueCode', { ns: 'common' })} />
              </label>
            )}
          </>
        )}

        {isAddress && (
          <>
            <label>
              {t('wb.status', { ns: 'common', defaultValue: 'Status' })}
              <SimpleStatusSelect
                name="status"
                value={form.status || ''}
                onChange={onChange}
              />
            </label>
          </>
        )}
          </>
        )}

        {isNew && companionEligible && (
          <PendingContentComposer
            package={pkg}
            resource={resource}
            pending={pendingContent}
            onChange={setPendingContent}
          />
        )}

        <div className="toolbar" style={{ marginBottom: 0, marginTop: 12 }}>
          <button type="submit" className="btn btn-primary">
            {isNew
              ? t('actions.create', { ns: 'common' })
              : t('actions.save', { ns: 'common' })}
          </button>
          <Link className="btn" to={listReturnHrefResolved}>
            {t('actions.cancel', { ns: 'common' })}
          </Link>
          {preferDesk && deskOpenHrefResolved && (
            <Link className="btn btn-secondary" to={deskOpenHrefResolved}>
              <ExternalLink size={16} />{' '}
              {t('masterData.openDesk', {
                ns: 'common',
                desk: deskLabel,
                defaultValue: 'Open on {{desk}}',
              })}
            </Link>
          )}
        </div>
      </form>

      {!isNew && companionWarn && (
        <div className="banner-error" style={{ marginTop: 12 }}>
          {companionWarn}
        </div>
      )}

      {!isNew && id && pkg && resource && companionEligible && (
        <ContentRail package={pkg} resource={resource} id={id} />
      )}
    </div>
  )
}
