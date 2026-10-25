// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import mermaid from 'mermaid'
import { localizeProcessSteps } from '../utils/localizeProcess'

let mermaidReady = false
function ensureMermaid() {
  if (mermaidReady) return
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'loose',
    theme: 'neutral',
    flowchart: { curve: 'basis', htmlLabels: true, padding: 12 },
  })
  mermaidReady = true
}

function escapeMermaidLabel(text) {
  return String(text ?? '').replace(/"/g, "'").replace(/\n/g, ' ')
}

/**
 * Build flowchart from steps (mirrors CapProcessService.toMermaid).
 * Prefer this over API mermaid when steps are i18n-localized — catalogue mermaid is English-only.
 */
function buildMermaid(steps = [], mermaidText = '', noStepsLabel = 'No steps', { forceRebuild = false } = {}) {
  if (!forceRebuild && mermaidText?.trim()) return mermaidText.trim()
  const ordered = [...(steps || [])].sort((a, b) => (a.stepNo || 0) - (b.stepNo || 0))
  if (!ordered.length) return `flowchart LR\n  empty["${escapeMermaidLabel(noStepsLabel)}"]\n`
  const lines = ['flowchart LR']
  const idToNode = {}
  ordered.forEach((s) => {
    const nodeId = `S${s.stepNo}`
    if (s.id != null) idToNode[s.id] = nodeId
    const name = escapeMermaidLabel(s.name || `Step ${s.stepNo}`)
    const label = s.outcomeStatus
      ? `${name}\\n(${escapeMermaidLabel(s.outcomeStatus)})`
      : name
    lines.push(`  ${nodeId}["${label}"]`)
  })
  let anyEdge = false
  ordered.forEach((s) => {
    if (s.predecessorId != null && idToNode[s.predecessorId]) {
      lines.push(`  ${idToNode[s.predecessorId]} --> ${idToNode[s.id] || `S${s.stepNo}`}`)
      anyEdge = true
    } else if (s.stepNo > 1 && ordered.some((x) => Number(x.stepNo) === Number(s.stepNo) - 1)) {
      lines.push(`  S${s.stepNo - 1} --> S${s.stepNo}`)
      anyEdge = true
    }
  })
  if (!anyEdge && ordered.length > 1) {
    for (let i = 0; i < ordered.length - 1; i++) {
      lines.push(`  S${ordered[i].stepNo} --> S${ordered[i + 1].stepNo}`)
    }
  }
  return lines.join('\n')
}

function stepByNo(steps, stepNo) {
  return (steps || []).find((s) => Number(s.stepNo) === Number(stepNo))
}

function TipPopover({ step, onClose }) {
  const { t } = useTranslation('processes')
  if (!step) return null
  const hasRich =
    step.tipWhat || step.tipWho || step.tipWhere || step.tipPitfall || step.stub
  return (
    <div
      className="process-tip-popover"
      role="dialog"
      aria-label={t('ui.stepTipAria', { stepNo: step.stepNo })}
    >
      <div className="process-tip-header">
        <strong>
          #{step.stepNo} {step.name}
        </strong>
        {step.stub ? <span className="badge badge-warn">{t('ui.tipStub')}</span> : null}
        <button
          type="button"
          className="btn btn-ghost"
          onClick={onClose}
          aria-label={t('ui.closeTip')}
        >
          ×
        </button>
      </div>
      {hasRich ? (
        <dl className="process-tip-dl">
          {step.tipWhat ? (
            <>
              <dt>{t('ui.tipWhat')}</dt>
              <dd>{step.tipWhat}</dd>
            </>
          ) : null}
          {step.tipWho ? (
            <>
              <dt>{t('ui.tipWho')}</dt>
              <dd>{step.tipWho}</dd>
            </>
          ) : null}
          {step.tipWhere || step.deskHint ? (
            <>
              <dt>{t('ui.tipWhere')}</dt>
              <dd>
                {step.tipWhere || step.deskHint}
                {step.deskHint ? (
                  <>
                    {' '}
                    <Link to={step.deskHint}>{t('ui.openDesk')}</Link>
                  </>
                ) : null}
              </dd>
            </>
          ) : null}
          {step.tipPitfall ? (
            <>
              <dt>{t('ui.tipPitfall')}</dt>
              <dd>{step.tipPitfall}</dd>
            </>
          ) : null}
        </dl>
      ) : (
        <p className="muted">{t('ui.noTip')}</p>
      )}
      {step.helpKey ? (
        <p className="muted" style={{ marginBottom: 0, fontSize: '0.85rem' }}>
          {t('ui.helpKey')}: <code>{step.helpKey}</code> ·{' '}
          <Link to="/help">{t('ui.helpCenter')}</Link>
        </p>
      ) : null}
    </div>
  )
}

/**
 * Mermaid flowchart for process steps with A7 hover/focus/tap tips.
 * When processCode is set, step names/tips are overlaid from the processes catalogue.
 */
export default function ProcessDiagram({
  steps = [],
  mermaid: mermaidSource = '',
  title,
  processCode,
  highlightStatus,
}) {
  const { t, i18n } = useTranslation('processes')
  const reactId = useId().replace(/:/g, '')
  const hostRef = useRef(null)
  const [error, setError] = useState(null)
  const [activeStepNo, setActiveStepNo] = useState(null)
  const [pinned, setPinned] = useState(false)

  const localizedSteps = useMemo(() => {
    if (!processCode) return steps || []
    return localizeProcessSteps(
      steps,
      (key, opts) => t(key, opts),
      processCode,
    )
  }, [steps, processCode, t, i18n.language])

  const text = useMemo(
    () =>
      buildMermaid(localizedSteps, mermaidSource, t('ui.noSteps'), {
        // SYSTEM catalogue mermaid from API is English — rebuild from localized steps.
        forceRebuild: Boolean(processCode && localizedSteps?.length),
      }),
    [localizedSteps, mermaidSource, processCode, t, i18n.language],
  )
  const ordered = useMemo(
    () => [...(localizedSteps || [])].sort((a, b) => (a.stepNo || 0) - (b.stepNo || 0)),
    [localizedSteps],
  )
  const activeStep = activeStepNo != null ? stepByNo(ordered, activeStepNo) : null

  const showStep = useCallback((stepNo, { pin = false } = {}) => {
    setActiveStepNo(stepNo)
    if (pin) setPinned(true)
  }, [])

  const clearTip = useCallback(() => {
    setActiveStepNo(null)
    setPinned(false)
  }, [])

  const bindNode = useCallback(
    (el, stepNo) => {
      if (!el || stepNo == null) return
      el.setAttribute('tabindex', '0')
      el.style.cursor = 'pointer'
      el.setAttribute('role', 'button')
      el.setAttribute('aria-label', t('ui.stepAria', { stepNo }))
      const onEnter = () => {
        if (!pinned) showStep(stepNo)
      }
      const onLeave = () => {
        if (!pinned) setActiveStepNo(null)
      }
      const onFocus = () => showStep(stepNo)
      const onBlur = () => {
        if (!pinned) setActiveStepNo(null)
      }
      const onClick = (e) => {
        e.preventDefault()
        e.stopPropagation()
        if (pinned && Number(activeStepNo) === Number(stepNo)) {
          clearTip()
        } else {
          showStep(stepNo, { pin: true })
        }
      }
      const onKey = (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick(e)
        } else if (e.key === 'Escape') {
          clearTip()
        }
      }
      el.addEventListener('mouseenter', onEnter)
      el.addEventListener('mouseleave', onLeave)
      el.addEventListener('focus', onFocus)
      el.addEventListener('blur', onBlur)
      el.addEventListener('click', onClick)
      el.addEventListener('keydown', onKey)
      return () => {
        el.removeEventListener('mouseenter', onEnter)
        el.removeEventListener('mouseleave', onLeave)
        el.removeEventListener('focus', onFocus)
        el.removeEventListener('blur', onBlur)
        el.removeEventListener('click', onClick)
        el.removeEventListener('keydown', onKey)
      }
    },
    [activeStepNo, clearTip, pinned, showStep, t],
  )

  useEffect(() => {
    let cancelled = false
    const cleanups = []
    const run = async () => {
      if (!hostRef.current || !text) return
      setError(null)
      try {
        ensureMermaid()
        const id = `proc-mmd-${reactId}-${Date.now()}`
        const { svg } = await mermaid.render(id, text)
        if (cancelled || !hostRef.current) return
        hostRef.current.innerHTML = svg
        const nodes = hostRef.current.querySelectorAll('g.node')
        nodes.forEach((node) => {
          const idAttr = node.id || ''
          const m = idAttr.match(/(?:^|-)S(\d+)(?:-|$)/i) || idAttr.match(/S(\d+)/)
          if (!m) return
          const stepNo = Number(m[1])
          const c = bindNode(node, stepNo)
          if (c) cleanups.push(c)
        })
      } catch (e) {
        if (!cancelled) {
          setError(e?.message || t('ui.renderError'))
          if (hostRef.current) hostRef.current.innerHTML = ''
        }
      }
    }
    run()
    return () => {
      cancelled = true
      cleanups.forEach((c) => c())
    }
  }, [text, reactId, bindNode, t])

  useEffect(() => {
    const onDoc = (e) => {
      if (e.key === 'Escape') clearTip()
    }
    document.addEventListener('keydown', onDoc)
    return () => document.removeEventListener('keydown', onDoc)
  }, [clearTip])

  return (
    <div className="process-diagram">
      {title ? <div className="process-diagram-title">{title}</div> : null}
      <p className="muted process-tip-hint">{t('ui.hoverHint')}</p>
      <div
        className="process-mermaid-render"
        ref={hostRef}
        aria-label={t('ui.diagramAria')}
      />
      {activeStep ? <TipPopover step={activeStep} onClose={clearTip} /> : null}
      {ordered.length > 0 && (
        <div className="process-step-strip" role="list">
          {ordered.map((s, i) => {
            const tipActive = Number(activeStepNo) === Number(s.stepNo)
            const statusActive =
              highlightStatus != null &&
              highlightStatus !== '' &&
              s.outcomeStatus != null &&
              String(s.outcomeStatus).toUpperCase() === String(highlightStatus).toUpperCase()
            return (
              <button
                type="button"
                key={s.id || s.stepNo || i}
                className={[
                  'process-step-node',
                  tipActive || statusActive ? 'is-active' : '',
                  statusActive ? 'is-status-here' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                role="listitem"
                aria-current={statusActive ? 'step' : undefined}
                onMouseEnter={() => {
                  if (!pinned) showStep(s.stepNo)
                }}
                onMouseLeave={() => {
                  if (!pinned) setActiveStepNo(null)
                }}
                onFocus={() => showStep(s.stepNo)}
                onClick={() => {
                  if (pinned && Number(activeStepNo) === Number(s.stepNo)) clearTip()
                  else showStep(s.stepNo, { pin: true })
                }}
              >
                <div className="process-step-no">#{s.stepNo ?? i + 1}</div>
                <div className="process-step-name">{s.name}</div>
                {s.outcomeStatus && (
                  <div className="process-step-outcome mono">{s.outcomeStatus}</div>
                )}
                {i < ordered.length - 1 && (
                  <div className="process-step-arrow" aria-hidden>
                    →
                  </div>
                )}
              </button>
            )
          })}
        </div>
      )}
      {error && (
        <p className="muted" style={{ fontSize: '0.8rem', width: '100%' }}>
          {error}
        </p>
      )}
    </div>
  )
}
