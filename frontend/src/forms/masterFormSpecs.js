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

import { assigneePairForBody, assigneePairFromRow } from '../utils/taskAssignee'

/**
 * Shared master-entity form specs (P1 master-data ↔ desk parity).
 * Used by DomainForm and desk create panels.
 *
 * Each field: { name, labelKey, label, type, required?, options?, placeholder?, inputMode? }
 * type: text | textarea | email | select | checkbox | number | date | assignee | currentUser | …
 */

const ASSIGNEE_FIELD = {
  name: 'assignee',
  label: 'Assignee',
  type: 'assignee',
  fullWidth: true,
}

export const MASTER_FORM_KEYS = [
  'Customer',
  'Vendor',
  'Product',
  'Service',
  'Resource',
  'ServiceLevel',
  'Budget',
  'Payable',
  'CatalogueItem',
  'PurchaseOrder',
  'CustomerQuote',
  'SalesOrder',
  'PriceList',
  'Opportunity',
  'Competitor',
  'StrategyItem',
  'DevelopmentProject',
  'PhysicalAsset',
  'ServiceOrder',
  'Inquiry',
  'Warranty',
  'Claims',
  'Rma',
]

function currentPeriodYyyyMm() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const customerSpec = {
  resource: 'Customer',
  packages: ['sales'],
  empty: () => ({
    name: '',
    description: '',
    customerKind: 'ORGANIZATION',
    email: '',
    phone: '',
    contactName: '',
    taxId: '',
    currencyCode: 'EUR',
    communicationLocale: '',
    active: true,
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'customerKind',
      label: 'Kind',
      type: 'select',
      required: true,
      options: [
        { value: 'ORGANIZATION', label: 'Organization' },
        { value: 'PERSON', label: 'Person' },
      ],
    },
    { name: 'contactName', label: 'Contact name', type: 'text' },
    { name: 'email', label: 'Email', type: 'email' },
    { name: 'phone', label: 'Phone', type: 'text' },
    { name: 'taxId', label: 'Tax ID', type: 'text' },
    { name: 'currencyCode', label: 'Preferred currency', type: 'currency', required: true },
    {
      name: 'communicationLocale',
      label: 'Communication language',
      type: 'locale',
    },
    ASSIGNEE_FIELD,
    { name: 'active', label: 'Active', type: 'checkbox' },
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    customerKind: f.customerKind || 'ORGANIZATION',
    contactName: f.contactName || null,
    email: f.email || null,
    phone: f.phone || null,
    taxId: f.taxId || null,
    currencyCode: f.currencyCode || 'EUR',
    communicationLocale: f.communicationLocale || null,
    active: f.active !== false,
    ...assigneePairForBody(f),
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    customerKind: row.customerKind || 'ORGANIZATION',
    contactName: row.contactName || '',
    email: row.email || '',
    phone: row.phone || '',
    taxId: row.taxId || '',
    currencyCode: row.currencyCode || 'EUR',
    communicationLocale: row.communicationLocale || '',
    active: row.active !== false,
    ...assigneePairFromRow(row),
  }),
  listColumns: [
    { key: 'customerKind', label: 'Kind' },
    { key: 'email', label: 'Email', muted: true },
    { key: 'currencyCode', label: 'Ccy', mono: true },
    { key: 'communicationLocale', label: 'Lang', mono: true },
  ],
}

const vendorSpec = {
  resource: 'Vendor',
  packages: ['procurement'],
  empty: () => ({
    name: '',
    description: '',
    email: '',
    phone: '',
    contactName: '',
    taxId: '',
    currencyCode: 'EUR',
    communicationLocale: '',
    active: true,
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'contactName', label: 'Contact name', type: 'text' },
    { name: 'email', label: 'Email', type: 'email' },
    { name: 'phone', label: 'Phone', type: 'text' },
    { name: 'taxId', label: 'Tax ID', type: 'text' },
    { name: 'currencyCode', label: 'Preferred currency', type: 'currency', required: true },
    {
      name: 'communicationLocale',
      label: 'Communication language',
      type: 'locale',
    },
    { name: 'active', label: 'Active', type: 'checkbox' },
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    contactName: f.contactName || null,
    email: f.email || null,
    phone: f.phone || null,
    taxId: f.taxId || null,
    currencyCode: f.currencyCode || 'EUR',
    communicationLocale: f.communicationLocale || null,
    active: f.active !== false,
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    contactName: row.contactName || '',
    email: row.email || '',
    phone: row.phone || '',
    taxId: row.taxId || '',
    currencyCode: row.currencyCode || 'EUR',
    communicationLocale: row.communicationLocale || '',
    active: row.active !== false,
  }),
  listColumns: [
    { key: 'email', label: 'Email', muted: true },
    { key: 'contactName', label: 'Contact' },
    { key: 'currencyCode', label: 'Ccy', mono: true },
    { key: 'communicationLocale', label: 'Lang', mono: true },
  ],
}

const productSpec = {
  resource: 'Product',
  packages: ['production'],
  empty: () => ({
    name: '',
    description: '',
    sku: '',
    unit: 'EA',
    listPrice: '',
    currency: 'EUR',
    trackLots: false,
    isService: false,
    active: true,
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'sku', label: 'SKU', type: 'text', placeholder: 'optional unique code' },
    { name: 'unit', label: 'Unit', type: 'text', required: true },
    {
      name: 'listPrice',
      label: 'List price',
      type: 'number',
      inputMode: 'decimal',
    },
    { name: 'currency', label: 'Price currency', type: 'currency', required: true },
    { name: 'trackLots', label: 'Track lots (provenance)', type: 'checkbox' },
    { name: 'isService', label: 'Is service product', type: 'checkbox' },
    { name: 'active', label: 'Active', type: 'checkbox' },
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    sku: f.sku || null,
    unit: f.unit || 'EA',
    listPrice: f.listPrice === '' || f.listPrice == null ? null : f.listPrice,
    currency: f.currency || 'EUR',
    trackLots: !!f.trackLots,
    isService: !!f.isService,
    active: f.active !== false,
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    sku: row.sku || '',
    unit: row.unit || 'EA',
    listPrice: row.listPrice != null ? String(row.listPrice) : '',
    currency: row.currency || row.currencyCode || 'EUR',
    trackLots: !!row.trackLots,
    isService: !!row.isService,
    active: row.active !== false,
  }),
  listColumns: [
    { key: 'sku', label: 'SKU', mono: true },
    { key: 'unit', label: 'Unit' },
    {
      key: 'trackLots',
      label: 'Lots',
      format: (v) => (v ? 'yes' : '—'),
    },
  ],
}

const serviceSpec = {
  resource: 'Service',
  packages: ['delivery'],
  empty: () => ({
    name: '',
    description: '',
    code: '',
    unit: 'HOUR',
    listPriceEur: '100',
    currency: 'EUR',
    slaCode: '',
    active: true,
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'code', label: 'Code', type: 'text' },
    {
      name: 'unit',
      label: 'Unit',
      type: 'select',
      required: true,
      options: ['HOUR', 'DAY', 'FIXED', 'UNIT'].map((u) => ({ value: u, label: u })),
    },
    {
      name: 'listPriceEur',
      label: 'List price',
      type: 'number',
      inputMode: 'decimal',
    },
    { name: 'currency', label: 'Price currency', type: 'currency', required: true },
    { name: 'slaCode', label: 'SLA code', type: 'text' },
    { name: 'active', label: 'Active', type: 'checkbox' },
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    code: f.code || null,
    unit: f.unit || 'HOUR',
    listPriceEur: f.listPriceEur === '' || f.listPriceEur == null ? null : f.listPriceEur,
    currency: f.currency || 'EUR',
    slaCode: f.slaCode || null,
    active: f.active !== false,
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    code: row.code || '',
    unit: row.unit || 'HOUR',
    listPriceEur:
      row.listPriceEur != null
        ? String(row.listPriceEur)
        : row.listPrice != null
          ? String(row.listPrice)
          : '',
    currency: row.currency || row.currencyCode || 'EUR',
    slaCode: row.slaCode || '',
    active: row.active !== false,
  }),
  listColumns: [
    { key: 'code', label: 'Code', mono: true },
    { key: 'unit', label: 'Unit' },
    {
      key: 'listPriceEur',
      label: 'Price',
      format: (v) => (v != null ? Number(v).toFixed(2) : '—'),
    },
  ],
}

const resourceSpec = {
  resource: 'Resource',
  packages: ['delivery'],
  empty: () => ({
    name: '',
    description: '',
    resourceKind: 'HUMAN',
    capacityHours: '40',
    email: '',
    active: true,
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'resourceKind',
      label: 'Kind',
      type: 'select',
      required: true,
      options: ['HUMAN', 'AGENT', 'OTHER'].map((k) => ({ value: k, label: k })),
    },
    {
      name: 'capacityHours',
      label: 'Capacity hours',
      type: 'number',
      inputMode: 'decimal',
    },
    { name: 'email', label: 'Email', type: 'email' },
    { name: 'active', label: 'Active', type: 'checkbox' },
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    resourceKind: f.resourceKind || 'HUMAN',
    capacityHours:
      f.capacityHours === '' || f.capacityHours == null ? null : f.capacityHours,
    email: f.email || null,
    active: f.active !== false,
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    resourceKind: row.resourceKind || 'HUMAN',
    capacityHours: row.capacityHours != null ? String(row.capacityHours) : '',
    email: row.email || '',
    active: row.active !== false,
  }),
  listColumns: [
    { key: 'resourceKind', label: 'Kind' },
    { key: 'capacityHours', label: 'Hours' },
    { key: 'email', label: 'Email', muted: true },
  ],
}

const serviceLevelSpec = {
  resource: 'ServiceLevel',
  packages: ['service'],
  empty: () => ({
    name: '',
    description: '',
    code: '',
    channel: 'ANY',
    responseHours: '24',
    resolveHours: '72',
    active: true,
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'code', label: 'Code', type: 'text' },
    {
      name: 'channel',
      label: 'Channel',
      type: 'select',
      required: true,
      options: ['ANY', 'EMAIL', 'CHAT', 'PHONE', 'OTHER'].map((c) => ({
        value: c,
        label: c,
      })),
    },
    {
      name: 'responseHours',
      label: 'Response hours',
      type: 'number',
      inputMode: 'numeric',
    },
    {
      name: 'resolveHours',
      label: 'Resolve hours',
      type: 'number',
      inputMode: 'numeric',
    },
    { name: 'active', label: 'Active', type: 'checkbox' },
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    code: f.code || null,
    channel: f.channel || 'ANY',
    responseHours:
      f.responseHours === '' || f.responseHours == null ? null : Number(f.responseHours),
    resolveHours:
      f.resolveHours === '' || f.resolveHours == null ? null : Number(f.resolveHours),
    active: f.active !== false,
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    code: row.code || '',
    channel: row.channel || 'ANY',
    responseHours: row.responseHours != null ? String(row.responseHours) : '',
    resolveHours: row.resolveHours != null ? String(row.resolveHours) : '',
    active: row.active !== false,
  }),
  listColumns: [
    { key: 'code', label: 'Code', mono: true },
    { key: 'channel', label: 'Channel' },
    { key: 'responseHours', label: 'Resp h' },
  ],
}

/** Finance Budget — same fields as FinanceHub budgets tab (period + amount). */
const budgetSpec = {
  resource: 'Budget',
  packages: ['finance'],
  empty: () => ({
    name: '',
    description: '',
    period: currentPeriodYyyyMm(),
    amountEur: '1000',
    currencyCode: 'EUR',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'period',
      label: 'Period (yyyy-MM)',
      type: 'text',
      required: true,
      placeholder: '2026-10',
    },
    {
      name: 'amountEur',
      label: 'Amount (EUR)',
      type: 'number',
      required: true,
      inputMode: 'decimal',
    },
    { name: 'currencyCode', label: 'Currency', type: 'currency', required: true },
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    period: (f.period || '').trim(),
    amountEur: f.amountEur === '' || f.amountEur == null ? null : Number(f.amountEur),
    currencyCode: f.currencyCode || 'EUR',
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    period: row.period || currentPeriodYyyyMm(),
    amountEur: row.amountEur != null ? String(row.amountEur) : '',
    currencyCode: row.currencyCode || 'EUR',
  }),
  listColumns: [
    { key: 'period', label: 'Period', mono: true },
    {
      key: 'amountEur',
      label: 'EUR',
      mono: true,
      format: (v) => (v != null ? Number(v).toFixed(2) : '—'),
    },
    { key: 'currencyCode', label: 'Ccy', mono: true },
  ],
}

/** Catalogue master — goods/services; field shape adapted from Product. */
const catalogueItemSpec = {
  resource: 'CatalogueItem',
  packages: ['catalogue'],
  empty: () => ({
    name: '',
    description: '',
    sku: '',
    unit: 'EA',
    listPrice: '',
    currency: 'EUR',
    nature: 'GOODS',
    isService: false,
    active: true,
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'sku', label: 'SKU / code', type: 'text', placeholder: 'optional unique code' },
    { name: 'unit', label: 'Unit', type: 'text', required: true },
    {
      name: 'listPrice',
      label: 'List price',
      type: 'number',
      inputMode: 'decimal',
    },
    { name: 'currency', label: 'Price currency', type: 'currency', required: true },
    {
      name: 'nature',
      label: 'Kind',
      type: 'select',
      required: true,
      options: [
        { value: 'GOODS', label: 'Product' },
        { value: 'SERVICE', label: 'Service' },
      ],
    },
    ASSIGNEE_FIELD,
    { name: 'active', label: 'Active', type: 'checkbox' },
  ],
  toBody: (f) => {
    const nature = f.nature === 'SERVICE' || f.isService ? 'SERVICE' : 'GOODS'
    return {
      name: f.name,
      description: f.description || null,
      code: f.sku || null,
      unit: f.unit || 'EA',
      listPrice: f.listPrice === '' || f.listPrice == null ? null : f.listPrice,
      currency: f.currency || 'EUR',
      currencyCode: f.currency || 'EUR',
      nature,
      active: f.active !== false,
      canBeBought: true,
      canBeSold: true,
      ...assigneePairForBody(f),
    }
  },
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    sku: row.code || row.sku || '',
    unit: row.unit || 'EA',
    listPrice: row.listPrice != null ? String(row.listPrice) : '',
    currency: row.currency || row.currencyCode || 'EUR',
    nature: row.nature || (row.isService ? 'SERVICE' : 'GOODS'),
    isService: row.nature === 'SERVICE' || !!row.isService,
    active: row.active !== false,
    ...assigneePairFromRow(row),
  }),
  /** Domain stores GOODS|SERVICE; operators see Product|Service. */
  listColumns: [
    { key: 'code', label: 'Code', mono: true },
    {
      key: 'nature',
      label: 'Kind',
      format: (v) =>
        v === 'SERVICE' ? 'Service' : v === 'GOODS' ? 'Product' : v || '—',
    },
    { key: 'unit', label: 'Unit' },
  ],
}

/** DRAFT PO header only — lines edited separately in RecordEditModal. */
const purchaseOrderSpec = {
  resource: 'PurchaseOrder',
  packages: ['procurement'],
  empty: () => ({
    name: '',
    description: '',
    currencyCode: 'EUR',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'currencyCode', label: 'Currency', type: 'currency', required: true },
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    currencyCode: f.currencyCode || 'EUR',
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    currencyCode: row.currencyCode || 'EUR',
  }),
  listColumns: [
    { key: 'poNumber', label: 'Number', mono: true },
    { key: 'currencyCode', label: 'Ccy', mono: true },
  ],
}

/** DRAFT quote header only — lines shown in RecordView belowFields. */
const customerQuoteSpec = {
  resource: 'CustomerQuote',
  packages: ['sales'],
  empty: () => ({
    name: '',
    description: '',
    quoteStatus: 'DRAFT',
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'quoteStatus',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'DRAFT', label: 'Draft' },
        { value: 'SENT', label: 'Sent' },
        { value: 'ACCEPTED', label: 'Accepted' },
        { value: 'REJECTED', label: 'Rejected' },
        { value: 'EXPIRED', label: 'Expired' },
      ],
    },
    ASSIGNEE_FIELD,
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    quoteStatus: f.quoteStatus || 'DRAFT',
    ...assigneePairForBody(f),
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    quoteStatus: row.quoteStatus || 'DRAFT',
    ...assigneePairFromRow(row),
  }),
  listColumns: [
    { key: 'quoteNumber', label: 'Number', mono: true },
    { key: 'quoteStatus', label: 'Status' },
  ],
}

/**
 * DRAFT sales order header + lines in RecordEditModal (confirm/cancel stay on desk).
 * orderStatus is read for the badge; not written from the modal.
 */
const salesOrderSpec = {
  resource: 'SalesOrder',
  packages: ['sales'],
  empty: () => ({
    name: '',
    description: '',
    currencyCode: 'EUR',
    orderStatus: 'DRAFT',
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'currencyCode', label: 'Currency', type: 'currency', required: true },
    ASSIGNEE_FIELD,
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    currencyCode: f.currencyCode || 'EUR',
    ...assigneePairForBody(f),
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    currencyCode: row.currencyCode || 'EUR',
    orderStatus: row.orderStatus || 'DRAFT',
    ...assigneePairFromRow(row),
  }),
  listColumns: [
    { key: 'orderNumber', label: 'Number', mono: true },
    { key: 'orderStatus', label: 'Status' },
    { key: 'currencyCode', label: 'Ccy', mono: true },
  ],
}

/** Finance Payable — vendor + amount aligned with FinanceHub payables create. */
const payableSpec = {
  resource: 'Payable',
  packages: ['finance'],
  empty: () => ({
    name: '',
    description: '',
    vendorName: '',
    amountEur: '50',
    currencyCode: 'EUR',
    payableStatus: 'OPEN',
  }),
  fields: [
    { name: 'vendorName', label: 'Vendor', type: 'text', required: true },
    { name: 'name', label: 'Label', type: 'text' },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'amountEur',
      label: 'Amount (EUR)',
      type: 'number',
      required: true,
      inputMode: 'decimal',
    },
    { name: 'currencyCode', label: 'Currency', type: 'currency', required: true },
    {
      name: 'payableStatus',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'DRAFT', label: 'Draft' },
        { value: 'OPEN', label: 'Open' },
        { value: 'PAID', label: 'Paid' },
        { value: 'VOID', label: 'Void' },
      ],
    },
  ],
  toBody: (f) => ({
    vendorName: f.vendorName || null,
    name: f.name || f.vendorName || 'Payable',
    description: f.description || null,
    amountEur: f.amountEur === '' || f.amountEur == null ? null : Number(f.amountEur),
    currencyCode: f.currencyCode || 'EUR',
    payableStatus: f.payableStatus || 'OPEN',
  }),
  fromRow: (row, empty) => ({
    ...empty,
    vendorName: row.vendorName || '',
    name: row.name || '',
    description: row.description || '',
    amountEur: row.amountEur != null ? String(row.amountEur) : '',
    currencyCode: row.currencyCode || 'EUR',
    payableStatus: row.payableStatus || 'OPEN',
  }),
  listColumns: [
    { key: 'vendorName', label: 'Vendor' },
    {
      key: 'amountEur',
      label: 'EUR',
      mono: true,
      format: (v) => (v != null ? Number(v).toFixed(2) : '—'),
    },
    { key: 'payableStatus', label: 'Status' },
  ],
}

const competitorSpec = {
  resource: 'Competitor',
  packages: ['strategy'],
  empty: () => ({
    name: '',
    type: '',
    description: '',
    sources: '',
    status: 'ACTIVE',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'type', label: 'Type', type: 'text', maxLength: 128 },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'sources', label: 'Sources', type: 'textarea' },
    {
      name: 'status',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'ACTIVE', label: 'Active' },
        { value: 'WATCH', label: 'Watch' },
        { value: 'INACTIVE', label: 'Inactive' },
      ],
    },
  ],
  toBody: (f) => ({
    name: f.name,
    type: f.type || null,
    description: f.description || null,
    sources: f.sources || null,
    status: f.status || 'ACTIVE',
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    type: row.type || '',
    description: row.description || '',
    sources: row.sources || '',
    status: row.status || 'ACTIVE',
  }),
  listColumns: [
    { key: 'type', label: 'Type' },
    { key: 'status', label: 'Status' },
  ],
}

const strategyItemSpec = {
  resource: 'StrategyItem',
  packages: ['strategy'],
  empty: () => ({
    name: '',
    type: '',
    description: '',
    itemKind: 'STRATEGY',
    status: 'DRAFT',
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'type', label: 'Type', type: 'text', maxLength: 128 },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'itemKind',
      label: 'Kind',
      type: 'select',
      required: true,
      options: [
        { value: 'VISION', label: 'Vision' },
        { value: 'MISSION', label: 'Mission' },
        { value: 'STRATEGY', label: 'Strategy' },
        { value: 'NEED', label: 'Need' },
        { value: 'FACTOR', label: 'Factor' },
      ],
    },
    {
      name: 'status',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'DRAFT', label: 'Draft' },
        { value: 'ACTIVE', label: 'Active' },
        { value: 'ARCHIVED', label: 'Archived' },
      ],
    },
    ASSIGNEE_FIELD,
  ],
  toBody: (f) => ({
    name: f.name,
    type: f.type || null,
    description: f.description || null,
    itemKind: f.itemKind || 'STRATEGY',
    status: f.status || 'DRAFT',
    ...assigneePairForBody(f),
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    type: row.type || '',
    description: row.description || '',
    itemKind: row.itemKind || 'STRATEGY',
    status: row.status || 'DRAFT',
    ...assigneePairFromRow(row),
  }),
  listColumns: [
    { key: 'type', label: 'Type' },
    { key: 'description', label: 'Description' },
    { key: 'itemKind', label: 'Kind' },
    { key: 'status', label: 'Status' },
  ],
}

function toDateInputValue(v) {
  if (v == null || v === '') return ''
  if (typeof v === 'string') {
    const m = v.match(/^(\d{4}-\d{2}-\d{2})/)
    return m ? m[1] : ''
  }
  if (v instanceof Date && !Number.isNaN(v.getTime())) {
    return v.toISOString().slice(0, 10)
  }
  return ''
}

/** DevelopmentBasics — NPD / revision project (G4). */
const developmentProjectSpec = {
  resource: 'DevelopmentProject',
  packages: ['development'],
  empty: () => ({
    name: '',
    description: '',
    projectKind: 'NPD',
    projectStatus: 'DRAFT',
    catalogueItemId: '',
    startDate: '',
    endDate: '',
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'projectKind',
      label: 'Kind',
      type: 'select',
      required: true,
      options: [
        { value: 'NPD', label: 'NPD' },
        { value: 'REVISION', label: 'Revision' },
        { value: 'OTHER', label: 'Other' },
      ],
    },
    {
      name: 'projectStatus',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'DRAFT', label: 'Draft' },
        { value: 'ACTIVE', label: 'Active' },
        { value: 'DONE', label: 'Done' },
        { value: 'CANCELLED', label: 'Cancelled' },
      ],
    },
    {
      name: 'catalogueItemId',
      label: 'Catalogue item id',
      type: 'number',
      inputMode: 'numeric',
      placeholder: 'Optional',
    },
    { name: 'startDate', label: 'Start date', type: 'date', placeholder: 'YYYY-MM-DD' },
    { name: 'endDate', label: 'End date', type: 'date', placeholder: 'YYYY-MM-DD' },
    ASSIGNEE_FIELD,
  ],
  toBody: (f) => {
    const rawId = f.catalogueItemId
    let catalogueItemId = null
    if (rawId !== '' && rawId != null) {
      const n = Number(rawId)
      catalogueItemId = Number.isFinite(n) ? n : null
    }
    return {
      name: f.name,
      description: f.description || null,
      projectKind: f.projectKind || 'NPD',
      projectStatus: f.projectStatus || 'DRAFT',
      catalogueItemId,
      startDate: f.startDate || null,
      endDate: f.endDate || null,
      ...assigneePairForBody(f),
    }
  },
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    projectKind: row.projectKind || 'NPD',
    projectStatus: row.projectStatus || 'DRAFT',
    catalogueItemId: row.catalogueItemId != null ? String(row.catalogueItemId) : '',
    startDate: toDateInputValue(row.startDate),
    endDate: toDateInputValue(row.endDate),
    ...assigneePairFromRow(row),
  }),
  listColumns: [
    { key: 'projectKind', label: 'Kind' },
    { key: 'projectStatus', label: 'Status' },
  ],
}

/** AssetBasics — physical plant / vehicles / tools (G4). */
const physicalAssetSpec = {
  resource: 'PhysicalAsset',
  packages: ['asset'],
  empty: () => ({
    name: '',
    description: '',
    assetTag: '',
    location: '',
    ownerName: '',
    status: 'DRAFT',
    itManaged: false,
    configItemId: '',
    financialDepreciation: false,
    requiresMaintenance: false,
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'assetTag', label: 'Asset tag', type: 'text', placeholder: 'Inventory / plate / serial' },
    { name: 'location', label: 'Location', type: 'text' },
    { name: 'ownerName', label: 'Owner', type: 'currentUser' },
    ASSIGNEE_FIELD,
    {
      name: 'status',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'DRAFT', label: 'Draft' },
        { value: 'IN_SERVICE', label: 'In service' },
        { value: 'MAINTENANCE', label: 'Maintenance' },
        { value: 'RETIRED', label: 'Retired' },
      ],
    },
    { name: 'itManaged', label: 'IT-managed (link ConfigItem)', type: 'checkbox' },
    {
      name: 'configItemId',
      label: 'Config item id',
      type: 'number',
      inputMode: 'numeric',
      placeholder: 'Optional when IT-managed',
    },
    { name: 'financialDepreciation', label: 'Financial depreciation', type: 'checkbox' },
    { name: 'requiresMaintenance', label: 'Requires maintenance', type: 'checkbox' },
  ],
  toBody: (f) => {
    const rawId = f.configItemId
    let configItemId = null
    if (rawId !== '' && rawId != null) {
      const n = Number(rawId)
      configItemId = Number.isFinite(n) ? n : null
    }
    return {
      name: f.name,
      description: f.description || null,
      assetTag: f.assetTag || null,
      location: f.location || null,
      ownerName: f.ownerName || null,
      status: f.status || 'DRAFT',
      itManaged: !!f.itManaged,
      configItemId,
      financialDepreciation: !!f.financialDepreciation,
      requiresMaintenance: !!f.requiresMaintenance,
      ...assigneePairForBody(f),
    }
  },
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    assetTag: row.assetTag || '',
    location: row.location || '',
    ownerName: row.ownerName || '',
    status: row.status || 'DRAFT',
    itManaged: row.itManaged === true || row.itManaged === 'true',
    configItemId: row.configItemId != null ? String(row.configItemId) : '',
    financialDepreciation:
      row.financialDepreciation === true || row.financialDepreciation === 'true',
    requiresMaintenance: row.requiresMaintenance === true || row.requiresMaintenance === 'true',
    ...assigneePairFromRow(row),
  }),
  listColumns: [
    { key: 'assetTag', label: 'Tag' },
    { key: 'location', label: 'Location' },
    { key: 'status', label: 'Status' },
  ],
}

/** DRAFT ServiceOrder header — lines/process stay on the desk modal. */
const serviceOrderSpec = {
  resource: 'ServiceOrder',
  packages: ['delivery'],
  empty: () => ({
    name: '',
    description: '',
    currencyCode: 'EUR',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'currencyCode', label: 'Currency', type: 'currency', required: true },
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    currencyCode: f.currencyCode || 'EUR',
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    currencyCode: row.currencyCode || 'EUR',
  }),
  listColumns: [
    { key: 'orderNumber', label: 'Number', mono: true },
    { key: 'fulfillStatus', label: 'Status' },
  ],
}

const inquirySpec = {
  resource: 'Inquiry',
  packages: ['service'],
  empty: () => ({
    name: '',
    description: '',
    caseStatus: 'OPEN',
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'caseStatus',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'OPEN', label: 'Open' },
        { value: 'IN_PROGRESS', label: 'In progress' },
        { value: 'RESOLVED', label: 'Resolved' },
        { value: 'CLOSED', label: 'Closed' },
        { value: 'CANCELLED', label: 'Cancelled' },
      ],
    },
    // Optional Assignable pair — do not bind legacy free-text assignedTo
    ASSIGNEE_FIELD,
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    caseStatus: f.caseStatus || 'OPEN',
    ...assigneePairForBody(f),
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    caseStatus: row.caseStatus || 'OPEN',
    ...assigneePairFromRow(row),
  }),
  listColumns: [{ key: 'caseStatus', label: 'Status' }],
}

const warrantySpec = {
  resource: 'Warranty',
  packages: ['service'],
  empty: () => ({
    name: '',
    description: '',
    coverStatus: 'DRAFT',
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'coverStatus',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'DRAFT', label: 'Draft' },
        { value: 'ACTIVE', label: 'Active' },
        { value: 'EXPIRED', label: 'Expired' },
        { value: 'VOID', label: 'Void' },
      ],
    },
    ASSIGNEE_FIELD,
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    coverStatus: f.coverStatus || 'DRAFT',
    ...assigneePairForBody(f),
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    coverStatus: row.coverStatus || 'DRAFT',
    ...assigneePairFromRow(row),
  }),
  listColumns: [{ key: 'coverStatus', label: 'Status' }],
}

const claimsSpec = {
  resource: 'Claims',
  packages: ['service'],
  empty: () => ({
    name: '',
    description: '',
    claimStatus: 'RECEIVED',
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'claimStatus',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'RECEIVED', label: 'Received' },
        { value: 'VALIDATED', label: 'Validated' },
        { value: 'APPROVED', label: 'Approved' },
        { value: 'REJECTED', label: 'Rejected' },
        { value: 'CLOSED', label: 'Closed' },
      ],
    },
    ASSIGNEE_FIELD,
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    claimStatus: f.claimStatus || 'RECEIVED',
    ...assigneePairForBody(f),
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    claimStatus: row.claimStatus || 'RECEIVED',
    ...assigneePairFromRow(row),
  }),
  listColumns: [{ key: 'claimStatus', label: 'Status' }],
}

const rmaSpec = {
  resource: 'Rma',
  packages: ['service'],
  empty: () => ({
    name: '',
    description: '',
    rmaStatus: 'REQUESTED',
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'rmaStatus',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'REQUESTED', label: 'Requested' },
        { value: 'CONFIRMED', label: 'Confirmed' },
        { value: 'CLOSED', label: 'Closed' },
        { value: 'CANCELLED', label: 'Cancelled' },
      ],
    },
    ASSIGNEE_FIELD,
  ],
  toBody: (f) => ({
    name: f.name,
    description: f.description || null,
    rmaStatus: f.rmaStatus || 'REQUESTED',
    ...assigneePairForBody(f),
  }),
  fromRow: (row, empty) => ({
    ...empty,
    name: row.name || '',
    description: row.description || '',
    rmaStatus: row.rmaStatus || 'REQUESTED',
    ...assigneePairFromRow(row),
  }),
  listColumns: [{ key: 'rmaStatus', label: 'Status' }],
}

/** Price list header — lines edited in RecordView belowFields (SalesAdvanced). */
const priceListSpec = {
  resource: 'PriceList',
  packages: ['sales'],
  empty: () => ({
    name: '',
    code: '',
    listKind: 'STANDARD',
    currencyCode: 'EUR',
    active: true,
    validFrom: '',
    validTo: '',
    customerId: '',
    description: '',
    defaultDiscountPercent: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    { name: 'code', label: 'Code', type: 'text' },
    {
      name: 'listKind',
      label: 'Kind',
      type: 'select',
      required: true,
      options: [
        { value: 'STANDARD', label: 'STANDARD' },
        { value: 'CUSTOMER', label: 'CUSTOMER' },
        { value: 'VOLUME', label: 'VOLUME' },
      ],
    },
    { name: 'currencyCode', label: 'Currency', type: 'currency', required: true },
    { name: 'active', label: 'Active', type: 'checkbox' },
    { name: 'customerId', label: 'Customer id', type: 'number', inputMode: 'numeric' },
    { name: 'validFrom', label: 'Valid from', type: 'date' },
    { name: 'validTo', label: 'Valid to', type: 'date' },
    {
      name: 'defaultDiscountPercent',
      label: 'Default discount %',
      type: 'number',
      inputMode: 'decimal',
    },
    { name: 'description', label: 'Description', type: 'textarea' },
  ],
  toBody: (f) => {
    const body = {
      name: (f.name || '').trim(),
      code: (f.code || '').trim() || null,
      listKind: f.listKind || 'STANDARD',
      currencyCode: f.currencyCode || 'EUR',
      active: f.active !== false,
      description: (f.description || '').trim() || null,
      validFrom: f.validFrom || null,
      validTo: f.validTo || null,
    }
    if (f.defaultDiscountPercent !== '' && f.defaultDiscountPercent != null) {
      body.defaultDiscountPercent = Number(f.defaultDiscountPercent)
    } else {
      body.defaultDiscountPercent = null
    }
    if (f.listKind === 'CUSTOMER' || f.customerId) {
      body.customerId = f.customerId ? Number(f.customerId) : null
    }
    return body
  },
  fromRow: (row, empty) => {
    const toDate = (v) => {
      if (v == null || v === '') return ''
      if (typeof v === 'number') return new Date(v).toISOString().slice(0, 10)
      const s = String(v)
      return s.length >= 10 ? s.slice(0, 10) : s
    }
    return {
      ...empty,
      name: row.name || '',
      code: row.code || '',
      listKind: row.listKind || 'STANDARD',
      currencyCode: row.currencyCode || 'EUR',
      active: row.active !== false,
      validFrom: toDate(row.validFrom),
      validTo: toDate(row.validTo),
      customerId: row.customerId != null ? String(row.customerId) : '',
      description: row.description || '',
      defaultDiscountPercent:
        row.defaultDiscountPercent != null ? String(row.defaultDiscountPercent) : '',
    }
  },
  listColumns: [
    { key: 'listKind', label: 'Kind' },
    { key: 'currencyCode', label: 'Ccy', mono: true },
    { key: 'active', label: 'Active' },
  ],
}

/** SalesAdvanced CRM opportunity (OI-S08) — modal edit on /sales/opportunities + domain list. */
const opportunitySpec = {
  resource: 'Opportunity',
  packages: ['sales'],
  empty: () => ({
    name: '',
    description: '',
    stage: 'LEAD',
    customerId: '',
    contactName: '',
    contactEmail: '',
    expectedAmountEur: '',
    currencyCode: 'EUR',
    expectedCloseDate: '',
    ownerName: '',
    source: '',
    active: true,
    assigneeHumanNpub: '',
    assigneeAgentId: '',
  }),
  fields: [
    { name: 'name', label: 'Name', type: 'text', required: true },
    {
      name: 'stage',
      label: 'Stage',
      type: 'select',
      required: true,
      options: [
        { value: 'LEAD', label: 'LEAD' },
        { value: 'QUALIFIED', label: 'QUALIFIED' },
        { value: 'PROPOSAL', label: 'PROPOSAL' },
        { value: 'NEGOTIATION', label: 'NEGOTIATION' },
        { value: 'WON', label: 'WON' },
        { value: 'LOST', label: 'LOST' },
      ],
    },
    { name: 'customerId', label: 'Customer', type: 'customer' },
    { name: 'contactName', label: 'Contact name', type: 'text' },
    { name: 'contactEmail', label: 'Contact email', type: 'email' },
    {
      name: 'expectedAmountEur',
      label: 'Expected amount',
      type: 'number',
      inputMode: 'decimal',
    },
    { name: 'currencyCode', label: 'Currency', type: 'currency', required: true },
    { name: 'expectedCloseDate', label: 'Expected close', type: 'date' },
    { name: 'ownerName', label: 'Owner', type: 'text' },
    // Optional Assignable — keep free-text ownerName
    ASSIGNEE_FIELD,
    {
      name: 'source',
      label: 'Source',
      type: 'select',
      options: [
        { value: '', label: '—' },
        { value: 'WEB', label: 'WEB' },
        { value: 'REFERRAL', label: 'REFERRAL' },
        { value: 'COLD', label: 'COLD' },
        { value: 'PARTNER', label: 'PARTNER' },
        { value: 'OTHER', label: 'OTHER' },
      ],
    },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'active', label: 'Active', type: 'checkbox' },
  ],
  toBody: (f) => {
    const body = {
      name: (f.name || '').trim(),
      stage: f.stage || 'LEAD',
      currencyCode: f.currencyCode || 'EUR',
      contactName: (f.contactName || '').trim() || null,
      contactEmail: (f.contactEmail || '').trim() || null,
      ownerName: (f.ownerName || '').trim() || null,
      source: f.source || null,
      description: (f.description || '').trim() || null,
      expectedCloseDate: f.expectedCloseDate || null,
      active: f.active !== false,
      ...assigneePairForBody(f),
    }
    if (f.customerId !== '' && f.customerId != null) {
      body.customerId = Number(f.customerId)
    } else {
      body.customerId = null
    }
    if (f.expectedAmountEur !== '' && f.expectedAmountEur != null) {
      body.expectedAmountEur = Number(f.expectedAmountEur)
    } else {
      body.expectedAmountEur = null
    }
    return body
  },
  fromRow: (row, empty) => {
    const toDate = (v) => {
      if (v == null || v === '') return ''
      if (typeof v === 'number') return new Date(v).toISOString().slice(0, 10)
      const s = String(v)
      return s.length >= 10 ? s.slice(0, 10) : s
    }
    return {
      ...empty,
      name: row.name || '',
      description: row.description || '',
      stage: row.stage || row.status || 'LEAD',
      customerId: row.customerId != null ? String(row.customerId) : '',
      contactName: row.contactName || '',
      contactEmail: row.contactEmail || '',
      expectedAmountEur: row.expectedAmountEur != null ? String(row.expectedAmountEur) : '',
      currencyCode: row.currencyCode || 'EUR',
      expectedCloseDate: toDate(row.expectedCloseDate),
      ownerName: row.ownerName || '',
      source: row.source || '',
      active: row.active !== false,
      ...assigneePairFromRow(row),
    }
  },
  listColumns: [
    { key: 'stage', label: 'Stage' },
    { key: 'contactName', label: 'Contact' },
    { key: 'expectedAmountEur', label: 'Amount' },
    { key: 'currencyCode', label: 'Ccy', mono: true },
  ],
}

const SPECS = {
  Customer: customerSpec,
  Vendor: vendorSpec,
  Product: productSpec,
  Service: serviceSpec,
  Resource: resourceSpec,
  ServiceLevel: serviceLevelSpec,
  Budget: budgetSpec,
  Payable: payableSpec,
  CatalogueItem: catalogueItemSpec,
  PurchaseOrder: purchaseOrderSpec,
  CustomerQuote: customerQuoteSpec,
  SalesOrder: salesOrderSpec,
  PriceList: priceListSpec,
  Opportunity: opportunitySpec,
  Competitor: competitorSpec,
  StrategyItem: strategyItemSpec,
  DevelopmentProject: developmentProjectSpec,
  PhysicalAsset: physicalAssetSpec,
  ServiceOrder: serviceOrderSpec,
  Inquiry: inquirySpec,
  Warranty: warrantySpec,
  Claims: claimsSpec,
  Rma: rmaSpec,
}

/** @returns {typeof customerSpec | null} */
export function getMasterFormSpec(pkg, resource) {
  const spec = SPECS[resource]
  if (!spec) return null
  if (spec.packages && pkg && !spec.packages.includes(pkg)) {
    // allow if resource name unique (StockMove is under production path etc.)
    // ServiceLevel only under service; CatalogueItem under catalogue
    if (resource === 'ServiceLevel' && pkg !== 'service') return null
    if (resource === 'CatalogueItem' && pkg !== 'catalogue') return null
  }
  return spec
}

export function hasMasterFormSpec(pkg, resource) {
  return !!getMasterFormSpec(pkg, resource)
}

export { SPECS as MASTER_FORM_SPECS }
