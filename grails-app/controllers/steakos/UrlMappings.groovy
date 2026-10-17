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

package steakos

class UrlMappings {

    static mappings = {

        // A4 — public vulnerability contact (RFC 9116)
        "/.well-known/security.txt"(controller: 'wellKnown', action: 'securityTxt', method: 'GET')

        // --- FE API (dynamic domain CRUD + CEO dashboard) ---
        "/api/domains"(controller: 'domainApi', action: 'index', method: 'GET')
        "/api/domains/$package/$resource/schema"(controller: 'domainApi', action: 'schema', method: 'GET')
        "/api/domains/$package/$resource"(controller: 'domainApi') {
            action = [GET: 'listResource', POST: 'save']
        }
        "/api/domains/$package/$resource/$id"(controller: 'domainApi') {
            action = [GET: 'show', PUT: 'update', DELETE: 'delete', POST: 'update']
        }
        "/api/dashboard/ceo"(controller: 'dashboardApi', action: 'ceo', method: 'GET')

        // KPI registry (Silver WP-K0–K1) — /api/kpi/*
        // Static paths before /api/kpi/$id so catalogue/hero are not captured as ids.
        "/api/kpi/catalogue"(controller: 'kpiApi', action: 'catalogue', method: 'GET')
        "/api/kpi/dashboard-hero"(controller: 'kpiApi', action: 'dashboardHero', method: 'GET')
        "/api/kpi/$id"(controller: 'kpiApi', action: 'show', method: 'GET')

        // GRRCBasics (G7 / PR-6) — /api/grrc/* (SILVER+ / Iron eval; Bronze refused)
        "/api/grrc/risks"(controller: 'grrcApi') {
            action = [GET: 'riskList', POST: 'riskCreate']
        }
        "/api/grrc/risks/$id"(controller: 'grrcApi') {
            action = [GET: 'riskShow', PUT: 'riskUpdate', DELETE: 'riskDelete']
        }
        "/api/grrc/risks/$id/close"(controller: 'grrcApi', action: 'riskClose', method: 'POST')
        "/api/grrc/compliance"(controller: 'grrcApi') {
            action = [GET: 'complianceList', POST: 'complianceCreate']
        }
        "/api/grrc/compliance/$id"(controller: 'grrcApi') {
            action = [GET: 'complianceShow', PUT: 'complianceUpdate', DELETE: 'complianceDelete']
        }

        // Product version (VERSIONS.md V7) + system vitals
        "/api/version"(controller: 'versionApi', action: 'index', method: 'GET')
        "/api/version/stats"(controller: 'versionApi', action: 'stats', method: 'GET')

        // Locales (public) + current user prefs (i18n proof)
        "/api/locales"(controller: 'localesApi', action: 'index', method: 'GET')
        "/api/me"(controller: 'meApi', action: 'index', method: 'GET')
        "/api/me/company"(controller: 'meApi', action: 'selectCompany', method: 'POST')
        "/api/me/preferences"(controller: 'meApi') {
            action = [GET: 'preferences', PUT: 'updatePreferences']
        }

        // PDR-023 BTC FX hints (server-side cascade)
        "/api/fx/btc"(controller: 'fxApi', action: 'btc', method: 'GET')

        // OI-CUR01 — currency catalog + company in-use
        "/api/currencies"(controller: 'currenciesApi', action: 'index', method: 'GET')
        "/api/currencies/in-use"(controller: 'currenciesApi') {
            action = [GET: 'inUse', PUT: 'setInUse']
        }

        // OI-F23 — tax code catalog + company in-use
        "/api/tax-codes"(controller: 'taxCodesApi', action: 'index', method: 'GET')
        "/api/tax-codes/in-use"(controller: 'taxCodesApi') {
            action = [GET: 'inUse', PUT: 'setInUse']
        }

        // AUTH01–04 + NIP-07
        "/api/auth/totp/status"(controller: 'authApi', action: 'totpStatus', method: 'GET')
        "/api/auth/totp/begin"(controller: 'authApi', action: 'totpBegin', method: 'POST')
        "/api/auth/totp/confirm"(controller: 'authApi', action: 'totpConfirm', method: 'POST')
        "/api/auth/totp/verify"(controller: 'authApi', action: 'totpVerify', method: 'POST')
        "/api/auth/totp/clear"(controller: 'authApi', action: 'totpClear', method: 'POST')
        "/api/auth/totp/begin-for"(controller: 'authApi', action: 'totpBeginFor', method: 'POST')
        "/api/auth/totp/confirm-for"(controller: 'authApi', action: 'totpConfirmFor', method: 'POST')
        "/api/auth/admin-totp-bypass"(controller: 'authApi', action: 'adminTotpBypassGet', method: 'GET')
        "/api/auth/admin-totp-bypass"(controller: 'authApi', action: 'adminTotpBypassPut', method: 'PUT')
        "/api/auth/nostr/challenge"(controller: 'authApi', action: 'nostrChallenge', method: 'POST')
        "/api/auth/nostr/verify"(controller: 'authApi', action: 'nostrVerify', method: 'POST')
        "/api/auth/nostr/link"(controller: 'authApi', action: 'nostrLink', method: 'POST')
        "/api/auth/users"(controller: 'authApi') {
            action = [GET: 'usersList', POST: 'usersCreate', PUT: 'usersUpdate']
        }
        "/api/auth/role-profiles"(controller: 'authApi', action: 'roleProfilesList', method: 'GET')
        "/api/auth/role-profiles/$profileId"(controller: 'authApi', action: 'roleProfileShow', method: 'GET')
        "/api/auth/role-profiles/$profileId"(controller: 'authApi', action: 'roleProfileUpdate', method: 'PUT')
        "/api/auth/role-profiles/$profileId/reset"(controller: 'authApi', action: 'roleProfileReset', method: 'POST')
        "/api/auth/role-profiles/$profileId/apply"(controller: 'authApi', action: 'roleProfileApply', method: 'POST')

        // Config wizard / SetupState (ADR-026, OI-W01–W02)
        "/api/setup/status"(controller: 'setupApi', action: 'status', method: 'GET')
        "/api/setup/draft"(controller: 'setupApi') {
            action = [GET: 'draftGet', PUT: 'draftPut']
        }
        "/api/setup/apply"(controller: 'setupApi', action: 'apply', method: 'POST')
        "/api/setup/complete"(controller: 'setupApi', action: 'complete', method: 'POST')
        "/api/setup/reset"(controller: 'setupApi', action: 'reset', method: 'POST')
        "/api/setup/check"(controller: 'setupApi', action: 'check', method: 'GET')
        "/api/setup/profile"(controller: 'setupApi', action: 'profile', method: 'GET')
        "/api/setup/demo-seed"(controller: 'setupApi', action: 'demoSeed', method: 'POST')
        "/api/setup/demo-reset"(controller: 'setupApi', action: 'demoReset', method: 'POST')
        "/api/setup/heard-cook-seed"(controller: 'setupApi', action: 'heardCookSeed', method: 'POST')
        "/api/setup/heard-cook-reset"(controller: 'setupApi', action: 'heardCookReset', method: 'POST')
        "/api/setup/template-tier"(controller: 'setupApi', action: 'templateTier', method: 'POST')
        "/api/setup/privacy/hr-ack"(controller: 'setupApi', action: 'privacyHrAck', method: 'POST')

        // OI-NOSTR01 — Nostr integration (Integrations hub)
        "/api/nostr/settings"(controller: 'nostrApi') {
            action = [GET: 'settingsGet', PUT: 'settingsPut']
        }
        "/api/nostr/probe"(controller: 'nostrApi', action: 'probe', method: 'POST')
        "/api/nostr/relay/status"(controller: 'nostrApi', action: 'relayStatus', method: 'GET')
        "/api/nostr/relay/ensure"(controller: 'nostrApi', action: 'relayEnsure', method: 'POST')
        "/api/nostr/publish-stub"(controller: 'nostrApi', action: 'publishStub', method: 'POST')
        "/api/nostr/last-intent"(controller: 'nostrApi', action: 'lastIntent', method: 'GET')
        "/api/nostr/identity"(controller: 'nostrApi', action: 'identityHint', method: 'GET')

        // ADR-037 agent opaque bearer tokens (human admin)
        "/api/agent/tokens"(controller: 'agentTokenApi', action: 'list', method: 'GET')
        "/api/agent/tokens"(controller: 'agentTokenApi', action: 'mint', method: 'POST')
        "/api/agent/tokens/$id/revoke"(controller: 'agentTokenApi', action: 'revoke', method: 'POST')
        "/api/agent/users"(controller: 'agentTokenApi', action: 'ensure', method: 'POST')
        // ADR-037 P1–P3 agent gateway
        "/api/agent/me"(controller: 'agentGatewayApi', action: 'me', method: 'GET')
        "/api/agent/l0/summary"(controller: 'agentGatewayApi', action: 'l0Summary', method: 'GET')
        "/api/agent/capabilities"(controller: 'agentGatewayApi', action: 'capabilities', method: 'GET')
        "/api/agent/proposals"(controller: 'agentGatewayApi', action: 'proposalList', method: 'GET')
        "/api/agent/proposals"(controller: 'agentGatewayApi', action: 'proposalCreate', method: 'POST')
        "/api/agent/proposals/$id"(controller: 'agentGatewayApi', action: 'proposalShow', method: 'GET')

        // Unified catalogue (CATALOGUE-ITEM-CONCEPT)
        "/api/catalogue/items"(controller: 'catalogueApi') {
            action = [GET: 'list', POST: 'create']
        }
        "/api/catalogue/items/$id"(controller: 'catalogueApi') {
            action = [GET: 'show', PUT: 'update']
        }

        // CSV import packs (Wave B / OI-I01)
        "/api/import/packs"(controller: 'importApi', action: 'packs', method: 'GET')
        "/api/import/jobs"(controller: 'importApi') {
            action = [GET: 'list', POST: 'upload']
        }
        "/api/import/jobs/$id"(controller: 'importApi', action: 'show', method: 'GET')
        "/api/import/jobs/$id/preview"(controller: 'importApi', action: 'preview', method: 'POST')
        "/api/import/jobs/$id/commit"(controller: 'importApi', action: 'commit', method: 'POST')

        // Contextual help (Wave B / OI-H01)
        "/api/help/topics"(controller: 'helpApi', action: 'topics', method: 'GET')
        "/api/help/resolve"(controller: 'helpApi', action: 'resolve', method: 'GET')
        "/api/help/topics/$helpKey"(controller: 'helpApi', action: 'show', method: 'GET')

        // Finance ledger kernel (F-S1 / APQC 9.3)
        "/api/finance/ledger/seed"(controller: 'financeLedgerApi', action: 'seed', method: 'POST')
        // PDR-027 — posting ruleset + Admin change log
        "/api/finance/posting-rules"(controller: 'financeLedgerApi') {
            action = [GET: 'postingRulesGet', PUT: 'postingRulesSave']
        }
        "/api/finance/posting-rules/seed"(controller: 'financeLedgerApi', action: 'postingRulesSeed', method: 'POST')
        "/api/finance/posting-rules/reseed"(controller: 'financeLedgerApi', action: 'postingRulesReseed', method: 'POST')
        "/api/finance/posting-rules/changes"(controller: 'financeLedgerApi', action: 'postingRulesChangeLog', method: 'GET')
        "/api/finance/ledger/accounts"(controller: 'financeLedgerApi') {
            action = [GET: 'accounts', POST: 'createAccount']
        }
        "/api/finance/ledger/accounts/$id/balance"(controller: 'financeLedgerApi', action: 'balance', method: 'GET')
        "/api/finance/ledger/entries"(controller: 'financeLedgerApi', action: 'postEntry', method: 'POST')
        "/api/finance/ledger/entries/$id/reverse"(controller: 'financeLedgerApi', action: 'reverse', method: 'POST')
        "/api/finance/ledger/trial-balance"(controller: 'financeLedgerApi', action: 'trialBalance', method: 'GET')
        "/api/finance/ledger/periods/$period/close"(controller: 'financeLedgerApi', action: 'closePeriod', method: 'POST')
        "/api/finance/ledger/periods/$period"(controller: 'financeLedgerApi', action: 'periodStatus', method: 'GET')

        // Finance revenue / invoices (F-S2 / APQC 9.2)
        "/api/finance/revenue/invoices"(controller: 'financeRevenueApi') {
            action = [GET: 'list', POST: 'create']
        }
        "/api/finance/revenue/invoices/open"(controller: 'financeRevenueApi', action: 'open', method: 'GET')
        "/api/finance/revenue/invoices/$id"(controller: 'financeRevenueApi', action: 'show', method: 'GET')
        "/api/finance/revenue/invoices/$id/issue"(controller: 'financeRevenueApi', action: 'issue', method: 'POST')
        "/api/finance/revenue/invoices/$id/options"(controller: 'financeRevenueApi', action: 'addOption', method: 'POST')
        "/api/finance/revenue/invoices/$id/settle"(controller: 'financeRevenueApi', action: 'settle', method: 'POST')
        "/api/finance/revenue/invoices/$id/void"(controller: 'financeRevenueApi', action: 'voidInvoice', method: 'POST')
        "/api/finance/revenue/invoices/$id/pdf"(controller: 'financeRevenueApi', action: 'pdf', method: 'GET')

        // Finance ops F-S3–F-S6 (treasury, AP, plan, tax, payroll stub, control)
        "/api/finance/treasury/seed"(controller: 'financeOpsApi', action: 'treasurySeed', method: 'POST')
        "/api/finance/treasury/positions"(controller: 'financeOpsApi') {
            action = [GET: 'treasuryList', POST: 'treasuryRegister']
        }
        "/api/finance/treasury/positions/$id/reconcile"(controller: 'financeOpsApi', action: 'treasuryReconcile', method: 'POST')
        "/api/finance/treasury/webhook/btcpay"(controller: 'financeOpsApi', action: 'btcpayWebhook', method: 'POST')

        // Bitcoin payment rail (OI-F27 Phase 0 Mock + Phase 1 BTCPay Greenfield)
        "/api/finance/payments/settings"(controller: 'financeOpsApi') {
            action = [GET: 'paymentsSettingsGet', PUT: 'paymentsSettingsPut']
        }
        "/api/finance/payments/btc-level"(controller: 'financeOpsApi') {
            action = [GET: 'btcIntegrationLevelGet', PUT: 'btcIntegrationLevelPut']
        }
        "/api/finance/payments/provider"(controller: 'financeOpsApi', action: 'paymentsProviderStatus', method: 'GET')
        "/api/finance/payments/probe"(controller: 'financeOpsApi', action: 'paymentsProbe', method: 'POST')
        "/api/finance/payments/inbound"(controller: 'financeOpsApi', action: 'paymentsInboundCreate', method: 'POST')
        "/api/finance/payments/reconcile"(controller: 'financeOpsApi', action: 'paymentsReconcile', method: 'POST')
        "/api/finance/payments/reconcile/one"(controller: 'financeOpsApi', action: 'paymentsReconcileOne', method: 'POST')

        "/api/finance/payables"(controller: 'financeOpsApi') {
            action = [GET: 'payableList', POST: 'payableCreate']
        }
        "/api/finance/payables/$id/pay"(controller: 'financeOpsApi', action: 'payablePay', method: 'POST')
        "/api/finance/payables/$id/void"(controller: 'financeOpsApi', action: 'payableVoid', method: 'POST')

        "/api/finance/budgets"(controller: 'financeOpsApi') {
            action = [GET: 'budgetList', POST: 'budgetCreate']
        }
        "/api/finance/budgets/variance"(controller: 'financeOpsApi', action: 'budgetVariance', method: 'GET')

        "/api/finance/tax"(controller: 'financeOpsApi', action: 'taxList', method: 'GET')
        "/api/finance/tax/estimate"(controller: 'financeOpsApi', action: 'taxEstimate', method: 'POST')

        "/api/finance/payroll"(controller: 'financeOpsApi') {
            action = [GET: 'payrollList', POST: 'payrollCreate']
        }
        "/api/finance/payroll/post"(controller: 'financeOpsApi', action: 'payrollPost', method: 'POST')
        "/api/finance/payroll/pay"(controller: 'financeOpsApi', action: 'payrollPay', method: 'POST')

        "/api/finance/control/audit"(controller: 'financeOpsApi', action: 'controlAudit', method: 'GET')
        "/api/finance/control/periods/$period"(controller: 'financeOpsApi', action: 'controlPeriod', method: 'GET')
        "/api/finance/control/periods/$period/close"(controller: 'financeOpsApi', action: 'controlClose', method: 'POST')

        // Sales commercial (S-S1–S-S3)
        "/api/sales/customers"(controller: 'salesApi') {
            action = [GET: 'customerList', POST: 'customerCreate']
        }
        "/api/sales/customers/$id"(controller: 'salesApi') {
            action = [GET: 'customerShow', PUT: 'customerUpdate']
        }
        "/api/sales/quotes"(controller: 'salesApi') {
            action = [GET: 'quoteList', POST: 'quoteCreate']
        }
        "/api/sales/quotes/$id"(controller: 'salesApi', action: 'quoteShow', method: 'GET')
        "/api/sales/quotes/$id/pdf"(controller: 'salesApi', action: 'quotePdf', method: 'GET')
        "/api/sales/quotes/$id/lines"(controller: 'salesApi', action: 'quoteLines', method: 'PUT')
        "/api/sales/quotes/$id/send"(controller: 'salesApi', action: 'quoteSend', method: 'POST')
        "/api/sales/quotes/$id/accept"(controller: 'salesApi', action: 'quoteAccept', method: 'POST')
        "/api/sales/quotes/$id/reject"(controller: 'salesApi', action: 'quoteReject', method: 'POST')
        "/api/sales/orders"(controller: 'salesApi') {
            action = [GET: 'orderList', POST: 'orderCreate']
        }
        "/api/sales/orders/open"(controller: 'salesApi', action: 'orderOpen', method: 'GET')
        "/api/sales/orders/$id"(controller: 'salesApi', action: 'orderShow', method: 'GET')
        "/api/sales/orders/$id/pdf"(controller: 'salesApi', action: 'orderPdf', method: 'GET')
        "/api/sales/orders/$id/lines"(controller: 'salesApi', action: 'orderLines', method: 'PUT')
        "/api/sales/orders/$id/confirm"(controller: 'salesApi', action: 'orderConfirm', method: 'POST')
        "/api/sales/orders/$id/cancel"(controller: 'salesApi', action: 'orderCancel', method: 'POST')

        // Supply chain (SC kernel) — /api/scm/*
        "/api/scm/vendors"(controller: 'scmApi') {
            action = [GET: 'vendorList', POST: 'vendorCreate']
        }
        "/api/scm/vendors/$id"(controller: 'scmApi') {
            action = [GET: 'vendorShow', PUT: 'vendorUpdate']
        }
        "/api/scm/products"(controller: 'scmApi') {
            action = [GET: 'productList', POST: 'productCreate']
        }
        "/api/scm/products/$id"(controller: 'scmApi') {
            action = [GET: 'productShow', PUT: 'productUpdate']
        }
        "/api/scm/purchase-orders"(controller: 'scmApi') {
            action = [GET: 'poList', POST: 'poCreate']
        }
        "/api/scm/purchase-orders/$id"(controller: 'scmApi', action: 'poShow', method: 'GET')
        "/api/scm/purchase-orders/$id/pdf"(controller: 'scmApi', action: 'poPdf', method: 'GET')
        "/api/scm/purchase-orders/$id/lines"(controller: 'scmApi', action: 'poLines', method: 'PUT')
        "/api/scm/purchase-orders/$id/send"(controller: 'scmApi', action: 'poSend', method: 'POST')
        "/api/scm/purchase-orders/$id/confirm"(controller: 'scmApi', action: 'poConfirm', method: 'POST')
        "/api/scm/purchase-orders/$id/receive"(controller: 'scmApi', action: 'poReceive', method: 'POST')
        "/api/scm/purchase-orders/$id/cancel"(controller: 'scmApi', action: 'poCancel', method: 'POST')
        "/api/scm/boms"(controller: 'scmApi') {
            action = [GET: 'bomList', POST: 'bomCreate']
        }
        "/api/scm/boms/$id"(controller: 'scmApi', action: 'bomShow', method: 'GET')
        "/api/scm/production-orders"(controller: 'scmApi') {
            action = [GET: 'prodList', POST: 'prodCreate']
        }
        "/api/scm/production-orders/from-sales"(controller: 'scmApi', action: 'prodFromSales', method: 'POST')
        "/api/scm/production-orders/$id"(controller: 'scmApi', action: 'prodShow', method: 'GET')
        "/api/scm/production-orders/$id/pdf"(controller: 'scmApi', action: 'prodPdf', method: 'GET')
        "/api/scm/production-orders/$id/release"(controller: 'scmApi', action: 'prodRelease', method: 'POST')
        "/api/scm/production-orders/$id/start"(controller: 'scmApi', action: 'prodStart', method: 'POST')
        "/api/scm/production-orders/$id/complete"(controller: 'scmApi', action: 'prodComplete', method: 'POST')
        "/api/scm/production-orders/$id/cancel"(controller: 'scmApi', action: 'prodCancel', method: 'POST')
        "/api/scm/lots"(controller: 'scmApi') {
            action = [GET: 'lotList', POST: 'lotCreate']
        }
        "/api/scm/lots/$id"(controller: 'scmApi', action: 'lotShow', method: 'GET')
        "/api/scm/lots/$id/check"(controller: 'scmApi', action: 'lotCheck', method: 'POST')
        "/api/scm/inventory/on-hand"(controller: 'scmApi', action: 'inventoryOnHand', method: 'GET')
        "/api/scm/inventory/moves"(controller: 'scmApi', action: 'inventoryMoves', method: 'GET')
        "/api/scm/inventory/adjust"(controller: 'scmApi', action: 'inventoryAdjust', method: 'POST')

        // Delivery (D-S1–D-S3) — /api/delivery/*
        "/api/delivery/services"(controller: 'deliveryApi') {
            action = [GET: 'serviceList', POST: 'serviceCreate']
        }
        "/api/delivery/services/$id"(controller: 'deliveryApi') {
            action = [GET: 'serviceShow', PUT: 'serviceUpdate']
        }
        "/api/delivery/resources"(controller: 'deliveryApi') {
            action = [GET: 'resourceList', POST: 'resourceCreate']
        }
        "/api/delivery/resources/$id"(controller: 'deliveryApi') {
            action = [GET: 'resourceShow', PUT: 'resourceUpdate']
        }
        "/api/delivery/orders"(controller: 'deliveryApi') {
            action = [GET: 'orderList', POST: 'orderCreate']
        }
        "/api/delivery/orders/open"(controller: 'deliveryApi', action: 'orderOpen', method: 'GET')
        "/api/delivery/orders/from-sales"(controller: 'deliveryApi', action: 'orderFromSales', method: 'POST')
        "/api/delivery/orders/$id"(controller: 'deliveryApi', action: 'orderShow', method: 'GET')
        "/api/delivery/orders/$id/pdf"(controller: 'deliveryApi', action: 'orderPdf', method: 'GET')
        "/api/delivery/orders/$id/lines"(controller: 'deliveryApi', action: 'orderLines', method: 'PUT')
        "/api/delivery/orders/$id/assign"(controller: 'deliveryApi', action: 'orderAssign', method: 'POST')
        "/api/delivery/orders/$id/schedule"(controller: 'deliveryApi', action: 'orderSchedule', method: 'POST')
        "/api/delivery/orders/$id/start"(controller: 'deliveryApi', action: 'orderStart', method: 'POST')
        "/api/delivery/orders/$id/complete"(controller: 'deliveryApi', action: 'orderComplete', method: 'POST')
        "/api/delivery/orders/$id/cancel"(controller: 'deliveryApi', action: 'orderCancel', method: 'POST')

        // AfterSales (AS-S0–AS-S3) — /api/aftersales/*
        "/api/aftersales/service-levels"(controller: 'afterSalesApi') {
            action = [GET: 'serviceLevelList', POST: 'serviceLevelCreate']
        }
        "/api/aftersales/service-levels/$id"(controller: 'afterSalesApi') {
            action = [GET: 'serviceLevelShow', PUT: 'serviceLevelUpdate']
        }
        "/api/aftersales/inquiries"(controller: 'afterSalesApi') {
            action = [GET: 'inquiryList', POST: 'inquiryCreate']
        }
        "/api/aftersales/inquiries/open"(controller: 'afterSalesApi', action: 'inquiryOpen', method: 'GET')
        "/api/aftersales/inquiries/$id"(controller: 'afterSalesApi') {
            action = [GET: 'inquiryShow', PUT: 'inquiryUpdate']
        }
        "/api/aftersales/inquiries/$id/start"(controller: 'afterSalesApi', action: 'inquiryStart', method: 'POST')
        "/api/aftersales/inquiries/$id/resolve"(controller: 'afterSalesApi', action: 'inquiryResolve', method: 'POST')
        "/api/aftersales/inquiries/$id/close"(controller: 'afterSalesApi', action: 'inquiryClose', method: 'POST')
        "/api/aftersales/inquiries/$id/cancel"(controller: 'afterSalesApi', action: 'inquiryCancel', method: 'POST')
        "/api/aftersales/warranties"(controller: 'afterSalesApi') {
            action = [GET: 'warrantyList', POST: 'warrantyCreate']
        }
        "/api/aftersales/warranties/$id"(controller: 'afterSalesApi') {
            action = [GET: 'warrantyShow', PUT: 'warrantyUpdate']
        }
        "/api/aftersales/warranties/$id/activate"(controller: 'afterSalesApi', action: 'warrantyActivate', method: 'POST')
        "/api/aftersales/warranties/$id/void"(controller: 'afterSalesApi', action: 'warrantyVoid', method: 'POST')
        "/api/aftersales/claims"(controller: 'afterSalesApi') {
            action = [GET: 'claimList', POST: 'claimCreate']
        }
        "/api/aftersales/claims/open"(controller: 'afterSalesApi', action: 'claimOpen', method: 'GET')
        "/api/aftersales/claims/$id"(controller: 'afterSalesApi', action: 'claimShow', method: 'GET')
        "/api/aftersales/claims/$id/validate"(controller: 'afterSalesApi', action: 'claimValidate', method: 'POST')
        "/api/aftersales/claims/$id/approve"(controller: 'afterSalesApi', action: 'claimApprove', method: 'POST')
        "/api/aftersales/claims/$id/reject"(controller: 'afterSalesApi', action: 'claimReject', method: 'POST')
        "/api/aftersales/claims/$id/close"(controller: 'afterSalesApi', action: 'claimClose', method: 'POST')
        "/api/aftersales/claims/$id/notes"(controller: 'afterSalesApi', action: 'claimNotes', method: 'PUT')
        "/api/aftersales/rmas"(controller: 'afterSalesApi') {
            action = [GET: 'rmaList', POST: 'rmaCreate']
        }
        "/api/aftersales/rmas/open"(controller: 'afterSalesApi', action: 'rmaOpenList', method: 'GET')
        "/api/aftersales/rmas/$id"(controller: 'afterSalesApi', action: 'rmaShow', method: 'GET')
        "/api/aftersales/rmas/$id/pdf"(controller: 'afterSalesApi', action: 'rmaPdf', method: 'GET')
        "/api/aftersales/rmas/$id/confirm"(controller: 'afterSalesApi', action: 'rmaConfirm', method: 'POST')
        "/api/aftersales/rmas/$id/close"(controller: 'afterSalesApi', action: 'rmaClose', method: 'POST')
        "/api/aftersales/rmas/$id/cancel"(controller: 'afterSalesApi', action: 'rmaCancel', method: 'POST')

        // HR (HR-S0–HR-S3) — /api/hr/*
        "/api/hr/files"(controller: 'hrApi') {
            action = [GET: 'fileList', POST: 'fileCreate']
        }
        "/api/hr/files/$id"(controller: 'hrApi') {
            action = [GET: 'fileShow', PUT: 'fileUpdate']
        }
        "/api/hr/files/$id/exit"(controller: 'hrApi', action: 'fileExit', method: 'POST')
        "/api/hr/candidates"(controller: 'hrApi') {
            action = [GET: 'candidateList', POST: 'candidateCreate']
        }
        "/api/hr/candidates/$id"(controller: 'hrApi', action: 'candidateShow', method: 'GET')
        "/api/hr/candidates/$id/advance"(controller: 'hrApi', action: 'candidateAdvance', method: 'POST')
        "/api/hr/candidates/$id/hire"(controller: 'hrApi', action: 'candidateHire', method: 'POST')
        "/api/hr/candidates/$id/reject"(controller: 'hrApi', action: 'candidateReject', method: 'POST')
        "/api/hr/time"(controller: 'hrApi') {
            action = [GET: 'timeList', POST: 'timeCreate']
        }
        "/api/hr/payrolls"(controller: 'hrApi') {
            action = [GET: 'payrollList', POST: 'payrollCreate']
        }
        "/api/hr/payrolls/$id"(controller: 'hrApi', action: 'payrollShow', method: 'GET')
        "/api/hr/payrolls/$id/lines"(controller: 'hrApi', action: 'payrollLines', method: 'PUT')
        "/api/hr/payrolls/$id/ready"(controller: 'hrApi', action: 'payrollReady', method: 'POST')
        "/api/hr/payrolls/$id/submit"(controller: 'hrApi', action: 'payrollSubmit', method: 'POST')
        "/api/hr/payrolls/$id/close"(controller: 'hrApi', action: 'payrollClose', method: 'POST')
        "/api/hr/plans"(controller: 'hrApi') {
            action = [GET: 'planList', POST: 'planCreate']
        }
        "/api/hr/goals"(controller: 'hrApi') {
            action = [GET: 'goalList', POST: 'goalCreate']
        }
        "/api/hr/goals/$id/progress"(controller: 'hrApi', action: 'goalProgress', method: 'POST')
        "/api/hr/goals/$id/complete"(controller: 'hrApi', action: 'goalComplete', method: 'POST')
        "/api/hr/policies"(controller: 'hrApi') {
            action = [GET: 'policyList', POST: 'policyCreate']
        }
        "/api/hr/parties"(controller: 'hrApi') {
            action = [GET: 'partyList', POST: 'partyCreate']
        }

        // Digital Technology (DIG-S0–DIG-S2) — /api/digital/*
        "/api/digital/systems"(controller: 'digitalApi') {
            action = [GET: 'systemList', POST: 'systemCreate']
        }
        "/api/digital/systems/$id"(controller: 'digitalApi') {
            action = [GET: 'systemShow', PUT: 'systemUpdate']
        }
        "/api/digital/data"(controller: 'digitalApi') {
            action = [GET: 'dataList', POST: 'dataCreate']
        }
        "/api/digital/data/$id"(controller: 'digitalApi', action: 'dataShow', method: 'GET')
        "/api/digital/solutions"(controller: 'digitalApi') {
            action = [GET: 'solutionList', POST: 'solutionCreate']
        }
        "/api/digital/solutions/$id"(controller: 'digitalApi', action: 'solutionShow', method: 'GET')
        "/api/digital/solutions/$id/activate"(controller: 'digitalApi', action: 'solutionActivate', method: 'POST')
        "/api/digital/solutions/$id/retire"(controller: 'digitalApi', action: 'solutionRetire', method: 'POST')
        "/api/digital/changes"(controller: 'digitalApi') {
            action = [GET: 'changeList', POST: 'changeCreate']
        }
        "/api/digital/changes/open"(controller: 'digitalApi', action: 'changeOpen', method: 'GET')
        "/api/digital/changes/$id/start"(controller: 'digitalApi', action: 'changeStart', method: 'POST')
        "/api/digital/changes/$id/complete"(controller: 'digitalApi', action: 'changeComplete', method: 'POST')
        "/api/digital/changes/$id/cancel"(controller: 'digitalApi', action: 'changeCancel', method: 'POST')
        "/api/digital/cis"(controller: 'digitalApi') {
            action = [GET: 'ciList', POST: 'ciCreate']
        }
        "/api/digital/cis/$id"(controller: 'digitalApi', action: 'ciShow', method: 'GET')
        "/api/digital/tickets"(controller: 'digitalApi') {
            action = [GET: 'ticketList', POST: 'ticketCreate']
        }
        "/api/digital/tickets/open"(controller: 'digitalApi', action: 'ticketOpen', method: 'GET')
        "/api/digital/tickets/$id/start"(controller: 'digitalApi', action: 'ticketStart', method: 'POST')
        "/api/digital/tickets/$id/resolve"(controller: 'digitalApi', action: 'ticketResolve', method: 'POST')
        "/api/digital/tickets/$id/close"(controller: 'digitalApi', action: 'ticketClose', method: 'POST')

        // Capability (CAP-S0–CAP-S3) — /api/capability/*
        "/api/capability/processes"(controller: 'capabilityApi') {
            action = [GET: 'processList', POST: 'processCreate']
        }
        "/api/capability/processes/reseed"(controller: 'capabilityApi', action: 'processReseed', method: 'POST')
        "/api/capability/processes/by-code/$code"(controller: 'capabilityApi', action: 'processByCode', method: 'GET')
        "/api/capability/processes/$id"(controller: 'capabilityApi') {
            action = [GET: 'processShow', PUT: 'processUpdate']
        }
        "/api/capability/processes/$id/activate"(controller: 'capabilityApi', action: 'processActivate', method: 'POST')
        "/api/capability/processes/$id/retire"(controller: 'capabilityApi', action: 'processRetire', method: 'POST')
        "/api/capability/processes/$id/mermaid"(controller: 'capabilityApi', action: 'processMermaid', method: 'GET')
        "/api/capability/processes/$id/steps"(controller: 'capabilityApi') {
            action = [GET: 'processSteps', POST: 'processStepCreate']
        }
        "/api/capability/procedures"(controller: 'capabilityApi') {
            action = [GET: 'procedureList', POST: 'procedureCreate']
        }
        "/api/capability/portfolios"(controller: 'capabilityApi') {
            action = [GET: 'portfolioList', POST: 'portfolioCreate']
        }
        "/api/capability/portfolios/$id"(controller: 'capabilityApi', action: 'portfolioShow', method: 'GET')
        "/api/capability/portfolios/$id/close"(controller: 'capabilityApi', action: 'portfolioClose', method: 'POST')
        "/api/capability/programs"(controller: 'capabilityApi') {
            action = [GET: 'programList', POST: 'programCreate']
        }
        "/api/capability/projects"(controller: 'capabilityApi') {
            action = [GET: 'projectList', POST: 'projectCreate']
        }
        "/api/capability/projects/open"(controller: 'capabilityApi', action: 'projectOpen', method: 'GET')
        "/api/capability/projects/$id"(controller: 'capabilityApi', action: 'projectShow', method: 'GET')
        "/api/capability/projects/$id/start"(controller: 'capabilityApi', action: 'projectStart', method: 'POST')
        "/api/capability/projects/$id/complete"(controller: 'capabilityApi', action: 'projectComplete', method: 'POST')
        "/api/capability/projects/$id/cancel"(controller: 'capabilityApi', action: 'projectCancel', method: 'POST')
        "/api/capability/projects/$id/items"(controller: 'capabilityApi') {
            action = [GET: 'projectItems', POST: 'projectItemCreate']
        }
        "/api/capability/qualities"(controller: 'capabilityApi') {
            action = [GET: 'qualityList', POST: 'qualityCreate']
        }
        "/api/capability/qualities/$id"(controller: 'capabilityApi', action: 'qualityShow', method: 'GET')
        "/api/capability/qualities/$id/archive"(controller: 'capabilityApi', action: 'qualityArchive', method: 'POST')
        "/api/capability/qualities/$id/items"(controller: 'capabilityApi') {
            action = [GET: 'qualityItems', POST: 'qualityItemCreate']
        }
        "/api/capability/quality-items/$id/close"(controller: 'capabilityApi', action: 'qualityItemClose', method: 'POST')
        "/api/capability/changes"(controller: 'capabilityApi') {
            action = [GET: 'changeList', POST: 'changeCreate']
        }
        "/api/capability/changes/open"(controller: 'capabilityApi', action: 'changeOpen', method: 'GET')
        "/api/capability/changes/$id"(controller: 'capabilityApi', action: 'changeShow', method: 'GET')
        "/api/capability/changes/$id/implement"(controller: 'capabilityApi', action: 'changeImplement', method: 'POST')
        "/api/capability/changes/$id/close"(controller: 'capabilityApi', action: 'changeClose', method: 'POST')
        "/api/capability/changes/$id/cancel"(controller: 'capabilityApi', action: 'changeCancel', method: 'POST')
        "/api/capability/changes/$id/items"(controller: 'capabilityApi') {
            action = [GET: 'changeItems', POST: 'changeItemCreate']
        }
        "/api/capability/knowledge"(controller: 'capabilityApi') {
            action = [GET: 'knowledgeList', POST: 'knowledgeCreate']
        }
        "/api/capability/knowledge/$id"(controller: 'capabilityApi', action: 'knowledgeShow', method: 'GET')
        "/api/capability/knowledge/$id/children"(controller: 'capabilityApi', action: 'knowledgeChildren', method: 'GET')
        "/api/capability/standards"(controller: 'capabilityApi') {
            action = [GET: 'standardList', POST: 'standardCreate']
        }
        "/api/capability/standards/$id"(controller: 'capabilityApi') {
            action = [GET: 'standardShow', PUT: 'standardUpdate']
        }

        // Agentic Resources (AR-S0–AR-S3) — /api/agents/*
        "/api/agents/agents"(controller: 'agentsApi') {
            action = [GET: 'agentList', POST: 'agentCreate']
        }
        "/api/agents/agents/seed-core"(controller: 'agentsApi', action: 'agentSeedCore', method: 'POST')
        "/api/agents/agents/$id"(controller: 'agentsApi') {
            action = [GET: 'agentShow', PUT: 'agentUpdate']
        }
        "/api/agents/agents/$id/activate"(controller: 'agentsApi', action: 'agentActivate', method: 'POST')
        "/api/agents/agents/$id/pause"(controller: 'agentsApi', action: 'agentPause', method: 'POST')
        "/api/agents/agents/$id/retire"(controller: 'agentsApi', action: 'agentRetire', method: 'POST')
        "/api/agents/hives"(controller: 'agentsApi') {
            action = [GET: 'hiveList', POST: 'hiveCreate']
        }
        "/api/agents/hives/$id"(controller: 'agentsApi', action: 'hiveShow', method: 'GET')
        "/api/agents/hives/$id/master"(controller: 'agentsApi', action: 'hiveSetMaster', method: 'POST')
        "/api/agents/hives/$id/assign"(controller: 'agentsApi', action: 'hiveAssign', method: 'POST')
        "/api/agents/hives/$id/members"(controller: 'agentsApi', action: 'hiveMembers', method: 'GET')
        "/api/agents/hives/$id/activate"(controller: 'agentsApi', action: 'hiveActivate', method: 'POST')
        "/api/agents/hives/$id/close"(controller: 'agentsApi', action: 'hiveClose', method: 'POST')
        "/api/agents/approvals"(controller: 'agentsApi') {
            action = [GET: 'approvalList', POST: 'approvalCreate']
        }
        "/api/agents/approvals/open"(controller: 'agentsApi', action: 'approvalOpen', method: 'GET')
        "/api/agents/approvals/$id"(controller: 'agentsApi', action: 'approvalShow', method: 'GET')
        "/api/agents/approvals/$id/approve"(controller: 'agentsApi', action: 'approvalApprove', method: 'POST')
        "/api/agents/approvals/$id/reject"(controller: 'agentsApi', action: 'approvalReject', method: 'POST')
        "/api/agents/approvals/$id/cancel"(controller: 'agentsApi', action: 'approvalCancel', method: 'POST')
        "/api/agents/memories"(controller: 'agentsApi') {
            action = [GET: 'memoryList', POST: 'memoryCreate']
        }
        "/api/agents/memories/$id"(controller: 'agentsApi', action: 'memoryShow', method: 'GET')
        "/api/agents/memories/$id/archive"(controller: 'agentsApi', action: 'memoryArchive', method: 'POST')
        "/api/agents/agents/$id/memories"(controller: 'agentsApi', action: 'memoryByAgent', method: 'GET')
        "/api/agents/links"(controller: 'agentsApi') {
            action = [GET: 'linkList', POST: 'linkCreate']
        }
        "/api/agents/links/$id/unlink"(controller: 'agentsApi', action: 'linkUnlink', method: 'POST')

        // Object Companion / Content Rail (CT-S0–S2) — /api/content/*
        // NOTE: use hostPackage/hostResource — $package is reserved/ambiguous in Grails mappings
        "/api/content/types"(controller: 'contentApi', action: 'typeList', method: 'GET')
        "/api/content/summary"(controller: 'contentApi', action: 'summary', method: 'GET')
        "/api/content/profiles/$hostPackage/$hostResource"(controller: 'contentApi', action: 'profileShow', method: 'GET')
        "/api/content/on/$hostPackage/$hostResource/$hostId"(controller: 'contentApi', action: 'rail', method: 'GET')
        "/api/content/on/$hostPackage/$hostResource/$hostId/links"(controller: 'contentApi', action: 'addLink', method: 'POST')
        "/api/content/on/$hostPackage/$hostResource/$hostId/bodies"(controller: 'contentApi', action: 'addBody', method: 'POST')
        "/api/content/on/$hostPackage/$hostResource/$hostId/files"(controller: 'contentApi', action: 'addFile', method: 'POST')
        "/api/content/entry/$id"(controller: 'contentApi') {
            action = [PUT: 'contentUpdate', DELETE: 'contentDelete']
        }
        "/api/content/entry/$id/pin"(controller: 'contentApi', action: 'contentPin', method: 'POST')
        "/api/content/entry/$id/download"(controller: 'contentApi', action: 'contentDownload', method: 'GET')

        // SPA deep-link helper: authenticated users can open /app → redirect note page
        "/app"(controller: 'appBridge', action: 'index')

        "/$namespace/$controller/$action?/$id?(.$format)?" {}
        "/$controller/$action?/$id?(.$format)?" {
            constraints {
            }
        }

        "/"(view: "/index")
        "500"(view: '/error')
        "404"(view: '/notFound')
    }
}
