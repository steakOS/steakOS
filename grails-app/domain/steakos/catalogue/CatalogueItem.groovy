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

package steakos.catalogue

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.Versioned
import steakos.traits.DomainTraitConstraints
import steakos.traits.Assignable

/**
 * Unified commercial catalogue master (goods + services).
 * Concept: docs/domain classes/steakos app/CATALOGUE-ITEM-CONCEPT.md
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'code,name,nature', includeNames = true, includePackage = false)
class CatalogueItem implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Versioned, Assignable, Serializable {

    private static final long serialVersionUID = 1L

    static final String NATURE_GOODS = 'GOODS'
    static final String NATURE_SERVICE = 'SERVICE'

    String name
    String description
    /** SKU / service code */
    String code
    String unit = 'EA'
    /** GOODS | SERVICE */
    String nature = NATURE_GOODS
    Boolean canBeSold = Boolean.TRUE
    Boolean canBeBought = Boolean.TRUE
    Boolean active = Boolean.TRUE
    BigDecimal listPrice
    /** ISO currency e.g. EUR */
    String currency = 'EUR'
    /** OI-F23 D9 — optional item tax-code override (system TaxCode.code) */
    String defaultTaxCode

    static constraints = {
        DomainTraitConstraints.applyNpubOwned(delegate)
        DomainTraitConstraints.applyAccessControl(delegate)
        DomainTraitConstraints.applyClassifiable(delegate)
        DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAssignable(delegate)
        DomainTraitConstraints.applyAuditable(delegate)
        DomainTraitConstraints.applyVersioned(delegate)

        name nullable: false, blank: false, maxSize: 255
        description nullable: true, maxSize: 4000
        code nullable: true, blank: true, maxSize: 64, unique: 'npubOwner'
        unit nullable: false, blank: false, maxSize: 16
        nature nullable: false, blank: false, maxSize: 16, inList: [NATURE_GOODS, NATURE_SERVICE]
        canBeSold nullable: false
        canBeBought nullable: false
        active nullable: false
        listPrice nullable: true, scale: 8
        currency nullable: false, blank: false, maxSize: 8
        defaultTaxCode nullable: true, maxSize: 32
    }

    static mapping = {
        table 'catalogue_item'
        description type: 'text'
        listPrice scale: 8
        code index: 'catalogue_item_code_idx'
        nature index: 'catalogue_item_nature_idx'
    }

    boolean isGoods() { nature == NATURE_GOODS }
    boolean isServiceNature() { nature == NATURE_SERVICE }
}
