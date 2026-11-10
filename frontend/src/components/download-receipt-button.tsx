import { useState } from 'react'
import { DownloadIcon } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { downloadReceiptPdf } from '@/api/finance'
import { Button } from '@/components/ui/button'

/** Saves a payment's receipt as a PDF. */
export function DownloadReceiptButton({
  paymentId,
  receiptNo,
  label = 'Download PDF',
  variant = 'outline',
}: {
  paymentId: number
  receiptNo: string
  label?: string
  variant?: 'outline' | 'ghost'
}) {
  const [busy, setBusy] = useState(false)

  async function download() {
    setBusy(true)
    try {
      await downloadReceiptPdf(paymentId, receiptNo)
    } catch (error) {
      toast.error(apiErrorMessage(error, 'The receipt could not be downloaded. Please try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Button type="button" size="sm" variant={variant} onClick={() => void download()} disabled={busy}>
      <DownloadIcon className="size-3.5" />
      {busy ? 'Preparing...' : label}
    </Button>
  )
}
