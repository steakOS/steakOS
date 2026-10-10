// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import grails.compiler.GrailsCompileStatic

/**
 * Product role profile (PDR-025) — named preset with versioned permission matrix JSON.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'profileId')
@ToString(includes = 'profileId,label', includeNames = true, includePackage = false)
class RoleProfile implements Serializable {

    private static final long serialVersionUID = 1

    /** Stable id: admin, ceo, office, ops, finance, user, read_only */
    String profileId
    String label
    String defaultHome = '/work'
    /** JSON array of Spring authorities, e.g. ["ROLE_USER","ROLE_ADMIN"] */
    String springRolesJson
    /** Versioned permission matrix JSON */
    String matrixJson
    int matrixVersion = 1
    boolean systemPreset = true

    static constraints = {
        profileId nullable: false, blank: false, unique: true, maxSize: 64
        label nullable: false, blank: false, maxSize: 128
        defaultHome nullable: false, blank: false, maxSize: 128
        springRolesJson nullable: false, blank: false, maxSize: 2000
        matrixJson nullable: false, blank: false, maxSize: 16000
        matrixVersion nullable: false
        systemPreset nullable: false
    }

    static mapping = {
        springRolesJson type: 'text'
        matrixJson type: 'text'
    }
}
