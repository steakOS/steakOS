// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

/**
 * Overlay i18n catalogue strings onto a CapProcess payload (SYSTEM English from API).
 * Keys live in the `processes` namespace: catalogue.<processCode>.{name,description,steps.<stepCode>.*}
 *
 * @param {object|null|undefined} process — API process (name, description, steps, processCode)
 * @param {(key: string, opts?: object) => string} t — i18next t bound to ns `processes` (or multi-ns)
 * @param {string} [processCode] — override when process.processCode is missing
 * @returns {object|null}
 */
export function localizeProcess(process, t, processCode) {
  if (!process || typeof t !== 'function') return process ?? null
  const code = processCode || process.processCode
  if (!code) return process

  const prefix = `catalogue.${code}`
  const nameKey = `${prefix}.name`
  const descKey = `${prefix}.description`
  const name = t(nameKey, { defaultValue: process.name || code })
  const description =
    process.description != null
      ? t(descKey, { defaultValue: process.description })
      : process.description

  const steps = Array.isArray(process.steps)
    ? process.steps.map((step) => localizeProcessStep(step, t, code))
    : process.steps

  return {
    ...process,
    name: name === nameKey ? process.name || code : name,
    description:
      description === descKey ? process.description : description,
    steps,
  }
}

/**
 * Localize a single process step (name + tip fields) by stepCode.
 */
export function localizeProcessStep(step, t, processCode) {
  if (!step || typeof t !== 'function' || !processCode) return step
  const stepCode = step.stepCode
  if (!stepCode) return step

  const base = `catalogue.${processCode}.steps.${stepCode}`
  const fields = ['name', 'tipWhat', 'tipWho', 'tipWhere', 'tipPitfall']
  const next = { ...step }
  for (const field of fields) {
    const raw = step[field]
    if (raw == null || raw === '') continue
    const key = `${base}.${field}`
    const translated = t(key, { defaultValue: raw })
    next[field] = translated === key ? raw : translated
  }
  return next
}

/**
 * Localize a list of steps for a known processCode (e.g. CapabilityHub diagram).
 */
export function localizeProcessSteps(steps, t, processCode) {
  if (!Array.isArray(steps)) return steps || []
  return steps.map((s) => localizeProcessStep(s, t, processCode))
}

/**
 * Desk toolbar label for a SYSTEM processCode.
 */
export function processButtonLabel(t, processCode, fallback) {
  if (!processCode || typeof t !== 'function') return fallback || processCode
  const key = `labels.${processCode}`
  const label = t(key, { defaultValue: fallback || processCode })
  return label === key ? fallback || processCode : label
}
