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

package steakos.generic

/**
 * System default ObjectContentProfile allowlists (CT4 / CT8).
 * Note is always injected by ContentProfileService if missing.
 */
class ContentProfileDefaults {

    /** hostPackage|hostResource → ordered BODY type codes */
    static final Map<String, List<String>> PROFILES = [
            'sales|CustomerQuote'   : ['Note', 'Target', 'Risk', 'Plan', 'Issue', 'Assessment'],
            'sales|SalesOrder'      : ['Note', 'Risk', 'Issue', 'Plan'],
            'sales|Customer'        : ['Note', 'Communication', 'Feedback'],
            'sales|Opportunity'     : ['Note', 'Plan', 'Risk', 'Communication', 'Assessment'],
            'catalogue|CatalogueItem': ['Note', 'Document', 'Plan', 'Risk'],
            'finance|RevenueItem'   : ['Note', 'Issue', 'Risk'],
            'finance|Invoice'       : ['Note', 'Issue', 'Communication'],
            'finance|Payable'       : ['Note', 'Issue'],
            'service|Inquiry'       : ['Note', 'Communication', 'Issue', 'Feedback'],
            'service|Claims'        : ['Note', 'Issue', 'Communication', 'Assessment'],
            'service|Rma'           : ['Note', 'Issue', 'Plan'],
            'service|Warranty'      : ['Note', 'Document'],
            'capability|Project'    : ['Note', 'Plan', 'Risk', 'Issue', 'Target', 'Baseline'],
            'capability|Process'    : ['Note', 'Document', 'Requirement', 'Risk'],
            'human|HRFile'          : ['Note', 'Plan', 'Issue'],
            'human|Candidate'       : ['Note', 'Evaluation', 'Feedback'],
            'agents|Agent'          : ['Note', 'Feedback', 'Evaluation'],
            'agents|Hive'           : ['Note', 'Plan', 'Communication'],
            'delivery|ServiceOrder' : ['Note', 'Issue', 'Plan'],
            'supplychain|PurchaseOrder': ['Note', 'Issue', 'Risk'],
            'procurement|PurchaseOrder': ['Note', 'Issue', 'Risk'],
            'production|ProductionOrder': ['Note', 'Issue', 'Plan'],
            'digital|ITChange'      : ['Note', 'Plan', 'Risk', 'Issue'],
            'digital|ItTicket'      : ['Note', 'Issue', 'Communication'],
            'digital|Solution'      : ['Note', 'Document', 'Plan'],
            'digital|System'        : ['Note', 'Document', 'Plan'],
            'digital|Data'          : ['Note', 'Document', 'Risk'],
            'digital|ConfigItem'    : ['Note', 'Document', 'Issue'],
            'finance|RevenueItem'   : ['Note', 'Issue', 'Risk'],
            'strategy|Competitor'   : ['Note', 'Document', 'Assessment', 'Risk'],
            'strategy|StrategyItem' : ['Note', 'Plan', 'Document'],
            'development|DevelopmentProject': ['Note', 'Plan', 'Document', 'Risk'],
            'asset|PhysicalAsset'   : ['Note', 'Document', 'Plan'],
    ].asImmutable()

    static String key(String hostPackage, String hostResource) {
        "${hostPackage}|${hostResource}"
    }

    static List<String> forHost(String hostPackage, String hostResource) {
        PROFILES[key(hostPackage, hostResource)] ?: [ContentTypeCodes.NOTE]
    }
}
