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

package steakos.sales

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames

/**
 * SalesAdvanced light CRM (OI-S08 / G5) — Opportunity + CrmActivity.
 * Company-scoped via npubOwner. Convert-to-quote emits {@code sales.opportunity.won}.
 */
@Slf4j
class SalesCrmService {

    DomainEventBusService domainEventBusService
    SalesCustomerService salesCustomerService
    SalesQuoteService salesQuoteService
    steakos.configuration.CompanyCurrencyService companyCurrencyService

    // -------------------------------------------------------------------------
    // Opportunities
    // -------------------------------------------------------------------------

    @Transactional
    Opportunity createOpportunity(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')

        String stage = (fields.stage ?: Opportunity.STAGE_LEAD) as String
        if (!(stage in Opportunity.STAGES)) {
            throw new IllegalArgumentException("invalid stage: ${stage}")
        }

        Long customerId = parseLong(fields.customerId)
        if (customerId) {
            salesCustomerService?.getCustomer(customerId, npub)
        }

        String ccy = (fields.currencyCode ?: 'EUR') as String
        companyCurrencyService?.validateWritableCode(npub, ccy)

        Opportunity opp = new Opportunity(
                npubOwner         : npub,
                name              : name,
                description       : fields.description as String,
                stage             : stage,
                status            : stage,
                customerId        : customerId,
                contactName       : blankToNull(fields.contactName),
                contactEmail      : blankToNull(fields.contactEmail),
                expectedAmountEur : parseDecimal(fields.expectedAmountEur),
                currencyCode      : ccy,
                expectedCloseDate : parseDate(fields.expectedCloseDate),
                ownerName         : blankToNull(fields.ownerName),
                source            : blankToNull(fields.source),
                active            : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE
        )
        opp.save(failOnError: true, flush: true)
        return opp
    }

    @Transactional
    Opportunity updateOpportunity(Long id, String npubOwner, Map fields) {
        Opportunity opp = loadOpportunity(id, npubOwner)
        assertMutable(opp)

        if (fields.name) opp.name = fields.name as String
        if (fields.containsKey('description')) opp.description = fields.description as String
        if (fields.stage) {
            String stage = fields.stage as String
            if (!(stage in Opportunity.STAGES)) {
                throw new IllegalArgumentException("invalid stage: ${stage}")
            }
            if (opp.stage == Opportunity.STAGE_WON && stage != Opportunity.STAGE_WON) {
                throw new IllegalStateException('cannot reopen a WON opportunity')
            }
            opp.stage = stage
            opp.status = stage
        }
        if (fields.containsKey('customerId')) {
            Long customerId = parseLong(fields.customerId)
            if (customerId) {
                salesCustomerService?.getCustomer(customerId, npubOwner)
            }
            opp.customerId = customerId
        }
        if (fields.containsKey('contactName')) opp.contactName = blankToNull(fields.contactName)
        if (fields.containsKey('contactEmail')) opp.contactEmail = blankToNull(fields.contactEmail)
        if (fields.containsKey('expectedAmountEur')) {
            opp.expectedAmountEur = parseDecimal(fields.expectedAmountEur)
        }
        if (fields.currencyCode) {
            companyCurrencyService?.validateWritableCode(npubOwner, fields.currencyCode as String)
            opp.currencyCode = fields.currencyCode as String
        }
        if (fields.containsKey('expectedCloseDate')) {
            opp.expectedCloseDate = parseDate(fields.expectedCloseDate)
        }
        if (fields.containsKey('ownerName')) opp.ownerName = blankToNull(fields.ownerName)
        if (fields.containsKey('source')) opp.source = blankToNull(fields.source)
        if (fields.containsKey('active')) opp.active = fields.active as Boolean

        opp.save(failOnError: true, flush: true)
        return opp
    }

    @Transactional
    Opportunity markLost(Long id, String npubOwner, Map fields = [:]) {
        Opportunity opp = loadOpportunity(id, npubOwner)
        assertMutable(opp)
        if (fields.containsKey('description') && fields.description) {
            String note = fields.description as String
            opp.description = opp.description ? "${opp.description}\n${note}" : note
        }
        opp.stage = Opportunity.STAGE_LOST
        opp.status = Opportunity.STAGE_LOST
        opp.save(failOnError: true, flush: true)
        return opp
    }

    @Transactional
    Opportunity deactivate(Long id, String npubOwner) {
        Opportunity opp = loadOpportunity(id, npubOwner)
        opp.active = Boolean.FALSE
        opp.save(failOnError: true, flush: true)
        return opp
    }

    @Transactional
    void deleteOpportunity(Long id, String npubOwner) {
        Opportunity opp = loadOpportunity(id, npubOwner)
        CrmActivity.findAllByOpportunity(opp).each { it.delete(flush: false) }
        opp.delete(flush: true)
    }

    /**
     * Convert open opportunity → draft CustomerQuote; mark WON; emit sales.opportunity.won.
     * Requires customerId on the opportunity or in {@code fields}.
     */
    @Transactional
    Map convertToQuote(Long id, String npubOwner, Map fields = [:]) {
        Opportunity opp = loadOpportunity(id, npubOwner)
        if (opp.resultingQuoteId) {
            CustomerQuote existing = CustomerQuote.get(opp.resultingQuoteId)
            if (existing && existing.npubOwner == opp.npubOwner) {
                return [
                        opportunity     : toMap(opp, true),
                        quote           : salesQuoteService?.toMap(existing, true),
                        alreadyConverted: true
                ]
            }
        }
        if (opp.stage == Opportunity.STAGE_LOST) {
            throw new IllegalStateException('cannot convert a LOST opportunity')
        }
        if (opp.stage == Opportunity.STAGE_WON) {
            throw new IllegalStateException('opportunity is already WON')
        }

        Long customerId = parseLong(fields.customerId) ?: opp.customerId
        if (!customerId) {
            throw new IllegalArgumentException('customerId is required to convert to quote')
        }
        Customer customer = salesCustomerService.getCustomer(customerId, npubOwner)
        if (!opp.customerId) {
            opp.customerId = customerId
        }

        Map quoteFields = [
                customerId  : customerId,
                name        : (fields.name ?: "Quote from ${opp.name}") as String,
                description : fields.description ?: opp.description,
                currencyCode: fields.currencyCode ?: opp.currencyCode ?: customer.currencyCode,
                amountEur   : fields.amountEur != null ? fields.amountEur : opp.expectedAmountEur
        ]
        if (fields.lines instanceof List) {
            quoteFields.lines = fields.lines
        }

        CustomerQuote quote = salesQuoteService.createQuote(npubOwner, quoteFields)

        opp.stage = Opportunity.STAGE_WON
        opp.status = Opportunity.STAGE_WON
        opp.resultingQuoteId = quote.id
        opp.save(failOnError: true, flush: true)

        emitWon(opp, quote)

        return [
                opportunity     : toMap(opp, true),
                quote           : salesQuoteService.toMap(quote, true),
                alreadyConverted: false
        ]
    }

    @Transactional(readOnly = true)
    Opportunity getOpportunity(Long id, String npubOwner) {
        loadOpportunity(id, npubOwner)
    }

    static final List OPP_SORT_FIELDS = [
            'id', 'name', 'stage', 'contactName', 'expectedAmountEur',
            'expectedCloseDate', 'ownerName', 'source', 'dateCreated', 'lastUpdated'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listOpportunitiesPage(String npubOwner, boolean activeOnly = true, int max = 25, int offset = 0,
                              String q = null, String stage = null, Long customerId = null,
                              boolean openOnly = false, Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', OPP_SORT_FIELDS)
        List items = Opportunity.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            if (stage?.trim()) eq('stage', stage.trim().toUpperCase())
            if (openOnly) inList('stage', Opportunity.OPEN_STAGES as List)
            if (customerId) eq('customerId', customerId)
            if (q?.trim()) {
                String like = "%${q.trim()}%"
                or {
                    ilike('name', like)
                    ilike('contactName', like)
                    ilike('contactEmail', like)
                    ilike('ownerName', like)
                }
            }
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List

        Number total = Opportunity.createCriteria().count {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            if (stage?.trim()) eq('stage', stage.trim().toUpperCase())
            if (openOnly) inList('stage', Opportunity.OPEN_STAGES as List)
            if (customerId) eq('customerId', customerId)
            if (q?.trim()) {
                String like = "%${q.trim()}%"
                or {
                    ilike('name', like)
                    ilike('contactName', like)
                    ilike('contactEmail', like)
                    ilike('ownerName', like)
                }
            }
        } as Number

        [items: items, total: total as int, sort: so.sort, order: so.order]
    }

    // -------------------------------------------------------------------------
    // Activities
    // -------------------------------------------------------------------------

    @Transactional
    CrmActivity createActivity(Long opportunityId, String npubOwner, Map fields) {
        Opportunity opp = loadOpportunity(opportunityId, npubOwner)
        String kind = (fields.kind ?: 'NOTE') as String
        if (!(kind in CrmActivity.KINDS)) {
            throw new IllegalArgumentException("invalid activity kind: ${kind}")
        }
        String channel = blankToNull(fields.channel)
        if (channel && !(channel in CrmActivity.CHANNELS)) {
            throw new IllegalArgumentException("invalid channel: ${channel}")
        }

        CrmActivity act = new CrmActivity(
                npubOwner   : opp.npubOwner,
                opportunity : opp,
                name        : blankToNull(fields.name),
                description : fields.description as String,
                kind        : kind,
                channel     : channel,
                activityAt  : parseDate(fields.activityAt) ?: new Date(),
                dueDate     : parseDate(fields.dueDate),
                status      : (fields.status ?: 'OPEN') as String
        )
        act.save(failOnError: true, flush: true)
        return act
    }

    @Transactional
    CrmActivity updateActivity(Long activityId, String npubOwner, Map fields) {
        CrmActivity act = loadActivity(activityId, npubOwner)
        if (fields.name) act.name = fields.name as String
        if (fields.containsKey('description')) act.description = fields.description as String
        if (fields.kind) {
            String kind = fields.kind as String
            if (!(kind in CrmActivity.KINDS)) {
                throw new IllegalArgumentException("invalid activity kind: ${kind}")
            }
            act.kind = kind
        }
        if (fields.containsKey('channel')) {
            String channel = blankToNull(fields.channel)
            if (channel && !(channel in CrmActivity.CHANNELS)) {
                throw new IllegalArgumentException("invalid channel: ${channel}")
            }
            act.channel = channel
        }
        if (fields.containsKey('activityAt')) act.activityAt = parseDate(fields.activityAt) ?: act.activityAt
        if (fields.containsKey('dueDate')) act.dueDate = parseDate(fields.dueDate)
        if (fields.status) act.status = fields.status as String
        act.save(failOnError: true, flush: true)
        return act
    }

    @Transactional
    void deleteActivity(Long activityId, String npubOwner) {
        CrmActivity act = loadActivity(activityId, npubOwner)
        act.delete(flush: true)
    }

    @Transactional(readOnly = true)
    List<CrmActivity> listActivities(Long opportunityId, String npubOwner) {
        Opportunity opp = loadOpportunity(opportunityId, npubOwner)
        CrmActivity.findAllByOpportunity(opp, [sort: 'activityAt', order: 'desc'])
    }

    @Transactional(readOnly = true)
    CrmActivity getActivity(Long activityId, String npubOwner) {
        loadActivity(activityId, npubOwner)
    }

    // -------------------------------------------------------------------------
    // Maps
    // -------------------------------------------------------------------------

    Map toMap(Opportunity opp, boolean includeActivities = false) {
        if (!opp) return null
        Map m = [
                id               : opp.id,
                name             : opp.name,
                description      : opp.description,
                stage            : opp.stage,
                status           : opp.status,
                customerId       : opp.customerId,
                contactName      : opp.contactName,
                contactEmail     : opp.contactEmail,
                expectedAmountEur: opp.expectedAmountEur,
                currencyCode     : opp.currencyCode,
                expectedCloseDate: opp.expectedCloseDate,
                ownerName        : opp.ownerName,
                source           : opp.source,
                resultingQuoteId : opp.resultingQuoteId,
                active           : opp.active,
                npubOwner        : opp.npubOwner,
                activityCount    : CrmActivity.countByOpportunity(opp)
        ]
        if (includeActivities) {
            m.activities = listActivities(opp.id, opp.npubOwner).collect { activityToMap(it) }
        }
        m
    }

    Map activityToMap(CrmActivity act) {
        if (!act) return null
        [
                id            : act.id,
                name          : act.name,
                description   : act.description,
                opportunityId : act.opportunity?.id,
                kind          : act.kind,
                channel       : act.channel,
                activityAt    : act.activityAt,
                dueDate       : act.dueDate,
                status        : act.status,
                npubOwner     : act.npubOwner
        ]
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void emitWon(Opportunity opp, CustomerQuote quote) {
        try {
            domainEventBusService?.publish(
                    DomainEventNames.SALES_OPPORTUNITY_WON,
                    'sales',
                    opp.npubOwner,
                    [
                            opportunityId: opp.id,
                            quoteId      : quote?.id,
                            customerId   : opp.customerId,
                            name         : opp.name,
                            amountEur    : opp.expectedAmountEur
                    ]
            )
        } catch (Exception e) {
            log.warn("sales.opportunity.won publish failed: ${e.message}")
        }
    }

    private static void assertMutable(Opportunity opp) {
        if (opp.stage == Opportunity.STAGE_WON) {
            throw new IllegalStateException('opportunity is already WON')
        }
    }

    private Opportunity loadOpportunity(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Opportunity opp = Opportunity.get(id)
        if (!opp || opp.npubOwner != npub) {
            throw new IllegalArgumentException("opportunity not found: ${id}")
        }
        opp
    }

    private CrmActivity loadActivity(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        CrmActivity act = CrmActivity.get(id)
        if (!act || act.npubOwner != npub) {
            throw new IllegalArgumentException("crm activity not found: ${id}")
        }
        act
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static String blankToNull(Object v) {
        if (v == null) return null
        String s = v.toString().trim()
        s ? s : null
    }

    private static Long parseLong(Object v) {
        if (v == null || v.toString().trim().isEmpty()) return null
        if (v instanceof Number) return (v as Number).longValue()
        try {
            return Long.parseLong(v.toString().trim())
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid id: ${v}")
        }
    }

    private static BigDecimal parseDecimal(Object v) {
        if (v == null || v.toString().trim().isEmpty()) return null
        if (v instanceof BigDecimal) return v as BigDecimal
        if (v instanceof Number) return new BigDecimal(v.toString())
        try {
            return new BigDecimal(v.toString().trim())
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid decimal: ${v}")
        }
    }

    private static Date parseDate(Object v) {
        if (v == null || v.toString().trim().isEmpty()) return null
        if (v instanceof Date) return v as Date
        if (v instanceof Number) return new Date((v as Number).longValue())
        String s = v.toString().trim()
        if (s.isLong()) return new Date(Long.parseLong(s))
        try {
            return Date.parse('yyyy-MM-dd', s.take(10))
        } catch (Exception ignored) {
            return null
        }
    }
}
