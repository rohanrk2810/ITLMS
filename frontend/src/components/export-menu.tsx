import { useState } from 'react'
import { ChevronDown, Download, FileSpreadsheet, FileText, Loader2, Printer } from 'lucide-react'
import { toast } from 'sonner'

import { assetUrl } from '@/api/branding'
import { apiErrorMessage } from '@/api/client'
import { type ExportedReport, noteDownload } from '@/api/reporting'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { type Report, letterheadOf } from '@/lib/report'
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
  const { branding, name } = useBranding()

  async function run(kind: Kind) {
    setBusy(true)
    try {
      const built = await build()
      if (built.rows.length === 0) {
        toast.info('There is nothing to download yet.')
        return
      }
      const letterhead = letterheadOf(branding, assetUrl(branding?.logoUrl ?? null), name)
      if (kind === 'xlsx') await saveXlsx(built, letterhead)
      else if (kind === 'csv') saveCsv(built)
      else await printReport(built, letterhead)
      toast.success(kind === 'pdf' ? 'In the print window, choose "Save as PDF" or a printer.' : `Downloaded ${built.rows.length} row${built.rows.length === 1 ? '' : 's'}`)
      if (report) {
        void noteDownload(report, built.rows.length, detail)
      }
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not prepare the report.'))
    } finally {
      setBusy(false)
    }
  }

  return (
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
  )
}
