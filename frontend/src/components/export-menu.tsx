import { useState } from 'react'
import { ChevronDown, Download, FileSpreadsheet, FileText, Loader2, Printer } from 'lucide-react'
import { toast } from 'sonner'

import { assetUrl } from '@/api/branding'
import { apiErrorMessage } from '@/api/client'
import { type ExportedReport, noteDownload } from '@/api/reporting'
import { ColumnDialog, initialPicks, saveColumns } from '@/components/column-dialog'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { type Report, letterheadOf, pickColumns } from '@/lib/report'
import { printReport, saveCsv, saveXlsx } from '@/lib/report-output'
import { useBranding } from '@/lib/use-branding'

type Kind = 'xlsx' | 'pdf' | 'csv'

/**
 * One button for every report: Excel and a printed PDF both carry the institute's letterhead (logo, name, address);
 * CSV is the plain data. `build` fetches the data and returns the report; the download is noted in the audit log
 * when `report` says what it is.
 */
export function ExportMenu({
  label = 'Download',
  report,
  detail,
  build,
  variant = 'outline',
}: {
  label?: string
  report?: ExportedReport
  detail?: string
  build: () => Promise<Report>
  variant?: 'outline' | 'ghost' | 'secondary'
}) {
  const [busy, setBusy] = useState(false)
  // A built report waiting for the person to choose its columns.
  const [choosing, setChoosing] = useState<{ kind: Kind; built: Report; picked: boolean[] } | null>(null)
  const { branding, name } = useBranding()

  async function run(kind: Kind) {
    setBusy(true)
    try {
      const built = await build()
      if (built.rows.length === 0) {
        toast.info('There is nothing to download yet.')
        return
      }
      if (built.header.length < 2) {
        await deliver(kind, built)
        return
      }
      setChoosing({ kind, built, picked: initialPicks(built.fileName, built.header) })
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not prepare the report.'))
    } finally {
      setBusy(false)
    }
  }

  async function confirm() {
    if (!choosing) return
    const { kind, built, picked } = choosing
    const keep = picked.flatMap((on, i) => (on ? [i] : []))
    saveColumns(built.fileName, keep.map((i) => built.header[i]))
    setChoosing(null)
    setBusy(true)
    try {
      await deliver(kind, pickColumns(built, keep))
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not prepare the report.'))
    } finally {
      setBusy(false)
    }
  }

  async function deliver(kind: Kind, built: Report) {
    const letterhead = letterheadOf(branding, assetUrl(branding?.logoUrl ?? null), name)
    if (kind === 'xlsx') await saveXlsx(built, letterhead)
    else if (kind === 'csv') saveCsv(built)
    else await printReport(built, letterhead)
    toast.success(kind === 'pdf' ? 'In the print window, choose "Save as PDF" or a printer.' : `Downloaded ${built.rows.length} row${built.rows.length === 1 ? '' : 's'}`)
    if (report) {
      void noteDownload(report, built.rows.length, detail)
    }
  }

  return (
    <>
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant={variant} size="sm" disabled={busy} className="no-print">
          {busy ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
          {busy ? 'Preparing...' : label}
          <ChevronDown className="size-3.5 opacity-60" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={() => void run('xlsx')}>
          <FileSpreadsheet className="size-4" /> Excel (.xlsx), with letterhead
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => void run('pdf')}>
          <Printer className="size-4" /> PDF or print, with letterhead
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => void run('csv')}>
          <FileText className="size-4" /> CSV (plain data)
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
    <ColumnDialog
      open={choosing !== null}
      headers={choosing?.built.header ?? []}
      picked={choosing?.picked ?? []}
      onPickedChange={(picked) => setChoosing((c) => (c ? { ...c, picked } : c))}
      onConfirm={() => void confirm()}
      onClose={() => setChoosing(null)}
    />
    </>
  )
}
