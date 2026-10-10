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
import groovy.util.logging.Slf4j

/**
 * Programs & projects (APQC 13.2.2–13.2.3). Flexible PPP (CAP1 A). No money (CAP7 A).
 */
@Slf4j
class CapProjectService {

    CapPortfolioService capPortfolioService

    @Transactional
    Program createProgram(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Long portfolioId = longOrNull(fields.portfolioId)
        if (portfolioId) capPortfolioService.get(portfolioId, npub)
        Program prog = new Program(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                portfolioId  : portfolioId,
                programStatus: (fields.programStatus ?: 'ACTIVE') as String,
                status       : (fields.programStatus ?: 'ACTIVE') as String
        )
        prog.save(failOnError: true, flush: true)
        return prog
    }

    @Transactional
    Project createProject(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Long programId = longOrNull(fields.programId)
        Long portfolioId = longOrNull(fields.portfolioId)
        if (programId) loadProgram(programId, npub)
        if (portfolioId) capPortfolioService.get(portfolioId, npub)
        Project proj = new Project(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                programId    : programId,
                portfolioId  : portfolioId,
                startDate    : parseDate(fields.startDate),
                endDate      : parseDate(fields.endDate),
                projectStatus: (fields.projectStatus ?: 'DRAFT') as String,
                status       : (fields.projectStatus ?: 'DRAFT') as String
        )
        proj.save(failOnError: true, flush: true)
        return proj
    }

    @Transactional
    ProjectItem addItem(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Long projectId = longOrNull(fields.projectId)
        if (!projectId) throw new IllegalArgumentException('projectId is required')
        loadProject(projectId, npub)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Integer itemNo = fields.itemNo != null ? fields.itemNo as Integer : nextItemNo(npub, projectId)
        ProjectItem item = new ProjectItem(
                npubOwner : npub,
                name      : name,
                description: fields.description as String,
                projectId : projectId,
                itemNo    : itemNo,
                itemStatus: (fields.itemStatus ?: 'OPEN') as String,
                taskId    : longOrNull(fields.taskId),
                status    : (fields.itemStatus ?: 'OPEN') as String
        )
        item.save(failOnError: true, flush: true)
        return item
    }

    @Transactional
    Project start(Long id, String npubOwner) {
        Project p = loadProject(id, npubOwner)
        if (p.projectStatus in ['DONE', 'CANCELLED']) {
            throw new IllegalStateException("cannot start ${p.projectStatus}")
        }
        p.projectStatus = 'ACTIVE'
        p.status = 'ACTIVE'
        if (!p.startDate) p.startDate = new Date()
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional
    Project complete(Long id, String npubOwner) {
        Project p = loadProject(id, npubOwner)
        if (p.projectStatus == 'CANCELLED') throw new IllegalStateException('cannot complete cancelled')
        p.projectStatus = 'DONE'
        p.status = 'DONE'
        if (!p.endDate) p.endDate = new Date()
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional
    Project cancel(Long id, String npubOwner) {
        Project p = loadProject(id, npubOwner)
        if (p.projectStatus == 'DONE') throw new IllegalStateException('cannot cancel done')
        p.projectStatus = 'CANCELLED'
        p.status = 'CANCELLED'
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional(readOnly = true)
    Project getProject(Long id, String npubOwner) { loadProject(id, npubOwner) }

    @Transactional(readOnly = true)
    Program getProgram(Long id, String npubOwner) { loadProgram(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Project> listProjects(String npubOwner, String projectStatus = null) {
        listProjectsPage(npubOwner, projectStatus, 500, 0).items as List<Project>
    }

    @Transactional(readOnly = true)
    Map listProjectsPage(String npubOwner, String projectStatus = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        if (projectStatus) {
            return [
                    items: Project.findAllByNpubOwnerAndProjectStatus(npub, projectStatus, opts),
                    total: Project.countByNpubOwnerAndProjectStatus(npub, projectStatus) as int
            ]
        }
        return [
                items: Project.findAllByNpubOwner(npub, opts),
                total: Project.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    List<Project> listOpen(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Project.findAllByNpubOwnerAndProjectStatusInList(
                npub, ['DRAFT', 'ACTIVE'], [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    List<Program> listPrograms(String npubOwner) {
        listProgramsPage(npubOwner, 500, 0).items as List<Program>
    }

    @Transactional(readOnly = true)
    Map listProgramsPage(String npubOwner, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        [
                items: Program.findAllByNpubOwner(npub, opts),
                total: Program.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    List<ProjectItem> listItems(String npubOwner, Long projectId) {
        String npub = requireNpub(npubOwner)
        loadProject(projectId, npub)
        ProjectItem.findAllByNpubOwnerAndProjectId(npub, projectId, [sort: 'itemNo', order: 'asc'])
    }

    Map toProjectMap(Project p) {
        if (!p) return null
        [
                id           : p.id,
                name         : p.name,
                description  : p.description,
                programId    : p.programId,
                portfolioId  : p.portfolioId,
                startDate    : p.startDate,
                endDate      : p.endDate,
                projectStatus: p.projectStatus,
                npubOwner    : p.npubOwner
        ]
    }

    Map toProgramMap(Program p) {
        if (!p) return null
        [
                id           : p.id,
                name         : p.name,
                description  : p.description,
                portfolioId  : p.portfolioId,
                programStatus: p.programStatus,
                npubOwner    : p.npubOwner
        ]
    }

    Map toItemMap(ProjectItem i) {
        if (!i) return null
        [
                id        : i.id,
                name      : i.name,
                description: i.description,
                projectId : i.projectId,
                itemNo    : i.itemNo,
                itemStatus: i.itemStatus,
                taskId    : i.taskId,
                npubOwner : i.npubOwner
        ]
    }

    private Project loadProject(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Project p = Project.get(id)
        if (!p || p.npubOwner != npub) throw new IllegalArgumentException("project not found: ${id}")
        p
    }

    private Program loadProgram(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Program p = Program.get(id)
        if (!p || p.npubOwner != npub) throw new IllegalArgumentException("program not found: ${id}")
        p
    }

    private Integer nextItemNo(String npub, Long projectId) {
        List<ProjectItem> items = ProjectItem.findAllByNpubOwnerAndProjectId(npub, projectId, [sort: 'itemNo', order: 'desc', max: 1])
        if (!items) return 1
        (items[0].itemNo ?: 0) + 1
    }

    private static Long longOrNull(Object v) {
        if (v == null || v == '') return null
        return v as Long
    }

    private static Date parseDate(Object raw) {
        if (raw == null || raw == '') return null
        if (raw instanceof Date) return raw
        String s = raw.toString().trim()
        if (s.length() >= 10) {
            try { return Date.parse('yyyy-MM-dd', s.substring(0, 10)) } catch (ignored) {}
        }
        null
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
