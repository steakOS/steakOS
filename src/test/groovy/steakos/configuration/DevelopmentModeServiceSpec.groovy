// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.configuration

import spock.lang.Specification

class DevelopmentModeServiceSpec extends Specification {

    def "AI:* statuses are recognized"() {
        expect:
        DevelopmentModeService.isAiTaskStatus('AI:done')
        DevelopmentModeService.isAiTaskStatus('AI:new')
        !DevelopmentModeService.isAiTaskStatus('DONE')
        !DevelopmentModeService.isAiTaskStatus('IN_PROGRESS')
        DevelopmentModeService.isHumanTaskStatus('DONE')
        DevelopmentModeService.isHumanTaskStatus('OPEN')
    }

    def "agents may set AI:done"() {
        expect:
        DevelopmentModeService.requireAgentAiTaskStatus('AI:done', true) == 'AI:done'
        DevelopmentModeService.requireAgentAiTaskStatus('AI:fixing', true) == 'AI:fixing'
    }

    def "agents cannot set DONE or other human statuses"() {
        when:
        DevelopmentModeService.requireAgentAiTaskStatus('DONE', true)

        then:
        SecurityException ex = thrown()
        ex.message.contains(DevelopmentModeService.AGENT_TASK_STATUS_CODE)

        when:
        DevelopmentModeService.requireAgentAiTaskStatus('IN_PROGRESS', true)

        then:
        thrown(SecurityException)

        when:
        DevelopmentModeService.requireAgentAiTaskStatus('OPEN', true)

        then:
        thrown(SecurityException)
    }

    def "blank status on update is allowed (leave unchanged)"() {
        expect:
        DevelopmentModeService.requireAgentAiTaskStatus(null, false) == null
        DevelopmentModeService.requireAgentAiTaskStatus('', false) == null
    }
}
