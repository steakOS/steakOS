// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.ops

import groovy.util.logging.Slf4j
import steakos.version.ProductVersionService

import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.MemoryUsage
import java.lang.management.RuntimeMXBean
import java.lang.management.ThreadMXBean

/**
 * OBS-03 Phase 1 — lightweight Prometheus text without full Micrometer HTTP RED.
 * Prefix: steakos_ + standard jvm_* process_* names. Cardinality: no customer ids.
 */
@Slf4j
class OpsMetricsService {

    OpsHealthService opsHealthService
    ProductVersionService productVersionService

    String prometheusText() {
        StringBuilder sb = new StringBuilder(2048)
        RuntimeMXBean rt = ManagementFactory.runtimeMXBean
        MemoryMXBean mem = ManagementFactory.memoryMXBean
        ThreadMXBean threads = ManagementFactory.threadMXBean

        String ver = 'unknown'
        try {
            def info = productVersionService?.current()
            ver = info?.semVerLabel ?: info?.softwareVersion ?: 'unknown'
        } catch (ignored) {}
        ver = sanitizeLabel(ver)

        help(sb, 'steakos_info', 'steakOS build identity')
        type(sb, 'steakos_info', 'gauge')
        sb.append("steakos_info{version=\"${ver}\"} 1\n")

        Map ready
        try {
            ready = opsHealthService.readiness()
        } catch (Exception e) {
            ready = [status: 'DOWN']
        }
        int readyVal = ready.status == 'UP' ? 1 : (ready.status == 'DEGRADED' ? 0 : -1)
        help(sb, 'steakos_ready', '1=UP 0=DEGRADED -1=DOWN')
        type(sb, 'steakos_ready', 'gauge')
        sb.append("steakos_ready ${readyVal}\n")

        help(sb, 'process_uptime_seconds', 'JVM uptime')
        type(sb, 'process_uptime_seconds', 'gauge')
        sb.append("process_uptime_seconds ${(rt.uptime / 1000.0d)}\n")

        emitMemory(sb, 'heap', mem.heapMemoryUsage)
        emitMemory(sb, 'nonheap', mem.nonHeapMemoryUsage)

        help(sb, 'jvm_threads_live', 'Live threads')
        type(sb, 'jvm_threads_live', 'gauge')
        sb.append("jvm_threads_live ${threads.threadCount}\n")
        help(sb, 'jvm_threads_daemon', 'Daemon threads')
        type(sb, 'jvm_threads_daemon', 'gauge')
        sb.append("jvm_threads_daemon ${threads.daemonThreadCount}\n")
        help(sb, 'jvm_threads_peak', 'Peak threads')
        type(sb, 'jvm_threads_peak', 'gauge')
        sb.append("jvm_threads_peak ${threads.peakThreadCount}\n")

        Runtime runtime = Runtime.runtime
        help(sb, 'process_files_open_hint', 'Available processors (not FD count)')
        type(sb, 'process_files_open_hint', 'gauge')
        // Avoid OS-specific FD APIs; expose CPUs as a coarse capacity signal
        sb.append("steakos_available_processors ${runtime.availableProcessors()}\n")

        return sb.toString()
    }

    private static void emitMemory(StringBuilder sb, String area, MemoryUsage u) {
        if (!u) return
        String a = sanitizeLabel(area)
        help(sb, "jvm_memory_used_bytes", 'JVM memory used')
        type(sb, "jvm_memory_used_bytes", 'gauge')
        sb.append("jvm_memory_used_bytes{area=\"${a}\"} ${u.used}\n")
        help(sb, "jvm_memory_committed_bytes", 'JVM memory committed')
        type(sb, "jvm_memory_committed_bytes", 'gauge')
        sb.append("jvm_memory_committed_bytes{area=\"${a}\"} ${u.committed}\n")
        if (u.max >= 0) {
            help(sb, "jvm_memory_max_bytes", 'JVM memory max')
            type(sb, "jvm_memory_max_bytes", 'gauge')
            sb.append("jvm_memory_max_bytes{area=\"${a}\"} ${u.max}\n")
        }
    }

    private static void help(StringBuilder sb, String name, String desc) {
        sb.append("# HELP ").append(name).append(' ').append(desc).append('\n')
    }

    private static void type(StringBuilder sb, String name, String t) {
        sb.append("# TYPE ").append(name).append(' ').append(t).append('\n')
    }

    private static String sanitizeLabel(String s) {
        if (!s) return ''
        s.replaceAll(/[^A-Za-z0-9_.:\\-]/, '_').take(64)
    }
}
