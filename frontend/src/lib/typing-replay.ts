/** One edit of a coding answer, as the student's browser recorded it. */
export interface ReplayEdit {
  /** When it happened, in ms since the epoch. */
  t: number
  offset: number
  /** Characters removed at `offset`. -1 means the whole text was replaced by `text` (the starting point). */
  removed: number
  text: string
  pasted: boolean
}

/** The edits of every batch, in order. A batch that cannot be read is skipped: a gap in the replay, not a failure. */
export function parseEdits(chunks: { seq: number; events: string }[]): ReplayEdit[] {
  const edits: ReplayEdit[] = []
  for (const chunk of [...chunks].sort((a, b) => a.seq - b.seq)) {
    let list: unknown
    try {
      list = JSON.parse(chunk.events)
    } catch {
      continue
    }
    if (!Array.isArray(list)) continue
    for (const item of list) {
      if (!Array.isArray(item) || item.length < 4) continue
      const [t, offset, removed, text, pasted] = item as [number, number, number, string, number?]
      if (typeof t !== 'number' || typeof offset !== 'number' || typeof removed !== 'number' || typeof text !== 'string') continue
      edits.push({ t, offset, removed, text, pasted: pasted === 1 })
    }
  }
  return edits
}

export function applyEdit(code: string, edit: ReplayEdit): string {
  if (edit.removed === -1) return edit.text
  const offset = Math.min(Math.max(edit.offset, 0), code.length)
  return code.slice(0, offset) + edit.text + code.slice(offset + Math.max(edit.removed, 0))
}

const CHECKPOINT_EVERY = 200

/** The text after the first `count` edits. Checkpoints keep dragging the slider quick for a long sitting. */
export class Replay {
  readonly edits: ReplayEdit[]
  private readonly checkpoints: string[] = []
  private readonly starter: string

  constructor(edits: ReplayEdit[], starter: string) {
    this.edits = edits
    this.starter = starter
    let code = starter
    this.checkpoints.push(code)
    edits.forEach((edit, i) => {
      code = applyEdit(code, edit)
      if ((i + 1) % CHECKPOINT_EVERY === 0) this.checkpoints.push(code)
    })
  }

  get length(): number {
    return this.edits.length
  }

  textAt(count: number): string {
    const n = Math.min(Math.max(count, 0), this.edits.length)
    const base = Math.floor(n / CHECKPOINT_EVERY)
    let code = this.checkpoints[base] ?? this.starter
    for (let i = base * CHECKPOINT_EVERY; i < n; i++) code = applyEdit(code, this.edits[i])
    return code
  }

  /** How long to wait before showing edit `index`, at normal speed: the real gap, but a long idle is cut short. */
  gapBefore(index: number, maxGapMs = 1500): number {
    if (index <= 0 || index >= this.edits.length) return 0
    return Math.min(Math.max(this.edits[index].t - this.edits[index - 1].t, 0), maxGapMs)
  }
}

export interface PasteMark {
  /** The edit's position, so the replay can jump to just after it. */
  index: number
  characters: number
  /** ms since the first edit. */
  at: number
}

export interface ReplayStats {
  edits: number
  /** Characters inserted by typing. */
  typed: number
  /** Characters inserted by pasting. */
  pasted: number
  pastes: PasteMark[]
  largestPaste: number
  /** First edit to last, in ms. */
  durationMs: number
}

/** A paste of at least this many characters is worth a trainer's eye; a few characters are a shortcut or a snippet. */
export const NOTABLE_PASTE = 40

export function statsOf(edits: ReplayEdit[]): ReplayStats {
  // The first edit is the snapshot of where the answer started: not typing, not a paste.
  const real = edits.filter((e) => e.removed !== -1)
  const first = edits.length > 0 ? edits[0].t : 0
  let typed = 0
  let pasted = 0
  const pastes: PasteMark[] = []
  edits.forEach((edit, index) => {
    if (edit.removed === -1 || edit.text.length === 0) return
    if (edit.pasted) {
      pasted += edit.text.length
      pastes.push({ index, characters: edit.text.length, at: edit.t - first })
    } else {
      typed += edit.text.length
    }
  })
  return {
    edits: real.length,
    typed,
    pasted,
    pastes,
    largestPaste: pastes.reduce((max, p) => Math.max(max, p.characters), 0),
    durationMs: edits.length > 1 ? edits[edits.length - 1].t - first : 0,
  }
}

/** The same code, ignoring the difference between Windows and Unix line ends and trailing space. */
export function sameCode(a: string | null | undefined, b: string | null | undefined): boolean {
  const clean = (s: string | null | undefined) => (s ?? '').replace(/\r\n/g, '\n').trimEnd()
  return clean(a) === clean(b)
}

export function clock(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor(total / 60) % 60
  const s = total % 60
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`
}
