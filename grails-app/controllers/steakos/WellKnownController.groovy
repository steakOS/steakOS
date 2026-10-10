// SPDX-License-Identifier: Apache-2.0
package steakos

import grails.plugin.springsecurity.annotation.Secured

/**
 * A4 — public RFC 9116 security.txt (Spring static often 404s dotted paths).
 */
@Secured('permitAll')
class WellKnownController {

    static allowedMethods = [securityTxt: 'GET']

    def securityTxt() {
        String body = '''# steakOS security contact (RFC 9116)
# Process: docs/installation/SECURITY-DISCLOSURE.md · SECURITY.md

Contact: mailto:security@steakos.org
Preferred-Languages: en, de
Expires: 2027-08-22T00:00:00.000Z
'''
        response.contentType = 'text/plain; charset=UTF-8'
        render text: body, contentType: 'text/plain', encoding: 'UTF-8'
    }
}
