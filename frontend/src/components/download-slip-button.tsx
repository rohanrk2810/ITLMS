import { useState } from 'react'
import { DownloadIcon } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { downloadSlipPdf } from '@/api/payroll'
import { Button } from '@/components/ui/button'

/** Saves a salary slip as a PDF. */
export function DownloadSlipButton({ id, slipNo, label = 'Slip' }: { id: number; slipNo: string; label?: string }) {
  const [busy, setBusy] = useState(false)
  async function download() {
    setBusy(true)
    try {
      await downloadSlipPdf(id, slipNo)
    } catch (error) {
      toast.error(apiErrorMessage(error, 'The salary slip could not be downloaded.'))
    } finally {
      setBusy(false)
    }
  }
  return (
    <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => void download()}>
      <DownloadIcon className="size-3.5" /> {busy ? 'Preparing...' : label}
    </Button>
  )
}
