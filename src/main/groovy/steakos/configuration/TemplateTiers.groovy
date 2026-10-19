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

package steakos.configuration

/**
 * Product template ladder for operators: Bronze → Silver → Gold.
 * Iron and Copper remain as legacy/dev constants only — not shown in setup wizard or tour.
 *
 * IR-02 / OI-A07: only metals open for metal selection may be applied.
 * Family sets aligned to docs/PLUGINS.md §8 G1–G10.
 */
class TemplateTiers {

    /** @deprecated Dev-only legacy tier — not offered in wizard/tour. */
    static final String IRON = 'IRON'
    /** @deprecated Dev-only legacy tier — not offered in wizard/tour. */
    static final String COPPER = 'COPPER'
    static final String BRONZE = 'BRONZE'
    static final String SILVER = 'SILVER'
    static final String GOLD = 'GOLD'

    /** Default for new installs / metal selection fallback. */
    static final String DEFAULT_OPERATOR_TIER = BRONZE

    /** All known codes (incl. legacy Iron/Copper for existing tenants). */
    static final List<String> ALL = [IRON, COPPER, BRONZE, SILVER, GOLD].asImmutable()

    /** Codes shown in setup wizard / tour ladder (no Iron, no Copper). */
    static final List<String> OPERATOR_LADDER = [BRONZE, SILVER, GOLD].asImmutable()

    /**
     * Metals open for metal selection in wizard/settings today.
     * Bronze + Silver are productizable; Gold stays closed until productized.
     */
    static final List<String> OPEN_FOR_METAL_SELECTION = [BRONZE, SILVER].asImmutable()

    /**
     * Family codes used in nav / enablement.
     * Sparse codes (strategy, development, asset, grrc, stakeholder) reserved for Bronze/Silver/Gold packs.
     */
    static final List<String> ALL_FAMILIES = [
            'core', 'sales', 'scm', 'delivery', 'aftersales', 'human',
            'digital', 'finance', 'capability', 'agents',
            'strategy', 'development', 'asset', 'grrc', 'stakeholder'
    ].asImmutable()

    static boolean isValid(String tier) {
        tier != null && ALL.contains(tier.toUpperCase())
    }

    /** True if the operator may select this metal as the live tenant pack. */
    static boolean isOpenForMetalSelection(String tier) {
        tier != null && OPEN_FOR_METAL_SELECTION.contains(tier.toUpperCase())
    }

    /** True if the code is offered in wizard/tour (excludes Iron/Copper). */
    static boolean isOperatorFacing(String tier) {
        tier != null && OPERATOR_LADDER.contains(tier.toUpperCase())
    }

    static String normalize(String tier) {
        if (!tier?.trim()) return DEFAULT_OPERATOR_TIER
        String t = tier.trim().toUpperCase()
        return isValid(t) ? t : DEFAULT_OPERATOR_TIER
    }

    /**
     * Force to a metal that is open for metal selection (for apply / seed).
     * Invalid, closed, or legacy Iron/Copper fall back to Bronze.
     */
    static String normalizeMetalSelection(String tier) {
        String t = normalize(tier)
        return isOpenForMetalSelection(t) ? t : DEFAULT_OPERATOR_TIER
    }

    /**
     * Copper pack (legacy) — kept for existing COPPER tenants only.
     */
    static final List<String> COPPER_FAMILIES = [
            'core', 'sales', 'finance', 'delivery', 'aftersales', 'capability',
            'scm', 'agents'
    ].asImmutable()

    static final List<String> COPPER_FORBIDDEN = [
            'human', 'digital',
            'strategy', 'development', 'asset', 'grrc', 'stakeholder'
    ].asImmutable()

    /**
     * Bronze pack: people (HR) + IT + thin Strategy/Development/Asset on commercial base.
     */
    static final List<String> BRONZE_FAMILIES = [
            'core', 'sales', 'scm', 'finance', 'delivery', 'aftersales', 'capability', 'agents',
            'human', 'digital',
            'strategy', 'development', 'asset'
    ].asImmutable()

    /** Silver pack (productizable): Bronze + grrc + stakeholder. */
    static final List<String> SILVER_FAMILIES = [
            'core', 'sales', 'scm', 'finance', 'delivery', 'aftersales', 'capability', 'agents',
            'human', 'digital',
            'strategy', 'development', 'asset',
            'grrc', 'stakeholder'
    ].asImmutable()

    /**
     * Default enabled families for a tier.
     * Legacy Iron/Gold = all family codes.
     */
    static List<String> defaultFamilies(String tier) {
        switch (normalize(tier)) {
            case IRON:
            case GOLD:
                return new ArrayList<>(ALL_FAMILIES)
            case COPPER:
                return new ArrayList<>(COPPER_FAMILIES)
            case BRONZE:
                return new ArrayList<>(BRONZE_FAMILIES)
            case SILVER:
                return new ArrayList<>(SILVER_FAMILIES)
            default:
                return new ArrayList<>(BRONZE_FAMILIES)
        }
    }

    /**
     * Sanitize enabledFamilies for a metal.
     * Always includes core. Unknown codes dropped.
     */
    static List<String> sanitizeEnabledFamilies(String tier, List rawFamilies) {
        String t = normalize(tier)
        List<String> defaults = defaultFamilies(t)
        List<String> requested = (rawFamilies ?: []).collect { it?.toString()?.trim()?.toLowerCase() }.findAll { it }
        if (!requested) {
            return new ArrayList<>(defaults)
        }
        if (t == COPPER) {
            List forbidden = requested.findAll { COPPER_FORBIDDEN.contains(it) }
            if (forbidden) {
                throw new IllegalArgumentException(
                        "Copper cannot enable families: ${forbidden.join(', ')} (use Bronze/Silver)")
            }
            LinkedHashSet allowed = new LinkedHashSet(['core'])
            requested.each { String f ->
                if (COPPER_FAMILIES.contains(f)) {
                    allowed.add(f)
                }
            }
            if (allowed.size() == 1) {
                return new ArrayList<>(COPPER_FAMILIES)
            }
            return new ArrayList<>(allowed)
        }
        List<String> pack = defaults
        LinkedHashSet out = new LinkedHashSet(['core'])
        requested.each { String f ->
            if (pack.contains(f) || (t == IRON || t == GOLD) && ALL_FAMILIES.contains(f)) {
                out.add(f)
            }
        }
        if (t == BRONZE || t == SILVER) {
            LinkedHashSet allowed = new LinkedHashSet(['core'])
            requested.each { String f ->
                if (pack.contains(f)) {
                    allowed.add(f)
                }
            }
            return allowed.size() > 1 ? new ArrayList<>(allowed) : new ArrayList<>(defaults)
        }
        return out.size() > 1 ? new ArrayList<>(out) : new ArrayList<>(defaults)
    }

    // --- Plugin depth (Sales/Finance Advanced; stored in CompanySetting template.pluginDepth) ---

    static final String DEPTH_BASICS = 'BASICS'
    static final String DEPTH_ADVANCED = 'ADVANCED'
    static final String DEPTH_PRO = 'PRO'

    static final List<String> DEPTH_CODES = [DEPTH_BASICS, DEPTH_ADVANCED, DEPTH_PRO].asImmutable()

    /** Families that carry Basics/Advanced/Pro depth variants (PLUGINS G5). */
    static final List<String> DEPTH_FAMILIES = ['sales', 'finance'].asImmutable()

    /**
     * Families that claim Advanced depth at Silver+ (G5). Used by gates / status defaults.
     * Advanced families for the metal (Sales/Finance from Silver+).
     */
    static List<String> advancedFamilies(String tier) {
        String t = normalize(tier)
        if (t == SILVER || t == GOLD || t == IRON) {
            return new ArrayList<>(DEPTH_FAMILIES)
        }
        return []
    }

    /**
     * Default pluginDepth map for a metal. Silver/Gold → Advanced sales+finance; else Basics.
     */
    static Map<String, String> defaultPluginDepth(String tier) {
        Map<String, String> out = new LinkedHashMap<>()
        List<String> advanced = advancedFamilies(tier)
        DEPTH_FAMILIES.each { String f ->
            out[f] = advanced.contains(f) ? DEPTH_ADVANCED : DEPTH_BASICS
        }
        return out
    }

    /**
     * Sanitize a raw pluginDepth map. Unknown families dropped; invalid depths → BASICS.
     * When tier is Silver+, sales/finance are forced to at least ADVANCED (G5 pack-mandatory).
     */
    static Map<String, String> sanitizePluginDepth(String tier, Map raw) {
        Map<String, String> defaults = defaultPluginDepth(tier)
        Map<String, String> out = new LinkedHashMap<>(defaults)
        if (raw) {
            raw.each { k, v ->
                String family = k?.toString()?.trim()?.toLowerCase()
                if (!family || !DEPTH_FAMILIES.contains(family)) return
                String depth = normalizeDepth(v?.toString())
                out[family] = depth
            }
        }
        // G5 honesty: Silver+ always Advanced (or Pro) for sales/finance
        advancedFamilies(tier).each { String f ->
            String d = out[f] ?: DEPTH_BASICS
            if (d == DEPTH_BASICS) {
                out[f] = DEPTH_ADVANCED
            }
        }
        return out
    }

    static String normalizeDepth(String depth) {
        if (!depth?.trim()) return DEPTH_BASICS
        String d = depth.trim().toUpperCase()
        return DEPTH_CODES.contains(d) ? d : DEPTH_BASICS
    }

    /**
     * Operator-facing ladder for setup wizard / tour (Bronze → Silver → Gold).
     * Iron and Copper are omitted — temp/dev only, not for metal selection UI.
     */
    static List<Map> catalog() {
        [
                [code: BRONZE, label: 'Bronze', openForMetalSelection: isOpenForMetalSelection(BRONZE),
                 blurb: 'Team pack — sales, money, supply chain, delivery, people (HR), IT, and thin Strategy/Development/Asset. Recommended start.'],
                [code: SILVER, label: 'Silver', openForMetalSelection: isOpenForMetalSelection(SILVER),
                 blurb: 'Growing company — Bronze plus Sales/Finance Advanced, Governance, and Stakeholder. Optional email/Telegram (off by default).'],
                [code: GOLD, label: 'Gold', openForMetalSelection: isOpenForMetalSelection(GOLD),
                 blurb: 'Full enterprise surface + family Pros (when productized). Coming soon.']
        ]
    }
}
