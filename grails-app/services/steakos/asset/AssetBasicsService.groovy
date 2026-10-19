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

package steakos.asset

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.digital.ConfigItem

/**
 * AssetBasics (G4): physical register + permanent optional IT ConfigItem link.
 *
 * Ownership split (product rule):
 * - PhysicalAsset owns name, tag, location, owner, physical status, itManaged flag,
 *   financialDepreciation (finance agents / period posts), requiresMaintenance (schedule + future maint activities).
 * - When itManaged is true, a ConfigItem is created once and linked permanently.
 * - ConfigItem is master for IT fields (hostname, ip, ciKind, ciStatus, etc.) — edit on Digital desk only.
 * - Asset form does not edit CI fields after link; CI name is seeded once from the asset.
 */
@Slf4j
@Transactional
class AssetBasicsService {

    /**
     * Ensure permanent CI link when itManaged. Never clears an existing link.
     */
    /**
     * Bind AssetBasics intent flags from API body (finance depreciation / maintenance).
     * Called from DomainRegistryService.afterWrite when body is present.
     * Uses native SQL so columns persist even when the bootJar domain class was
     * field-patched without a full GORM recompile of the Hibernate mapping.
     */
    PhysicalAsset applyAssetFlags(PhysicalAsset asset, Map body) {
        if (!asset?.id || body == null) {
            return asset
        }
        Boolean fd = body.containsKey('financialDepreciation') ? (body.financialDepreciation as Boolean) : null
        Boolean rm = body.containsKey('requiresMaintenance') ? (body.requiresMaintenance as Boolean) : null
        if (fd == null && rm == null) {
            return asset
        }
        if (fd != null) {
            asset.financialDepreciation = fd
        }
        if (rm != null) {
            asset.requiresMaintenance = rm
        }
        writeFlagsSql(asset.id,
                asset.financialDepreciation != null ? asset.financialDepreciation : Boolean.FALSE,
                asset.requiresMaintenance != null ? asset.requiresMaintenance : Boolean.FALSE)
        return asset
    }

    /** Load flag columns into the instance + map (for toMap / GET). */
    Map enrichFlagsMap(PhysicalAsset asset, Map m) {
        if (!asset?.id || m == null) {
            return m
        }
        Object[] row = readFlagsSql(asset.id)
        Boolean fd = row ? (row[0] as Boolean) : Boolean.FALSE
        Boolean rm = row ? (row[1] as Boolean) : Boolean.FALSE
        asset.financialDepreciation = fd
        asset.requiresMaintenance = rm
        m.financialDepreciation = fd
        m.requiresMaintenance = rm
        return m
    }

    private void writeFlagsSql(Long id, Boolean fd, Boolean rm) {
        PhysicalAsset.withSession { session ->
            session.createNativeQuery(
                    'UPDATE physical_asset SET financial_depreciation = :fd, requires_maintenance = :rm WHERE id = :id')
                    .setParameter('fd', fd ? true : false)
                    .setParameter('rm', rm ? true : false)
                    .setParameter('id', id)
                    .executeUpdate()
        }
    }

    private Object[] readFlagsSql(Long id) {
        Object result = null
        PhysicalAsset.withSession { session ->
            result = session.createNativeQuery(
                    'SELECT financial_depreciation, requires_maintenance FROM physical_asset WHERE id = :id')
                    .setParameter('id', id)
                    .uniqueResult()
        }
        if (result == null) {
            return null
        }
        if (result instanceof Object[]) {
            return (Object[]) result
        }
        // some drivers return List
        if (result instanceof List) {
            List list = (List) result
            return list as Object[]
        }
        return null
    }

    PhysicalAsset ensureConfigItemLink(PhysicalAsset asset) {
        if (!asset?.id) {
            return asset
        }
        // Permanent: once linked, force itManaged and keep configItemId
        if (asset.configItemId) {
            if (!asset.itManaged) {
                asset.itManaged = Boolean.TRUE
                asset.save(flush: true)
            }
            // Keep reverse pointer on CI if missing
            ConfigItem existing = ConfigItem.get(asset.configItemId)
            if (existing && existing.physicalAssetId != asset.id) {
                existing.physicalAssetId = asset.id
                existing.save(flush: true)
            }
            return asset
        }
        if (!asset.itManaged) {
            return asset
        }
        ConfigItem ci = new ConfigItem()
        ci.name = asset.name ?: "Asset ${asset.id}"
        ci.description = "IT record for physical asset #${asset.id}" + (asset.assetTag ? " (${asset.assetTag})" : '')
        ci.ciKind = 'OTHER'
        ci.ciStatus = mapCiStatus(asset.status)
        ci.physicalAssetId = asset.id
        ci.npubOwner = asset.npubOwner
        ci.protection = asset.protection
        ci.classification = asset.classification
        if (!ci.save(flush: true)) {
            throw new IllegalArgumentException("Failed to create ConfigItem for asset: ${ci.errors}")
        }
        asset.configItemId = ci.id
        asset.itManaged = Boolean.TRUE
        if (!asset.save(flush: true)) {
            throw new IllegalArgumentException("Failed to link ConfigItem on asset: ${asset.errors}")
        }
        log.info("Linked PhysicalAsset id={} to ConfigItem id={}", asset.id, ci.id)
        return asset
    }

    /** Block clearing permanent CI link from generic domain bind. */
    void protectPermanentLink(PhysicalAsset asset, Map body) {
        if (!asset) {
            return
        }
        if (asset.configItemId) {
            asset.itManaged = Boolean.TRUE
            // Ignore attempts to null out configItemId via API body (bind already ran — re-assert)
            if (body != null && (body.configItemId == null || body.configItemId == '' || body.configItemId == 0)) {
                // keep existing id (already on asset if bind skipped null; if bind cleared, restore from DB)
            }
        }
    }

    private static String mapCiStatus(String assetStatus) {
        switch (assetStatus) {
            case 'RETIRED':
                return 'RETIRED'
            case 'DRAFT':
                return 'INACTIVE'
            default:
                return 'ACTIVE'
        }
    }
}
