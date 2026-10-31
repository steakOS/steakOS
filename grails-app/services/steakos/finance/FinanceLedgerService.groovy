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

package steakos.finance

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.configuration.TenantMoneyService

/**
 * Double-entry ledger kernel (APQC 9.3, F-S1, FS6 hard rule).
 * Sole writer of {@link JournalEntry} / {@link JournalLine} posts and reversals.
 * Functional currency follows tenant primary value rail (PDR-023 T1).
 * Column names debitEur/creditEur mean <em>functional</em> amounts (legacy names).
 *
 * @see docs/metals/copper/copper-masterplan.md (FinanceBasics); historical: docs/archive/services__steakos-app__finance__FINANCE-SERVICES-CONCEPT.md
 */
@Slf4j
class FinanceLedgerService {

    static final String SOURCE_PERIOD_CLOSE = 'PERIOD_CLOSE'
    /** Fallback when tenant prefs unavailable (tests / unset). Prefer {@link #functionalCurrencyFor}. */
    static final String FUNCTIONAL_CURRENCY = 'EUR'
    static final String DEFAULT_FUNCTIONAL_CURRENCY = FUNCTIONAL_CURRENCY

    TenantMoneyService tenantMoneyService

    private static final List<Map> SBM_COA = [
            [code: '1000', name: 'Cash / Bank (fiat)', type: 'ASSET', sub: 'FIAT_BANK'],
            [code: '1010', name: 'BTC Wallet', type: 'ASSET', sub: 'BTC_WALLET'],
            [code: '1020', name: 'Lightning Receivable', type: 'ASSET', sub: 'LN_WALLET'],
            [code: '1100', name: 'Accounts Receivable', type: 'ASSET', sub: 'AR'],
            [code: '2000', name: 'Accounts Payable', type: 'LIABILITY', sub: 'AP'],
            [code: '2100', name: 'Tax Payable', type: 'LIABILITY', sub: 'TAX'],
            // PDR-027 — payroll liabilities / expense
            [code: '2200', name: 'Wages payable', type: 'LIABILITY', sub: 'PAYROLL'],
            [code: '2210', name: 'Payroll tax payable', type: 'LIABILITY', sub: 'PAYROLL_TAX'],
            [code: '3000', name: 'Equity / Opening', type: 'EQUITY', sub: null],
            [code: '4000', name: 'Revenue', type: 'REVENUE', sub: null],
            [code: '5000', name: 'Operating Expense', type: 'EXPENSE', sub: null],
            [code: '5100', name: 'COGS / Direct cost', type: 'EXPENSE', sub: null],
            [code: '5200', name: 'Wage / payroll expense', type: 'EXPENSE', sub: 'PAYROLL'],
    ].asImmutable()

    // -------------------------------------------------------------------------
    // COA
    // -------------------------------------------------------------------------

    /**
     * Idempotent seed of minimal SBM chart of accounts for the given npub.
     * @return accounts created or already present (full list for npub after seed)
     */
    /** Tenant functional currency (EUR|USD|BTC). Safe without Spring wiring. */
    String functionalCurrencyFor(String npubOwner) {
        try {
            if (tenantMoneyService != null) {
                String fc = tenantMoneyService.functionalCurrency(npubOwner)
                if (fc) {
                    return fc
                }
            }
        } catch (Exception e) {
            log.debug('functionalCurrencyFor({}): {}', npubOwner, e.message)
        }
        return DEFAULT_FUNCTIONAL_CURRENCY
    }

    @Transactional
    List<Account> ensureChartSeeded(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String fc = functionalCurrencyFor(npub)
        SBM_COA.each { Map defn ->
            Account existing = Account.findByNpubOwnerAndCode(npub, defn.code as String)
            if (!existing) {
                // Wallet accounts keep BTC/LN identity; COA currencyCode = books currency
                String acctCur = (defn.sub in ['BTC_WALLET', 'LN_WALLET']) ? 'BTC' : fc
                if (fc == 'BTC' && !(defn.sub in ['BTC_WALLET', 'LN_WALLET'])) {
                    acctCur = 'BTC'
                }
                Account a = new Account(
                        npubOwner     : npub,
                        code          : defn.code as String,
                        name          : defn.name as String,
                        accountType   : defn.type as String,
                        accountSubType: defn.sub as String,
                        currencyCode  : acctCur,
                        systemAccount : Boolean.TRUE,
                        active        : Boolean.TRUE,
                        description   : 'SBM seed COA (FinanceLedgerService.ensureChartSeeded)'
                )
                a.save(failOnError: true, flush: true)
                log.info('Seeded account {} for {}', defn.code, npub)
            }
        }
        return listAccounts(npub)
    }

    @Transactional(readOnly = true)
    List<Account> listAccounts(String npubOwner, boolean activeOnly = true) {
        listAccountsPage(npubOwner, activeOnly, 500, 0).items as List<Account>
    }

    static final List ACCOUNT_SORT_FIELDS = [
            'id', 'code', 'name', 'accountType', 'accountSubType',
            'currencyCode', 'active', 'dateCreated', 'lastUpdated'
    ].asImmutable()

    /** Paged chart of accounts (LIST-PAGINATION). */
    @Transactional(readOnly = true)
    Map listAccountsPage(String npubOwner, boolean activeOnly = true, int max = 25, int offset = 0,
                         Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'code', 'asc', ACCOUNT_SORT_FIELDS)
        List items = Account.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Account.createCriteria().count {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional
    Account createAccount(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String code = fields.code?.toString()?.trim()
        if (!code) {
            throw new IllegalArgumentException('account code is required')
        }
        if (Account.findByNpubOwnerAndCode(npub, code)) {
            throw new IllegalArgumentException("account code already exists: ${code}")
        }
        Account a = new Account(
                npubOwner     : npub,
                code          : code,
                name          : (fields.name ?: code) as String,
                description   : fields.description as String,
                accountType   : (fields.accountType ?: 'ASSET') as String,
                accountSubType: fields.accountSubType as String,
                currencyCode  : (fields.currencyCode ?: functionalCurrencyFor(npub)) as String,
                parentCode    : fields.parentCode as String,
                systemAccount : fields.containsKey('systemAccount') ? fields.systemAccount as Boolean : Boolean.FALSE,
                active        : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE
        )
        a.save(failOnError: true, flush: true)
        return a
    }

    @Transactional(readOnly = true)
    Account findAccount(String npubOwner, String code) {
        Account.findByNpubOwnerAndCode(requireNpub(npubOwner), code)
    }

    // -------------------------------------------------------------------------
    // Post / reverse
    // -------------------------------------------------------------------------

    /**
     * Atomic balanced post. Idempotent when {@code eventId} is set (same npub + sourceType + sourceId + eventId).
     */
    @Transactional
    JournalEntry postEntry(PostJournalCommand cmd) {
        if (!cmd) {
            throw new IllegalArgumentException('command is required')
        }
        String npub = requireNpub(cmd.npubOwner)
        Date entryDate = cmd.entryDate ?: new Date()
        String period = cmd.period?.trim() ?: new java.text.SimpleDateFormat('yyyy-MM').format(entryDate)
        if (!(period ==~ /^\d{4}-\d{2}$/)) {
            throw new IllegalArgumentException("period must be yyyy-MM, got: ${period}")
        }
        if (isPeriodClosed(npub, period)) {
            throw new IllegalStateException("period ${period} is closed for ${npub}")
        }

        // Idempotency
        if (cmd.eventId?.trim()) {
            JournalEntry existing = JournalEntry.findByNpubOwnerAndEventId(npub, cmd.eventId.trim())
            if (existing) {
                log.debug('Idempotent hit for eventId={}', cmd.eventId)
                return existing
            }
        }

        List<PostJournalLineCommand> lines = cmd.lines ?: []
        if (lines.size() < 2) {
            throw new IllegalArgumentException('at least two journal lines are required')
        }

        BigDecimal totalDebit = BigDecimal.ZERO
        BigDecimal totalCredit = BigDecimal.ZERO
        List<Map> resolved = []

        lines.eachWithIndex { PostJournalLineCommand line, int idx ->
            Account account = resolveAccount(npub, line)
            BigDecimal debit = nz(line.debitEur)
            BigDecimal credit = nz(line.creditEur)
            if (debit < BigDecimal.ZERO || credit < BigDecimal.ZERO) {
                throw new IllegalArgumentException("line ${idx + 1}: amounts must be non-negative")
            }
            boolean hasDebit = debit > BigDecimal.ZERO
            boolean hasCredit = credit > BigDecimal.ZERO
            if (hasDebit == hasCredit) {
                throw new IllegalArgumentException(
                        "line ${idx + 1}: exactly one of debitEur or creditEur must be positive (got debit=${debit}, credit=${credit})")
            }
            totalDebit = totalDebit.add(debit)
            totalCredit = totalCredit.add(credit)
            resolved << [account: account, debit: debit, credit: credit, line: line, idx: idx]
        }

        String fc = functionalCurrencyFor(npub)
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException(
                    "entry not balanced: debits=${totalDebit} credits=${totalCredit} (functional ${fc})")
        }
        if (totalDebit.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException('entry total must be greater than zero')
        }

        String entryName = cmd.name?.trim() ?: "JE ${period} ${cmd.sourceType ?: 'MANUAL'}"
        JournalEntry entry = new JournalEntry(
                npubOwner         : npub,
                name              : entryName,
                description       : cmd.description,
                entryDate         : entryDate,
                period            : period,
                // T1: books currency = tenant primary rail (cmd override only if service unset)
                functionalCurrency: fc,
                sourceType        : cmd.sourceType ?: 'MANUAL',
                sourceId          : cmd.sourceId,
                eventId           : cmd.eventId?.trim(),
                memo              : cmd.memo,
                posted            : Boolean.TRUE,
                postedAt          : new Date(),
                status            : 'POSTED'
        )
        entry.save(failOnError: true, flush: true)

        resolved.each { Map r ->
            PostJournalLineCommand line = r.line as PostJournalLineCommand
            Account account = r.account as Account
            int idx = r.idx as int
            JournalLine jl = new JournalLine(
                    npubOwner   : npub,
                    journalEntry: entry,
                    account     : account,
                    lineNo      : idx + 1,
                    name        : line.name?.trim() ?: "L${idx + 1} ${account.code}",
                    // debitEur/creditEur = functional units (EUR, USD, or BTC major)
                    debitEur    : r.debit as BigDecimal,
                    creditEur   : r.credit as BigDecimal,
                    amountSats  : line.amountSats,
                    memo        : line.memo
            )
            jl.save(failOnError: true, flush: true)
        }

        log.info('Posted JE id={} period={} total={} {} source={}/{}',
                entry.id, period, totalDebit, fc, entry.sourceType, entry.sourceId)
        return entry
    }

    /**
     * Create a reversing entry (swap debits/credits). Original stays posted (immutable).
     */
    @Transactional
    JournalEntry reverseEntry(Long entryId, String npubOwner, String reason = null) {
        String npub = requireNpub(npubOwner)
        JournalEntry original = JournalEntry.get(entryId)
        if (!original || original.npubOwner != npub) {
            throw new IllegalArgumentException("journal entry not found: ${entryId}")
        }
        if (!original.posted) {
            throw new IllegalStateException('cannot reverse an unposted entry')
        }
        if (isPeriodClosed(npub, original.period)) {
            throw new IllegalStateException("period ${original.period} is closed")
        }

        List<JournalLine> origLines = JournalLine.findAllByJournalEntry(original, [sort: 'lineNo', order: 'asc'])
        if (!origLines) {
            throw new IllegalStateException('original entry has no lines')
        }

        PostJournalCommand rev = new PostJournalCommand(
                npubOwner : npub,
                name      : "REV ${original.name}",
                description: reason ?: "Reversal of JE ${original.id}",
                entryDate : new Date(),
                period    : original.period,
                sourceType: 'REVERSAL',
                sourceId  : original.id?.toString(),
                eventId   : "reverse:${original.id}:${System.currentTimeMillis()}",
                memo      : reason,
                lines     : origLines.collect { JournalLine ol ->
                    new PostJournalLineCommand(
                            accountId : ol.account?.id,
                            accountCode: ol.account?.code,
                            // swap
                            debitEur  : ol.creditEur,
                            creditEur : ol.debitEur,
                            amountSats: ol.amountSats,
                            memo      : "REV: ${ol.memo ?: ''}".trim(),
                            name      : "REV ${ol.name}"
                    )
                }
        )
        JournalEntry reversing = postEntry(rev)
        reversing.reversesEntryId = original.id
        reversing.save(failOnError: true, flush: true)
        return reversing
    }

    // -------------------------------------------------------------------------
    // Balances & trial balance
    // -------------------------------------------------------------------------

    /**
     * Net balance in tenant functional currency for an account.
     * ASSET/EXPENSE: debit − credit; LIABILITY/EQUITY/REVENUE: credit − debit (credit-normal).
     * Map keys debitsEur/creditsEur/balanceEur are legacy names for functional amounts.
     */
    @Transactional(readOnly = true)
    Map getAccountBalance(String npubOwner, Long accountId, Date asOf = null) {
        String npub = requireNpub(npubOwner)
        String fc = functionalCurrencyFor(npub)
        Account account = Account.get(accountId)
        if (!account || account.npubOwner != npub) {
            throw new IllegalArgumentException("account not found: ${accountId}")
        }

        List<JournalLine> lines = JournalLine.createCriteria().list {
            eq('npubOwner', npub)
            eq('account', account)
            journalEntry {
                eq('posted', Boolean.TRUE)
                if (asOf) {
                    le('entryDate', asOf)
                }
            }
        } as List<JournalLine>

        BigDecimal debits = BigDecimal.ZERO
        BigDecimal credits = BigDecimal.ZERO
        lines.each { JournalLine l ->
            debits = debits.add(nz(l.debitEur))
            credits = credits.add(nz(l.creditEur))
        }
        BigDecimal net = netBalance(account.accountType, debits, credits)

        return [
                accountId  : account.id,
                code       : account.code,
                name       : account.name,
                accountType: account.accountType,
                debitsEur  : debits,
                creditsEur : credits,
                balanceEur : net,
                asOf       : asOf,
                currency   : fc
        ]
    }

    /**
     * Trial balance for a calendar period (posted entries only).
     */
    @Transactional(readOnly = true)
    Map trialBalance(String npubOwner, String period) {
        String npub = requireNpub(npubOwner)
        String fc = functionalCurrencyFor(npub)
        if (!(period ==~ /^\d{4}-\d{2}$/)) {
            throw new IllegalArgumentException("period must be yyyy-MM, got: ${period}")
        }

        List<JournalLine> lines = JournalLine.createCriteria().list {
            eq('npubOwner', npub)
            journalEntry {
                eq('posted', Boolean.TRUE)
                eq('period', period)
                ne('sourceType', SOURCE_PERIOD_CLOSE)
            }
        } as List<JournalLine>

        Map<Long, Map> byAccount = [:].withDefault { Long id ->
            Account a = Account.get(id)
            [
                    accountId  : id,
                    code       : a?.code,
                    name       : a?.name,
                    accountType: a?.accountType,
                    debitsEur  : BigDecimal.ZERO,
                    creditsEur : BigDecimal.ZERO
            ]
        }

        lines.each { JournalLine l ->
            Long aid = l.account?.id
            if (!aid) {
                return
            }
            Map row = byAccount[aid]
            row.debitsEur = (row.debitsEur as BigDecimal).add(nz(l.debitEur))
            row.creditsEur = (row.creditsEur as BigDecimal).add(nz(l.creditEur))
        }

        List rows = byAccount.values().collect { Map row ->
            row.balanceEur = netBalance(row.accountType as String,
                    row.debitsEur as BigDecimal, row.creditsEur as BigDecimal)
            row
        }.sort { a, b -> (a.code ?: '') <=> (b.code ?: '') }

        BigDecimal totalDebits = rows.inject(BigDecimal.ZERO) { BigDecimal acc, Map r ->
            acc.add(r.debitsEur as BigDecimal)
        } as BigDecimal
        BigDecimal totalCredits = rows.inject(BigDecimal.ZERO) { BigDecimal acc, Map r ->
            acc.add(r.creditsEur as BigDecimal)
        } as BigDecimal

        return [
                period       : period,
                npubOwner    : npub,
                currency     : fc,
                closed       : isPeriodClosed(npub, period),
                lines        : rows,
                totalDebits  : totalDebits,
                totalCredits : totalCredits,
                balanced     : totalDebits.compareTo(totalCredits) == 0
        ]
    }

    // -------------------------------------------------------------------------
    // Period close (soft)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    boolean isPeriodClosed(String npubOwner, String period) {
        String npub = requireNpub(npubOwner)
        JournalEntry.countByNpubOwnerAndSourceTypeAndSourceIdAndPosted(
                npub, SOURCE_PERIOD_CLOSE, period, Boolean.TRUE) > 0
    }

    /**
     * Soft-close a period by writing a marker journal header (no money lines).
     * Further posts to that period are rejected.
     */
    @Transactional
    Map closePeriod(String npubOwner, String period) {
        String npub = requireNpub(npubOwner)
        if (!(period ==~ /^\d{4}-\d{2}$/)) {
            throw new IllegalArgumentException("period must be yyyy-MM, got: ${period}")
        }
        if (isPeriodClosed(npub, period)) {
            return [period: period, closed: true, alreadyClosed: true]
        }
        String eventId = "period-close:${npub}:${period}"
        JournalEntry marker = new JournalEntry(
                npubOwner         : npub,
                name              : "Period close ${period}",
                description       : 'Soft period close marker (FinanceLedgerService)',
                entryDate         : new Date(),
                period            : period,
                functionalCurrency: functionalCurrencyFor(npub),
                sourceType        : SOURCE_PERIOD_CLOSE,
                sourceId          : period,
                eventId           : eventId,
                memo              : 'PERIOD_CLOSED',
                posted            : Boolean.TRUE,
                postedAt          : new Date(),
                status            : 'PERIOD_CLOSED'
        )
        marker.save(failOnError: true, flush: true)
        log.info('Closed period {} for {}', period, npub)
        return [period: period, closed: true, alreadyClosed: false, markerEntryId: marker.id]
    }

    // -------------------------------------------------------------------------
    // Mapping helpers for API
    // -------------------------------------------------------------------------

    Map accountToMap(Account a) {
        if (!a) {
            return null
        }
        [
                id            : a.id,
                code          : a.code,
                name          : a.name,
                description   : a.description,
                accountType   : a.accountType,
                accountSubType: a.accountSubType,
                currencyCode  : a.currencyCode,
                parentCode    : a.parentCode,
                systemAccount : a.systemAccount,
                active        : a.active,
                npubOwner     : a.npubOwner
        ]
    }

    Map entryToMap(JournalEntry e, boolean includeLines = true) {
        if (!e) {
            return null
        }
        Map m = [
                id                : e.id,
                name              : e.name,
                description       : e.description,
                entryDate         : e.entryDate,
                period            : e.period,
                functionalCurrency: e.functionalCurrency,
                sourceType        : e.sourceType,
                sourceId          : e.sourceId,
                eventId           : e.eventId,
                memo              : e.memo,
                posted            : e.posted,
                postedAt          : e.postedAt,
                reversesEntryId   : e.reversesEntryId,
                status            : e.status,
                npubOwner         : e.npubOwner
        ]
        if (includeLines) {
            List<JournalLine> lines = JournalLine.findAllByJournalEntry(e, [sort: 'lineNo', order: 'asc'])
            m.lines = lines.collect { JournalLine l ->
                [
                        id         : l.id,
                        lineNo     : l.lineNo,
                        name       : l.name,
                        accountId  : l.account?.id,
                        accountCode: l.account?.code,
                        debitEur   : l.debitEur,
                        creditEur  : l.creditEur,
                        amountSats : l.amountSats,
                        memo       : l.memo
                ]
            }
        }
        return m
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private Account resolveAccount(String npub, PostJournalLineCommand line) {
        Account account = null
        if (line.accountId) {
            account = Account.get(line.accountId)
        }
        if (!account && line.accountCode) {
            account = Account.findByNpubOwnerAndCode(npub, line.accountCode.trim())
        }
        if (!account) {
            throw new IllegalArgumentException(
                    "account not found (code=${line.accountCode}, id=${line.accountId}) for ${npub}")
        }
        if (account.npubOwner != npub) {
            throw new IllegalArgumentException("account ${account.code} does not belong to ${npub}")
        }
        if (account.active == Boolean.FALSE) {
            throw new IllegalArgumentException("account ${account.code} is inactive")
        }
        return account
    }

    private static BigDecimal netBalance(String accountType, BigDecimal debits, BigDecimal credits) {
        String t = (accountType ?: '').toUpperCase()
        // debit-normal: assets, expenses
        if (t in ['ASSET', 'EXPENSE']) {
            return debits.subtract(credits)
        }
        // credit-normal: liabilities, equity, revenue
        return credits.subtract(debits)
    }

    private static BigDecimal nz(BigDecimal v) {
        v ?: BigDecimal.ZERO
    }

    private static String requireNpub(String npubOwner) {
        String n = npubOwner?.trim()
        if (!n) {
            throw new IllegalArgumentException('npubOwner is required')
        }
        return n
    }
}
