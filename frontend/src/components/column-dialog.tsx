import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'

const KEY = 'itilms.export-columns'

/** The columns last chosen for this report (by header name), so the next download starts from the same choice. */
export function savedColumns(fileName: string): string[] | null {
  try {
    const all = JSON.parse(localStorage.getItem(KEY) ?? '{}') as Record<string, string[]>
    return Array.isArray(all[fileName]) ? all[fileName] : null
  } catch {
    return null
  }
}

export function saveColumns(fileName: string, headers: string[]) {
  try {
    const all = JSON.parse(localStorage.getItem(KEY) ?? '{}') as Record<string, string[]>
    all[fileName] = headers
    localStorage.setItem(KEY, JSON.stringify(all))
  } catch {
    // the choice just is not remembered
  }
}

/** Which of `headers` start ticked: the saved choice for this report, or all of them. */
export function initialPicks(fileName: string, headers: string[]): boolean[] {
  const saved = savedColumns(fileName)
  const picked = headers.map((h) => (saved ? saved.includes(h) : true))
  // Nothing left from the saved choice (the report changed): start again from all of them.
  return picked.some(Boolean) ? picked : headers.map(() => true)
}

/** "Choose the columns" before a download: only the ticked columns go into the file. */
export function ColumnDialog({
  open,
  headers,
  picked,
  onPickedChange,
  onConfirm,
  onClose,
}: {
  open: boolean
  headers: string[]
  picked: boolean[]
  onPickedChange: (picked: boolean[]) => void
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Choose the columns</DialogTitle>
          <DialogDescription>Only the ticked columns go into the file.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <div className="flex gap-2">
            <Button type="button" size="sm" variant="outline" onClick={() => onPickedChange(headers.map(() => true))}>
              All
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={() => onPickedChange(headers.map(() => false))}>
              None
            </Button>
          </div>
          <div className="grid gap-2 sm:grid-cols-2">
            {headers.map((h, i) => (
              <div key={`${h}-${i}`} className="flex items-center gap-2">
                <Checkbox
                  id={`export-col-${i}`}
                  checked={picked[i]}
                  onCheckedChange={(on) => onPickedChange(picked.map((v, j) => (j === i ? on === true : v)))}
                />
                <Label htmlFor={`export-col-${i}`} className="font-normal">
                  {h || `Column ${i + 1}`}
                </Label>
              </div>
            ))}
          </div>
        </div>
        <DialogFooter>
          <Button type="button" onClick={onConfirm} disabled={!picked.some(Boolean)}>
            Download
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
