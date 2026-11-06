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

package steakos.capability

import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

/**
 * Business processes (APQC 13.1) + SYSTEM catalogue seed / mermaid (PROCESS-CATALOG-VISUAL).
 * Domain is steakos.capability.Process — fully qualified.
 */
@Slf4j
class CapProcessService {

    private static final List KINDS = ['SYSTEM', 'HUMAN', 'AGENTIC', 'OTHER'].asImmutable()
    private static final List ACTORS = ['SYSTEM', 'HUMAN', 'AGENT'].asImmutable()

    @Transactional
    steakos.capability.Process createProcess(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String kind = normalizeKind(fields.processKind ?: 'HUMAN')
        if (kind == 'SYSTEM' && !fields.allowSystemCreate) {
            // User-created processes are never SYSTEM unless seed path sets allowSystemCreate
            kind = 'HUMAN'
        }
        steakos.capability.Process p = new steakos.capability.Process(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                processCode  : fields.processCode as String,
                processStatus: (fields.processStatus ?: 'DRAFT') as String,
                processKind  : kind,
                versionLabel : fields.versionLabel as String,
                ownerRef     : fields.ownerRef as String,
                status       : (fields.processStatus ?: 'DRAFT') as String
        )
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional
    steakos.capability.Process updateProcess(Long id, String npubOwner, Map fields) {
        steakos.capability.Process p = loadProcess(id, npubOwner)
        assertNotSystemLocked(p, 'update')
        if (fields.name) p.name = fields.name as String
        if (fields.containsKey('description')) p.description = fields.description as String
        if (fields.containsKey('processCode')) p.processCode = fields.processCode as String
        if (fields.containsKey('versionLabel')) p.versionLabel = fields.versionLabel as String
        if (fields.containsKey('ownerRef')) p.ownerRef = fields.ownerRef as String
        if (fields.containsKey('processKind')) {
            String k = normalizeKind(fields.processKind)
            if (k == 'SYSTEM') {
                throw new IllegalArgumentException('Cannot promote a process to SYSTEM via API; use reseed')
            }
            p.processKind = k
        }
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional
    steakos.capability.Process activate(Long id, String npubOwner) {
        setProcessStatus(id, npubOwner, 'ACTIVE')
    }

    @Transactional
    steakos.capability.Process retire(Long id, String npubOwner) {
        setProcessStatus(id, npubOwner, 'RETIRED')
    }

    @Transactional
    ProcessStep addStep(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Long processId = longOrNull(fields.processId)
        if (!processId) throw new IllegalArgumentException('processId is required')
        steakos.capability.Process proc = loadProcess(processId, npub)
        assertNotSystemLocked(proc, 'add step')
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Integer stepNo = fields.stepNo != null ? fields.stepNo as Integer : nextStepNo(npub, processId)
        Long predId = longOrNull(fields.predecessorId)
        if (predId) {
            ProcessStep pred = ProcessStep.get(predId)
            if (!pred || pred.npubOwner != npub || pred.processId != processId) {
                throw new IllegalArgumentException("predecessor not found: ${predId}")
            }
        }
        ProcessStep step = new ProcessStep(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                processId    : processId,
                stepNo       : stepNo,
                predecessorId: predId,
                stepCode     : fields.stepCode as String,
                deskHint     : fields.deskHint as String,
                outcomeStatus: fields.outcomeStatus as String,
                actorHint    : normalizeActor(fields.actorHint),
                tipWhat      : fields.tipWhat as String,
                tipWho       : fields.tipWho as String,
                tipWhere     : fields.tipWhere as String,
                tipPitfall   : fields.tipPitfall as String,
                stub         : fields.stub == true || fields.stub?.toString()?.equalsIgnoreCase('true'),
                helpKey      : fields.helpKey as String,
                status       : 'ACTIVE'
        )
        step.save(failOnError: true, flush: true)
        return step
    }

    @Transactional
    Procedure createProcedure(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Long processId = longOrNull(fields.processId)
        if (processId) loadProcess(processId, npub)
        Procedure proc = new Procedure(
                npubOwner   : npub,
                name        : name,
                description : fields.description as String,
                processId   : processId,
                versionLabel: fields.versionLabel as String,
                active      : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE,
                status      : 'ACTIVE'
        )
        proc.save(failOnError: true, flush: true)
        return proc
    }

    /**
     * Idempotent SYSTEM process seed from classpath JSON (D3 full set, D4 reseed).
     * @return summary map
     */
    @Transactional
    Map seedSystemProcesses(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Map catalog = loadCatalog()
        List processes = (catalog.processes as List) ?: []
        int created = 0
        int updated = 0
        int stepsUpserted = 0
        processes.each { Object raw ->
            Map defn = raw as Map
            String code = defn.processCode?.toString()?.trim()
            if (!code) return
            String dependsCsv = joinDependsOn(defn.dependsOnProcessCodes)
            steakos.capability.Process p = steakos.capability.Process.findByNpubOwnerAndProcessCode(npub, code)
            if (!p) {
                p = new steakos.capability.Process(
                        npubOwner      : npub,
                        name           : defn.name as String,
                        description    : defn.description as String,
                        processCode    : code,
                        processKind    : 'SYSTEM',
                        processStatus  : (defn.processStatus ?: 'ACTIVE') as String,
                        versionLabel   : defn.versionLabel as String,
                        dependsOnCodes : dependsCsv,
                        status         : (defn.processStatus ?: 'ACTIVE') as String
                )
                p.save(failOnError: true, flush: true)
                created++
            } else if (p.processKind == 'SYSTEM') {
                p.name = defn.name as String
                p.description = defn.description as String
                p.processStatus = (defn.processStatus ?: 'ACTIVE') as String
                p.status = p.processStatus
                p.versionLabel = defn.versionLabel as String
                p.dependsOnCodes = dependsCsv
                p.save(failOnError: true, flush: true)
                updated++
                // Replace SYSTEM steps for this process (keep catalogue honest)
                ProcessStep.findAllByNpubOwnerAndProcessId(npub, p.id).each { it.delete(flush: false) }
                ProcessStep.withSession { it.flush() }
            } else {
                log.info("Skip seed for processCode={} — existing non-SYSTEM process id={}", code, p.id)
                return
            }

            Map<Integer, Long> stepNoToId = [:]
            List stepDefs = (defn.steps as List) ?: []
            stepDefs.sort { (it as Map).stepNo as Integer }
            stepDefs.each { Object sraw ->
                Map s = sraw as Map
                Integer sn = s.stepNo as Integer
                Long predId = null
                if (s.predecessorStepNo != null) {
                    predId = stepNoToId[s.predecessorStepNo as Integer]
                }
                ProcessStep step = new ProcessStep(
                        npubOwner    : npub,
                        name         : s.name as String,
                        description  : s.description as String,
                        processId    : p.id,
                        stepNo       : sn,
                        predecessorId: predId,
                        stepCode     : s.stepCode as String,
                        deskHint     : s.deskHint as String,
                        outcomeStatus: s.outcomeStatus as String,
                        actorHint    : normalizeActor(s.actorHint),
                        tipWhat      : s.tipWhat as String,
                        tipWho       : s.tipWho as String,
                        tipWhere     : s.tipWhere as String,
                        tipPitfall   : s.tipPitfall as String,
                        stub         : s.stub == true || s.stub?.toString()?.equalsIgnoreCase('true'),
                        helpKey      : s.helpKey as String,
                        status       : 'ACTIVE'
                )
                step.save(failOnError: true, flush: true)
                stepNoToId[sn] = step.id
                stepsUpserted++
            }
        }
        [
                npubOwner    : npub,
                created      : created,
                updated      : updated,
                stepsUpserted: stepsUpserted,
                catalogSize  : processes.size()
        ]
    }

    @Transactional(readOnly = true)
    steakos.capability.Process getProcess(Long id, String npubOwner) { loadProcess(id, npubOwner) }

    @Transactional(readOnly = true)
    steakos.capability.Process findByCode(String npubOwner, String processCode) {
        String npub = requireNpub(npubOwner)
        if (!processCode?.trim()) return null
        String code = processCode.trim()
        // Tenant-owned first, then shared SYSTEM catalogue (any npub)
        steakos.capability.Process.findByNpubOwnerAndProcessCode(npub, code)
                ?: steakos.capability.Process.findByProcessKindAndProcessCode('SYSTEM', code)
    }

    @Transactional(readOnly = true)
    List<steakos.capability.Process> listProcesses(String npubOwner, String processStatus = null, String processKind = null) {
        listProcessesPage(npubOwner, processStatus, processKind, 500, 0).items as List<steakos.capability.Process>
    }

    /**
     * Tenant processes for {@code npub}, plus shared SYSTEM catalogue rows (any owner).
     * Fixes empty process browser when active company npub ≠ the seed tenant (user:admin).
     */
    static final List PROCESS_SORT_FIELDS = [
            'id', 'name', 'processCode', 'processStatus', 'processKind', 'status',
            'versionLabel', 'ownerRef'
    ].asImmutable()

    static final List PROCEDURE_SORT_FIELDS = [
            'id', 'name', 'status', 'processId'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listProcessesPage(String npubOwner, String processStatus = null, String processKind = null,
                          int max = 25, int offset = 0, Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        String kind = processKind?.trim()
        boolean tenantOnly = kind && kind != 'SYSTEM'
        boolean systemOnly = kind == 'SYSTEM'
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', PROCESS_SORT_FIELDS)
        def list = steakos.capability.Process.createCriteria().list(max: max, offset: offset) {
            if (tenantOnly) {
                eq 'npubOwner', npub
            } else if (!systemOnly) {
                or {
                    eq 'npubOwner', npub
                    eq 'processKind', 'SYSTEM'
                }
            }
            // systemOnly: no npub filter — shared SYSTEM catalogue
            if (processStatus) {
                eq 'processStatus', processStatus
            }
            if (kind) {
                eq 'processKind', kind
            }
            steakos.api.ListPaging.applyOrders(delegate, so)
        }
        int total = steakos.capability.Process.createCriteria().count {
            if (tenantOnly) {
                eq 'npubOwner', npub
            } else if (!systemOnly) {
                or {
                    eq 'npubOwner', npub
                    eq 'processKind', 'SYSTEM'
                }
            }
            if (processStatus) {
                eq 'processStatus', processStatus
            }
            if (kind) {
                eq 'processKind', kind
            }
        } as int
        [items: list, total: total, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<ProcessStep> listSteps(String npubOwner, Long processId) {
        steakos.capability.Process proc = loadProcess(processId, npubOwner)
        // Steps live under the process owner's npub (SYSTEM catalogue may differ from session tenant)
        ProcessStep.findAllByNpubOwnerAndProcessId(proc.npubOwner, processId, [sort: 'stepNo', order: 'asc'])
    }

    @Transactional(readOnly = true)
    List<Procedure> listProcedures(String npubOwner, Long processId = null) {
        listProceduresPage(npubOwner, processId, 500, 0).items as List<Procedure>
    }

    @Transactional(readOnly = true)
    Map listProceduresPage(String npubOwner, Long processId = null, int max = 25, int offset = 0,
                           Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', PROCEDURE_SORT_FIELDS)
        List items = Procedure.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (processId) eq('processId', processId)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Procedure.createCriteria().count {
            eq('npubOwner', npub)
            if (processId) eq('processId', processId)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    /** Mermaid flowchart LR from ordered steps (D6). */
    @Transactional(readOnly = true)
    String toMermaid(String npubOwner, Long processId) {
        List<ProcessStep> steps = listSteps(npubOwner, processId)
        if (!steps) {
            return 'flowchart LR\n  empty["No steps"]\n'
        }
        StringBuilder sb = new StringBuilder('flowchart LR\n')
        Map<Long, String> idToNode = [:]
        steps.each { ProcessStep s ->
            String nodeId = "S${s.stepNo}"
            idToNode[s.id] = nodeId
            String label = escapeMermaid(s.name)
            if (s.outcomeStatus) {
                label = "${label}\\n(${escapeMermaid(s.outcomeStatus)})"
            }
            sb.append("  ${nodeId}[\"${label}\"]\n")
        }
        steps.each { ProcessStep s ->
            if (s.predecessorId && idToNode[s.predecessorId]) {
                sb.append("  ${idToNode[s.predecessorId]} --> ${idToNode[s.id]}\n")
            } else if (s.stepNo > 1) {
                // fallback linear edge if predecessor missing
                String prev = "S${s.stepNo - 1}"
                if (steps.any { it.stepNo == s.stepNo - 1 }) {
                    sb.append("  ${prev} --> ${idToNode[s.id]}\n")
                }
            }
        }
        // If no edges at all, chain by stepNo
        boolean anyEdge = sb.toString().contains('-->')
        if (!anyEdge && steps.size() > 1) {
            steps.sort { it.stepNo }
            for (int i = 0; i < steps.size() - 1; i++) {
                sb.append("  S${steps[i].stepNo} --> S${steps[i + 1].stepNo}\n")
            }
        }
        return sb.toString()
    }

    Map toProcessMap(steakos.capability.Process p) {
        if (!p) return null
        [
                id                 : p.id,
                name               : p.name,
                description        : p.description,
                processCode        : p.processCode,
                processStatus      : p.processStatus,
                processKind        : p.processKind ?: 'HUMAN',
                versionLabel       : p.versionLabel,
                ownerRef           : p.ownerRef,
                dependsOnCodes     : p.dependsOnCodeList(),
                systemLocked       : p.processKind == 'SYSTEM',
                npubOwner          : p.npubOwner
        ]
    }

    private static String joinDependsOn(Object raw) {
        if (raw == null) return null
        if (raw instanceof Collection) {
            List<String> codes = (raw as Collection).collect { it?.toString()?.trim() }.findAll { it }
            return codes ? codes.join(',') : null
        }
        String s = raw.toString()?.trim()
        return s ?: null
    }

    Map toStepMap(ProcessStep s) {
        if (!s) return null
        [
                id           : s.id,
                name         : s.name,
                description  : s.description,
                processId    : s.processId,
                stepNo       : s.stepNo,
                predecessorId: s.predecessorId,
                stepCode     : s.stepCode,
                deskHint     : s.deskHint,
                outcomeStatus: s.outcomeStatus,
                actorHint    : s.actorHint,
                tipWhat      : s.tipWhat,
                tipWho       : s.tipWho,
                tipWhere     : s.tipWhere,
                tipPitfall   : s.tipPitfall,
                stub         : s.stub == true,
                helpKey      : s.helpKey,
                npubOwner    : s.npubOwner
        ]
    }

    Map toProcedureMap(Procedure p) {
        if (!p) return null
        [
                id          : p.id,
                name        : p.name,
                description : p.description,
                processId   : p.processId,
                versionLabel: p.versionLabel,
                active      : p.active,
                npubOwner   : p.npubOwner
        ]
    }

    private steakos.capability.Process setProcessStatus(Long id, String npubOwner, String st) {
        steakos.capability.Process p = loadProcess(id, npubOwner)
        // SYSTEM may be activated/retired for lifecycle, but not structurally edited
        p.processStatus = st
        p.status = st
        p.save(failOnError: true, flush: true)
        return p
    }

    private void assertNotSystemLocked(steakos.capability.Process p, String action) {
        if (p?.processKind == 'SYSTEM') {
            throw new IllegalArgumentException("SYSTEM process is read-only (${action}); clone to HUMAN to customize")
        }
    }

    /**
     * Load by id. Tenant-owned rows require matching npub; SYSTEM catalogue is readable
     * from any company session (shared process browser / How-it-works drawers).
     */
    private steakos.capability.Process loadProcess(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        steakos.capability.Process p = steakos.capability.Process.get(id)
        if (!p) throw new IllegalArgumentException("process not found: ${id}")
        if (p.npubOwner == npub) return p
        if (p.processKind == 'SYSTEM') return p
        throw new IllegalArgumentException("process not found: ${id}")
    }

    private Integer nextStepNo(String npub, Long processId) {
        List<ProcessStep> steps = ProcessStep.findAllByNpubOwnerAndProcessId(npub, processId, [sort: 'stepNo', order: 'desc', max: 1])
        if (!steps) return 1
        (steps[0].stepNo ?: 0) + 1
    }

    private static Map loadCatalog() {
        InputStream is = CapProcessService.classLoader.getResourceAsStream('steakos/capability/system-processes.json')
        if (!is) {
            throw new IllegalStateException('system-processes.json not found on classpath')
        }
        try {
            return new JsonSlurper().parse(is) as Map
        } finally {
            is.close()
        }
    }

    private static String normalizeKind(Object raw) {
        String k = raw?.toString()?.trim()?.toUpperCase()
        if (!k || !(k in KINDS)) return 'HUMAN'
        return k
    }

    private static String normalizeActor(Object raw) {
        if (raw == null || raw.toString().trim().isEmpty()) return null
        String a = raw.toString().trim().toUpperCase()
        return a in ACTORS ? a : null
    }

    private static String escapeMermaid(String s) {
        if (!s) return ''
        s.replace('"', "'").replace('[', '(').replace(']', ')').replace('\n', ' ')
    }

    private static Long longOrNull(Object v) {
        if (v == null || v == '') return null
        return v as Long
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
