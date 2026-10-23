// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.api

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import steakos.ops.OpsHealthService
import steakos.ops.OpsMetricsService

/**
 * OBS-01 / OBS-03 — public health + Prometheus text for synthetics (concept §8.1–8.2).
 * Uptime Kuma probes these without a session.
 */
@Secured(['permitAll'])
class OpsHealthApiController {

    static allowedMethods = [health: 'GET', ready: 'GET', metrics: 'GET', dependencies: 'GET']

    OpsHealthService opsHealthService
    OpsMetricsService opsMetricsService

    /** GET /api/health — liveness */
    def health() {
        Map body = opsHealthService.liveness()
        render(status: 200, contentType: 'application/json', text: (body as JSON).toString())
    }

    /** GET /api/health/ready — readiness (503 when DOWN) */
    def ready() {
        Map body = opsHealthService.readiness()
        int code = body.status == 'DOWN' ? 503 : 200
        render(status: code, contentType: 'application/json', text: (body as JSON).toString())
    }

    /** GET /api/ops/metrics — Prometheus text exposition */
    def metrics() {
        String text = opsMetricsService.prometheusText()
        render(status: 200, contentType: 'text/plain; version=0.0.4; charset=utf-8', text: text)
    }

    /** GET /api/ops/dependencies — authenticated dependency board */
    @Secured(['IS_AUTHENTICATED_REMEMBERED'])
    def dependencies() {
        Map body = opsHealthService.readiness()
        render(status: 200, contentType: 'application/json', text: (body as JSON).toString())
    }
}
