/**
 * Shared frame annotation for demo walkthrough captures.
 * Caption bar + cursor rings + amber hint bubble with word-wrapped text inside the box.
 */
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'

const BUBBLE_WIDTH = 520
const BUBBLE_PAD_X = 16
const BUBBLE_PAD_Y = 14
const LINE_HEIGHT = 26
const POINTSIZE = 20
const MAX_CHARS_PER_LINE = 42
const MAX_LINES = 5

/** Word-wrap hint into lines that fit the bubble width (char estimate). */
export function wrapHint(text, maxChars = MAX_CHARS_PER_LINE, maxLines = MAX_LINES) {
  const raw = String(text || '').replace(/\s+/g, ' ').trim()
  if (!raw) return []
  const words = raw.split(' ')
  const lines = []
  let cur = ''
  for (const w of words) {
    const next = cur ? `${cur} ${w}` : w
    if (next.length <= maxChars) {
      cur = next
    } else {
      if (cur) lines.push(cur)
      // hard-break overlong tokens
      if (w.length > maxChars) {
        let rest = w
        while (rest.length > maxChars && lines.length < maxLines) {
          lines.push(rest.slice(0, maxChars))
          rest = rest.slice(maxChars)
        }
        cur = rest
      } else {
        cur = w
      }
    }
    if (lines.length >= maxLines) {
      cur = ''
      break
    }
  }
  if (cur && lines.length < maxLines) lines.push(cur)
  if (lines.length === maxLines && (cur || words.join(' ').length > lines.join(' ').length)) {
    const last = lines[maxLines - 1]
    lines[maxLines - 1] = (last.length > maxChars - 1 ? last.slice(0, maxChars - 1) : last) + '…'
  }
  return lines
}

function escDraw(s) {
  return String(s).replace(/\\/g, '\\\\').replace(/'/g, "'\\''")
}

/**
 * Annotate a raw screenshot → outPath.
 * @returns {{ cx:number, cy:number, lines:string[] }}
 */
export function annotateFrame(rawPath, outPath, { step, title, hint, cx = 720, cy = 450 }) {
  const caption = escDraw(`${step}. ${title}`)
  const lines = wrapHint(hint)
  const bubbleInnerH = lines.length ? lines.length * LINE_HEIGHT + BUBBLE_PAD_Y * 2 : 0
  const bubbleH = bubbleInnerH
  const bubbleW = BUBBLE_WIDTH

  // Keep bubble fully on 1440×900 canvas
  let bx = Math.min(Math.max(cx - 40, 24), 1440 - bubbleW - 24)
  let by = cy + 36
  if (by + bubbleH > 880) by = Math.max(90, cy - bubbleH - 36)
  if (by < 84) by = 84

  const args = [
    rawPath,
    '-fill', 'rgba(15,23,42,0.88)',
    '-draw', 'rectangle 0,0 1440,72',
    '-fill', 'white',
    '-font', 'DejaVu-Sans-Bold',
    '-pointsize', '26',
    '-draw', `text 24,48 '${caption}'`,
    '-fill', 'rgba(59,130,246,0.35)',
    '-stroke', '#2563eb',
    '-strokewidth', '3',
    '-draw', `circle ${cx},${cy} ${cx + 18},${cy}`,
    '-fill', 'none',
    '-stroke', '#ef4444',
    '-strokewidth', '4',
    '-draw', `circle ${cx},${cy} ${cx + 8},${cy}`,
  ]

  if (lines.length) {
    const x2 = bx + bubbleW
    const y2 = by + bubbleH
    args.push(
      '-fill', 'rgba(254,243,199,0.97)',
      '-stroke', '#b45309',
      '-strokewidth', '2',
      '-draw', `roundrectangle ${bx},${by} ${x2},${y2} 12,12`,
      '-fill', '#78350f',
      '-stroke', 'none',
      '-font', 'DejaVu-Sans',
      '-pointsize', String(POINTSIZE),
    )
    lines.forEach((line, i) => {
      const ty = by + BUBBLE_PAD_Y + (i + 1) * LINE_HEIGHT - 6
      args.push('-draw', `text ${bx + BUBBLE_PAD_X},${ty} '${escDraw(line)}'`)
    })
  }

  args.push(outPath)
  try {
    execFileSync('convert', args, { stdio: 'pipe' })
  } catch (e) {
    // Fallback: caption via temporary caption image (better wrap) + composite
    try {
      annotateWithCaptionComposite(rawPath, outPath, { step, title, hint, cx, cy, bx, by, bubbleW })
    } catch (e2) {
      fs.copyFileSync(rawPath, outPath)
      console.warn('annotate failed', e.message, e2.message)
    }
  }
  return { cx, cy, lines }
}

function annotateWithCaptionComposite(rawPath, outPath, { step, title, hint, cx, cy, bx, by, bubbleW }) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'steakos-ann-'))
  const bar = path.join(tmp, 'bar.png')
  const bub = path.join(tmp, 'bub.png')
  const base = path.join(tmp, 'base.png')

  execFileSync(
    'convert',
    [
      rawPath,
      '-fill', 'rgba(15,23,42,0.88)',
      '-draw', 'rectangle 0,0 1440,72',
      '-fill', 'white',
      '-font', 'DejaVu-Sans-Bold',
      '-pointsize', '26',
      '-draw', `text 24,48 '${escDraw(`${step}. ${title}`)}'`,
      '-fill', 'rgba(59,130,246,0.35)',
      '-stroke', '#2563eb',
      '-strokewidth', '3',
      '-draw', `circle ${cx},${cy} ${cx + 18},${cy}`,
      '-fill', 'none',
      '-stroke', '#ef4444',
      '-strokewidth', '4',
      '-draw', `circle ${cx},${cy} ${cx + 8},${cy}`,
      base,
    ],
    { stdio: 'pipe' },
  )

  if (hint) {
    execFileSync(
      'convert',
      [
        '-background', 'rgba(254,243,199,0.97)',
        '-fill', '#78350f',
        '-font', 'DejaVu-Sans',
        '-pointsize', String(POINTSIZE),
        '-size', `${bubbleW - 8}x`,
        `caption:${hint}`,
        '-bordercolor', '#b45309',
        '-border', '3',
        bub,
      ],
      { stdio: 'pipe' },
    )
    execFileSync('convert', [base, bub, '-geometry', `+${bx}+${by}`, '-composite', outPath], { stdio: 'pipe' })
  } else {
    fs.copyFileSync(base, outPath)
  }

  fs.rmSync(tmp, { recursive: true, force: true })
}

/** GIF delay centiseconds / MP4 seconds — prior 1.2s/1.4s + 1.0s. */
export const FRAME_DELAY_CS = 220 // 2.2s for ImageMagick -delay
export const FRAME_DURATION_SEC = 2.4

export function buildGifMp4(frameFiles, { gifPath, mp4Path, listFile }) {
  const gifArgs = []
  for (const f of frameFiles) {
    gifArgs.push('-delay', String(FRAME_DELAY_CS), f)
  }
  gifArgs.push('-loop', '0', gifPath)
  console.log(`Building GIF (${FRAME_DELAY_CS / 100}s/frame)…`)
  execFileSync('convert', gifArgs, { stdio: 'inherit' })

  const concat =
    frameFiles.map((f) => `file '${f}'\nduration ${FRAME_DURATION_SEC}`).join('\n') +
    `\nfile '${frameFiles[frameFiles.length - 1]}'\n`
  fs.writeFileSync(listFile, concat)
  console.log(`Building MP4 (${FRAME_DURATION_SEC}s/frame)…`)
  execFileSync(
    'ffmpeg',
    [
      '-y',
      '-f',
      'concat',
      '-safe',
      '0',
      '-i',
      listFile,
      '-vf',
      'scale=1440:900:force_original_aspect_ratio=decrease,pad=1440:900:(ow-iw)/2:(oh-ih)/2',
      '-pix_fmt',
      'yuv420p',
      mp4Path,
    ],
    { stdio: 'inherit' },
  )
}

export function resolveRepoRoot(scriptDir) {
  let dir = scriptDir
  for (let i = 0; i < 8; i++) {
    if (fs.existsSync(path.join(dir, 'demos')) && fs.existsSync(path.join(dir, 'docs'))) {
      return dir
    }
    if (fs.existsSync(path.join(dir, 'grails-app')) && fs.existsSync(path.join(dir, 'frontend'))) {
      return dir
    }
    const parent = path.dirname(dir)
    if (parent === dir) break
    dir = parent
  }
  return path.resolve(scriptDir, '../../..')
}

/** @param {'steakhouse'|'architects'} demo */
export function resolveDemoOutDir(scriptDir, demo) {
  return path.join(resolveRepoRoot(scriptDir), 'demos', demo)
}

/** @deprecated use resolveDemoOutDir(scriptDir, 'steakhouse'|'architects') */
export function resolveWalkthroughOutDir(scriptDir) {
  return resolveDemoOutDir(scriptDir, 'steakhouse')
}

