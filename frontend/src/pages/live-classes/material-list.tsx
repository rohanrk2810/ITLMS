import { type ReactNode, useCallback, useEffect, useState } from 'react'
import { Copy, Pencil, Send, Share2, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  type Difficulty,
  type LiveMaterial,
  type LiveQuestionType,
  type MaterialFilter,
  LIVE_QUESTION_TYPE_LABEL,
  copyMaterial,
  deleteMaterial,
  listMaterials,
  sendMaterial,
} from '@/api/live-classes'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

const DIFFICULTIES: Difficulty[] = ['EASY', 'MEDIUM', 'HARD']

/**
 * The trainer's question bank with search and filters. In a live class each item has a Send button; on the bank page
 * an own item can be edited or deleted and another trainer's shared one copied. {@code leading} puts something (a
 * checkbox, when picking questions for a test) before each row.
 */
export function MaterialList({ liveSessionId, refreshKey = 0, onSent, onEdit, leading, folder }: {
  /** Show only this folder and the ones beneath it. */
  folder?: string
  liveSessionId?: number
  refreshKey?: number
  onSent?: () => void
  onEdit?: (item: LiveMaterial) => void
  leading?: (item: LiveMaterial) => ReactNode
}) {
  const [items, setItems] = useState<LiveMaterial[] | null>(null)
  const [scope, setScope] = useState<MaterialFilter['scope']>(undefined)
  const [query, setQuery] = useState('')
  const [tag, setTag] = useState('')
  const [difficulty, setDifficulty] = useState<Difficulty | ''>('')
  const [type, setType] = useState<LiveQuestionType | ''>('')

  const load = useCallback(() => {
    listMaterials({
      scope,
      query: query.trim() || undefined,
      tag: tag.trim() || undefined,
      difficulty: difficulty || undefined,
      folder: folder || undefined,
      type: type || undefined,
    })
      .then(setItems)
      .catch((err) => toast.error(apiErrorMessage(err, 'Could not load the question bank.')))
  }, [scope, query, tag, difficulty, folder, type])

  // Typing in the search box waits a moment so each keystroke is not a request.
  useEffect(() => {
    const timer = window.setTimeout(load, 250)
    return () => window.clearTimeout(timer)
  }, [load, refreshKey])

  async function send(item: LiveMaterial) {
    try {
      await sendMaterial(item.id, liveSessionId!)
      toast.success('Sent to the class')
      onSent?.()
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not send it.'))
    }
  }

  async function copy(item: LiveMaterial) {
    try {
      await copyMaterial(item.id)
      toast.success('Copied to your bank')
      load()
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not copy it.'))
    }
  }

  async function remove(item: LiveMaterial) {
    if (!window.confirm(`Delete "${item.title}"?`)) return
    try {
      await deleteMaterial(item.id)
      load()
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not delete it.'))
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-1">
        {([[undefined, 'All'], ['mine', 'Mine'], ['shared', 'Shared by others']] as const).map(([value, label]) => (
          <Button key={label} size="sm" variant={scope === value ? 'default' : 'outline'} onClick={() => setScope(value)}>
            {label}
          </Button>
        ))}
      </div>
      <div className="flex flex-wrap gap-2">
        <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search..." className="h-8 min-w-32 flex-1" />
        <Input value={tag} onChange={(e) => setTag(e.target.value)} placeholder="Tag" className="h-8 w-24" />
        <select
          value={type}
          onChange={(e) => setType(e.target.value as LiveQuestionType | '')}
          className="h-8 rounded-md border bg-transparent px-2 text-sm"
        >
          <option value="">Any format</option>
          {(Object.keys(LIVE_QUESTION_TYPE_LABEL) as LiveQuestionType[]).map((t) => (
            <option key={t} value={t}>{LIVE_QUESTION_TYPE_LABEL[t]}</option>
          ))}
        </select>
        <select
          value={difficulty}
          onChange={(e) => setDifficulty(e.target.value as Difficulty | '')}
          className="h-8 rounded-md border bg-transparent px-2 text-sm"
        >
          <option value="">Any level</option>
          {DIFFICULTIES.map((d) => (
            <option key={d} value={d}>{d.charAt(0) + d.slice(1).toLowerCase()}</option>
          ))}
        </select>
      </div>

      {items && items.length === 0 && (
        <p className="text-sm text-muted-foreground">Nothing here. Save a question to build your bank, or clear the filters.</p>
      )}
      <ul className="flex flex-col gap-1">
        {items?.map((item) => (
          <li key={item.id} className="flex items-center gap-2 rounded-md border px-2 py-1.5 text-sm">
            {leading?.(item)}
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{item.title}</p>
              <p className="flex flex-wrap items-center gap-x-1 text-xs text-muted-foreground">
                {LIVE_QUESTION_TYPE_LABEL[item.question.type]}
                {item.question.snippet && ' · code'}
                {item.question.dataset?.length ? ` · ${item.question.dataset.length} table(s)` : ''}
                {item.question.type === 'CODING' && item.question.language && ` · ${item.question.language}`}
                {item.question.testCases?.length ? ` · ${item.question.testCases.length} test case(s)` : ''}
                {item.folder && !folder && ` · 📁 ${item.folder}`}
                {item.topic && ` · ${item.topic}`}
                {item.difficulty && ` · ${item.difficulty.toLowerCase()}`}
                {!item.mine && item.ownerName && ` · by ${item.ownerName}`}
              </p>
              {(item.tags.length > 0 || item.shared) && (
                <div className="mt-0.5 flex flex-wrap gap-1">
                  {item.shared && item.mine && <Badge variant="secondary" className="gap-1 text-[10px]"><Share2 className="size-3" />Shared</Badge>}
                  {item.tags.map((t) => (
                    <Badge key={t} variant="outline" className="text-[10px]">{t}</Badge>
                  ))}
                </div>
              )}
            </div>
            {liveSessionId != null && (
              <Button size="sm" onClick={() => void send(item)}>
                <Send className="size-3.5" /> Send
              </Button>
            )}
            {item.mine && onEdit && (
              <Button size="icon" variant="ghost" title="Edit" onClick={() => onEdit(item)}>
                <Pencil className="size-4" />
              </Button>
            )}
            {!item.mine && liveSessionId == null && !leading && (
              <Button size="icon" variant="ghost" title="Copy to my bank" onClick={() => void copy(item)}>
                <Copy className="size-4" />
              </Button>
            )}
            {item.mine && !leading && (
              <Button size="icon" variant="ghost" title="Delete" onClick={() => void remove(item)}>
                <Trash2 className="size-4" />
              </Button>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
