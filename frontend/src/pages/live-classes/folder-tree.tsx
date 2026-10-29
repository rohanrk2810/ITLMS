import { useCallback, useEffect, useMemo, useState } from 'react'
import { Folder, FolderOpen, FolderPlus, Pencil, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type BankFolder, createFolder, deleteFolder, listFolders, renameFolder } from '@/api/live-classes'
import { Button } from '@/components/ui/button'

interface Node {
  path: string
  name: string
  depth: number
  /** Questions in this folder and every folder beneath it. */
  total: number
}

/** Flattens the folder paths into the rows of a tree, adding the levels that were only implied (Java for Java/OOPs). */
function buildRows(folders: BankFolder[]): Node[] {
  const direct = new Map(folders.map((f) => [f.path, f.count]))
  const paths = new Set<string>()
  for (const f of folders) {
    const parts = f.path.split('/')
    for (let i = 1; i <= parts.length; i++) paths.add(parts.slice(0, i).join('/'))
  }
  return [...paths]
    .sort((a, b) => a.localeCompare(b))
    .map((path) => {
      const parts = path.split('/')
      let total = 0
      for (const [p, count] of direct) if (p === path || p.startsWith(path + '/')) total += count
      return { path, name: parts[parts.length - 1], depth: parts.length - 1, total }
    })
}

/**
 * The bank's folders, like a file explorer. Picking a folder narrows the list beside it to that folder and
 * everything under it; "All questions" clears it.
 */
export function FolderTree({ selected, onSelect, refreshKey = 0, onChanged }: {
  selected: string
  onSelect: (path: string) => void
  refreshKey?: number
  onChanged?: () => void
}) {
  const [folders, setFolders] = useState<BankFolder[]>([])

  const load = useCallback(() => {
    listFolders().then(setFolders).catch(() => undefined)
  }, [])
  useEffect(load, [load, refreshKey])

  const rows = useMemo(() => buildRows(folders), [folders])
  const looseCount = useMemo(() => folders.reduce((sum, f) => sum + f.count, 0), [folders])

  async function add() {
    const name = window.prompt(selected ? `New folder inside ${selected}:` : 'New folder (e.g. Java/OOPs/Coding):')
    if (!name?.trim()) return
    try {
      const created = await createFolder(selected ? `${selected}/${name.trim()}` : name.trim())
      onSelect(created.path)
      load()
      onChanged?.()
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not make the folder.'))
    }
  }

  async function rename() {
    const name = window.prompt('Rename folder (the full path, so it can also be moved):', selected)
    if (!name?.trim() || name.trim() === selected) return
    try {
      await renameFolder(selected, name.trim())
      onSelect(name.trim())
      load()
      onChanged?.()
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not rename the folder.'))
    }
  }

  async function remove() {
    if (!window.confirm(`Delete the empty folder "${selected}"?`)) return
    try {
      await deleteFolder(selected)
      onSelect('')
      load()
      onChanged?.()
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not delete the folder.'))
    }
  }

  return (
    <div className="flex w-full shrink-0 flex-col gap-1 sm:w-52">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-muted-foreground">Folders</span>
        <div className="flex">
          <Button size="icon" variant="ghost" className="size-7" title="New folder" onClick={() => void add()}>
            <FolderPlus className="size-4" />
          </Button>
          <Button size="icon" variant="ghost" className="size-7" title="Rename / move" disabled={!selected} onClick={() => void rename()}>
            <Pencil className="size-4" />
          </Button>
          <Button size="icon" variant="ghost" className="size-7" title="Delete empty folder" disabled={!selected} onClick={() => void remove()}>
            <Trash2 className="size-4" />
          </Button>
        </div>
      </div>
      <button
        type="button"
        onClick={() => onSelect('')}
        className={`flex items-center gap-2 rounded-md px-2 py-1 text-left text-sm hover:bg-muted ${selected === '' ? 'bg-muted font-medium' : ''}`}
      >
        <FolderOpen className="size-4 shrink-0" /> All questions
      </button>
      {rows.map((row) => (
        <button
          key={row.path}
          type="button"
          onClick={() => onSelect(row.path)}
          style={{ paddingLeft: `${0.5 + row.depth * 0.9}rem` }}
          className={`flex items-center gap-2 rounded-md py-1 pr-2 text-left text-sm hover:bg-muted ${selected === row.path ? 'bg-muted font-medium' : ''}`}
        >
          <Folder className="size-4 shrink-0 text-amber-500" />
          <span className="min-w-0 flex-1 truncate">{row.name}</span>
          <span className="text-xs text-muted-foreground">{row.total}</span>
        </button>
      ))}
      {rows.length === 0 && (
        <p className="px-2 text-xs text-muted-foreground">
          No folders yet. Make one, e.g. Java/OOPs/Coding, then file questions in it ({looseCount} loose).
        </p>
      )}
    </div>
  )
}
