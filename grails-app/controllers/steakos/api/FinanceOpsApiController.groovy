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

package steakos.api

import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import steakos.User
import steakos.finance.*
import steakos.payments.PaymentSettlementService
import steakos.payments.PaymentSettingsService
import steakos.payments.PaymentProviderService
import steakos.payments.PaymentReconcileService
import steakos.payments.PaymentProviderException

/**
 * Combined FinanceBasics ops API (F-S3–F-S6): treasury, payables, plan, tax, payroll, control.
 * Payments rail (OI-F27): settings + webhook via PaymentSettlementService.
 * PDR-027: payroll recognize ({@code /payroll/post}) + pay ({@code /payroll/pay}).
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class FinanceOpsApiController {

    FinanceTreasuryService financeTreasuryService
    FinancePayableService financePayableService
    FinancePlanService financePlanService
    FinanceTaxService financeTaxService
    FinancePayrollService financePayrollService
    FinanceControlService financeControlService
    FinanceRevenueService financeRevenueService
    PaymentSettlementService paymentSettlementService
    PaymentSettingsService paymentSettingsService
    PaymentProviderService paymentProviderService
    PaymentReconcileService paymentReconcileService
    steakos.payments.BtcIntegrationLevelService btcIntegrationLevelService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    // ----- Treasury F-S3 -----
    def treasurySeed() {
        wrap { String npub ->
            def list = financeTreasuryService.ensureDefaultPositions(npub)
            [npubOwner: npub, items: list.collect { financeTreasuryService.toMap(it) }]
        }
    }

    def treasuryList() {
        wrap { String npub ->
            Map page = steakos.api.ListPaging.pageParams(params, steakos.api.ListPaging.DEFAULT_MAX, steakos.api.ListPaging.HARD_MAX)
            Map listOpts = [sort: params.sort, order: params.order]
            Map result = financeTreasuryService.listPositionsPage(
                    npub, page.max as int, page.offset as int, listOpts)
            steakos.api.ListPaging.envelope(
                    (result.items as List).collect { financeTreasuryService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: result.sort ?: 'accountKind', order: result.order ?: 'asc']
            )
        }
    }

    def treasuryRegister() {
        wrapBody { String npub, Map body ->
            def t = financeTreasuryService.registerPosition(npub, body)
            [status: 201, body: financeTreasuryService.toMap(t)]
        }
    }

    def treasuryReconcile() {
        wrapBody { String npub, Map body ->
            def t = financeTreasuryService.reconcile(params.long('id'), npub, body)
            financeTreasuryService.toMap(t)
        }
    }

    /**
     * Payment rail webhook. Mock: steakOS invoiceId. BTCPay: Greenfield invoiceId + BTCPAY-SIG.
     */
    def btcpayWebhook() {
        wrapBody { String npub, Map body ->
            body.npubOwner = npub
            Map headers = [:]
            request.headerNames?.each { String name ->
                headers[name] = request.getHeader(name)
            }
            // Also common casing
            if (request.getHeader('BTCPAY-SIG')) {
                headers['BTCPAY-SIG'] = request.getHeader('BTCPAY-SIG')
            }
            paymentSettlementService.handleInboundWebhook(npub, body, headers, null)
        }
    }

    /** Redacted payment settings (OI-F27). */
    def paymentsSettingsGet() {
        wrap { String npub ->
            paymentSettingsService.publicSettings(npub)
        }
    }

    def paymentsSettingsPut() {
        wrapBody { String npub, Map body ->
            // OI-BTC01: optional level change on same payload
            if (body.containsKey('btcIntegrationLevel') || body.containsKey('level')) {
                User u = springSecurityService.currentUser as User
                Collection auths = springSecurityService.authentication?.authorities*.authority ?: []
                boolean isAdmin = auths.contains('ROLE_ADMIN')
                boolean isCeo = auths.contains('ROLE_CEO')
                Map levelBody = new LinkedHashMap(body)
                if (!levelBody.level && body.btcIntegrationLevel) {
                    levelBody.level = body.btcIntegrationLevel
                }
                btcIntegrationLevelService.setLevel(npub, levelBody, u?.username, isAdmin, isCeo)
            }
            paymentSettingsService.updateSettings(npub, body)
        }
    }

    /** OI-BTC01 — Bitcoin integration level (Admin set; CEO ack on upgrade). */
    def btcIntegrationLevelGet() {
        wrap { String npub ->
            btcIntegrationLevelService.publicLevel(npub)
        }
    }

    def btcIntegrationLevelPut() {
        wrapBody { String npub, Map body ->
            User u = springSecurityService.currentUser as User
            Collection auths = springSecurityService.authentication?.authorities*.authority ?: []
            boolean isAdmin = auths.contains('ROLE_ADMIN')
            boolean isCeo = auths.contains('ROLE_CEO')
            btcIntegrationLevelService.setLevel(npub, body, u?.username, isAdmin, isCeo)
        }
    }

    /** Probe BTCPay Greenfield store (uses saved credentials). */
    def paymentsProbe() {
        wrap { String npub ->
            paymentSettingsService.probeConnection(npub)
        }
    }

    /** Create inbound rail object for invoice (Mock/BTCPay). */
    def paymentsInboundCreate() {
        wrapBody { String npub, Map body ->
            Long invoiceId = (body.invoiceId ?: body.steakosInvoiceId) as Long
            if (!invoiceId) throw new IllegalArgumentException('invoiceId required')
            def created = paymentSettlementService.createInboundForInvoice(npub, invoiceId, body)
            [status: 201, body: created]
        }
    }

    /** Reconcile open external invoices (missed webhooks). */
    def paymentsReconcile() {
        wrap { String npub ->
            paymentReconcileService.reconcileOpen(npub)
        }
    }

    /** Sync one external id. */
    def paymentsReconcileOne() {
        wrapBody { String npub, Map body ->
            String ext = (body.externalId ?: body.invoiceId)?.toString()
            if (!ext) throw new IllegalArgumentException('externalId required')
            Long sid = body.steakosInvoiceId != null ? body.steakosInvoiceId as Long : null
            paymentSettlementService.reconcileExternal(npub, ext, sid)
        }
    }

    /** Active provider code for tenant. */
    def paymentsProviderStatus() {
        wrap { String npub ->
            def p = paymentProviderService.providerFor(npub)
            [
                    npubOwner: npub,
                    provider : p.code,
                    settings : paymentSettingsService.publicSettings(npub)
            ]
        }
    }

    // ----- Payables F-S4 -----
    def payableList() {
        wrap { String npub ->
            Map page = steakos.api.ListPaging.pageParams(params, steakos.api.ListPaging.DEFAULT_MAX, steakos.api.ListPaging.HARD_MAX)
            Map listOpts = [sort: params.sort, order: params.order]
            Map result = financePayableService.listPayablesPage(
                    npub, params.status as String, page.max as int, page.offset as int, listOpts)
            steakos.api.ListPaging.envelope(
                    (result.items as List).collect { financePayableService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: result.sort ?: 'id', order: result.order ?: 'desc']
            )
        }
    }

    def payableCreate() {
        wrapBody { String npub, Map body ->
            def p = financePayableService.createPayable(npub, body)
            [status: 201, body: financePayableService.toMap(p)]
        }
    }

    def payablePay() {
        if (isAgentPrincipal()) {
            render(status: 403, text: ([error: 'Agents cannot settle or pay money', code: 'AGENT_MONEY_DENIED'] as JSON))
            return
        }
        wrapBody { String npub, Map body ->
            financePayableService.recordPayment(params.long('id'), npub, body)
        }
    }

    def payableVoid() {
        wrapBody { String npub, Map body ->
            financePayableService.toMap(financePayableService.voidPayable(params.long('id'), npub))
        }
    }

    // ----- Plan F-S5 -----
    def budgetList() {
        wrap { String npub ->
            Map page = steakos.api.ListPaging.pageParams(params, steakos.api.ListPaging.DEFAULT_MAX, steakos.api.ListPaging.HARD_MAX)
            Map listOpts = [sort: params.sort, order: params.order]
            Map result = financePlanService.listBudgetsPage(
                    npub, params.period as String, page.max as int, page.offset as int, listOpts)
            steakos.api.ListPaging.envelope(
                    (result.items as List).collect { financePlanService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: result.sort ?: 'period', order: result.order ?: 'desc']
            )
        }
    }

    def budgetCreate() {
        wrapBody { String npub, Map body ->
            def b = financePlanService.createBudget(npub, body)
            [status: 201, body: financePlanService.toMap(b)]
        }
    }

    def budgetVariance() {
        wrap { String npub ->
            String period = params.period ?: new java.text.SimpleDateFormat('yyyy-MM').format(new Date())
            financePlanService.compareToActuals(npub, period)
        }
    }

    // ----- Tax F-S6 -----
    def taxList() {
        wrap { String npub ->
            Map page = steakos.api.ListPaging.pageParams(params, steakos.api.ListPaging.DEFAULT_MAX, steakos.api.ListPaging.HARD_MAX)
            Map listOpts = [sort: params.sort, order: params.order]
            Map result = financeTaxService.listTaxPeriodsPage(
                    npub, page.max as int, page.offset as int, listOpts)
            steakos.api.ListPaging.envelope(
                    (result.items as List).collect { financeTaxService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: result.sort ?: 'period', order: result.order ?: 'desc']
            )
        }
    }

    def taxEstimate() {
        wrapBody { String npub, Map body ->
            String period = body.period ?: params.period
            def t = financeTaxService.estimatePeriodTax(npub, period as String, body)
            financeTaxService.toMap(t)
        }
    }

    // ----- Payroll (PDR-027 recognize + pay) -----
    def payrollList() {
        wrap { String npub ->
            Map page = steakos.api.ListPaging.pageParams(params, steakos.api.ListPaging.DEFAULT_MAX, steakos.api.ListPaging.HARD_MAX)
            Map listOpts = [sort: params.sort, order: params.order]
            Map result = financePayrollService.listLinesPage(
                    npub, page.max as int, page.offset as int, listOpts)
            steakos.api.ListPaging.envelope(
                    (result.items as List).collect { financePayrollService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: result.sort ?: 'id', order: result.order ?: 'desc']
            )
        }
    }

    def payrollCreate() {
        wrapBody { String npub, Map body ->
            def i = financePayrollService.createLine(npub, body)
            [status: 201, body: financePayrollService.toMap(i)]
        }
    }

    /** Recognize / post payroll run (payroll.recognize). */
    def payrollPost() {
        wrapBody { String npub, Map body ->
            financePayrollService.recognizePayroll(npub, body.period as String ?: params.period, body)
        }
    }

    /** Pay net wages (payroll.pay). Requires prior recognize. */
    def payrollPay() {
        if (isAgentPrincipal()) {
            render(status: 403, text: ([error: 'Agents cannot settle or pay money', code: 'AGENT_MONEY_DENIED'] as JSON))
            return
        }
        wrapBody { String npub, Map body ->
            financePayrollService.payPayroll(npub, body.period as String ?: params.period, body)
        }
    }

    // ----- Control F-S6 -----
    def controlAudit() {
        wrap { String npub ->
            Map page = steakos.api.ListPaging.pageParams(params, steakos.api.ListPaging.DEFAULT_MAX, steakos.api.ListPaging.HARD_MAX)
            Map listOpts = [sort: params.sort, order: params.order]
            Map result = financeControlService.listAuditTrailPage(
                    npub, page.max as int, page.offset as int, listOpts)
            steakos.api.ListPaging.envelope(
                    result.items as List,
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: result.sort ?: 'id', order: result.order ?: 'desc']
            )
        }
    }

    def controlPeriod() {
        wrap { String npub ->
            financeControlService.periodStatus(npub, params.period as String)
        }
    }

    def controlClose() {
        wrapBody { String npub, Map body ->
            financeControlService.lockPeriod(npub, (body.period ?: params.period) as String)
        }
    }

    // ----- helpers -----
    private void wrap(Closure c) {
        try {
            String npub = resolveNpub(params)
            def result = c.call(npub)
            render(result as JSON)
        } catch (PaymentProviderException e) {
            render(status: e.httpStatus ?: 400, text: ([error: e.message, code: e.code] as JSON))
        } catch (PostingRulesException e) {
            render(status: 400, text: ([error: e.message, code: e.code] as JSON))
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    private void wrapBody(Closure c) {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            def result = c.call(npub, body)
            if (result instanceof Map && result.status && result.body != null) {
                render(status: result.status as int, text: (result.body as JSON))
            } else {
                render(result as JSON)
            }
        } catch (PaymentProviderException e) {
            render(status: e.httpStatus ?: 400, text: ([error: e.message, code: e.code] as JSON))
        } catch (PostingRulesException e) {
            render(status: 400, text: ([error: e.message, code: e.code] as JSON))
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }

    /** ROLE_AI without admin/C — agents must not pay (HITL + human settle). */
    private boolean isAgentPrincipal() {
        steakos.security.AgentPrincipal.isAgentOnly(springSecurityService?.authentication)
    }
}
